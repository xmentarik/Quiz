package com.xmentarik.quiz.audio

import com.xmentarik.quiz.game.GamePhase
import com.xmentarik.quiz.game.MoneyLadder
import com.xmentarik.quiz.ui.QuizUiState
import com.xmentarik.quiz.ui.Screen

enum class SoundCue(val volume: Float = 1f) {
    SELECT(0.7f),
    FINAL_ANSWER,
    CORRECT,
    MILESTONE,
    WRONG,
    TIME_UP,
    TICK(0.6f),
    LIFELINE(0.8f),
    NEXT_QUESTION(0.7f),
    GAME_START,
    WIN,
    WALK_AWAY,
}

enum class MusicTrack { MENU, CALM, TENSE, INTENSE }

data class FeedbackSettings(
    val soundEnabled: Boolean = true,
    val musicEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
)

/** Decides what the player hears for each UI state. Pure, so it can be unit tested. */
object AudioDirector {
    /** The clock starts ticking audibly for the last seconds of a timed question. */
    const val TICK_FROM_SECONDS = 10

    fun music(state: QuizUiState): MusicTrack? = when (state.screen) {
        Screen.HOME, Screen.LEADERBOARD, Screen.CHALLENGES, Screen.CHALLENGE_RESULTS -> MusicTrack.MENU
        Screen.RESULT -> null
        Screen.GAME -> {
            val game = state.game
            // Silence during the "final answer" suspense and while the result is shown.
            if (game == null || game.phase != GamePhase.PLAYING || state.lockedOption != null) {
                null
            } else {
                when {
                    game.index < 5 -> MusicTrack.CALM
                    game.index < 10 -> MusicTrack.TENSE
                    else -> MusicTrack.INTENSE
                }
            }
        }
    }

    /** Sound effects triggered by going from [previous] to [current]; nothing on the first observation. */
    fun cues(previous: QuizUiState?, current: QuizUiState): List<SoundCue> {
        if (previous == null) return emptyList()
        if (current.screen == Screen.GAME && previous.screen != Screen.GAME) return listOf(SoundCue.GAME_START)

        val before = previous.game
        val after = current.game
        if (current.screen != Screen.GAME || before == null || after == null) return emptyList()

        val cues = mutableListOf<SoundCue>()
        if (current.pendingOption != null && current.pendingOption != previous.pendingOption) cues += SoundCue.SELECT
        if (current.lockedOption != null && previous.lockedOption == null) cues += SoundCue.FINAL_ANSWER
        if (after.usedLifelines.size > before.usedLifelines.size) cues += SoundCue.LIFELINE
        if (after.index > before.index) cues += SoundCue.NEXT_QUESTION

        if (after.phase != before.phase) {
            when (after.phase) {
                GamePhase.CORRECT ->
                    cues += if (MoneyLadder.isSafeHaven(after.index)) SoundCue.MILESTONE else SoundCue.CORRECT
                GamePhase.WON -> cues += SoundCue.WIN
                GamePhase.WRONG -> cues += SoundCue.WRONG
                GamePhase.TIME_UP -> cues += SoundCue.TIME_UP
                GamePhase.WALKED_AWAY -> cues += SoundCue.WALK_AWAY
                GamePhase.PLAYING -> Unit
            }
        }

        val left = current.secondsLeft
        val wasLeft = previous.secondsLeft
        if (after.phase == GamePhase.PLAYING && left != null && wasLeft != null &&
            left < wasLeft && left in 1..TICK_FROM_SECONDS
        ) {
            cues += SoundCue.TICK
        }
        return cues
    }
}
