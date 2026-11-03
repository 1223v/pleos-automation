package com.pleos.routine.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import com.pleos.routine.core.WorkflowJson

@Serializable data class ExplanationDetail(val nodeId:String?,val explanation:String,val eventSequences:List<Long>)
@Serializable data class RunExplanation(val runId:String,val summary:String,val details:List<ExplanationDetail>,val limitations:List<String>) {
    companion object {
        fun read(value:JsonObject,request:JsonObject):RunExplanation {
            val answer=WorkflowJson.format.decodeFromJsonElement<RunExplanation>(value)
            val run=request.getValue("run").jsonObject
            fun text(s:String,limit:Int)=s.isNotBlank() && s.length<=limit
            require(answer.runId==run.getValue("runId").jsonPrimitive.content && text(answer.summary,2000)) { "설명의 실행 ID 또는 본문이 올바르지 않습니다." }
            require(answer.details.size<=64 && answer.limitations.size<=16 && answer.limitations.all { text(it,500) })
            val events=run.getValue("events").jsonArray.associate { e ->
                e.jsonObject.getValue("sequence").jsonPrimitive.long to e.jsonObject.getValue("nodeId").jsonPrimitive.contentOrNull
            }
            answer.details.forEach { detail ->
                require(text(detail.explanation,2000) && detail.eventSequences.size in 1..128 && detail.eventSequences.distinct()==detail.eventSequences)
                require(detail.eventSequences.all { events.containsKey(it) && events[it]==detail.nodeId }) { "설명에 기록되지 않은 단계가 포함되어 있습니다." }
            }
            return answer
        }
    }
}
