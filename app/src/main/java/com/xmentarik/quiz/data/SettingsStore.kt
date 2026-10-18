package com.xmentarik.quiz.data

import android.content.Context
import android.content.res.Resources
import androidx.core.content.edit
import com.xmentarik.quiz.audio.FeedbackSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _feedback = MutableStateFlow(
        FeedbackSettings(
            soundEnabled = prefs.getBoolean(KEY_SOUND, true),
            musicEnabled = prefs.getBoolean(KEY_MUSIC, true),
            vibrationEnabled = prefs.getBoolean(KEY_VIBRATION, true),
        ),
    )
    val feedback: StateFlow<FeedbackSettings> = _feedback.asStateFlow()

    private val _language = MutableStateFlow(
        AppLanguage.fromCode(prefs.getString(KEY_LANGUAGE, null))
            ?: AppLanguage.defaultFor(Resources.getSystem().configuration.locales[0].language),
    )
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun updateFeedback(settings: FeedbackSettings) {
        prefs.edit {
            putBoolean(KEY_SOUND, settings.soundEnabled)
            putBoolean(KEY_MUSIC, settings.musicEnabled)
            putBoolean(KEY_VIBRATION, settings.vibrationEnabled)
        }
        _feedback.value = settings
    }

    fun updateLanguage(language: AppLanguage) {
        prefs.edit { putString(KEY_LANGUAGE, language.code) }
        _language.value = language
    }

    private companion object {
        const val KEY_SOUND = "sound_enabled"
        const val KEY_MUSIC = "music_enabled"
        const val KEY_VIBRATION = "vibration_enabled"
        const val KEY_LANGUAGE = "language"
    }
}
