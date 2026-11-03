package com.pleos.routine.data

import androidx.room.withTransaction
import com.pleos.routine.core.*
import kotlinx.serialization.encodeToString

enum class RunStartCause { MANUAL, ACTUAL_TEST, AUTOMATIC }

/** All writes are awaited. A failed event write prevents the next side effect. No command replay. */
class RunJournalRepository(private val database:RoutineDatabase) {
    private val dao get()=database.routines()
    suspend fun begin(workflow:WorkflowDefinition,runId:String,cause:RunStartCause=RunStartCause.MANUAL)=database.withTransaction {
        require(runId.isNotBlank())
        dao.insertRun(RunRecord(runId,workflow.id,null,"RUNNING",false,definition=WorkflowJson.encode(workflow),
            revision=workflow.revision,semanticHash=WorkflowJson.semanticHash(workflow),startCause=cause.name))
    }
    suspend fun append(event:ExecutionEvent)=database.withTransaction {
        check(dao.run(event.runId)?.state=="RUNNING") { "No active run for event" }
        check(event.sequence==dao.lastSequence(event.runId)+1) { "Journal sequence mismatch" }
        dao.insertEvent(RunEventRecord(event.runId,event.sequence,WorkflowJson.format.encodeToString(event)))
    }
    suspend fun finish(result:RunResult)=database.withTransaction {
        val previous=requireNotNull(dao.run(result.runId))
        check(previous.state=="RUNNING" && previous.routineId==result.workflowId &&
            previous.revision==result.revision && previous.semanticHash==result.semanticHash && !result.simulation)
        // A failed observer may leave only a prefix; never replace durable events with invented ones.
        val stored=dao.events(result.runId).map { WorkflowJson.format.decodeFromString<ExecutionEvent>(it.eventJson) }
        check(stored==result.events || result.status==RunStatus.UNKNOWN)
        dao.putRun(previous.copy(result=WorkflowJson.format.encodeToString(result.copy(events=stored)),state=result.status.name))
    }
    suspend fun unknown(runId:String)=dao.markUnknown(runId)
    suspend fun recover()=dao.recoverInterrupted()
}
