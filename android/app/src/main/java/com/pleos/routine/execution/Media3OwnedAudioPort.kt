package com.pleos.routine.execution

import android.content.Context
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession

/** Media3 owns focus acquisition, transient pause/duck and resume. Never force play on focus gain. */
internal class Media3OwnedAudioPort(context:Context,private val onState:(String)->Unit,assetName:String="rest-tone.wav"):OwnedAudioPort {
    private val asset=assetName.also { require(it.matches(Regex("[a-zA-Z0-9_-]+\\.wav"))) }
    private val player=ExoPlayer.Builder(context).build()
    private val session:MediaSession
    private var released=false
    override var failure:String?=null
        private set
    override val isPlaying get()=!released && player.isPlaying
    init {
        try {
            player.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true)
            session=MediaSession.Builder(context,player).build()
            player.addListener(object:Player.Listener {
                override fun onPlayerError(error:PlaybackException) {
                    if(released)return
                    failure="앱 소유 음원 재생 중 오류가 발생했습니다.";publishState()
                }
                override fun onPlayWhenReadyChanged(ready:Boolean,reason:Int) {
                    if(released)return
                    if(!ready && reason==Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)
                        failure="다른 앱으로 오디오 포커스가 이동해 음원을 중지했습니다."
                    publishState()
                }
                override fun onPlaybackSuppressionReasonChanged(reason:Int) { publishState() }
                override fun onIsPlayingChanged(playing:Boolean) { publishState() }
            })
        } catch(e:Exception) { player.release();throw e }
    }
    private fun publishState() {
        if(released)return
        onState(failure ?: when {
            player.playbackSuppressionReason==Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS -> "음원 일시정지 · 오디오 포커스 대기"
            player.isPlaying -> "앱 소유 음원 재생 중"
            else -> "음원 준비 또는 일시정지"
        })
    }
    override fun start() {
        player.repeatMode=Player.REPEAT_MODE_ONE
        player.setMediaItem(MediaItem.fromUri("asset:///$asset"));player.prepare();player.play();publishState()
    }
    override fun release() {
        if(released)return
        released=true
        try { session.release() } finally { player.release();onState("앱 소유 음원 중지") }
    }
}
