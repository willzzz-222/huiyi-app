package com.example.personalmemories.media

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

class AudioCoordinator(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var voicePlayer: ExoPlayer? = null
    private var startedAt: Long = 0L
    var onAudioFocusNeeded: (() -> Unit)? = null

    fun startRecording(output: File) {
        stopVoice()
        onAudioFocusNeeded?.invoke()
        output.parentFile?.mkdirs()
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioChannels(1)
            setAudioEncodingBitRate(96_000)
            setAudioSamplingRate(44_100)
            setOutputFile(output.absolutePath)
            prepare()
            start()
        }
        startedAt = System.currentTimeMillis()
    }

    fun stopRecording(): Long {
        val elapsed = System.currentTimeMillis() - startedAt
        recorder?.runCatching { stop() }
        recorder?.runCatching { release() }
        recorder = null
        startedAt = 0L
        return elapsed
    }

    fun cancelRecording() {
        recorder?.runCatching { stop() }
        recorder?.runCatching { release() }
        recorder = null
        startedAt = 0L
    }

    fun playVoice(file: File) {
        stopVoice()
        onAudioFocusNeeded?.invoke()
        voicePlayer = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true
        }
    }

    fun stopVoice() {
        voicePlayer?.release()
        voicePlayer = null
    }

    fun release() {
        cancelRecording()
        stopVoice()
    }
}
