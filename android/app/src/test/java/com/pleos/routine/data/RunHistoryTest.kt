package com.pleos.routine.data

import com.pleos.routine.core.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class RunHistoryTest {
    private val w=WorkflowExamples.commute().copy(revision=7)
    private fun result(events:List<ExecutionEvent> = emptyList())=RunResult("run-12345",w.id,7,WorkflowJson.semanticHash(w),true,RunStatus.COMPLETED,events,10)
    private fun row()=RunRecord("run-12345",w.id,WorkflowJson.format.encodeToString(result()),"COMPLETED",true,
        definition=WorkflowJson.encode(w),revision=7,semanticHash=WorkflowJson.semanticHash(w))

    @Test fun simulationPersistsExactVersionEvenWhenLaterEdited() {
        val saved=RunHistory.simulation(w,result())
        assertEquals(w,RunHistory.definition(saved));assertEquals(7,saved.revision)
        assertEquals("SIMULATION",saved.startCause)
        assertTrue(runCatching { RunHistory.simulation(w.copy(revision=8),result()) }.isFailure)
    }
    @Test fun historicalConditionOpensCopyRatherThanOverwritingNewerSavedVersion() {
        val condition=w.nodes.first { it.kind==NodeKind.IF }
        val latest=w.copy(revision=8,title="latest")
        val edit=RunHistory.edit(row(),condition.id,latest,"copy-id")
        assertEquals("copy-id",edit.workflow.id);assertEquals(condition,edit.workflow.nodes.first { it.id==condition.id })
        assertEquals(condition.id,edit.nodeId);assertTrue(edit.copied)
        assertEquals(w,RunHistory.edit(row(),condition.id,w,"unused").workflow)
        assertTrue(runCatching { RunHistory.edit(row(),"missing",w,"copy") }.isFailure)
    }
    @Test fun legacyOrMismatchedSnapshotCannotBeSubstitutedWithCurrentDefinition() {
        assertNull(RunHistory.definition(row().copy(definition=null)))
        assertNull(RunHistory.definition(row().copy(revision=8)))
        assertNull(RunHistory.definition(row().copy(semanticHash="incorrect")))
        assertTrue(runCatching { RunHistory.edit(row().copy(definition=null),null,w,"copy") }.isFailure)
    }
    @Test fun explanationUsesStoredSnapshotAndExplicitlyCountsOmittedEvents() {
        val events=(1L..200L).map { ExecutionEvent("run-12345",null,ActionStatus.PENDING,"event",it,sequence=it) }
        val stored=row().copy(result=WorkflowJson.format.encodeToString(result(events)))
        val request=RunHistory.explanationRequest(stored,emptyList(),"request-123")
        assertEquals(7,request.getValue("workflow").jsonObject.getValue("revision").jsonPrimitive.int)
        assertEquals(72,request.getValue("omittedEventCount").jsonPrimitive.int)
        val selected=request.getValue("run").jsonObject.getValue("events").jsonArray
        assertEquals(128,selected.size);assertEquals(1,selected.first().jsonObject.getValue("sequence").jsonPrimitive.int)
        assertEquals(200,selected.last().jsonObject.getValue("sequence").jsonPrimitive.int)
    }
    @Test fun interruptedRunExplainsOnlyDurablePrefixAndRemainsUnknown() {
        val event=ExecutionEvent("run-12345",null,ActionStatus.ACKNOWLEDGED,"response only",5,sequence=1)
        val recovered=row().copy(result=null,state="UNKNOWN",simulation=false)
        val request=RunHistory.explanationRequest(recovered,listOf(event),"request-123")
        val run=request.getValue("run").jsonObject
        assertEquals("UNKNOWN",run.getValue("status").jsonPrimitive.content)
        assertEquals(1,run.getValue("events").jsonArray.size)
        assertTrue(runCatching { RunHistory.explanationRequest(recovered.copy(state="RUNNING"),listOf(event),"request-456") }.isFailure)
    }
}
