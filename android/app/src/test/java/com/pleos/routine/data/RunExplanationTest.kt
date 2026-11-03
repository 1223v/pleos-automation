package com.pleos.routine.data

import com.pleos.routine.core.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class RunExplanationTest {
    private val w=WorkflowExamples.blank("explain")
    private val event=ExecutionEvent("run-12345","start",ActionStatus.PENDING,"start",0,sequence=1)
    private val result=RunResult(event.runId,w.id,w.revision,WorkflowJson.semanticHash(w),true,RunStatus.COMPLETED,listOf(event),0)
    private val request=RunHistory.explanationRequest(RunHistory.simulation(w,result),emptyList(),"request-123")
    private val valid="""{"runId":"run-12345","summary":"가상 실행 기록","details":[{"nodeId":"start","explanation":"시작 단계 기록","eventSequences":[1]}],"limitations":[]}"""
    @Test fun explanationAcceptsCitedEvidenceWithoutChangingRunResult() {
        val parsed=RunExplanation.read(Json.parseToJsonElement(valid).jsonObject,request)
        assertEquals("가상 실행 기록",parsed.summary);assertEquals(listOf(1L),parsed.details.single().eventSequences)
        assertEquals(RunStatus.COMPLETED,result.status)
    }
    @Test fun anotherRunFabricatedReferencesAndCommandsAreRejected() {
        listOf(valid.replace("run-12345","other-run"),valid.replace("[1]","[2]"),valid.replace("\"start\"","\"end\""),
            valid.replace("\"limitations\":[]","\"limitations\":[],\"patch\":{}"),valid.replace("가상 실행 기록"," ")).forEach { bad ->
            assertTrue(bad,runCatching { RunExplanation.read(Json.parseToJsonElement(bad).jsonObject,request) }.isFailure)
        }
    }
}
