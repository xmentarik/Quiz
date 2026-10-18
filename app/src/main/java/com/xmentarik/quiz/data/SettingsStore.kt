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

    val playerName: String? get() = prefs.getString(KEY_PLAYER_NAME, null)

    /** Codes of the challenges this player created or joined, newest first. */
    val myChallenges: List<String>
        get() = prefs.getString(KEY_CHALLENGES, null)?.split(',')?.filter { it.isNotBlank() }.orEmpty()

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

    fun updatePlayerName(name: String) = prefs.edit { putString(KEY_PLAYER_NAME, name) }

    /** Ids of the bundled questions shown recently, oldest first; the same ids are used in every language. */
    val recentQuestionIds: List<String>
        get() = prefs.getString(KEY_RECENT_QUESTIONS, null)?.split(',')?.filter { it.isNotBlank() }.orEmpty()

    fun rememberQuestions(ids: List<String>) {
        val updated = (recentQuestionIds - ids.toSet() + ids).takeLast(MAX_RECENT_QUESTIONS)
        prefs.edit { putString(KEY_RECENT_QUESTIONS, updated.joinToString(",")) }
    }

    /** When the in-app review prompt was last requested (System.currentTimeMillis), or null. */
    var lastReviewPromptAt: Long?
        get() = prefs.getLong(KEY_REVIEW_PROMPT, 0L).takeIf { it > 0L }
        set(value) = prefs.edit { putLong(KEY_REVIEW_PROMPT, value ?: 0L) }

    /** Remembers a challenge code and returns the updated list. */
    fun rememberChallenge(code: String): List<String> {
        val updated = (listOf(code) + myChallenges.filter { it != code }).take(MAX_CHALLENGES)
        prefs.edit { putString(KEY_CHALLENGES, updated.joinToString(",")) }
        return updated
    }

    private companion object {
        const val KEY_SOUND = "sound_enabled"
        const val KEY_MUSIC = "music_enabled"
        const val KEY_VIBRATION = "vibration_enabled"
        const val KEY_LANGUAGE = "language"
        const val KEY_PLAYER_NAME = "player_name"
        const val KEY_CHALLENGES = "my_challenges"
        const val MAX_CHALLENGES = 20
        const val KEY_RECENT_QUESTIONS = "recent_questions"
        const val KEY_REVIEW_PROMPT = "review_prompt_at"
        /** About 60 games: long enough to never see a repeat soon, short enough to cycle the whole bank. */
        const val MAX_RECENT_QUESTIONS = 900
    }
}
