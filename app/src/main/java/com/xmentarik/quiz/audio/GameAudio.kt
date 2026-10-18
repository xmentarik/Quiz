package com.xmentarik.quiz.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import com.xmentarik.quiz.R
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Plays sound effects (SoundPool) and looping background music (MediaPlayer) with fades. */
class GameAudio(context: Context) {
    private val appContext = context.applicationContext
    private val scope = MainScope()
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val loadedSounds = mutableSetOf<Int>()
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(attributes)
        .build()
        .apply { setOnLoadCompleteListener { _, id, status -> if (status == 0) loadedSounds += id } }
    private val soundIds: Map<SoundCue, Int> = SoundCue.entries.associateWith { soundPool.load(appContext, it.rawRes, 1) }

    private var settings = FeedbackSettings()
    private var paused = false
    private var wantedTrack: MusicTrack? = null
    private var playerTrack: MusicTrack? = null
    private var player: MediaPlayer? = null

    fun applySettings(newSettings: FeedbackSettings) {
        if (newSettings == settings) return
        settings = newSettings
        syncMusic()
    }

    fun play(cue: SoundCue) {
        if (!settings.soundEnabled) return
        val id = soundIds[cue] ?: return
        if (id in loadedSounds) soundPool.play(id, cue.volume, cue.volume, 1, 0, 1f)
    }

    fun setMusic(track: MusicTrack?) {
        if (track == wantedTrack) return
        wantedTrack = track
        syncMusic()
    }

    fun pause() {
        paused = true
        player?.takeIf { it.isPlaying }?.pause()
    }

    fun resume() {
        paused = false
        syncMusic()
    }

    fun release() {
        scope.cancel()
        player?.release()
        player = null
        soundPool.release()
    }

    private fun syncMusic() {
        val target = wantedTrack.takeIf { settings.musicEnabled && !paused }
        if (target == playerTrack) {
            player?.takeIf { target != null && !it.isPlaying }?.start()
            return
        }
        player?.let(::fadeOutAndRelease)
        player = null
        playerTrack = target
        if (target == null) return

        player = MediaPlayer.create(appContext, target.rawRes, attributes, AudioManager.AUDIO_SESSION_ID_GENERATE)
            ?.apply {
                isLooping = true
                setVolume(0f, 0f)
                start()
                fade(this, from = 0f, to = MUSIC_VOLUME)
            }
    }

    private fun fadeOutAndRelease(old: MediaPlayer) {
        fade(old, from = MUSIC_VOLUME, to = 0f) { old.release() }
    }

    private fun fade(target: MediaPlayer, from: Float, to: Float, onDone: () -> Unit = {}) {
        scope.launch {
            for (step in 1..FADE_STEPS) {
                val volume = from + (to - from) * step / FADE_STEPS
                runCatching { target.setVolume(volume, volume) }
                delay(FADE_MS / FADE_STEPS)
            }
            onDone()
        }
    }

    private val SoundCue.rawRes: Int
        get() = when (this) {
            SoundCue.SELECT -> R.raw.sfx_select
            SoundCue.FINAL_ANSWER -> R.raw.sfx_final_answer
            SoundCue.CORRECT -> R.raw.sfx_correct
            SoundCue.MILESTONE -> R.raw.sfx_milestone
            SoundCue.WRONG -> R.raw.sfx_wrong
            SoundCue.TIME_UP -> R.raw.sfx_time_up
            SoundCue.TICK -> R.raw.sfx_tick
            SoundCue.LIFELINE -> R.raw.sfx_lifeline
            SoundCue.NEXT_QUESTION -> R.raw.sfx_next_question
            SoundCue.GAME_START -> R.raw.sfx_game_start
            SoundCue.WIN -> R.raw.sfx_win
            SoundCue.WALK_AWAY -> R.raw.sfx_walk_away
        }

    private val MusicTrack.rawRes: Int
        get() = when (this) {
            MusicTrack.MENU -> R.raw.music_menu
            MusicTrack.CALM -> R.raw.music_calm
            MusicTrack.TENSE -> R.raw.music_tense
            MusicTrack.INTENSE -> R.raw.music_intense
        }

    private companion object {
        const val MUSIC_VOLUME = 0.45f
        const val FADE_MS = 500L
        const val FADE_STEPS = 10
    }
}
