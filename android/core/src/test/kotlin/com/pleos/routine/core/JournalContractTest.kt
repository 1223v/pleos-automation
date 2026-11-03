package com.pleos.routine.core

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JournalContractTest {
    private fun graph():WorkflowDefinition { val b=WorkflowExamples.blank("journal");return GraphEditing.insert(b,b.edges.single().id,NodeKind.ACTION,ActionSpec(ActionType.CALENDAR_READ)) }
    private open class Adapter:ActionAdapter {
        var calls=0;var stops=0
        override suspend fun execute(request:ActionRequest,onProgress:suspend(ActionProgress)->Unit):ActionResult { calls++;onProgress(ActionProgress(ActionStatus.ACKNOWLEDGED,"ack"));return ActionResult(ActionStatus.VERIFIED,"observed","42") }
        override suspend fun stop(runId:String):List<ActionResult> { stops++;return emptyList() }
    }
    @Test fun journalFailureBeforeDispatchPreventsSideEffect()=runTest {
        val a=Adapter();val r=WorkflowEngine(a,{ExecutionContext(0,0)}).run(graph(),"run") { if(it.status==ActionStatus.REQUESTED)error("disk full") }
        assertEquals(0,a.calls);assertEquals(1,a.stops);assertEquals(RunStatus.UNKNOWN,r.status)
    }
    @Test fun refusedWholeRunApprovalPrecedesEverySideEffect()=runTest {
        val a=object:Adapter() { override suspend fun authorizeRun(workflow:WorkflowDefinition,runId:String,context:ExecutionContext)=ActionResult(ActionStatus.CANCELLED,"declined") }
        val r=WorkflowEngine(a,{ExecutionContext(0,0)}).run(graph())
        assertEquals(RunStatus.CANCELLED,r.status);assertEquals(0,a.calls)
    }
    @Test fun orderedJournalCarriesRunIdentityAndObservedValue()=runTest {
        val stored=mutableListOf<ExecutionEvent>();val r=WorkflowEngine(Adapter(),{ExecutionContext(0,0)}).run(graph(),"known-run") { stored+=it;yield() }
        assertEquals(r.events,stored);assertTrue(stored.all { it.runId=="known-run" })
        assertEquals((1L..stored.size.toLong()).toList(),stored.map { it.sequence });assertEquals("42",stored.last().observedValue)
    }
    @Test fun cancellationStillAwaitsDurableTerminalAndStop()=runTest {
        val started=CompletableDeferred<Unit>();var result:RunResult?=null;val stored=mutableListOf<ExecutionEvent>()
        val a=object:Adapter() { override suspend fun execute(request:ActionRequest,onProgress:suspend(ActionProgress)->Unit):ActionResult { started.complete(Unit);awaitCancellation() } }
        val job=launch { result=WorkflowEngine(a,{ExecutionContext(0,0)}).run(graph()) { delay(1);stored+=it } }
        started.await();job.cancelAndJoin()
        assertNotNull(result);assertEquals(RunStatus.CANCELLED,result!!.status)
        assertTrue(stored.any { it.status==ActionStatus.CANCELLED });assertEquals(1,a.stops)
    }
    @Test fun elapsedDeadlineIsDifferentFromUserCancellation()=runTest {
        val b=WorkflowExamples.blank("deadline");val w=GraphEditing.insert(b,b.edges.single().id,NodeKind.TIMER)
        var clock=0L
        val r=WorkflowEngine(Adapter(),{ExecutionContext(0,clock)},sleep={clock=7_200_001},nowMillis={clock}).run(w)
        assertEquals(RunStatus.FAILED,r.status);assertTrue(r.events.any { it.message.contains("2시간") })
    }
    @Test fun adapterTimeoutIsNotReportedAsTwoHourDeadline()=runTest {
        val a=object:Adapter() { override suspend fun execute(request:ActionRequest,onProgress:suspend(ActionProgress)->Unit):ActionResult = withTimeout(1) { delay(2);error("unreachable") } }
        val r=WorkflowEngine(a,{ExecutionContext(0,0)}).run(graph())
        assertEquals(RunStatus.UNKNOWN,r.status);assertFalse(r.events.any { it.message.contains("2시간") })
    }
    @Test fun swallowedProgressFailureStillCancelsSiblingBeforeNewSideEffect()=runTest {
        val b=WorkflowExamples.blank("parallel-journal")
        val p=GraphEditing.insert(b,b.edges.single().id,NodeKind.PARALLEL)
        val waits=p.nodes.filter { it.kind==NodeKind.WAIT }
        val first=p.copy(nodes=p.nodes.map { if(it.id==waits[0].id)it.copy(kind=NodeKind.ACTION,durationSeconds=null,action=ActionSpec(ActionType.CALENDAR_READ)) else it })
        val w=GraphEditing.insert(first,first.edges.single { it.source==waits[1].id }.id,NodeKind.ACTION,ActionSpec(ActionType.PLACE_SEARCH,text="office"))
        var siblingCalls=0
        val a=object:Adapter() {
            override suspend fun execute(request:ActionRequest,onProgress:suspend(ActionProgress)->Unit):ActionResult {
                if(request.action.type==ActionType.CALENDAR_READ) {
                    try { onProgress(ActionProgress(ActionStatus.ACKNOWLEDGED,"accepted")) }
                    catch(_:Exception) { withContext(NonCancellable) { delay(2000) } }
                    return ActionResult(ActionStatus.UNKNOWN,"cleanup completed")
                }
                siblingCalls++;return ActionResult(ActionStatus.VERIFIED,"must not execute")
            }
        }
        val r=WorkflowEngine(a,{ExecutionContext(0,testScheduler.currentTime)},nowMillis={testScheduler.currentTime}).run(w) {
            if(it.status==ActionStatus.ACKNOWLEDGED)error("disk full")
        }
        assertEquals(RunStatus.UNKNOWN,r.status);assertEquals(0,siblingCalls)
    }
}
