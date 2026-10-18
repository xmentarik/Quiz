package com.xmentarik.quiz.game

import com.xmentarik.quiz.data.Question

enum class Lifeline { FIFTY_FIFTY, ASK_AUDIENCE, PHONE_A_FRIEND, SWITCH_QUESTION }

enum class GamePhase {
    PLAYING, CORRECT, WRONG, TIME_UP, WALKED_AWAY, WON;

    val isOver: Boolean get() = this == WRONG || this == TIME_UP || this == WALKED_AWAY || this == WON
}

data class FriendAdvice(val optionIndex: Int, val confidence: Int)

data class GameState(
    val questions: List<Question>,
    val reserve: List<Question>,
    val index: Int = 0,
    val phase: GamePhase = GamePhase.PLAYING,
    val selectedOption: Int? = null,
    val hiddenOptions: Set<Int> = emptySet(),
    val usedLifelines: Set<Lifeline> = emptySet(),
    /** Percentages per option, filled by the audience lifeline for the current question. */
    val audienceVotes: List<Int>? = null,
    val friendAdvice: FriendAdvice? = null,
    /** Prize currently held; final once the game is over. */
    val prize: Int = 0,
    /** The rewarded-ad "second chance" can save a game once. */
    val secondChanceUsed: Boolean = false,
) {
    val currentQuestion: Question get() = questions[index]

    val correctCount: Int
        get() = index + if (phase == GamePhase.CORRECT || phase == GamePhase.WON) 1 else 0

    val canGetSecondChance: Boolean
        get() = !secondChanceUsed && (phase == GamePhase.WRONG || phase == GamePhase.TIME_UP)

    fun canUse(lifeline: Lifeline): Boolean =
        phase == GamePhase.PLAYING && lifeline !in usedLifelines &&
            (lifeline != Lifeline.SWITCH_QUESTION || reserve.isNotEmpty())
}
