package com.xmentarik.quiz.store

import com.xmentarik.quiz.game.MoneyLadder

/** When to show Google Play's in-app review prompt: only right after a good game, and rarely. Pure for tests. */
object ReviewPolicy {
    const val MIN_GAMES = 3
    /** Past the second safe haven: the player is having a good time. */
    val MIN_PRIZE = MoneyLadder.amounts[MoneyLadder.safeHavens.max()]
    const val MIN_DAYS_BETWEEN = 30L
    private const val DAY_MS = 24 * 60 * 60_000L

    fun shouldAsk(gamesPlayed: Int, prize: Int, lastAskedAt: Long?, now: Long): Boolean =
        gamesPlayed >= MIN_GAMES &&
            prize >= MIN_PRIZE &&
            (lastAskedAt == null || now - lastAskedAt >= MIN_DAYS_BETWEEN * DAY_MS)
}
