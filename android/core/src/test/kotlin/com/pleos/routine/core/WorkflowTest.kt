package com.pleos.routine.core

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class WorkflowTest {
    @Test fun serverAndAndroidShareOneJsonContract()=runBlocking {
        val w=WorkflowJson.decode(java.io.File("../../contracts/fixtures/commute.json").readText())
        assertTrue(WorkflowValidator.validate(w).toString(),WorkflowValidator.validate(w).isEmpty())
        val result=WorkflowSimulator.simulate(w,ExecutionContext.simulated(Instant.parse("2026-10-25T23:00:00Z").toEpochMilli(),12.0))
        assertEquals(RunStatus.COMPLETED,result.status)
        assertEquals(listOf("navigation"),result.events.filter { it.status==ActionStatus.SIMULATED }.map { it.nodeId })
    }
    private val monday=Instant.parse("2026-10-25T23:00:00Z").toEpochMilli()
    private fun commute()=WorkflowExamples.commute().let { w -> w.copy(nodes=w.nodes.map { n -> if(n.id=="navigation")n.copy(action=n.action!!.copy(latitude=37.5665,longitude=126.978, text="시험 목적지")) else n }) }
    @Test fun coldAndWarmHaveIndependentNavigation()=runBlocking {
        val w=commute();assertTrue(WorkflowValidator.validate(w).toString(),WorkflowValidator.validate(w).isEmpty())
        val cold=WorkflowSimulator.simulate(w,ExecutionContext.simulated(monday,3.0))
        val warm=WorkflowSimulator.simulate(w,ExecutionContext.simulated(monday,12.0))
        assertEquals(RunStatus.COMPLETED,cold.status);assertEquals(RunStatus.COMPLETED,warm.status)
        assertEquals(listOf("navigation","heat"),cold.events.filter { it.status==ActionStatus.SIMULATED }.map { it.nodeId })
        assertEquals(listOf("navigation"),warm.events.filter { it.status==ActionStatus.SIMULATED }.map { it.nodeId })
    }
    @Test fun unknownTemperatureBlocksWithoutInventingFalse()=runBlocking {
        val r=WorkflowSimulator.simulate(commute(),ExecutionContext.simulated(monday,null))
        assertEquals(RunStatus.BLOCKED,r.status);assertFalse(r.events.any { it.nodeId=="heat" })
    }
    @Test fun layoutDoesNotInvalidateSemanticTest() {
        val w=commute();val moved=w.copy(title="new",revision=5,nodes=w.nodes.map { it.copy(position=NodePosition(9f,8f),label="renamed") })
        assertEquals(WorkflowJson.semanticHash(w),WorkflowJson.semanticHash(moved))
        val changed=w.copy(nodes=w.nodes.map { if(it.id=="cold")it.copy(condition=it.condition!!.copy(value=8.0)) else it })
        assertNotEquals(WorkflowJson.semanticHash(w),WorkflowJson.semanticHash(changed))
    }
    @Test fun typedJsonRoundTrip() { val w=commute();assertEquals(w,WorkflowJson.decode(WorkflowJson.encode(w))) }
    @Test fun staleAndNonzeroSpeedNeverAuthorize() {
        val x=ExecutionContext.simulated(monday)
        assertTrue(x.parkedAndStationary());assertFalse(x.copy(monotonicMillis=2001).parkedAndStationary())
        for(v in listOf(-0.001,0.001,Double.NaN,Double.POSITIVE_INFINITY))assertFalse(x.copy(speedMps=x.speedMps.copy(value=v)).parkedAndStationary())
        assertFalse(x.copy(speedMps=x.speedMps.copy(source=InputSource.REAL)).parkedAndStationary())
    }
    @Test fun simulationCannotReachRealAdapter()=runBlocking {
        var calls=0;var stops=0
        val adapter=object:ActionAdapter { override suspend fun execute(request:ActionRequest,onProgress:suspend (ActionProgress)->Unit):ActionResult { calls++;return ActionResult(ActionStatus.VERIFIED,"") };override suspend fun stop(runId:String):List<ActionResult> { stops++;return emptyList() } }
        val r=WorkflowEngine(adapter,{ExecutionContext.simulated(monday)}).run(commute())
        assertEquals(RunStatus.BLOCKED,r.status);assertEquals(0,calls);assertEquals(1,stops)
    }
    @Test fun malformedCycleAndDanglingConnectionRejected() {
        val w=commute();assertTrue(WorkflowValidator.validate(w.copy(edges=w.edges+WorkflowEdge("cycle","end","start"))).isNotEmpty())
        assertTrue(WorkflowValidator.validate(w.copy(edges=w.edges.map { it.copy(target="missing") })).isNotEmpty())
    }
    @Test fun repeatAndParallelUseStructuredScopes()=runBlocking {
        val base=WorkflowExamples.blank("scopes")
        val repeat=GraphEditing.insert(base,base.edges.single().id,NodeKind.REPEAT)
        assertTrue(WorkflowValidator.validate(repeat).toString(),WorkflowValidator.validate(repeat).isEmpty())
        assertEquals(2000,WorkflowSimulator.simulate(repeat,ExecutionContext.simulated(monday)).elapsedMillis)
        val parallel=GraphEditing.insert(base,base.edges.single().id,NodeKind.PARALLEL)
        assertTrue(WorkflowValidator.validate(parallel).toString(),WorkflowValidator.validate(parallel).isEmpty())
        val r=WorkflowSimulator.simulate(parallel,ExecutionContext.simulated(monday))
        assertEquals(RunStatus.COMPLETED,r.status);assertEquals(1000,r.elapsedMillis)
    }
    @Test fun parallelResourceCollisionRejected() {
        val b=WorkflowExamples.blank("conflict");val p=GraphEditing.insert(b,b.edges.single().id,NodeKind.PARALLEL)
        val collision=p.copy(nodes=p.nodes.map { if(it.kind==NodeKind.WAIT)it.copy(kind=NodeKind.ACTION,action=ActionSpec(ActionType.SET_HVAC_TARGET,value=23.0),durationSeconds=null) else it })
        assertTrue(WorkflowValidator.validate(collision).toString(),WorkflowValidator.validate(collision).any { it.nodeId==p.nodes.single { n -> n.kind==NodeKind.PARALLEL }.id })
    }
    @Test fun initialTrueAndUnknownToTrueDoNotFire() {
        val w=commute();val m=TriggerMonitor();val x=ExecutionContext.simulated(monday)
        assertFalse(m.evaluate(w,x));assertFalse(m.evaluate(w,x));assertFalse(m.evaluate(w,x.copy(seatOccupied=StateValue())))
        assertFalse(m.evaluate(w,x));assertFalse(m.evaluate(w,x.copy(seatOccupied=x.seatOccupied.copy(value=false))))
        assertTrue(m.evaluate(w,x));assertFalse(m.evaluate(w,x))
    }
    @Test fun timeFilterCannotCreateBoardingEvent() {
        val w=commute();val m=TriggerMonitor();val before=ExecutionContext.simulated(monday-7200000)
        assertFalse(m.evaluate(w,before));assertFalse(m.evaluate(w,ExecutionContext.simulated(monday)))
    }
    @Test fun patchIsOptimisticAndUndoPreservesOriginal() {
        val w=commute();val next=WorkflowEdits.applyPatch(w,WorkflowPatch(w.revision,listOf(PatchOperation(PatchType.CHANGE_TITLE,title="변경"))))
        assertEquals(2,next.revision);assertThrows(IllegalArgumentException::class.java) { WorkflowEdits.applyPatch(next,WorkflowPatch(1,listOf(PatchOperation(PatchType.CHANGE_TITLE,title="stale")))) }
        val h=WorkflowHistory(w);h.replace(next);assertEquals(w,h.undo());assertEquals(next,h.redo())
    }
    @Test fun failureStopsAndPreservesUnknown()=runBlocking {
        var stops=0
        val adapter=object:ActionAdapter { override suspend fun execute(request:ActionRequest,onProgress:suspend (ActionProgress)->Unit)=ActionResult(ActionStatus.FAILED,"failed");override suspend fun stop(runId:String):List<ActionResult> { stops++;return listOf(ActionResult(ActionStatus.UNKNOWN,"STOP uncertain")) } }
        val x=ExecutionContext.simulated(monday).let { it.copy(source=InputSource.REAL,gear=it.gear.copy(source=InputSource.REAL),speedMps=it.speedMps.copy(source=InputSource.REAL),outsideTempC=it.outsideTempC.copy(source=InputSource.REAL),seatOccupied=it.seatOccupied.copy(source=InputSource.REAL)) }
        val r=WorkflowEngine(adapter,{x}).run(commute())
        assertEquals(RunStatus.UNKNOWN,r.status);assertEquals(1,stops);assertFalse(r.events.any { it.nodeId=="heat" })
    }
}
