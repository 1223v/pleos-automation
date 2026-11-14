package com.pleos.routine.data

import com.pleos.routine.core.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

data class HistoryEdit(val workflow:WorkflowDefinition,val nodeId:String?,val copied:Boolean)
object RunHistory {
    fun simulation(w:WorkflowDefinition,r:RunResult):RunRecord {
        require(r.simulation && r.workflowId==w.id && r.revision==w.revision && r.semanticHash==WorkflowJson.semanticHash(w))
        return RunRecord(r.runId,w.id,WorkflowJson.format.encodeToString(r),r.status.name,true,
            definition=WorkflowJson.encode(w),revision=w.revision,semanticHash=r.semanticHash,startCause="SIMULATION")
    }
    fun definition(row:RunRecord):WorkflowDefinition?=runCatching {
        row.definition?.let(WorkflowJson::decode)?.takeIf {
            it.id==row.routineId && it.revision==row.revision && WorkflowJson.semanticHash(it)==row.semanticHash
        }
    }.getOrNull()
    fun result(row:RunRecord):RunResult?=runCatching {
        row.result?.let { WorkflowJson.format.decodeFromString<RunResult>(it) }?.takeIf {
            it.runId==row.id && it.workflowId==row.routineId && it.simulation==row.simulation && it.status.name==row.state &&
                (row.revision==null || row.revision==it.revision) && (row.semanticHash==null || row.semanticHash==it.semanticHash)
        }
    }.getOrNull()
    fun events(row:RunRecord,partial:List<ExecutionEvent>)=result(row)?.events ?: partial.filter { it.runId==row.id }.sortedBy { it.sequence }
    /** A run can complete while steps were deliberately left unexecuted; say so next to its status. */
    fun unexecuted(w:WorkflowDefinition?,events:List<ExecutionEvent>):String? {
        val ids=events.filter { it.status==ActionStatus.SKIPPED }.mapNotNull { it.nodeId }.distinct()
        if(ids.isEmpty())return null
        return "미실행 단계 ${ids.size}개 · "+ids.joinToString(", ") { id -> w?.nodes?.find { it.id==id }?.label ?: id }
    }
    fun edit(row:RunRecord,nodeId:String?,current:WorkflowDefinition?,newId:String):HistoryEdit {
        val snapshot=requireNotNull(definition(row)) { "실행 당시 규칙이 없거나 일치하지 않아 조건을 연결할 수 없습니다." }
        require(nodeId==null || snapshot.nodes.any { it.id==nodeId && it.kind==NodeKind.IF })
        val copied=current!=snapshot
        return HistoryEdit(if(copied)WorkflowEdits.clone(snapshot,newId) else snapshot,nodeId,copied)
    }
    fun explanationRequest(row:RunRecord,partial:List<ExecutionEvent>,requestId:String):JsonObject {
        val snapshot=requireNotNull(definition(row)) { "실행 당시 규칙이 없거나 일치하지 않아 AI 설명을 요청할 수 없습니다." }
        require(row.state!="RUNNING") { "실행이 끝난 뒤 설명을 요청하세요." }
        val all=events(row,partial)
        require(all.all { it.runId==row.id } && all.zipWithNext().all { (a,b)->a.sequence<b.sequence } && all.all { it.sequence>0 })
        val selected=if(all.size<=128)all else all.take(32)+all.takeLast(96)
        val run=result(row)?.copy(events=selected) ?: RunResult(row.id,snapshot.id,snapshot.revision,WorkflowJson.semanticHash(snapshot),row.simulation,
            RunStatus.UNKNOWN,selected,all.maxOfOrNull { it.elapsedMillis } ?: 0)
        return buildJsonObject {
            put("requestId",requestId);put("locale","ko-KR");put("workflow",WorkflowJson.format.encodeToJsonElement(snapshot))
            put("run",WorkflowJson.format.encodeToJsonElement(run));put("startCause",row.startCause ?: if(row.simulation)"SIMULATION" else "UNKNOWN")
            put("omittedEventCount",all.size-selected.size)
        }
    }
    fun cause(row:RunRecord)=when(row.startCause) {
        "MANUAL"->"직접 실행";"ACTUAL_TEST"->"실제 기능 테스트";"AUTOMATIC"->"자동 실행"
        "SIMULATION"->"가상 입력 테스트";else->"미기록 (과거 이력)"
    }
    fun eventDescription(w:WorkflowDefinition?,e:ExecutionEvent):String=buildString {
        append("#${e.sequence} · +${e.elapsedMillis}ms · ${e.status} · ${e.nodeId?.let { id->w?.nodes?.find { it.id==id }?.label ?: id } ?: "실행"}\n${e.message}")
        w?.nodes?.find { it.id==e.nodeId }?.let { append("\n설정: ${RuleDescriptions.node(it)}") }
        e.observedValue?.let { append("\n관찰값: $it") }
        if(e.iterations.isNotEmpty())append("\n반복: ${e.iterations.joinToString(" / ")}")
        if(e.branches.isNotEmpty())append(" · 분기: ${e.branches.joinToString(" / ")}")
    }
}
