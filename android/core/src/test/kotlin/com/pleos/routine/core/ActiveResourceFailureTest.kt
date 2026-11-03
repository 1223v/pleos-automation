package com.pleos.routine.core

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveResourceFailureTest {
    @Test fun latePlaybackFailureStopsTimerAndDownstreamActionsWithFailureReason()=runTest {
        var error:ActiveActionFailure?=null;val commands=mutableListOf<String>();var stops=0
        val adapter=object:ActionAdapter {
            override fun activeFailure()=error
            override suspend fun execute(request:ActionRequest,onProgress:suspend(ActionProgress)->Unit):ActionResult {
                commands+=request.nodeId
                if(request.nodeId=="audio")backgroundScope.launch { delay(500);error=ActiveActionFailure("audio","음원 재생 중 오류") }
                return ActionResult(ActionStatus.VERIFIED,"started")
            }
            override suspend fun stop(runId:String):List<ActionResult> { stops++;return emptyList() }
        }
        val w=WorkflowDefinition(id="late-audio",title="late audio",nodes=listOf(
            WorkflowNode("start",NodeKind.START,"start"),WorkflowNode("audio",NodeKind.ACTION,"audio",action=ActionSpec(ActionType.PLAY_LOCAL_AUDIO,targetId="rest-tone")),
            WorkflowNode("timer",NodeKind.TIMER,"timer",durationSeconds=10),WorkflowNode("next",NodeKind.ACTION,"next",action=ActionSpec(ActionType.CALENDAR_READ)),WorkflowNode("end",NodeKind.END,"end")),
            edges=listOf(WorkflowEdge("1","start","audio"),WorkflowEdge("2","audio","timer"),WorkflowEdge("3","timer","next"),WorkflowEdge("4","next","end")))
        val result=WorkflowEngine(adapter,{ExecutionContext(0,testScheduler.currentTime)},nowMillis={testScheduler.currentTime}).run(w)
        assertEquals(RunStatus.FAILED,result.status)
        assertEquals(listOf("audio"),commands)
        assertTrue(result.events.any { it.nodeId=="audio" && it.status==ActionStatus.FAILED && it.message=="음원 재생 중 오류" })
        assertFalse(result.events.any { it.nodeId=="timer" && it.status==ActionStatus.VERIFIED })
        assertEquals(1,stops)
    }
}
