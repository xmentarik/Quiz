package com.xmentarik.quiz.game

object TimeLimits {
    /** Seconds allowed for the question at [questionIndex]; null means no limit (last five questions). */
    fun secondsFor(questionIndex: Int): Int? = when {
        questionIndex < 5 -> 30
        questionIndex < 10 -> 45
        else -> null
    }
}
