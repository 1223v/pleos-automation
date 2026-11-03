package com.pleos.routine.core

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimerProgressTest {
    private val adapter=object:ActionAdapter {
        override suspend fun execute(request:ActionRequest,onProgress:suspend(ActionProgress)->Unit)=ActionResult(ActionStatus.VERIFIED,"ok")
        override suspend fun stop(runId:String)=emptyList<ActionResult>()
    }
    private fun graph(kind:NodeKind=NodeKind.TIMER,seconds:Long=2):WorkflowDefinition {
        val blank=WorkflowExamples.blank("timer-progress")
        val w=GraphEditing.insert(blank,blank.edges.single().id,kind)
        return w.copy(nodes=w.nodes.map { if(it.kind==kind)it.copy(durationSeconds=seconds) else it })
    }
    @Test fun realTimerReportsRemainingTimeAndCompletionAfterDurableEvent()=runTest {
        val updates=mutableListOf<TimerProgress>();val journal=mutableListOf<ExecutionEvent>()
        val engine=WorkflowEngine(adapter,{ExecutionContext(0,testScheduler.currentTime)},nowMillis={testScheduler.currentTime},onTimerProgress={
            if(it.completed)assertTrue(journal.any { e -> e.nodeId==it.nodeId && e.status==ActionStatus.VERIFIED })
            updates+=it
        })
        assertEquals(RunStatus.COMPLETED,engine.run(graph(),"run",journal::add).status)
        assertEquals(listOf(2000L,1000L,0L),updates.map { it.remainingMillis })
        assertEquals(listOf(false,false,true),updates.map { it.completed })
    }
    @Test fun waitAndSimulationDoNotPublishRealTimerProgress()=runTest {
        val updates=mutableListOf<TimerProgress>()
        WorkflowEngine(adapter,{ExecutionContext(0,testScheduler.currentTime)},nowMillis={testScheduler.currentTime},onTimerProgress={updates+=it}).run(graph(NodeKind.WAIT))
        WorkflowEngine(adapter,{ExecutionContext.simulated(0)},simulation=true,onTimerProgress={updates+=it}).run(graph())
        assertTrue(updates.isEmpty())
    }
    @Test fun elapsedTimeJumpCompletesWithoutNegativeRemainingTime()=runTest {
        var elapsed=0L;val updates=mutableListOf<TimerProgress>()
        val result=WorkflowEngine(adapter,{ExecutionContext(0,elapsed)},sleep={elapsed+=10_000},nowMillis={elapsed},onTimerProgress={updates+=it}).run(graph())
        assertEquals(RunStatus.COMPLETED,result.status)
        assertEquals(listOf(2000L,0L),updates.map { it.remainingMillis })
        assertTrue(updates.last().completed)
    }
    @Test fun cancelledTimerNeverPublishesCompletion()=runTest {
        val updates=mutableListOf<TimerProgress>();var result:RunResult?=null
        val job=launch { result=WorkflowEngine(adapter,{ExecutionContext(0,testScheduler.currentTime)},nowMillis={testScheduler.currentTime},onTimerProgress={updates+=it}).run(graph(seconds=20)) }
        runCurrent();advanceTimeBy(1500);job.cancelAndJoin()
        assertEquals(RunStatus.CANCELLED,result?.status)
        assertTrue(updates.isNotEmpty());assertFalse(updates.any { it.completed })
    }
    @Test fun failedCompletionJournalDoesNotPublishSuccessfulCompletion()=runTest {
        val updates=mutableListOf<TimerProgress>()
        val result=WorkflowEngine(adapter,{ExecutionContext(0,testScheduler.currentTime)},nowMillis={testScheduler.currentTime},onTimerProgress={updates+=it}).run(graph()) {
            if(it.status==ActionStatus.VERIFIED)error("durable storage failed")
        }
        assertEquals(RunStatus.UNKNOWN,result.status)
        assertTrue(updates.isNotEmpty());assertFalse(updates.any { it.completed })
    }
    @Test fun displayFailureCannotChangeExecutionOrCleanup()=runTest {
        val result=WorkflowEngine(adapter,{ExecutionContext(0,testScheduler.currentTime)},nowMillis={testScheduler.currentTime},onTimerProgress={error("notification unavailable")}).run(graph())
        assertEquals(RunStatus.COMPLETED,result.status)
    }
    @Test fun repeatedTimersHaveDistinctInvocationKeys()=runTest {
        val w=WorkflowDefinition(id="repeat-timer",title="repeat",nodes=listOf(
            WorkflowNode("start",NodeKind.START,"start"),WorkflowNode("end",NodeKind.END,"end"),
            WorkflowNode("repeat",NodeKind.REPEAT,"repeat",repeatCount=2,joinId="join"),WorkflowNode("join",NodeKind.JOIN,"join"),
            WorkflowNode("timer",NodeKind.TIMER,"timer",durationSeconds=1)),edges=listOf(
            WorkflowEdge("entry","start","repeat"),WorkflowEdge("body","repeat","timer","body"),
            WorkflowEdge("tail","repeat","end"),WorkflowEdge("timer-end","timer","join")))
        val updates=mutableListOf<TimerProgress>()
        val result=WorkflowEngine(adapter,{ExecutionContext(0,testScheduler.currentTime)},nowMillis={testScheduler.currentTime},onTimerProgress={updates+=it}).run(w,"run")
        assertEquals(RunStatus.COMPLETED,result.status)
        assertEquals(2,updates.filter { it.completed }.map { it.key }.distinct().size)
        assertEquals(listOf(listOf(1),listOf(2)),updates.filter { it.completed }.map { it.iterations })
    }
    @Test fun parallelTimersKeepIndependentRemainingTimesAndBranchKeys()=runTest {
        val w=WorkflowDefinition(id="parallel-timers",title="parallel",nodes=listOf(
            WorkflowNode("start",NodeKind.START,"start"),WorkflowNode("end",NodeKind.END,"end"),
            WorkflowNode("parallel",NodeKind.PARALLEL,"parallel",joinId="join"),WorkflowNode("join",NodeKind.JOIN,"join"),
            WorkflowNode("a",NodeKind.TIMER,"a",durationSeconds=1),WorkflowNode("b",NodeKind.TIMER,"b",durationSeconds=2)),edges=listOf(
            WorkflowEdge("entry","start","parallel"),WorkflowEdge("a","parallel","a","branch:0"),WorkflowEdge("b","parallel","b","branch:1"),
            WorkflowEdge("a-end","a","join"),WorkflowEdge("b-end","b","join"),WorkflowEdge("exit","join","end")))
        val updates=mutableListOf<TimerProgress>()
        val result=WorkflowEngine(adapter,{ExecutionContext(0,testScheduler.currentTime)},nowMillis={testScheduler.currentTime},onTimerProgress={updates+=it}).run(w)
        assertEquals(RunStatus.COMPLETED,result.status)
        assertEquals(setOf(listOf("branch:0"),listOf("branch:1")),updates.filter { it.completed }.map { it.branches }.toSet())
        assertEquals(listOf(2000L,1000L,0L),updates.filter { it.nodeId=="b" }.map { it.remainingMillis })
    }
}
