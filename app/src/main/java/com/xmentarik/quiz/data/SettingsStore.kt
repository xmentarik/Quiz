package com.xmentarik.quiz.data

import android.content.Context
import androidx.core.content.edit
import com.xmentarik.quiz.audio.AudioSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _audio = MutableStateFlow(
        AudioSettings(
            soundEnabled = prefs.getBoolean(KEY_SOUND, true),
            musicEnabled = prefs.getBoolean(KEY_MUSIC, true),
        ),
    )
    val audio: StateFlow<AudioSettings> = _audio.asStateFlow()

    fun updateAudio(settings: AudioSettings) {
        prefs.edit {
            putBoolean(KEY_SOUND, settings.soundEnabled)
            putBoolean(KEY_MUSIC, settings.musicEnabled)
        }
        _audio.value = settings
    }

    private companion object {
        const val KEY_SOUND = "sound_enabled"
        const val KEY_MUSIC = "music_enabled"
    }
}
