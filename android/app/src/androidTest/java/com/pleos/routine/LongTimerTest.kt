package com.pleos.routine

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pleos.routine.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Deliberately opt-in: measures real 30 minutes, never an accelerated clock. */
@RunWith(AndroidJUnit4::class)
class LongTimerTest {
    @Test fun reportPersistedThirtyMinuteResult():Unit=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("longTimerReport")=="true")
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RoutineApplication
        val row=withContext(Dispatchers.IO) { app.database.routines().observeRuns().first().first { it.routineId.startsWith("long-timer-") && it.state=="COMPLETED" } }
        val result=WorkflowJson.format.decodeFromString<RunResult>(row.result!!)
        assertFalse(result.simulation)
        val end=result.events.single { it.status==ActionStatus.VERIFIED && it.message=="타이머 완료" }
        val start=result.events.single { it.nodeId==end.nodeId && it.status==ActionStatus.PENDING }
        val duration=end.elapsedMillis-start.elapsedMillis
        assertTrue(duration>=1_800_000)
        val evidence="PERSISTED_LONG_TIMER routine=${row.routineId} timerMs=$duration totalRunMs=${result.elapsedMillis} journal=${row.state} simulation=${row.simulation}\n"
        InstrumentationRegistry.getInstrumentation().sendStatus(2,Bundle().apply { putString("stream",evidence) })
    }
    @Test fun realThirtyMinuteTimerAndOwnAudio():Unit=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("longTimer")=="1800")
        assumeTrue(Build.HARDWARE=="ranchu" && Build.FINGERPRINT.startsWith("Pleos/crp_avd/"))
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RoutineApplication
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try {
            delay(2500)
            assertFalse(app.platform.snapshot().uxRestricted)
            assertFalse(app.runtime.busy.value)
            val blank=WorkflowExamples.blank("long-timer-"+UUID.randomUUID())
            val audio=GraphEditing.insert(blank,blank.edges.single().id,NodeKind.ACTION,ActionSpec(ActionType.PLAY_LOCAL_AUDIO,targetId="rest-tone"))
            val audioNode=audio.nodes.single { it.kind==NodeKind.ACTION }
            val graph=GraphEditing.insert(audio,audio.edges.single { it.source==audioNode.id }.id,NodeKind.TIMER)
            val timer=graph.nodes.single { it.kind==NodeKind.TIMER }
            val w=graph.copy(nodes=graph.nodes.map { if(it.id==timer.id)it.copy(durationSeconds=1800) else it })
            withContext(Dispatchers.Main) { app.runtime.requestRun(w) }
            withTimeout(15000) { while(app.runtime.events.value.none { it.nodeId==timer.id && it.status==ActionStatus.PENDING })delay(50) }
            val started=SystemClock.elapsedRealtime()
            Log.i("RoutineTimerEvidence","START routine=${w.id} durationSeconds=1800 elapsedRealtimeMs=$started actualAudio=${withContext(Dispatchers.Main) { app.runtime.isOwnAudioPlaying }}")
            scenario.moveToState(Lifecycle.State.CREATED)
            assertFalse(app.runtime.visible)
            var lastMinute=-1L
            withTimeout(1_920_000) {
                while(app.runtime.busy.value) {
                    val elapsed=SystemClock.elapsedRealtime()-started
                    val minute=elapsed/60_000
                    val timerDone=app.runtime.events.value.any { it.nodeId==timer.id && it.status==ActionStatus.VERIFIED }
                    if(!timerDone)assertTrue("Own audio must remain playing before timer completion",withContext(Dispatchers.Main) { app.runtime.isOwnAudioPlaying })
                    if(minute!=lastMinute) {
                        Log.i("RoutineTimerEvidence","PROGRESS elapsedMs=$elapsed activityVisible=${app.runtime.visible} timerDone=$timerDone")
                        lastMinute=minute
                    }
                    delay(1000)
                }
            }
            val result=app.runtime.lastResult.value!!
            assertEquals(RunStatus.COMPLETED,result.status)
            assertFalse(result.simulation)
            val events=result.events.filter { it.nodeId==timer.id }
            val duration=events.single { it.status==ActionStatus.VERIFIED }.elapsedMillis-events.single { it.status==ActionStatus.PENDING }.elapsedMillis
            assertTrue("Timer must wait at least 1800 real seconds",duration>=1_800_000)
            assertFalse("Engine must release its audio on completion",withContext(Dispatchers.Main) { app.runtime.isOwnAudioPlaying })
            val row=withContext(Dispatchers.IO) { app.database.routines().observeRuns().first().first { it.routineId==w.id } }
            assertEquals("COMPLETED",row.state)
            Log.i("RoutineTimerEvidence","PASS routine=${w.id} engineTimerMs=$duration measuredSincePendingMs=${SystemClock.elapsedRealtime()-started} totalRunMs=${result.elapsedMillis} audioReleased=true journal=${row.state}")
        } finally {
            withContext(Dispatchers.Main) { app.runtime.cancel() }
            scenario.close()
        }
    }
}
