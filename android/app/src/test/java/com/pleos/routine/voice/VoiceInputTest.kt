package com.pleos.routine.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceInputTest {
    private class FakePort : SpeechPort {
        var started = 0
        var stopped = 0
        var events: SpeechEvents? = null
        override fun start(events: SpeechEvents) { started++; this.events = events }
        override fun stop() { stopped++ }
    }
    private fun voice(): Triple<VoiceInput, FakePort, MutableList<Pair<String, String>>> {
        val port = FakePort()
        val delivered = mutableListOf<Pair<String, String>>()
        return Triple(VoiceInput(port) { target, text -> delivered += target to text }, port, delivered)
    }

    @Test fun finalResultFillsOnlyTheRequestedFieldOnce() {
        val (v, port, delivered) = voice()
        v.start("request")
        assertEquals(VoiceState.Preparing("request"), v.state.value)
        port.events!!.ready()
        assertEquals("Ready means the driver can speak now", VoiceState.Listening("request", ""), v.state.value)
        port.events!!.text("겨", false); port.events!!.started()
        assertEquals("A late start signal must not erase the partial text", VoiceState.Listening("request", "겨"), v.state.value)
        port.events!!.text("겨울 차박", false)
        assertEquals(VoiceState.Listening("request", "겨울 차박"), v.state.value)
        assertTrue("A partial result must not fill the field", delivered.isEmpty())
        port.events!!.text("겨울 차박 루틴을 만들어 줘", true)
        assertEquals(listOf("request" to "겨울 차박 루틴을 만들어 줘"), delivered)
        assertEquals(VoiceState.Idle, v.state.value)
        port.events!!.ended(); port.events!!.text("늦은 결과", true)
        assertEquals("Callbacks after completion are ignored", 1, delivered.size)
    }

    @Test fun endingWithOnlyAPartialKeepsTheHeardTextButEmptyEndIsAFailure() {
        val (v, port, delivered) = voice()
        v.start("answer:0"); port.events!!.text("앞줄 왼쪽", false); port.events!!.ended()
        assertEquals(listOf("answer:0" to "앞줄 왼쪽"), delivered)
        v.start("answer:0"); port.events!!.ended()
        assertEquals(1, delivered.size)
        assertEquals(VoiceState.Failed("answer:0", "말소리를 인식하지 못했습니다. 다시 말하거나 직접 입력해 주세요."), v.state.value)
    }

    @Test fun errorsAreExplainedAndNothingIsDelivered() {
        val (v, port, delivered) = voice()
        v.start("request"); port.events!!.error("MIC_ERROR")
        val failed = v.state.value as VoiceState.Failed
        assertTrue(failed.message, failed.message.contains("마이크"))
        assertTrue(delivered.isEmpty())
        v.start("request"); port.events!!.error("SOMETHING_NEW")
        assertTrue((v.state.value as VoiceState.Failed).message.contains("SOMETHING_NEW"))
    }

    @Test fun cancelStopsTheEngineAndIgnoresLateCallbacksFromThatSession() {
        val (v, port, delivered) = voice()
        v.start("request"); val old = port.events!!
        v.cancel()
        assertEquals(1, port.stopped); assertEquals(VoiceState.Idle, v.state.value)
        old.text("취소 뒤 결과", true); old.error("TIMEOUT")
        assertTrue(delivered.isEmpty()); assertEquals(VoiceState.Idle, v.state.value)
        v.start("scoped"); val current = port.events!!
        old.text("이전 세션", true)
        current.text("10분 뒤 20%", true)
        assertEquals(listOf("scoped" to "10분 뒤 20%"), delivered)
    }

    @Test fun startingAgainWhileBusyIsIgnoredAndBlankFinalIsAFailure() {
        val (v, port, delivered) = voice()
        v.start("request"); v.start("answer:1")
        assertEquals(1, port.started); assertEquals("request", v.state.value.target)
        port.events!!.text("   ", true)
        assertTrue(delivered.isEmpty()); assertTrue(v.state.value is VoiceState.Failed)
    }

    @Test fun deliveredTextIsBoundedLikeTheRequestField() {
        val (v, port, delivered) = voice()
        v.start("request"); port.events!!.text("가".repeat(5000), true)
        assertEquals(4000, delivered.single().second.length)
    }
}
