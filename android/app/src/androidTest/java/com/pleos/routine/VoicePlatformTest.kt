package com.pleos.routine

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pleos.routine.voice.PleosSpeechPort
import com.pleos.routine.voice.SpeechEvents
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

/**
 * Explicitly selected emulator-only probe: the Pleos SpeechToText service must answer an ON_DEVICE request with
 * any callback. It records what the service reported; it does not claim a spoken sentence was recognised.
 */
@RunWith(AndroidJUnit4::class)
class VoicePlatformTest {
    @Test fun onDeviceRequestReachesThePleosSpeechService():Unit=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("voiceProbe")=="emulator-only")
        assumeTrue(Build.HARDWARE=="ranchu" && Build.FINGERPRINT.startsWith("Pleos/crp_avd/"))
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val seen=Collections.synchronizedList(mutableListOf<String>())
        val port=withContext(Dispatchers.Main) { PleosSpeechPort(context) }
        val events=object:SpeechEvents {
            override fun ready() { seen+="ready" }
            override fun started() { seen+="started" }
            override fun text(value:String,completed:Boolean) { seen+="text(completed=$completed,length=${value.length})" }
            override fun ended() { seen+="ended" }
            override fun error(type:String) { seen+="error:$type" }
        }
        try {
            withContext(Dispatchers.Main) { port.start(events) }
            withTimeoutOrNull(20_000) { while(seen.isEmpty())delay(100) }
            delay(3000)
            Log.i("RoutineEvidence","VOICE ON_DEVICE callbacks=${seen.toList()}")
            assertTrue("The speech service must answer the request with some callback",seen.isNotEmpty())
        } finally {
            withContext(Dispatchers.Main) { port.stop() }
        }
    }
}
