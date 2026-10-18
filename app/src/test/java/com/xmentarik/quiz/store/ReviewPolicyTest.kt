package com.xmentarik.quiz.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPolicyTest {
    private val day = 24 * 60 * 60_000L

    @Test
    fun asksOnlyAfterAGoodGameOnceThePlayerKnowsTheApp() {
        assertEquals(32_000, ReviewPolicy.MIN_PRIZE)
        assertTrue(ReviewPolicy.shouldAsk(gamesPlayed = 3, prize = 32_000, lastAskedAt = null, now = 0))
        assertFalse(ReviewPolicy.shouldAsk(gamesPlayed = 2, prize = 1_000_000, lastAskedAt = null, now = 0))
        assertFalse(ReviewPolicy.shouldAsk(gamesPlayed = 10, prize = 16_000, lastAskedAt = null, now = 0))
    }

    @Test
    fun asksAtMostOnceAMonth() {
        assertFalse(ReviewPolicy.shouldAsk(5, 64_000, lastAskedAt = 0, now = 29 * day))
        assertTrue(ReviewPolicy.shouldAsk(5, 64_000, lastAskedAt = 0, now = 30 * day))
    }
}
