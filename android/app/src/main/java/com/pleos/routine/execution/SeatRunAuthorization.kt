package com.pleos.routine.execution

import com.pleos.routine.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

object SeatRunAuthorization {
    const val EXCLUSION_TITLE="시트 단계 제외 실행 확인"
    const val EXCLUDED_MESSAGE="시트 이동·정지 미검증 · 사용자 동의로 미실행 · 차량 명령 미전송"

    /** Shown when no move/STOP calibration exists: the seat steps stay unexecuted and nothing is sent to the seat. */
    private fun exclusionDetail(workflow:WorkflowDefinition):String {
        val steps=workflow.nodes.filter { it.action?.type==ActionType.MOVE_SEAT_LIMITED }
        val lines=steps.joinToString("\n") { node ->
            val a=node.action!!
            val axis=when(a.axis) { SeatAxis.FORE_AFT->"시트 앞뒤";SeatAxis.BACKREST->"등받이";null->"축 미설정" }
            val direction=when(a.direction) { MoveDirection.FORWARD->"앞으로";MoveDirection.BACKWARD->"뒤로";null->"방향 미설정" }
            "· ${node.label} — ${a.area} · $axis · $direction"
        }
        val repetitions=workflow.nodes.filter { it.kind==NodeKind.REPEAT }.joinToString { "${it.label}: ${it.repeatCount}회" }
        return "이 환경에서는 시트 이동·정지가 검증되지 않았습니다.\n" +
            "아래 시트 단계 ${steps.size}개는 실행하지 않고 ‘미실행’으로 기록합니다. 시트 명령은 전송하지 않습니다.\n$lines\n" +
            (if(repetitions.isEmpty())"" else "반복 블록: $repetitions\n") +
            "나머지 동작은 실제로 실행됩니다. 이번 실행에만 적용되며 중지할 수 있습니다."
    }

    suspend fun authorize(workflow:WorkflowDefinition,context:ExecutionContext,plan:(List<ActionSpec>)->String?,confirm:suspend(String,String)->Boolean):ActionResult? {
        currentCoroutineContext().ensureActive()
        val seats=workflow.nodes.mapNotNull { it.action }.filter { it.type==ActionType.MOVE_SEAT_LIMITED }
        if(seats.isEmpty())return null
        val detail=plan(seats)
        if(detail==null) {
            if(context.source!=InputSource.REAL || !context.parkedAndStationary())return ActionResult(ActionStatus.BLOCKED,"시트 단계 제외 실행에는 실제 주차·정차 상태가 필요합니다.")
            val accepted=confirm(EXCLUSION_TITLE,exclusionDetail(workflow))
            currentCoroutineContext().ensureActive()
            if(!accepted)return ActionResult(ActionStatus.CANCELLED,"시트 단계 제외 실행 확인 취소")
            if(plan(seats)!=null)return ActionResult(ActionStatus.BLOCKED,"확인 중 시트 교정 상태가 바뀌었습니다. 새 실행에서 다시 확인하세요.")
            return null
        }
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
            val seats:List<ActionSpec>,val description:String?,val planKey:String?,val generation:Long,val execution:Job) {
            /** No calibrated plan was shown: the user agreed only to leave every seat step unexecuted. */
            val excludesSeats get()=seats.isNotEmpty() && description==null && planKey==null
        }
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
                // Both present: calibrated plan. Both absent: ask to exclude the seat steps. A mix is unusable evidence.
                if(seats.isNotEmpty() && (proposed.description==null)!=(proposed.planKey==null))return blocked("전체 시트 계획의 교정 증거를 확인하지 못했습니다.")
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
        /** Non-null only for a seat step of a run whose seat exclusion the user confirmed. Check validate() first. */
        fun skipped(action:ActionSpec):ActionResult? =
            if(action.type==ActionType.MOVE_SEAT_LIMITED && !invalidated && seal?.excludesSeats==true)
                ActionResult(ActionStatus.SKIPPED,EXCLUDED_MESSAGE) else null
        suspend fun beforeDispatch(request:ActionRequest):ActionResult? {
            currentCoroutineContext().ensureActive()
            validate(workflow,request.runId)?.let { return it }
            if(workflow.nodes.singleOrNull { it.id==request.nodeId }?.action!=request.action)return blocked()
            if(seal?.excludesSeats==true && request.action.type==ActionType.MOVE_SEAT_LIMITED)
                return blocked("시트 단계 제외 실행에서는 시트 명령을 보낼 수 없습니다.")
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
