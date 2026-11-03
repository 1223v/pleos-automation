package com.pleos.routine.vehicle

import com.pleos.routine.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicReference

enum class SeatCommand { FORWARD, BACKWARD, STOP }
data class SeatMotionPolicy(val min:Int,val max:Int,val windowMillis:Long,val maxMoveMillis:Long,val sampleMillis:Long,val evidenceId:String,val directions:Set<MoveDirection>,val forwardIncreases:Boolean)
data class MotionAck(val ok:Boolean,val atMillis:Long,val generation:Long)
interface SeatMotionPort {
    fun generation():Long
    fun pending():Boolean
    suspend fun persist():Boolean
    suspend fun clear():Boolean
    suspend fun safe():Boolean
    suspend fun position():Int?
    fun dispatch(command:SeatCommand):Deferred<MotionAck>
}
class SeatMotionCoordinator(private val now:()->Long,private val watchdogDispatcher:CoroutineDispatcher=Dispatchers.Default) {
    private val gate=Mutex()
    private class Active(val stopNow:()->Unit,val result:CompletableDeferred<ActionResult>)
    private val active=AtomicReference<Active?>(null)
    suspend fun stop():ActionResult? {
        val a=active.get() ?: return null
        a.stopNow()
        return withTimeoutOrNull(4500) { a.result.await() } ?: ActionResult(ActionStatus.UNKNOWN,"STOP 관찰 시간 초과")
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun move(port:SeatMotionPort,policy:SeatMotionPolicy,direction:MoveDirection,progress:suspend(ActionProgress)->Unit):ActionResult {
        fun blocked()=ActionResult(ActionStatus.BLOCKED,"시트 범위·교정·주차 상태 또는 이전 명령을 확인하세요.")
        if(!gate.tryLock())return blocked()
        try {
            if(port.pending() || policy.min>=policy.max || policy.windowMillis !in 1..policy.maxMoveMillis || policy.maxMoveMillis !in 1..1000 ||
                policy.sampleMillis !in 50..500 || policy.windowMillis+policy.sampleMillis>policy.maxMoveMillis || policy.evidenceId.isBlank() || direction !in policy.directions)return blocked()
            val generation=port.generation()
            fun inside(v:Int?)=v!=null && v in policy.min..policy.max
            fun canMove(v:Int?)=inside(v) && if((direction==MoveDirection.FORWARD)==policy.forwardIncreases)v!!<policy.max else v!!>policy.min
            if(!port.safe() || !canMove(port.position()))return blocked()
            if(!port.persist())return blocked()
            // Repeat every live gate after asynchronous queries and durable IO, immediately before dispatch.
            val before=port.position()
            if(!canMove(before) || !port.safe() || generation!=port.generation()) {
                port.clear();return blocked()
            }
            currentCoroutineContext().ensureActive()
            val sent=now();var stoppedAt:Long?=null;var stopAck:Deferred<MotionAck>?=null
            val ordering=Any();val completed=CompletableDeferred<ActionResult>()
            fun stopNow()=synchronized(ordering) {
                if(stoppedAt==null) {
                    stoppedAt=now()
                    stopAck=try { port.dispatch(SeatCommand.STOP) } catch(_:Exception) { CompletableDeferred(MotionAck(false,now(),port.generation())) }
                }
            }
            var moveAck:Deferred<MotionAck>?=null
            var healthy=true;var moved=false;var stopped=false
            var lastPosition=before!!
            val increasing=(direction==MoveDirection.FORWARD)==policy.forwardIncreases
            var outcome=ActionResult(ActionStatus.UNKNOWN,"이동 또는 정지 순서를 확인하지 못했습니다. 복구 기록을 유지합니다.")
            val current=Active(::stopNow,completed)
            try {
                // Non-suspending dispatch under the same ordering lock prevents STOP-before-MOVE races.
                synchronized(ordering) {
                    moveAck=port.dispatch(if(direction==MoveDirection.FORWARD)SeatCommand.FORWARD else SeatCommand.BACKWARD)
                    active.set(current)
                }
                coroutineScope {
                    val watchdog=launch(watchdogDispatcher) { delay((sent+policy.windowMillis-now()).coerceAtLeast(0));stopNow() }
                    try {
                        withTimeoutOrNull(policy.windowMillis) {
                            var reported=false
                            while(synchronized(ordering) { stoppedAt==null }) {
                                if(!port.safe() || port.generation()!=generation) { healthy=false;stopNow();break }
                                val ack=moveAck
                                if(!reported && ack?.isCompleted==true) {
                                    val response=ack.getCompleted()
                                    if(!response.ok || response.generation!=generation) { healthy=false;stopNow();break }
                                    reported=true;progress(ActionProgress(ActionStatus.ACKNOWLEDGED,"제한 이동 요청 응답 · STOP 관찰 전"))
                                }
                                delay(policy.sampleMillis)
                                val value=port.position()
                                if(!inside(value)) { healthy=false;stopNow();break }
                                if((increasing && value!!<lastPosition) || (!increasing && value!!>lastPosition)) { healthy=false;stopNow();break }
                                moved=moved || value!=before;lastPosition=value!!
                                if(!canMove(value)) { stopNow();break }
                            }
                        }
                    } finally { stopNow();watchdog.cancelAndJoin() }
                }
            } catch(e:CancellationException) { healthy=false;throw e
            } catch(_:Exception) { healthy=false
            } finally {
                withContext(NonCancellable) {
                    stopNow()
                    try {
                        stopped=withTimeoutOrNull(1500) {
                            val response=stopAck!!.await()
                            if(!response.ok || response.generation!=generation || response.atMillis !in stoppedAt!!..(sent+policy.maxMoveMillis))return@withTimeoutOrNull false
                            val samples=mutableListOf<Int>()
                            repeat(3) { index ->
                                delay(policy.sampleMillis);val value=port.position()
                                if(!inside(value) || index==0 && now()>sent+policy.maxMoveMillis)return@withTimeoutOrNull false
                                if((increasing && value!!<lastPosition) || (!increasing && value!!>lastPosition))healthy=false
                                samples+=value!!;moved=moved || value!=before
                            }
                            samples.distinct().size==1
                        }==true
                        val ack=moveAck?.takeIf { it.isCompleted && !it.isCancelled }?.getCompleted()
                        val ordered=ack?.ok==true && ack.generation==generation && ack.atMillis in sent..stoppedAt!! &&
                            stoppedAt!!-sent in 0..policy.maxMoveMillis && port.generation()==generation
                        // Stable positions alone cannot dismiss an SDK reconnect queue or late MOVE.
                        val cleared=stopped && ordered && port.clear()
                        outcome=if(cleared && healthy && moved)ActionResult(ActionStatus.VERIFIED,"위치 변화 및 기한 내 STOP·안정 관찰 확인")
                            else ActionResult(ActionStatus.UNKNOWN,"이동·정지 결과 불명 · ${if(cleared)"명령 순서와 STOP은 확인됨" else "복구 기록 유지"}")
                    } catch(_:Exception) { outcome=ActionResult(ActionStatus.UNKNOWN,"STOP 또는 복구 기록 확인 실패") }
                    completed.complete(outcome);active.compareAndSet(current,null)
                }
            }
            return outcome
        } finally { gate.unlock() }
    }
}
