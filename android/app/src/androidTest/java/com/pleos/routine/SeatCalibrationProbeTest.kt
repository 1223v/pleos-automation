package com.pleos.routine

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.Seat
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.MoveControl
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pleos.routine.core.InputSource
import com.pleos.routine.core.SeatAxis
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Raw SDK observation only. Does not unlock VehiclePlatform.seatPolicy or certify physical safety. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SeatCalibrationProbeTest {
    private data class Ack(val ok: Boolean, val at: Long, val detail: String)
    private data class Pulse(val moved: Boolean, val stopped: Boolean, val healthy: Boolean)
    private val area = SeatArea(SeatValue.ROW_1_LEFT)

    @Test fun foreAftForwardBackwardAndStop() = runBlocking { calibrate(SeatAxis.FORE_AFT) }
    @Test fun backrestForwardBackwardAndStop() = runBlocking { calibrate(SeatAxis.BACKREST) }

    private fun record(axis: SeatAxis, text: String) {
        Log.i(TAG, "axis=$axis area=ROW_1_LEFT elapsedRealtimeMs=${SystemClock.elapsedRealtime()} $text")
    }

    private suspend fun <T> query(block: ((T) -> Unit, (Exception) -> Unit) -> Unit): T =
        withTimeout(2000) {
            suspendCancellableCoroutine { c ->
                try {
                    block({ if (c.isActive) c.resume(it) }, { if (c.isActive) c.resumeWithException(it) })
                } catch (e: Exception) { if (c.isActive) c.resumeWithException(e) }
            }
        }

    private suspend fun position(seat: Seat, axis: SeatAxis): Int? = query { ok, fail ->
        if (axis == SeatAxis.FORE_AFT) seat.getCurrentSeatForeAftPosition(area, ok, fail)
        else seat.getCurrentSeatBackrestAnglePosition(area, ok, fail)
    }

    /** Dispatch returns immediately; awaiting this ACK must never delay the independent STOP deadline. */
    private fun dispatch(seat: Seat, axis: SeatAxis, direction: MoveControl): CompletableDeferred<Ack> {
        val done = CompletableDeferred<Ack>()
        record(axis, "REQUEST command=$direction")
        val success: (SeatArea, MoveControl?) -> Unit = { actual, value ->
            val ack = Ack(actual == area && value == direction, SystemClock.elapsedRealtime(), "areaMatch=${actual == area} value=$value")
            record(axis, "ACK command=$direction ok=${ack.ok} ${ack.detail}")
            done.complete(ack)
        }
        val failure: (Exception) -> Unit = { error ->
            val detail = "${error.javaClass.simpleName}:${error.message?.replace('\n', ' ')?.take(120)}"
            record(axis, "FAILED command=$direction error=$detail")
            done.complete(Ack(false, SystemClock.elapsedRealtime(), detail))
        }
        try {
            if (axis == SeatAxis.FORE_AFT) seat.moveSeatForeAft(area, direction, success, failure)
            else seat.moveSeatBackrestAngle(area, direction, success, failure)
        } catch (e: Exception) { failure(e) }
        return done
    }

    private suspend fun observeStop(seat: Seat, axis: SeatAxis, ack: Deferred<Ack>, samples: MutableList<Int>): Boolean {
        return try {
            withTimeout(4000) {
                val response = ack.await()
                if (!response.ok) return@withTimeout false
                repeat(3) { index ->
                    delay(150)
                    val value = position(seat, axis)
                    record(axis, "AFTER_STOP sample=${index + 1} rawPosition=$value unit=UNSPECIFIED")
                    if (value == null) return@withTimeout false
                    samples += value
                }
                samples.size == 3 && samples.distinct().size == 1
            }
        } catch (e: Exception) {
            record(axis, "STOP_UNKNOWN error=${e.javaClass.simpleName}")
            false
        }
    }

    private suspend fun pulse(app: RoutineApplication, seat: Seat, axis: SeatAxis, direction: MoveControl, bounds: AreaBounds<Int>): Pulse = coroutineScope {
        val before = position(seat, axis)
        record(axis, "BEFORE command=$direction rawPosition=$before range=${bounds.minValue}..${bounds.maxValue} unit=UNSPECIFIED")
        assertNotNull("Readable position required before MOVE", before)
        assertTrue("Position must be within observed capability", before!! in bounds.minValue..bounds.maxValue)
        val x = app.platform.snapshot()
        assertTrue("Fresh actual P, zero speed and unrestricted UX required", x.source == InputSource.REAL && x.parkedAndStationary())
        assertFalse("No product routine may run during calibration", app.runtime.busy.value)
        val journal = app.getSharedPreferences(JOURNAL, Context.MODE_PRIVATE)
        assertTrue("Calibration journal must be durable before MOVE", journal.edit().putString("axis", axis.name).commit())

        val sentAt = SystemClock.elapsedRealtime()
        val stopSentAt = AtomicLong(-1)
        val stopAck = CompletableDeferred<Ack>()
        var moveAck: Deferred<Ack>? = null
        var safeThroughout = true
        var queryHealthy = true
        val during = mutableListOf<Int>()
        val after = mutableListOf<Int>()
        var stopped = false
        fun stopNow(reason: String) {
            val now = SystemClock.elapsedRealtime()
            if (stopSentAt.compareAndSet(-1, now)) {
                record(axis, "STOP_DEADLINE reason=$reason sinceMoveMs=${now - sentAt} plannedMs=$MOVE_WINDOW_MS maximumMs=$MAX_MOVE_MS")
                // STOP bypasses state, ordinary movement gates and MOVE-ACK waiting.
                val response = dispatch(seat, axis, MoveControl.STOP)
                response.invokeOnCompletion { cause ->
                    if (cause != null) stopAck.complete(Ack(false, SystemClock.elapsedRealtime(), cause.javaClass.simpleName))
                    else stopAck.complete(response.getCompleted())
                }
            }
        }
        // Separate dispatcher: slow position queries or MOVE callbacks cannot consume the STOP budget.
        val watchdog = launch(Dispatchers.Default) {
            delay((sentAt + MOVE_WINDOW_MS - SystemClock.elapsedRealtime()).coerceAtLeast(0))
            stopNow("deadline")
        }
        try {
            moveAck = dispatch(seat, axis, direction)
            withTimeoutOrNull((sentAt + MOVE_WINDOW_MS - SystemClock.elapsedRealtime()).coerceAtLeast(1)) {
                while (isActive && stopSentAt.get() < 0) {
                    if (!app.platform.snapshot().parkedAndStationary()) {
                        safeThroughout = false
                        stopNow("parking-or-UX-lost")
                        break
                    }
                    delay(50)
                    val value = position(seat, axis)
                    record(axis, "DURING command=$direction rawPosition=$value unit=UNSPECIFIED")
                    if (value == null || value !in bounds.minValue..bounds.maxValue) {
                        queryHealthy = false
                        stopNow("position-unavailable")
                        break
                    }
                    during += value
                }
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            queryHealthy = false
            record(axis, "OBSERVATION_FAILED error=${e.javaClass.simpleName}")
        } finally {
            withContext(NonCancellable) {
                stopNow("finally")
                stopped = observeStop(seat, axis, stopAck, after)
                watchdog.cancelAndJoin()
            }
        }
        val ack = withTimeoutOrNull(1000) { moveAck?.await() }
        val timelyAck = ack?.ok == true && ack.at <= stopSentAt.get()
        val timelyStop = stopSentAt.get() - sentAt in 0..MAX_MOVE_MS
        val withinBounds = after.all { it in bounds.minValue..bounds.maxValue }
        val moved = (during + after).any { it != before }
        val healthy = safeThroughout && queryHealthy && timelyAck && timelyStop && withinBounds
        // Missing/late MOVE ACK can reflect the SDK reconnect queue. Stable samples alone do
        // not prove that a queued MOVE cannot arrive later; retain the recovery-only journal.
        if (stopped && timelyAck && timelyStop && withinBounds) {
            if (!journal.edit().clear().commit()) stopped = false
        } else stopped = false
        record(axis, "RESULT command=$direction moved=$moved stopObserved=$stopped healthy=$healthy timelyMoveAck=$timelyAck timelyStop=$timelyStop before=$before during=$during after=$after verdict=${if (moved && stopped && healthy) "PASS" else "FAIL_OR_UNKNOWN"}")
        Pulse(moved, stopped, healthy)
    }

    private suspend fun calibrate(axis: SeatAxis) {
        assumeTrue("Explicit emulator-only argument required", InstrumentationRegistry.getArguments().getString("seatCalibration") == "emulator-only")
        assumeTrue("Official emulator hardware and fingerprint required", Build.HARDWARE == "ranchu" &&
            (Build.FINGERPRINT.startsWith("Pleos/crp_avd/") || Build.FINGERPRINT.contains("generic")))
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RoutineApplication
        assertTrue("Automotive emulator required", app.platform.automotive)
        val vehicle = Vehicle(app)
        vehicle.initialize()
        try {
            delay(3000)
            val seat = vehicle.getSeat()
            val journal = app.getSharedPreferences(JOURNAL, Context.MODE_PRIVATE)
            journal.getString("axis", null)?.let { pending ->
                val interrupted = SeatAxis.valueOf(pending)
                record(interrupted, "RECOVERY_ONLY previous probe has unresolved MOVE; no new MOVE allowed")
                withContext(NonCancellable) {
                    observeStop(seat, interrupted, dispatch(seat, interrupted, MoveControl.STOP), mutableListOf())
                }
                fail("Unresolved prior calibration; STOP attempted. Inspect logs and SDK queue before a new probe.")
            }
            assertFalse("Stop product routines before calibration", app.runtime.busy.value)
            val x = app.platform.snapshot()
            record(axis, "REAL source=${x.source} gear=${x.gear.value} speedMps=${x.speedMps.value} uxRestricted=${x.uxRestricted}")
            assertTrue("Actual P, zero speed and unrestricted UX required", x.source == InputSource.REAL && x.parkedAndStationary())
            val move = query<Pair<CapabilityConfig, List<SeatArea>>> { ok, fail ->
                val done: (CapabilityConfig, List<SeatArea>) -> Unit = { c, areas -> ok(c to areas) }
                if (axis == SeatAxis.FORE_AFT) seat.checkMoveSeatForeAftCapabilities(done, fail)
                else seat.checkMoveSeatBackrestAngleCapabilities(done, fail)
            }
            val position = query<Pair<CapabilityConfig, Map<SeatArea, AreaBounds<Int>>>> { ok, fail ->
                val done: (CapabilityConfig, Map<SeatArea, AreaBounds<Int>>) -> Unit = { c, areas -> ok(c to areas) }
                if (axis == SeatAxis.FORE_AFT) seat.checkSeatForeAftPositionCapabilities(done, fail)
                else seat.checkSeatBackrestAnglePositionCapabilities(done, fail)
            }
            record(axis, "CAP moveAccess=${move.first.accessType} moveArea=${area in move.second} positionAccess=${position.first.accessType} positionBounds=${position.second[area]}")
            assertEquals("MOVE must support READ_WRITE", AccessType.READ_WRITE, move.first.accessType)
            assertTrue("Requested single seat must support MOVE", area in move.second)
            assertTrue("Position must be readable", position.first.accessType in setOf(AccessType.READ, AccessType.READ_WRITE))
            val bounds = position.second[area]
            assertNotNull("Same seat must expose position bounds", bounds)
            assertTrue("Position range must be nonempty", bounds!!.minValue < bounds.maxValue)
            val results = mutableListOf<Pulse>()
            for (direction in listOf(MoveControl.FORWARD, MoveControl.BACKWARD)) {
                val result = pulse(app, seat, axis, direction, bounds)
                results += result
                assertTrue("STOP or command ordering unknown; no further movement allowed", result.stopped)
                assertTrue("Safety or deadline evidence failed; no further movement allowed", result.healthy)
            }
            assertTrue("Both directions must show raw position changes; ACK alone is insufficient", results.all { it.moved })
        } catch (e: Throwable) {
            record(axis, "TEST_FAILED_OR_SKIPPED type=${e.javaClass.simpleName} detail=${e.message?.replace('\n', ' ')?.take(160)}")
            throw e
        } finally {
            vehicle.release()
        }
    }

    companion object {
        private const val TAG = "RoutineSeatEvidence"
        private const val JOURNAL = "seat_calibration_probe"
        private const val MOVE_WINDOW_MS = 250L
        private const val MAX_MOVE_MS = 300L
    }
}
