package com.xmentarik.quiz.game

object MoneyLadder {
    val amounts: List<Int> = listOf(
        100, 200, 300, 500, 1_000,
        2_000, 4_000, 8_000, 16_000, 32_000,
        64_000, 125_000, 250_000, 500_000, 1_000_000,
    )

    /** Indices of the "safe havens": once passed, that amount can no longer be lost. */
    val safeHavens: Set<Int> = setOf(4, 9)

    val size: Int get() = amounts.size
    val top: Int get() = amounts.last()

    fun isSafeHaven(index: Int): Boolean = index in safeHavens

    /** Prize held after [correctCount] correct answers (what the player keeps by walking away). */
    fun banked(correctCount: Int): Int = if (correctCount <= 0) 0 else amounts[correctCount - 1]

    /** Prize kept after a wrong answer or a timeout following [correctCount] correct answers. */
    fun guaranteed(correctCount: Int): Int =
        safeHavens.filter { it < correctCount }.maxOfOrNull { amounts[it] } ?: 0
}
