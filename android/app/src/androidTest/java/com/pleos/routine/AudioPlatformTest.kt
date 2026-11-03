package com.pleos.routine

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pleos.routine.core.ActionStatus
import com.pleos.routine.execution.Media3OwnedAudioPort
import com.pleos.routine.execution.OwnedAudio
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Media3/AVD calls; the competing focus client is controlled by this test, not another app. */
@RunWith(AndroidJUnit4::class)
class AudioPlatformTest {
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RoutineApplication
    @Test fun missingBundledAssetFailsAndReleasesActualPlayer():Unit=runBlocking {
        assumeTrue(!app.runtime.busy.value && !app.runtime.isOwnAudioPlaying)
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        val audio=OwnedAudio({Media3OwnedAudioPort(app,{},"missing-test-tone.wav")},SystemClock::elapsedRealtime)
        try {
            val result=withContext(Dispatchers.Main) { audio.play("missing-asset") }
            assertEquals(ActionStatus.FAILED,result.status)
            assertFalse(withContext(Dispatchers.Main) { audio.isPlaying })
        } finally { withContext(Dispatchers.Main) { audio.stop() };scenario.close() }
    }
    @Test fun controlledFocusClientPausesResumesAndPermanentlyStopsOwnAudio():Unit=runBlocking {
        assumeTrue(!app.runtime.busy.value && !app.runtime.isOwnAudioPlaying)
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        val manager=app.getSystemService(AudioManager::class.java)
        var state=""
        val audio=OwnedAudio({Media3OwnedAudioPort(app,{state=it})},SystemClock::elapsedRealtime)
        fun request(gain:Int)=AudioFocusRequest.Builder(gain).setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { }.build()
        val transient=request(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        val permanent=request(AudioManager.AUDIOFOCUS_GAIN)
        try {
            assertEquals(ActionStatus.VERIFIED,withContext(Dispatchers.Main) { audio.play("focus-test") }.status)
            assumeTrue("AVD must grant the controlled transient focus client",withContext(Dispatchers.Main) { manager.requestAudioFocus(transient) }==AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            withTimeout(5000) { while(withContext(Dispatchers.Main) { audio.isPlaying || !state.contains("일시정지") })delay(50) }
            assertNull(withContext(Dispatchers.Main) { audio.activeFailure() })
            withContext(Dispatchers.Main) { manager.abandonAudioFocusRequest(transient) }
            withTimeout(5000) { while(!withContext(Dispatchers.Main) { audio.isPlaying })delay(50) }
            assumeTrue("AVD must grant the controlled permanent focus client",withContext(Dispatchers.Main) { manager.requestAudioFocus(permanent) }==AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            withTimeout(5000) { while(withContext(Dispatchers.Main) { audio.activeFailure()==null })delay(50) }
            assertFalse(withContext(Dispatchers.Main) { audio.isPlaying })
        } finally {
            withContext(Dispatchers.Main) { manager.abandonAudioFocusRequest(transient);manager.abandonAudioFocusRequest(permanent);audio.stop() }
            scenario.close()
        }
    }
}
