package com.pleos.routine.execution

import com.pleos.routine.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OwnedAudioTest {
    private class Port:OwnedAudioPort {
        override var isPlaying=true
        override var failure:String?=null
        var starts=0;var releases=0
        override fun start() { starts++ }
        override fun release() { releases++ }
    }
    @Test fun startsAndReleasesOnlyItsOwnPlayerOnce()=runTest {
        val own=Port();val other=Port();val audio=OwnedAudio({own},{testScheduler.currentTime})
        assertEquals(ActionStatus.VERIFIED,audio.play("node").status)
        audio.stop();audio.stop()
        assertEquals(1,own.starts);assertEquals(1,own.releases);assertEquals(0,other.releases)
    }
    @Test fun loadFailureIsFailedAndReleasesThePlayer()=runTest {
        val port=Port().apply { isPlaying=false;failure="음원 로드 실패" };val audio=OwnedAudio({port},{testScheduler.currentTime})
        val result=audio.play("node")
        assertEquals(ActionStatus.FAILED,result.status);assertEquals("음원 로드 실패",result.message)
        assertEquals(1,port.releases);assertNull(audio.activeFailure())
    }
    @Test fun playbackConfirmationTimeoutReleasesItsPlayer()=runTest {
        val port=Port().apply { isPlaying=false };val audio=OwnedAudio({port},{testScheduler.currentTime})
        assertEquals(ActionStatus.FAILED,audio.play("node").status)
        assertEquals(5000L,testScheduler.currentTime);assertEquals(1,port.releases)
    }
    @Test fun cancellationWhileLoadingReleasesWithoutRestart()=runTest {
        val port=Port().apply { isPlaying=false };val audio=OwnedAudio({port},{testScheduler.currentTime})
        val job=launch { audio.play("node") }
        runCurrent();advanceTimeBy(300);job.cancelAndJoin()
        assertEquals(1,port.starts);assertEquals(1,port.releases);assertNull(audio.activeFailure())
    }
    @Test fun lateFailureKeepsItsSourceNodeAndDoesNotBecomeUserCancellation()=runTest {
        val port=Port();val audio=OwnedAudio({port},{testScheduler.currentTime})
        assertEquals(ActionStatus.VERIFIED,audio.play("audio-node").status)
        port.failure="음원 재생 중 오류"
        assertEquals(ActiveActionFailure("audio-node","음원 재생 중 오류"),audio.activeFailure())
    }
    @Test fun replacedPlayerCannotPoisonCurrentPlayerWithALateError()=runTest {
        val old=Port();val current=Port();val ports=ArrayDeque(listOf(old,current));val audio=OwnedAudio({ports.removeFirst()},{testScheduler.currentTime})
        audio.play("old");audio.play("current");old.failure="late old callback"
        assertEquals(1,old.releases);assertNull(audio.activeFailure());assertTrue(audio.isPlaying)
        audio.stop();assertEquals(1,current.releases)
    }
    @Test fun temporaryFocusPauseDoesNotForceReplayAndPermanentLossIsFailure()=runTest {
        val port=Port();val audio=OwnedAudio({port},{testScheduler.currentTime})
        audio.play("node");port.isPlaying=false
        assertNull(audio.activeFailure());assertFalse(audio.isPlaying)
        port.isPlaying=true
        assertEquals(1,port.starts);assertTrue(audio.isPlaying)
        port.isPlaying=false;port.failure="다른 앱으로 오디오 포커스가 이동해 음원을 중지했습니다."
        assertNotNull(audio.activeFailure());assertEquals(1,port.starts)
    }
    @Test fun factoryFailureIsReportedWithoutOwningAnExternalResource()=runTest {
        val audio=OwnedAudio({throw IllegalStateException("platform failed")},{testScheduler.currentTime})
        assertEquals(ActionStatus.FAILED,audio.play("node").status);assertFalse(audio.isPlaying)
        audio.stop();assertNull(audio.activeFailure())
    }
}
