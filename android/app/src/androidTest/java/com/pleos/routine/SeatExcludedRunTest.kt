package com.pleos.routine

import android.os.Build
import android.util.Log
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pleos.routine.core.*
import com.pleos.routine.data.RunHistory
import com.pleos.routine.execution.SeatRunAuthorization
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Explicitly selected emulator-only test. With no seat move/STOP calibration, a routine that contains a seat
 * step may run for real only after the user agrees to leave that step unexecuted. No seat command is sent.
 */
@RunWith(AndroidJUnit4::class)
class SeatExcludedRunTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val seat=ActionSpec(ActionType.MOVE_SEAT_LIMITED,area="ROW_1_LEFT",axis=SeatAxis.BACKREST,direction=MoveDirection.BACKWARD)
    private fun workflow(area:String,first:Double,second:Double):WorkflowDefinition {
        val light=ActionSpec(ActionType.SET_AMBIENT_BRIGHTNESS,area=area,value=first)
        return WorkflowDefinition(id="seat-excluded-"+UUID.randomUUID(),title="시트 제외 실행 시험",nodes=listOf(
            WorkflowNode("start",NodeKind.START,"시작"),
            WorkflowNode("before",NodeKind.ACTION,"조명 먼저",action=light),
            WorkflowNode("seat",NodeKind.ACTION,"시험 등받이",action=seat),
            WorkflowNode("after",NodeKind.ACTION,"조명 나중",action=light.copy(value=second)),
            WorkflowNode("end",NodeKind.END,"완료")),
            edges=listOf(WorkflowEdge("1","start","before"),WorkflowEdge("2","before","seat"),
                WorkflowEdge("3","seat","after"),WorkflowEdge("4","after","end")))
    }

    @Test fun consentedRunVerifiesLightsAndRecordsSeatAsUnexecutedWhileDecliningChangesNothing():Unit=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("vehicleWrites")=="emulator-only")
        assumeTrue(Build.HARDWARE=="ranchu" && Build.FINGERPRINT.startsWith("Pleos/crp_avd/"))
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RoutineApplication
        assumeTrue(!app.runtime.busy.value && withContext(Dispatchers.IO) { app.database.routines().enabled().isEmpty() })
        val created=mutableListOf<String>()
        var area:String?=null
        var original:Double?=null
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        suspend fun finished(w:WorkflowDefinition):RunResult {
            withTimeout(60_000) { while(app.runtime.lastResult.value?.workflowId!=w.id || app.runtime.busy.value)delay(100) }
            return app.runtime.lastResult.value!!
        }
        fun dialogShown() {
            compose.waitUntil(15_000) { compose.onAllNodesWithText(SeatRunAuthorization.EXCLUSION_TITLE).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("시험 등받이",substring=true).assertExists()
            compose.onNodeWithText("시트 명령은 전송하지 않습니다",substring=true).assertExists()
        }
        try {
            delay(3000)
            assertTrue("Actual P, zero speed and no UX restriction required",app.platform.snapshot().parkedAndStationary())
            app.platform.refreshCapabilities()
            assertNull("This test covers the uncalibrated product state only",app.platform.seatApprovalPlan(listOf(seat)))
            area=app.platform.capabilities.value.single { it.type==ActionType.SET_AMBIENT_BRIGHTNESS }.areas.single()
            val probe=ActionSpec(ActionType.SET_AMBIENT_BRIGHTNESS,area=area!!)
            original=app.platform.observedValue(probe)
            val first=if(original==20.0)40.0 else 20.0
            val second=if(original==30.0)50.0 else 30.0

            // Declining: nothing may be requested or changed.
            val declined=workflow(area!!,first,second);created+=declined.id
            withContext(Dispatchers.Main) { app.runtime.requestRun(declined) }
            dialogShown();compose.onNodeWithText("취소").performClick()
            val cancelled=finished(declined)
            assertEquals(RunStatus.CANCELLED,cancelled.status)
            assertTrue(cancelled.events.toString(),cancelled.events.none { it.status in setOf(ActionStatus.REQUESTED,ActionStatus.ACKNOWLEDGED,ActionStatus.VERIFIED,ActionStatus.SKIPPED) })
            assertEquals("A declined run must not change the vehicle",original,app.platform.observedValue(probe))

            // Consenting: lights are requested and observed, the seat step is recorded as unexecuted.
            val accepted=workflow(area!!,first,second);created+=accepted.id
            withContext(Dispatchers.Main) { app.runtime.requestRun(accepted) }
            dialogShown();compose.onNodeWithText("확인").performClick()
            val result=finished(accepted)
            Log.i("RoutineEvidence","SEAT_EXCLUDED status=${result.status} events="+result.events.joinToString(" | ") { "${it.nodeId}:${it.status}:${it.observedValue ?: ""}" })
            assertEquals(result.events.toString(),RunStatus.COMPLETED,result.status)
            val seatEvents=result.events.filter { it.nodeId=="seat" }
            assertEquals("The seat step must have exactly one unexecuted record and no request",listOf(ActionStatus.SKIPPED),seatEvents.map { it.status })
            assertEquals(SeatRunAuthorization.EXCLUDED_MESSAGE,seatEvents.single().message)
            for((node,value) in listOf("before" to first,"after" to second)) {
                val verified=result.events.single { it.nodeId==node && it.status==ActionStatus.VERIFIED }
                assertEquals("$node observed value",value,verified.observedValue?.toDouble())
            }
            assertEquals(second,app.platform.observedValue(probe))
            assertEquals("미실행 단계 1개 · 시험 등받이",RunHistory.unexecuted(accepted,result.events))
            val row=withContext(Dispatchers.IO) { app.database.routines().run(result.runId) }
            assertEquals("COMPLETED",row?.state);assertEquals(false,row?.simulation)
            Log.i("RoutineEvidence","SEAT_EXCLUDED area=$area original=$original first=$first second=$second observed=${app.platform.observedValue(probe)} journal=${row?.state} seat=${seatEvents.single().status}")
        } finally {
            withContext(Dispatchers.Main) { app.runtime.cancel() }
            withTimeoutOrNull(15_000) { while(app.runtime.busy.value)delay(100) }
            val restore=original;val target=area
            if(restore!=null && target!=null)runCatching {
                withContext(Dispatchers.Main) { app.platform.execute(ActionSpec(ActionType.SET_AMBIENT_BRIGHTNESS,area=target,value=restore)) { } }
            }
            scenario.close()
            withContext(Dispatchers.IO) {
                val sql=app.database.openHelper.writableDatabase
                created.forEach { sql.execSQL("DELETE FROM runs WHERE routineId=?",arrayOf(it)) }
            }
        }
    }
}
