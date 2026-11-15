package com.pleos.routine.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Callbacks of one recognition session, already on the main thread. */
interface SpeechEvents {
    fun ready()
    fun started()
    fun text(value: String, completed: Boolean)
    fun ended()
    fun error(type: String)
}

/** App-owned boundary over the Pleos SpeechToText SDK so the session logic can be tested without the service. */
interface SpeechPort {
    fun start(events: SpeechEvents)
    fun stop()
}

sealed interface VoiceState {
    val target: String?
    data object Idle : VoiceState { override val target: String? = null }
    data class Preparing(override val target: String) : VoiceState
    data class Listening(override val target: String, val partial: String) : VoiceState
    data class Failed(override val target: String, val message: String) : VoiceState
}

/**
 * Speech only fills the text field it was started for. It never sends anything to the AI or the vehicle;
 * the driver reviews the text and presses the existing button.
 */
class VoiceInput(private val port: SpeechPort, private val deliver: (target: String, text: String) -> Unit) {
    private val mutable = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = mutable.asStateFlow()
    private var session = 0
    private val busy get() = mutable.value is VoiceState.Preparing || mutable.value is VoiceState.Listening

    fun start(target: String) {
        if (busy) return
        val id = ++session
        mutable.value = VoiceState.Preparing(target)
        port.start(object : SpeechEvents {
            private fun current() = id == session && busy
            // The service reports ready once the microphone can take speech; on the emulator it may stop here.
            override fun ready() { if (current() && mutable.value is VoiceState.Preparing) mutable.value = VoiceState.Listening(target, "") }
            override fun started() { if (current() && mutable.value is VoiceState.Preparing) mutable.value = VoiceState.Listening(target, "") }
            override fun text(value: String, completed: Boolean) {
                if (!current()) return
                if (!completed) { mutable.value = VoiceState.Listening(target, value); return }
                finish(target, value)
            }
            override fun ended() {
                if (!current()) return
                finish(target, (mutable.value as? VoiceState.Listening)?.partial.orEmpty())
            }
            override fun error(type: String) {
                if (!current()) return
                session++
                mutable.value = VoiceState.Failed(target, message(type))
            }
        })
    }

    private fun finish(target: String, text: String) {
        session++
        val heard = text.trim()
        if (heard.isEmpty()) {
            mutable.value = VoiceState.Failed(target, "말소리를 인식하지 못했습니다. 다시 말하거나 직접 입력해 주세요.")
            return
        }
        mutable.value = VoiceState.Idle
        deliver(target, heard.take(4000))
    }

    /** Ends the session; anything the engine reports afterwards is ignored. */
    fun cancel() {
        val wasBusy = busy
        session++
        mutable.value = VoiceState.Idle
        if (wasBusy) port.stop()
    }

    fun dismissError() { if (mutable.value is VoiceState.Failed) mutable.value = VoiceState.Idle }

    companion object {
        fun message(type: String): String = when (type) {
            "MIC_ERROR" -> "마이크를 사용할 수 없습니다. 마이크 상태와 개인정보 보호 설정을 확인하세요."
            "AUDIO_ERROR" -> "입력 음성을 처리하지 못했습니다. 다시 말해 주세요."
            "PERMISSION_DENIED" -> "음성 인식 서비스 사용 권한이 없습니다."
            "ON_DEVICE_ENGINE_ERROR" -> "차량의 음성 인식 엔진을 사용할 수 없습니다."
            "UNAVAILABLE_LANGUAGE" -> "한국어 음성 인식을 사용할 수 없습니다."
            "NOT_SUPPORTED_API" -> "이 환경에서는 음성 인식을 지원하지 않습니다."
            "TIMEOUT" -> "음성 인식 시간이 초과되었습니다."
            "INVALID_REQUEST_ERROR" -> "음성 인식 요청 순서가 올바르지 않습니다. 잠시 후 다시 시도하세요."
            "NETWORK_ERROR", "SERVER_INTERNAL_ERROR", "UNAUTHENTICATED" -> "음성 인식 서비스에 연결하지 못했습니다."
            else -> "음성 인식 오류($type)가 발생했습니다."
        } + " 글로 입력할 수 있습니다."
    }
}
