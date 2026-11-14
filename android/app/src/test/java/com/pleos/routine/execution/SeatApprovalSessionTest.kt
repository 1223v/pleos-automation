package com.pleos.routine.execution

import com.pleos.routine.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SeatApprovalSessionTest {
    private fun graph():WorkflowDefinition = WorkflowDefinition(
        id="seat-run", title="Approved seat plan", nodes=listOf(
            WorkflowNode("start",NodeKind.START,"start"),
            WorkflowNode("wait",NodeKind.WAIT,"wait",durationSeconds=1),
            WorkflowNode("audio",NodeKind.ACTION,"audio",action=ActionSpec(ActionType.PLAY_LOCAL_AUDIO,targetId="rest-tone")),
            WorkflowNode("seat",NodeKind.ACTION,"seat",action=ActionSpec(ActionType.MOVE_SEAT_LIMITED,area="ROW_1_LEFT",axis=SeatAxis.FORE_AFT,direction=MoveDirection.BACKWARD)),
            WorkflowNode("end",NodeKind.END,"end")
        ), edges=listOf(WorkflowEdge("1","start","wait"),WorkflowEdge("2","wait","audio"),WorkflowEdge("3","audio","seat"),WorkflowEdge("4","seat","end")))
    private fun parked()=ExecutionContext(0,0,InputSource.REAL,StateValue("P",0,true,InputSource.REAL),StateValue(0.0,0,true,InputSource.REAL),uxRestricted=false)
    private class Environment {
        var generation=1L
        var context=ExecutionContext(0,0,InputSource.REAL,StateValue("P",0,true,InputSource.REAL),StateValue(0.0,0,true,InputSource.REAL),uxRestricted=false)
        var description:String?="ROW_1_LEFT FORE_AFT BACKWARD maximum 300ms"
        var calibration:String?="evidence-v1:window200:max300:sample50"
        var prompts=0
        var answer:suspend()->Boolean={true}
        fun session(w:WorkflowDefinition,runId:String="run-1")=SeatRunAuthorization.Session(
            workflow=w,runId=runId,connectionGeneration={generation},snapshot={context},
            plan={description},planKey={calibration},
            confirm={ _,_->prompts++;answer() })
    }
    private fun uncalibrated()=Environment().apply { description=null;calibration=null }
    private fun seat(w:WorkflowDefinition)=w.nodes.single { it.id=="seat" }.action!!
    private fun request(w:WorkflowDefinition,nodeId:String="audio",runId:String="run-1")=
        ActionRequest(runId,nodeId,w.nodes.single { it.id==nodeId }.action!!,parked())

    @Test fun connectionChangedWhileConfirmingBlocksTheWholeRun()=runTest {
        val w=graph();val e=Environment();e.answer={e.generation++;true};val session=e.session(w)
        assertEquals(ActionStatus.BLOCKED,session.authorize(parked())?.status)
        assertEquals(ActionStatus.BLOCKED,session.beforeDispatch(request(w))?.status)
    }
    @Test fun freshStateAfterConfirmationMustStillBeParked()=runTest {
        val e=Environment();e.answer={e.context=e.context.copy(gear=StateValue("D",0,true,InputSource.REAL));true}
        assertEquals(ActionStatus.BLOCKED,e.session(graph()).authorize(parked())?.status)
    }
    @Test fun reconnectDuringWaitBlocksAudioBeforeAnySeatCommand()=runTest {
        val w=graph();val e=Environment();val session=e.session(w);val dispatched=mutableListOf<ActionType>()
        val adapter=object:ActionAdapter {
            override suspend fun authorizeRun(workflow:WorkflowDefinition,runId:String,context:ExecutionContext)=session.authorize(context)
            override suspend fun preflight(action:ActionSpec,context:ExecutionContext)=session.validate(w,"run-1")
            override suspend fun execute(request:ActionRequest,onProgress:suspend(ActionProgress)->Unit):ActionResult {
                session.beforeDispatch(request)?.let { return it }
                dispatched+=request.action.type
                return ActionResult(ActionStatus.VERIFIED,"dispatched")
            }
            override suspend fun stop(runId:String):List<ActionResult> { session.invalidate();return emptyList() }
        }
        val result=WorkflowEngine(adapter,{e.context},sleep={delay(it);e.generation++},nowMillis={testScheduler.currentTime}).run(w,"run-1")
        assertEquals(RunStatus.BLOCKED,result.status)
        assertTrue("A stale approval must block even the audio preceding the seat",dispatched.isEmpty())
    }
    @Test fun sealCannotAuthorizeAnotherRunOrChangedDefinition()=runTest {
        val w=graph()
        val changes=listOf(w.copy(id="other"),w.copy(revision=2),w.copy(nodes=w.nodes.map { if(it.id=="seat")it.copy(action=it.action!!.copy(direction=MoveDirection.FORWARD)) else it }))
        for(changed in changes) {
            val session=Environment().session(w);assertNull(session.authorize(parked()))
            assertEquals(ActionStatus.BLOCKED,session.validate(changed,"run-1")?.status)
            assertEquals("Invalidation must remain latched",ActionStatus.BLOCKED,session.validate(w,"run-1")?.status)
        }
        val session=Environment().session(w);assertNull(session.authorize(parked()))
        assertEquals(ActionStatus.BLOCKED,session.beforeDispatch(request(w,runId="another-run"))?.status)
    }
    @Test fun changedCalibrationWithSameDisplayedLimitInvalidatesApproval()=runTest {
        val w=graph();val e=Environment();val session=e.session(w);assertNull(session.authorize(parked()))
        e.calibration="evidence-v2:window200:max300:sample50"
        assertEquals(ActionStatus.BLOCKED,session.beforeDispatch(request(w))?.status)
    }
    @Test fun eachMoveChecksFreshStateEvenAfterAnEarlierSideEffect()=runTest {
        val w=graph();val e=Environment();val session=e.session(w);assertNull(session.authorize(parked()))
        assertNull(session.beforeDispatch(request(w)))
        e.context=e.context.copy(speedMps=StateValue(0.1,0,true,InputSource.REAL))
        assertEquals(ActionStatus.BLOCKED,session.beforeDispatch(request(w,"seat"))?.status)
    }
    @Test fun firstDispatchRechecksStateAndActionIdentity()=runTest {
        val w=graph();val e=Environment();val session=e.session(w);assertNull(session.authorize(parked()))
        e.context=e.context.copy(uxRestricted=true)
        assertEquals(ActionStatus.BLOCKED,session.beforeDispatch(request(w))?.status)
        val other=Environment().session(w);assertNull(other.authorize(parked()))
        assertEquals(ActionStatus.BLOCKED,other.beforeDispatch(request(w).copy(action=ActionSpec(ActionType.STOP_LOCAL_AUDIO)))?.status)
    }
    @Test fun timeoutAndLateAnswerCannotReviveApproval()=runTest {
        val w=graph();val e=Environment();val reply=CompletableDeferred<Boolean>();e.answer={reply.await()};val session=e.session(w)
        val task=async { session.authorize(parked()) }
        advanceTimeBy(30_001);runCurrent()
        assertEquals(ActionStatus.CANCELLED,task.await()?.status)
        reply.complete(true)
        assertEquals(ActionStatus.BLOCKED,session.beforeDispatch(request(w))?.status)
        assertEquals(ActionStatus.BLOCKED,session.authorize(parked())?.status)
        assertEquals(1,e.prompts)
    }
    @Test fun cancellationDuringConfirmationCannotReviveApproval()=runTest {
        val w=graph();val e=Environment();val reply=CompletableDeferred<Boolean>();e.answer={reply.await()};val session=e.session(w)
        val task=launch { session.authorize(parked()) };runCurrent();task.cancelAndJoin();reply.complete(true)
        assertEquals(ActionStatus.BLOCKED,session.beforeDispatch(request(w))?.status)
    }
    @Test fun cancellingApprovedExecutionInvalidatesSealForOtherCoroutines()=runTest {
        val w=graph();val session=Environment().session(w);val accepted=CompletableDeferred<Unit>()
        val execution=launch { assertNull(session.authorize(parked()));accepted.complete(Unit);awaitCancellation() }
        accepted.await();execution.cancelAndJoin()
        assertEquals(ActionStatus.BLOCKED,session.beforeDispatch(request(w))?.status)
    }
    @Test fun completedRunCannotReuseSealButNewRunPromptsAgain()=runTest {
        val w=graph();val e=Environment();val session=e.session(w);assertNull(session.authorize(parked()))
        assertNull(session.beforeDispatch(request(w)));session.invalidate()
        assertEquals(ActionStatus.BLOCKED,session.beforeDispatch(request(w,"seat"))?.status)
        val next=e.session(w,"run-2");assertNull(next.authorize(parked()))
        assertNull(next.beforeDispatch(request(w,runId="run-2")));assertEquals(2,e.prompts)
    }
    @Test fun uncalibratedRunAsksOnceThenSkipsOnlySeatSteps()=runTest {
        val w=graph();val e=uncalibrated();val session=e.session(w)
        assertNull(session.authorize(parked()));assertEquals(1,e.prompts)
        assertEquals(ActionStatus.SKIPPED,session.skipped(seat(w))?.status)
        assertNull(session.skipped(w.nodes.single { it.id=="audio" }.action!!))
        assertNull(session.beforeDispatch(request(w)))
        assertEquals("A MOVE must never be dispatched from an exclusion run",ActionStatus.BLOCKED,session.beforeDispatch(request(w,"seat"))?.status)
    }
    @Test fun calibratedRunNeverSkipsItsSeatSteps()=runTest {
        val w=graph();val session=Environment().session(w);assertNull(session.authorize(parked()))
        assertNull(session.skipped(seat(w)))
    }
    @Test fun exclusionRunExecutesOtherStepsAndNeverRequestsTheSeat()=runTest {
        val w=graph();val e=uncalibrated();val session=e.session(w);val dispatched=mutableListOf<ActionType>()
        val adapter=object:ActionAdapter {
            override suspend fun authorizeRun(workflow:WorkflowDefinition,runId:String,context:ExecutionContext)=session.authorize(context)
            override suspend fun preflight(action:ActionSpec,context:ExecutionContext)=session.validate(w,"run-1") ?: session.skipped(action)
            override suspend fun execute(request:ActionRequest,onProgress:suspend(ActionProgress)->Unit):ActionResult {
                session.beforeDispatch(request)?.let { return it }
                dispatched+=request.action.type
                return ActionResult(ActionStatus.VERIFIED,"dispatched")
            }
            override suspend fun stop(runId:String):List<ActionResult> { session.invalidate();return emptyList() }
        }
        val result=WorkflowEngine(adapter,{e.context},nowMillis={testScheduler.currentTime}).run(w,"run-1")
        assertEquals(RunStatus.COMPLETED,result.status)
        assertEquals(listOf(ActionType.PLAY_LOCAL_AUDIO),dispatched)
        assertEquals(listOf(ActionStatus.SKIPPED),result.events.filter { it.nodeId=="seat" }.map { it.status })
    }
    @Test fun decliningOrTimingOutExclusionCancelsWithoutAnySkipAuthority()=runTest {
        val w=graph();val declined=uncalibrated().apply { answer={false} }.session(w)
        assertEquals(ActionStatus.CANCELLED,declined.authorize(parked())?.status)
        assertNull(declined.skipped(seat(w)))
        assertEquals(ActionStatus.BLOCKED,declined.beforeDispatch(request(w))?.status)
        val e=uncalibrated();val reply=CompletableDeferred<Boolean>();e.answer={reply.await()};val late=e.session(w)
        val task=async { late.authorize(parked()) }
        advanceTimeBy(30_001);runCurrent()
        assertEquals(ActionStatus.CANCELLED,task.await()?.status)
        reply.complete(true)
        assertNull(late.skipped(seat(w)))
    }
    @Test fun exclusionConsentIsPerRunAndEndsWithTheRun()=runTest {
        val w=graph();val e=uncalibrated();val first=e.session(w);assertNull(first.authorize(parked()))
        first.invalidate()
        assertNull(first.skipped(seat(w)))
        assertEquals(ActionStatus.BLOCKED,first.validate(w,"run-1")?.status)
        val second=e.session(w,"run-2");assertNull(second.authorize(parked()));assertEquals(2,e.prompts)
    }
    @Test fun calibrationAppearingAfterExclusionConsentInvalidatesTheRun()=runTest {
        val w=graph();val e=uncalibrated();val session=e.session(w);assertNull(session.authorize(parked()))
        e.description="ROW_1_LEFT FORE_AFT BACKWARD maximum 300ms";e.calibration="evidence-v1"
        assertEquals(ActionStatus.BLOCKED,session.validate(w,"run-1")?.status)
        assertNull(session.skipped(seat(w)))
    }
    @Test fun inconsistentCalibrationEvidenceBlocksWithoutPrompting()=runTest {
        val w=graph()
        for((description,key) in listOf<Pair<String?,String?>>("plan" to null,null to "key")) {
            val e=Environment().apply { this.description=description;calibration=key }
            assertEquals(ActionStatus.BLOCKED,e.session(w).authorize(parked())?.status);assertEquals(0,e.prompts)
        }
    }
    @Test fun movingVehicleBlocksExclusionWithoutPrompting()=runTest {
        val e=uncalibrated();e.context=e.context.copy(uxRestricted=true)
        assertEquals(ActionStatus.BLOCKED,e.session(graph()).authorize(e.context)?.status);assertEquals(0,e.prompts)
    }
    @Test fun editingSeparateWorkingCopyDoesNotCancelImmutableRunningRevision()=runTest {
        val w=graph();val e=Environment();val session=e.session(w);assertNull(session.authorize(parked()))
        val edited=w.copy(revision=2,title="next draft")
        assertNotEquals(w.revision,edited.revision)
        assertNull(session.beforeDispatch(request(w)))
        assertNull(session.beforeDispatch(request(w,"seat")))
        assertEquals(1,e.prompts)
    }
}
