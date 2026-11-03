package com.pleos.routine.execution

import com.pleos.routine.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

object SeatRunAuthorization {
    suspend fun authorize(workflow:WorkflowDefinition,context:ExecutionContext,plan:(List<ActionSpec>)->String?,confirm:suspend(String,String)->Boolean):ActionResult? {
        currentCoroutineContext().ensureActive()
        val seats=workflow.nodes.mapNotNull { it.action }.filter { it.type==ActionType.MOVE_SEAT_LIMITED }
        if(seats.isEmpty())return null
        val detail=plan(seats) ?: return ActionResult(ActionStatus.BLOCKED,"전체 시트 계획의 축·방향·제한 이동·STOP 교정 증거가 없습니다.")
        if(context.source!=InputSource.REAL || !context.parkedAndStationary())return ActionResult(ActionStatus.BLOCKED,"전체 시트 계획 승인에는 실제 주차·정차 상태가 필요합니다.")
        val repetitions=workflow.nodes.filter { it.kind==NodeKind.REPEAT }.joinToString { "${it.label}: ${it.repeatCount}회" }
        val accepted=confirm("시트 포함 전체 루틴 확인",detail+(if(repetitions.isEmpty())"\n" else "\n반복 블록: $repetitions\n")+
            "이 루틴의 다른 동작도 함께 시작됩니다. 이번 실행에만 적용되며 중지할 수 있습니다.")
        currentCoroutineContext().ensureActive()
        if(!accepted)return ActionResult(ActionStatus.CANCELLED,"전체 실행 확인 취소")
        if(plan(seats)!=detail)return ActionResult(ActionStatus.BLOCKED,"확인 중 시트 교정 계획이 바뀌었습니다. 새 실행에서 다시 확인하세요.")
        return null
    }

    /** In-memory authority for one immutable run. Never stored or reused after stop/recovery. */
    class Session(
        private val workflow:WorkflowDefinition,
        private val runId:String,
        private val connectionGeneration:()->Long,
        private val snapshot:suspend()->ExecutionContext,
        private val plan:(List<ActionSpec>)->String?,
        private val planKey:(List<ActionSpec>)->String?=plan,
        private val confirm:suspend(String,String)->Boolean,
    ) {
        private data class Seal(val runId:String,val workflowId:String,val revision:Int,val semanticHash:String,
            val seats:List<ActionSpec>,val description:String?,val planKey:String?,val generation:Long,val execution:Job)
        private val seats=workflow.nodes.mapNotNull { it.action }.filter { it.type==ActionType.MOVE_SEAT_LIMITED }
        // Authorization originates on Main; the final HTTP gate also reads/writes on IO.
        @Volatile private var seal:Seal?=null
        @Volatile private var attempted=false
        @Volatile private var invalidated=false
        @Volatile private var firstDispatched=false

        fun invalidate() { invalidated=true;seal=null }
        private fun blocked(message:String="이번 실행의 시트 승인이 유효하지 않습니다. 새 실행에서 다시 확인하세요."):ActionResult {
            invalidate()
            return ActionResult(ActionStatus.BLOCKED,message)
        }
        suspend fun authorize(context:ExecutionContext):ActionResult? {
            currentCoroutineContext().ensureActive()
            if(attempted || invalidated || runId.isBlank())return blocked()
            attempted=true
            val execution=currentCoroutineContext()[Job] ?: return blocked("이번 실행의 수명을 확인하지 못했습니다.")
            val proposed=Seal(runId,workflow.id,workflow.revision,WorkflowJson.semanticHash(workflow),seats,
                if(seats.isEmpty())null else plan(seats),if(seats.isEmpty())null else planKey(seats),connectionGeneration(),execution)
            try {
                if(seats.isNotEmpty() && (proposed.description==null || proposed.planKey==null))return blocked("전체 시트 계획의 교정 증거를 확인하지 못했습니다.")
                SeatRunAuthorization.authorize(workflow,context,plan) { title,detail ->
                    withTimeoutOrNull(30_000) { confirm(title,detail) } ?: false
                }?.let { invalidate();return it }
                currentCoroutineContext().ensureActive()
                if(seats.isNotEmpty()) {
                    val fresh=snapshot()
                    currentCoroutineContext().ensureActive()
                    if(fresh.source!=InputSource.REAL || !fresh.parkedAndStationary())return blocked("확인 후 실제 주차·정차 상태가 바뀌었습니다.")
                    if(connectionGeneration()!=proposed.generation || plan(seats)!=proposed.description || planKey(seats)!=proposed.planKey)
                        return blocked("확인 중 차량 연결 또는 시트 교정 계획이 바뀌었습니다.")
                }
                if(invalidated)return blocked()
                seal=proposed
                return null
            } catch(e:CancellationException) { invalidate();throw e }
        }
        /** Cheap check before preflight; repeat after every asynchronous preparation at dispatch. */
        fun validate(definition:WorkflowDefinition,requestRunId:String):ActionResult? {
            val approved=seal ?: return blocked()
            if(invalidated || !approved.execution.isActive || requestRunId!=approved.runId || definition.id!=approved.workflowId ||
                definition.revision!=approved.revision || WorkflowJson.semanticHash(definition)!=approved.semanticHash ||
                definition.nodes.mapNotNull { it.action }.filter { it.type==ActionType.MOVE_SEAT_LIMITED }!=approved.seats)return blocked()
            if(seats.isNotEmpty() && (connectionGeneration()!=approved.generation || plan(seats)!=approved.description || planKey(seats)!=approved.planKey))
                return blocked("차량 연결 또는 시트 교정 계획이 바뀌어 이번 실행 승인을 취소했습니다.")
            return null
        }
        suspend fun beforeDispatch(request:ActionRequest):ActionResult? {
            currentCoroutineContext().ensureActive()
            validate(workflow,request.runId)?.let { return it }
            if(workflow.nodes.singleOrNull { it.id==request.nodeId }?.action!=request.action)return blocked()
            if(seats.isNotEmpty() && (!firstDispatched || request.action.type==ActionType.MOVE_SEAT_LIMITED)) {
                val fresh=snapshot()
                currentCoroutineContext().ensureActive()
                validate(workflow,request.runId)?.let { return it }
                if(fresh.source!=InputSource.REAL || !fresh.parkedAndStationary())return blocked("명령 직전 실제 주차·정차 상태를 확인하지 못했습니다.")
            }
            firstDispatched=true
            return null
        }
    }
}
