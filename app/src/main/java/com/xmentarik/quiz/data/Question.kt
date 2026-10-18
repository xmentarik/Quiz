package com.xmentarik.quiz.data

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class Question(
    val id: String = "",
    val text: String,
    val options: List<String>,
    val correctIndex: Int,
    /** 1 = easy, 2 = medium, 3 = hard. */
    val difficulty: Int,
    val explanation: String = "",
    val category: String = "",
) {
    val isValid: Boolean
        get() = text.isNotBlank() &&
            options.size == OPTION_COUNT &&
            options.all { it.isNotBlank() } &&
            options.map { it.trim() }.toSet().size == OPTION_COUNT &&
            correctIndex in options.indices &&
            difficulty in MIN_DIFFICULTY..MAX_DIFFICULTY

    /** Returns a copy with the options in random order, keeping the correct answer in sync. */
    fun withShuffledOptions(random: Random): Question {
        val order = options.indices.shuffled(random)
        return copy(options = order.map { options[it] }, correctIndex = order.indexOf(correctIndex))
    }

    companion object {
        const val OPTION_COUNT = 4
        const val MIN_DIFFICULTY = 1
        const val MAX_DIFFICULTY = 3
    }
}

/** The 15 questions of one game plus spares used by the "switch question" lifeline. */
data class QuestionSet(
    val main: List<Question>,
    val reserve: List<Question>,
)
