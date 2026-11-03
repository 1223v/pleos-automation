package com.pleos.routine.execution

import com.pleos.routine.core.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SeatRunAuthorizationTest {
    private fun graph():WorkflowDefinition {
        val b=WorkflowExamples.rest()
        return b.copy(nodes=b.nodes+WorkflowNode("second-seat",NodeKind.ACTION,"Other seat",action=ActionSpec(ActionType.MOVE_SEAT_LIMITED,area="ROW_1_RIGHT",axis=SeatAxis.BACKREST,direction=MoveDirection.FORWARD)))
    }
    private fun parked()=ExecutionContext(0,0,InputSource.REAL,StateValue("P",0,true,InputSource.REAL),StateValue(0.0,0,true,InputSource.REAL),uxRestricted=false)
    @Test fun everyRunAsksOnceForAllPotentialSeatActions()=runTest {
        var prompts=0;var collected=emptyList<ActionSpec>()
        repeat(2) {
            assertNull(SeatRunAuthorization.authorize(graph(),parked(),{ collected=it;"all axes limits" }) { _,detail -> assertTrue(detail.contains("all axes limits"));prompts++;true })
        }
        assertEquals(2,prompts);assertEquals(graph().nodes.count { it.action?.type==ActionType.MOVE_SEAT_LIMITED },collected.size)
    }
    @Test fun absentCalibrationOrMovingVehicleNeverPromptsOrAuthorizes()=runTest {
        val confirm:suspend(String,String)->Boolean={ _,_->error("must not ask") }
        assertEquals(ActionStatus.BLOCKED,SeatRunAuthorization.authorize(graph(),parked(),{null},confirm)?.status)
        assertEquals(ActionStatus.BLOCKED,SeatRunAuthorization.authorize(graph(),parked().copy(uxRestricted=true),{"plan"},confirm)?.status)
    }
    @Test fun decliningBlocksEntireWorkflowAndSeatlessDoesNotPrompt()=runTest {
        assertEquals(ActionStatus.CANCELLED,SeatRunAuthorization.authorize(graph(),parked(),{"plan"}) { _,_->false }?.status)
        assertNull(SeatRunAuthorization.authorize(WorkflowExamples.blank("w"),parked(),{error("no seat")}) { _,_->error("no seat") })
    }
    @Test fun lateConfirmationAfterCancellationCannotAuthorizeSideEffects()=runTest {
        var sideEffects=0
        val execution=launch {
            val denied=SeatRunAuthorization.authorize(graph(),parked(),{"verified plan"}) { _,_ ->
                // A UI callback can race with the execution's cancellation without suspending.
                currentCoroutineContext().cancel()
                true
            }
            if(denied==null)sideEffects++
        }
        execution.join()
        assertEquals("A late positive answer must not authorize a cancelled execution",0,sideEffects)
    }
    @Test fun calibrationPlanChangedWhileConfirmingCannotAuthorizeSideEffects()=runTest {
        var currentPlan:String?="ROW_1_LEFT FORE_AFT BACKWARD max 300ms evidence-v1"
        var sideEffects=0
        val denied=SeatRunAuthorization.authorize(graph(),parked(),{currentPlan}) { _,_ ->
            currentPlan="ROW_1_LEFT FORE_AFT BACKWARD max 500ms evidence-v2"
            true
        }
        if(denied==null)sideEffects++
        assertEquals("The answer applies only to the exact calibrated plan shown",0,sideEffects)
        assertEquals(ActionStatus.BLOCKED,denied?.status)
    }
}
