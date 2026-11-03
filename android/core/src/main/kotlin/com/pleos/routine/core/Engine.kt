package com.pleos.routine.core

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

class WorkflowEngine(
    private val adapter: ActionAdapter,
    private val snapshot: suspend () -> ExecutionContext,
    private val simulation: Boolean = false,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val onTimerProgress: (TimerProgress) -> Unit = {},
) {
    private class Halt(val status: RunStatus): RuntimeException()
    private class DeadlineExceeded: RuntimeException()
    private class JournalFailure: RuntimeException()
    private class ActiveFailure(val failure:ActiveActionFailure):RuntimeException()
    suspend fun run(w: WorkflowDefinition, runId: String = UUID.randomUUID().toString(), onEvent: suspend (ExecutionEvent) -> Unit = {}): RunResult {
        val begin = nowMillis()
        val events = mutableListOf<ExecutionEvent>()
        val eventMutex = Mutex()
        val journalFailed = AtomicBoolean(false)
        var executionJob:Job?=null
        val semanticHash=runCatching { WorkflowJson.semanticHash(w) }.getOrNull()
        val counter = AtomicInteger()
        var elapsed = 0L
        var status = RunStatus.COMPLETED
        suspend fun log(node: String?, state: ActionStatus, message: String, at: Long = elapsed, iterations: List<Int> = emptyList(), branches: List<String> = emptyList(), observed: String? = null, cleanup:Boolean=false) = eventMutex.withLock {
            if(journalFailed.get() && !cleanup)throw JournalFailure()
            if (simulation) elapsed = maxOf(elapsed, at)
            val event = ExecutionEvent(runId, node, state, message, if (simulation) at else (nowMillis() - begin).coerceAtLeast(0), iterations, branches, events.size.toLong() + 1, observed)
            events.add(event)
            if(!journalFailed.get()) try { onEvent(event) } catch(e:CancellationException) { throw e } catch(_:Exception) {
                journalFailed.set(true)
                executionJob?.cancel(CancellationException("Execution journal failed"))
                throw JournalFailure()
            }
        }
        suspend fun terminalLog(state:ActionStatus,message:String) = withContext(NonCancellable) {
            try { log(null,state,message,cleanup=true) } catch(_:JournalFailure) { /* UNKNOWN is retained below. */ }
        }
        suspend fun context(at: Long): ExecutionContext {
            val x = snapshot()
            if (simulation && x.source != InputSource.SIMULATION || !simulation && x.source != InputSource.REAL) {
                log(null, ActionStatus.BLOCKED, "실제 입력과 시뮬레이션 입력을 혼합할 수 없습니다.", at)
                throw Halt(RunStatus.BLOCKED)
            }
            return if (!simulation) x else x.copy(epochMillis = x.epochMillis + at, monotonicMillis = 0)
        }
        suspend fun checkFilters(x: ExecutionContext) {
            for (filter in w.trigger.filters) {
                val result = Conditions.evaluate(filter, x, w.trigger.zoneId)
                if (result.truth != Truth.TRUE) {
                    log(null, ActionStatus.BLOCKED, "실행 조건: ${result.reason} · ${result.truth}")
                    throw Halt(RunStatus.BLOCKED)
                }
            }
        }
        fun terminate(r: ActionResult): Nothing = throw Halt(when (r.status) {
            ActionStatus.BLOCKED -> RunStatus.BLOCKED
            ActionStatus.UNKNOWN, ActionStatus.ACKNOWLEDGED, ActionStatus.REQUESTED -> RunStatus.UNKNOWN
            ActionStatus.CANCELLED -> RunStatus.CANCELLED
            else -> RunStatus.FAILED
        })
        fun publishTimer(value:TimerProgress) {
            // Presentation failure must not turn a completed physical action into a failure.
            try { onTimerProgress(value) } catch(e:CancellationException) { throw e } catch(_:Exception) { }
        }
        fun checkActiveFailure() { if(!simulation)adapter.activeFailure()?.let { throw ActiveFailure(it) } }
        val map = w.nodes.associateBy { it.id }
        val outgoing = w.edges.groupBy { it.source }
        suspend fun walk(start: String, stop: String?, offset: Long, iterations: List<Int>, branches: List<String>): Long {
            var id = start; var at = offset
            while (id != stop) {
                currentCoroutineContext().ensureActive()
                checkActiveFailure()
                if(!simulation && nowMillis()-begin > 7_200_000)throw DeadlineExceeded()
                if (counter.incrementAndGet() > ExecutionLimits.MAX_NODE_VISITS || at > 7_200_000) { log(id, ActionStatus.BLOCKED, "실행 한도를 초과했습니다.", at); throw Halt(RunStatus.BLOCKED) }
                val n = map.getValue(id)
                val x = context(at)
                fun target(port: String = "next") = outgoing[id].orEmpty().single { it.port == port }.target
                when (n.kind) {
                    NodeKind.END -> return at
                    NodeKind.START, NodeKind.JOIN -> { log(id, ActionStatus.PENDING, n.label, at, iterations, branches); id = target() }
                    NodeKind.IF -> {
                        val result = Conditions.evaluate(n.condition!!, x, w.trigger.zoneId)
                        log(id, if (result.truth == Truth.UNKNOWN) ActionStatus.BLOCKED else ActionStatus.PENDING, result.reason + " · " + result.truth, at, iterations, branches)
                        if (result.truth == Truth.UNKNOWN) throw Halt(RunStatus.BLOCKED)
                        id = target(if (result.truth == Truth.TRUE) "true" else "false")
                    }
                    NodeKind.ACTION -> {
                        val a = n.action!!
                        if(a.type in ActionResources.parkedTypes && !x.parkedAndStationary()) { log(id,ActionStatus.BLOCKED,"선택한 차량 동작에 필요한 주차·정차 상태가 아닙니다.",at);throw Halt(RunStatus.BLOCKED) }
                        adapter.preflight(a, x)?.let { log(id, it.status, it.message, at, iterations, branches); terminate(it) }
                        log(id, ActionStatus.REQUESTED, n.label, at, iterations, branches)
                        currentCoroutineContext().ensureActive()
                        val result = adapter.execute(ActionRequest(runId, id, a, x, iterations, branches)) { log(n.id, it.status, it.message, at, iterations, branches) }
                        currentCoroutineContext().ensureActive()
                        checkActiveFailure()
                        log(id, result.status, result.message, at, iterations, branches, result.observedValue)
                        if (result.status !in setOf(ActionStatus.VERIFIED, ActionStatus.SIMULATED, ActionStatus.SKIPPED)) terminate(result)
                        id = target()
                    }
                    NodeKind.WAIT, NodeKind.TIMER -> {
                        val duration = n.durationSeconds!! * 1000
                        if (at + duration > 7_200_000) { log(id, ActionStatus.BLOCKED, "전체 실행은 2시간 이내여야 합니다.", at); throw Halt(RunStatus.BLOCKED) }
                        log(id, ActionStatus.PENDING, "${n.label}: ${n.durationSeconds}초 대기", at, iterations, branches)
                        if (!simulation) {
                            val deadline=nowMillis()+duration
                            fun progress() {
                                if(n.kind==NodeKind.TIMER)publishTimer(TimerProgress(runId,n.id,n.label,
                                    (deadline-nowMillis()).coerceAtLeast(0),false,iterations,branches))
                            }
                            progress()
                            while(nowMillis()<deadline) {
                                if(nowMillis()-begin > 7_200_000)throw DeadlineExceeded()
                                sleep(minOf(1000L,deadline-nowMillis()))
                                currentCoroutineContext().ensureActive()
                                checkActiveFailure()
                                if(nowMillis()<deadline)progress()
                            }
                        }
                        at += duration
                        log(id, if (simulation) ActionStatus.SIMULATED else ActionStatus.VERIFIED, if (n.kind == NodeKind.TIMER) "타이머 완료" else "대기 완료", at, iterations, branches)
                        if(!simulation && n.kind==NodeKind.TIMER)publishTimer(TimerProgress(runId,n.id,n.label,0,true,iterations,branches))
                        id = target()
                    }
                    NodeKind.REPEAT -> {
                        repeat(n.repeatCount!!) { index -> at = walk(target("body"), n.joinId, at, iterations + (index + 1), branches) }
                        id = target()
                    }
                    NodeKind.PARALLEL -> {
                        val initial = at
                        at = coroutineScope { outgoing[id].orEmpty().map { edge -> async { walk(edge.target, n.joinId, initial, iterations, branches + edge.port) } }.awaitAll().max() }
                        id = n.joinId!!
                    }
                }
            }
            return at
        }
        try {
            val finished=withTimeoutOrNull(7_200_000) {
              coroutineScope {
                executionJob=currentCoroutineContext()[Job]
                val watchdog=if(simulation)null else launch {
                    while(isActive) { delay(1000);checkActiveFailure();if(nowMillis()-begin > 7_200_000)throw DeadlineExceeded() }
                }
                try {
                val issues = WorkflowValidator.validate(w)
                if (issues.isNotEmpty()) { issues.forEach { log(it.nodeId, ActionStatus.BLOCKED, it.message) }; throw Halt(RunStatus.BLOCKED) }
                if(semanticHash==null) { log(null,ActionStatus.BLOCKED,"직렬화할 수 없는 루틴 값입니다.");throw Halt(RunStatus.BLOCKED) }
                val x = context(0)
                if (simulation && w.trigger.type !in setOf(TriggerType.MANUAL, TriggerType.VOICE)) {
                    val eligibility = Conditions.evaluateTrigger(w.trigger, x)
                    if (eligibility.truth != Truth.TRUE) {
                        log(null, ActionStatus.BLOCKED, "가상 시작 조건: ${eligibility.reason} · ${eligibility.truth}")
                        throw Halt(RunStatus.BLOCKED)
                    }
                    log(null, ActionStatus.PENDING, "가상 시작 조건 충족 · 실제 진입 이벤트 발생을 뜻하지 않습니다. ${eligibility.reason}")
                }
                checkFilters(x)
                adapter.authorizeRun(w,runId,x)?.let { log(null, it.status, it.message); terminate(it) }
                checkFilters(context(0))
                elapsed = walk(w.nodes.single { it.kind == NodeKind.START }.id, null, 0, emptyList(), emptyList())
                } finally { watchdog?.cancel() }
              }
              true
            }
            if(finished!=true)throw DeadlineExceeded()
        } catch (e: Halt) { status = e.status
        } catch(e:ActiveFailure) { status=RunStatus.FAILED;withContext(NonCancellable) {
            try { log(e.failure.nodeId,ActionStatus.FAILED,e.failure.message,cleanup=true) } catch(_:JournalFailure) { }
        }
        } catch(_:DeadlineExceeded) { status=RunStatus.FAILED;terminalLog(ActionStatus.FAILED,"전체 실행 제한시간 2시간을 초과했습니다.")
        } catch(_:TimeoutCancellationException) { status=RunStatus.UNKNOWN;terminalLog(ActionStatus.UNKNOWN,"개별 작업의 응답 시간이 초과되어 결과를 확인할 수 없습니다.")
        } catch (_: CancellationException) { status = if(journalFailed.get())RunStatus.UNKNOWN else RunStatus.CANCELLED; terminalLog(if(journalFailed.get())ActionStatus.UNKNOWN else ActionStatus.CANCELLED, if(journalFailed.get())"실행 기록 저장 실패로 모든 분기를 중단했습니다." else "실행이 중지되었습니다.")
        } catch (_: Exception) { status = RunStatus.UNKNOWN; terminalLog(ActionStatus.UNKNOWN, "실행 결과를 확인할 수 없습니다.")
        } finally {
            withContext(NonCancellable) {
                try {
                    withTimeout(10_000) { adapter.stop(runId).forEach { r ->
                        log(null, r.status, r.message,cleanup=true)
                        if (r.status in setOf(ActionStatus.UNKNOWN, ActionStatus.FAILED)) status = RunStatus.UNKNOWN
                    } }
                } catch (_: Exception) { status = RunStatus.UNKNOWN; terminalLog(ActionStatus.UNKNOWN, "중지·정리 결과를 확인할 수 없습니다.") }
            }
        }
        if(journalFailed.get())status=RunStatus.UNKNOWN
        return RunResult(runId, w.id, w.revision, semanticHash.orEmpty(), simulation, status,
            events.toList().sortedBy { it.sequence }, if (simulation) elapsed else (nowMillis() - begin).coerceAtLeast(0))
    }
}

object WorkflowSimulator {
    suspend fun simulate(w: WorkflowDefinition, x: ExecutionContext): RunResult {
        val adapter = object: ActionAdapter {
            override suspend fun execute(request: ActionRequest, onProgress: suspend (ActionProgress) -> Unit) = ActionResult(ActionStatus.SIMULATED, "실행 예정 · ${request.action.type}")
            override suspend fun stop(runId: String) = emptyList<ActionResult>()
        }
        return WorkflowEngine(adapter, { x }, simulation = true).run(w)
    }
}
