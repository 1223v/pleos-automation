package com.pleos.routine.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ClockAndActivationTest {
    @Test fun disabledEventsAreNeverReplayedOnActivation() {
        val w=WorkflowExamples.blank("enable").copy(trigger=TriggerDefinition(TriggerType.SEAT_OCCUPIED))
        val x=ExecutionContext.simulated(0,occupied=false);val m=TriggerMonitor()
        m.syncActive(listOf(w));assertFalse(m.evaluate(w,x));m.syncActive(emptyList());m.syncActive(listOf(w))
        assertFalse(m.evaluate(w,x.copy(seatOccupied=x.seatOccupied.copy(value=true))))
        assertFalse(m.evaluate(w,x));assertTrue(m.evaluate(w,x.copy(seatOccupied=x.seatOccupied.copy(value=true))))
    }
    @Test fun timerCountsSleepAndRechecksDeadlineWithinOneSecond()=runBlocking {
        val b=WorkflowExamples.blank("clock");val w=GraphEditing.insert(b,b.edges.single().id,NodeKind.TIMER).let { it.copy(nodes=it.nodes.map { n->if(n.kind==NodeKind.TIMER)n.copy(durationSeconds=1800)else n }) }
        var elapsed=0L;val sleeps=mutableListOf<Long>()
        val adapter=object:ActionAdapter {
            override suspend fun execute(request:ActionRequest,onProgress:suspend (ActionProgress)->Unit)=ActionResult(ActionStatus.VERIFIED,"ok")
            override suspend fun stop(runId:String)=emptyList<ActionResult>()
        }
        val r=WorkflowEngine(adapter,{ExecutionContext(0,elapsed)},sleep={ ms->sleeps+=ms;elapsed+=1_800_000 },nowMillis={elapsed}).run(w)
        assertEquals(RunStatus.COMPLETED,r.status);assertEquals(listOf(1000L),sleeps);assertEquals(1_800_000,r.elapsedMillis)
    }
}
