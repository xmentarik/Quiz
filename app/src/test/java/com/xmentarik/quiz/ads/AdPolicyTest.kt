package com.xmentarik.quiz.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdPolicyTest {
    private val minute = 60_000L

    @Test
    fun interstitialEverySecondGameAndNotRightAfterAnotherAd() {
        assertFalse(AdPolicy.shouldShowInterstitial(gamesSinceLast = 1, lastFullScreenAt = null, now = 0))
        assertTrue(AdPolicy.shouldShowInterstitial(gamesSinceLast = 2, lastFullScreenAt = null, now = 0))
        // A rewarded ad watched a minute ago: wait.
        assertFalse(AdPolicy.shouldShowInterstitial(2, lastFullScreenAt = 10 * minute, now = 11 * minute))
        assertTrue(AdPolicy.shouldShowInterstitial(2, lastFullScreenAt = 10 * minute, now = 13 * minute))
    }

    @Test
    fun appOpenOnlyAfterARealAbsenceAndNeverDuringAGame() {
        val now = 100 * minute
        assertTrue(AdPolicy.shouldShowAppOpen(minute, null, null, now, gameInProgress = false))
        // Back from sharing a score after a few seconds.
        assertFalse(AdPolicy.shouldShowAppOpen(5_000, null, null, now, gameInProgress = false))
        assertFalse(AdPolicy.shouldShowAppOpen(minute, null, null, now, gameInProgress = true))
        // One app-open ad every 30 minutes at most.
        assertFalse(AdPolicy.shouldShowAppOpen(minute, lastAppOpenAt = now - 10 * minute, null, now, false))
        assertTrue(AdPolicy.shouldShowAppOpen(minute, lastAppOpenAt = now - 31 * minute, null, now, false))
    }

    @Test
    fun appOpenAdsExpireAfterFourHours() {
        assertTrue(AdPolicy.isAppOpenAdFresh(loadedAt = 0, now = 239 * minute))
        assertFalse(AdPolicy.isAppOpenAdFresh(loadedAt = 0, now = 240 * minute))
    }
}
