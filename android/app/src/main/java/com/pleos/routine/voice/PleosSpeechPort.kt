package com.pleos.routine.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale

/**
 * Pleos SpeechToText 3.3.1 in ON_DEVICE mode: no network and no app registration (official SDK guide).
 * One SDK instance lives for the process; each start() forwards callbacks to the current session only.
 */
class PleosSpeechPort(context: Context) : SpeechPort {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var sdk: SpeechToText? = null
    private var initialized = false
    private var initializing = false
    private var current: SpeechEvents? = null

    private val listener = object : ResultListener {
        override fun onReady() = post { it.ready() }
        override fun onStartedRecognition() = post { it.started() }
        override fun onUpdated(stt: String, completed: Boolean) = post { it.text(stt, completed) }
        override fun onUpdatedEpdData(on: Long, off: Long) {}
        override fun onUpdatedRms(rms: Double) {}
        override fun onEndedRecognition() = post { it.ended() }
        override fun onError(errorType: ErrorType) = post { it.error(errorType.name) }
    }

    private fun post(block: (SpeechEvents) -> Unit) {
        main.post { current?.let(block) }
    }

    override fun start(events: SpeechEvents) {
        current = events
        if (initialized) { request(); return }
        if (initializing) return
        initializing = true
        val created = try { SpeechToText(app, Mode.ON_DEVICE) } catch (e: Exception) {
            initializing = false
            Log.w(TAG, "create failed", e)
            post { it.error("NOT_SUPPORTED_API") }
            return
        }
        sdk = created
        try {
            created.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    main.post {
                        initializing = false
                        initialized = true
                        Log.i(TAG, "initialized mode=ON_DEVICE")
                        try { created.addListener(listener) } catch (e: Exception) { Log.w(TAG, "addListener failed", e) }
                        if (current != null) request()
                    }
                }
                override fun onFailure(errorType: ErrorType) {
                    main.post {
                        initializing = false
                        Log.w(TAG, "initialize failed $errorType")
                        current?.error(errorType.name)
                    }
                }
            })
        } catch (e: Exception) {
            initializing = false
            Log.w(TAG, "initialize threw", e)
            post { it.error("NOT_SUPPORTED_API") }
        }
    }

    private fun request() {
        val engine = sdk ?: return
        try {
            engine.setLocale(Locale.KOREA)
            engine.request()
            Log.i(TAG, "request sent locale=ko-KR")
        } catch (e: Exception) {
            Log.w(TAG, "request threw", e)
            post { it.error("INVALID_REQUEST_ERROR") }
        }
    }

    override fun stop() {
        current = null
        if (!initialized) return
        try { sdk?.stop() } catch (e: Exception) { Log.w(TAG, "stop threw", e) }
    }

    private companion object { const val TAG = "RoutineVoice" }
}
