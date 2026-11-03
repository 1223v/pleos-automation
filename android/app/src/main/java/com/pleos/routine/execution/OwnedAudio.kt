package com.pleos.routine.execution

import com.pleos.routine.core.*
import kotlinx.coroutines.*

/** Only this app's player is owned here; platform focus management remains in Media3. */
internal interface OwnedAudioPort {
    val isPlaying:Boolean
    val failure:String?
    fun start()
    fun release()
}
internal class OwnedAudio(private val create:()->OwnedAudioPort,private val nowMillis:()->Long) {
    private var port:OwnedAudioPort?=null
    private var nodeId:String?=null
    val isPlaying get()=port?.isPlaying==true
    fun activeFailure():ActiveActionFailure?=port?.failure?.let { ActiveActionFailure(nodeId!!,it) }
    fun stop() { val old=port;port=null;nodeId=null;old?.release() }
    suspend fun play(sourceNodeId:String):ActionResult {
        stop()
        val result=try {
            val owned=create();port=owned;nodeId=sourceNodeId
            owned.start()
            awaitStarted(owned)
        } catch(e:CancellationException) { stop();throw e
        } catch(_:Exception) { ActionResult(ActionStatus.FAILED,"앱 소유 음원을 준비하지 못했습니다.") }
        // Cleanup errors propagate as an uncertain result, rather than being hidden as a load error.
        if(result.status!=ActionStatus.VERIFIED)stop()
        return result
    }
    private suspend fun awaitStarted(owned:OwnedAudioPort):ActionResult {
        val deadline=nowMillis()+5000
        while(true) {
            currentCoroutineContext().ensureActive()
            owned.failure?.let { return ActionResult(ActionStatus.FAILED,it) }
            if(owned.isPlaying)return ActionResult(ActionStatus.VERIFIED,"앱 소유 음원 재생 상태 확인")
            if(nowMillis()>=deadline)return ActionResult(ActionStatus.FAILED,"음원 재생을 제한시간 안에 확인하지 못했습니다.")
            delay(minOf(100L,deadline-nowMillis()))
        }
    }
}
