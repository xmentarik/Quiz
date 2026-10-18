package com.xmentarik.quiz.ui

import com.xmentarik.quiz.game.GameState

enum class Screen { HOME, GAME, RESULT }

enum class LifelineDialog { AUDIENCE, FRIEND }

val QuizTopics = listOf(
    "ثقافة عامة", "علوم", "تاريخ", "جغرافيا", "رياضة", "دين وحضارة إسلامية",
    "تقنية وبرمجة", "أدب ولغة عربية", "أفلام ومسلسلات", "المغرب العربي",
)

data class QuizUiState(
    val screen: Screen = Screen.HOME,
    val loading: Boolean = false,
    val aiAvailable: Boolean = false,
    val useAi: Boolean = false,
    val topic: String = QuizTopics.first(),
    val game: GameState? = null,
    /** Option tapped but not yet confirmed as the final answer. */
    val pendingOption: Int? = null,
    /** Final answer waiting for the suspenseful reveal. */
    val lockedOption: Int? = null,
    val secondsLeft: Int? = null,
    val dialog: LifelineDialog? = null,
    val confirmWalkAway: Boolean = false,
    val aiFallbackNotice: Boolean = false,
    val newRecord: Boolean = false,
)
