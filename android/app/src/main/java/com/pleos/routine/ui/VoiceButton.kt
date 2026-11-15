package com.pleos.routine.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.pleos.routine.voice.VoiceInput
import com.pleos.routine.voice.VoiceState

/** Starts speech for one field. The heard text only fills that field; nothing is sent automatically. */
@Composable
fun VoiceButton(voice: VoiceInput, target: String, enabled: Boolean, modifier: Modifier = Modifier) {
    val state by voice.state.collectAsState()
    val mine = state.target == target
    Column(modifier) {
        when {
            mine && (state is VoiceState.Preparing || state is VoiceState.Listening) ->
                OutlinedButton(onClick = { voice.cancel() }, modifier = Modifier.testTag("voice-$target")) { Text("듣기 중지") }
            else ->
                OutlinedButton(onClick = { voice.dismissError(); voice.start(target) }, enabled = enabled && (state.target == null || state is VoiceState.Failed),
                    modifier = Modifier.testTag("voice-$target")) { Text("말로 입력") }
        }
        if (mine) when (val s = state) {
            is VoiceState.Preparing -> Text("음성 인식을 준비하는 중…")
            is VoiceState.Listening -> Text(if (s.partial.isBlank()) "듣고 있습니다. 말씀해 주세요." else "들은 말: ${s.partial}")
            is VoiceState.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error)
            else -> {}
        }
    }
}
