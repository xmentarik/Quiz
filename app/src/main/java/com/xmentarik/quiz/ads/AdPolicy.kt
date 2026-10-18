package com.xmentarik.quiz.ads

/** When full-screen ads may appear. Pure, so the rules are unit tested; times are in milliseconds. */
object AdPolicy {
    /** An interstitial at most every second finished game... */
    const val INTERSTITIAL_EVERY_GAMES = 2

    /** ...and never within two minutes of another full-screen ad (including a rewarded one). */
    const val MIN_GAP_BETWEEN_FULL_SCREEN_MS = 2 * 60_000L

    /** App-open ads only after a real absence, so sharing a score or a challenge never triggers one. */
    const val MIN_BACKGROUND_FOR_APP_OPEN_MS = 30_000L
    const val MIN_GAP_BETWEEN_APP_OPEN_MS = 30 * 60_000L

    /** Google expires app-open ads after four hours. */
    const val APP_OPEN_MAX_AGE_MS = 4 * 60 * 60_000L

    fun shouldShowInterstitial(gamesSinceLast: Int, lastFullScreenAt: Long?, now: Long): Boolean =
        gamesSinceLast >= INTERSTITIAL_EVERY_GAMES && gapElapsed(lastFullScreenAt, now, MIN_GAP_BETWEEN_FULL_SCREEN_MS)

    fun shouldShowAppOpen(
        backgroundMs: Long,
        lastAppOpenAt: Long?,
        lastFullScreenAt: Long?,
        now: Long,
        gameInProgress: Boolean,
    ): Boolean =
        !gameInProgress &&
            backgroundMs >= MIN_BACKGROUND_FOR_APP_OPEN_MS &&
            gapElapsed(lastAppOpenAt, now, MIN_GAP_BETWEEN_APP_OPEN_MS) &&
            gapElapsed(lastFullScreenAt, now, MIN_GAP_BETWEEN_FULL_SCREEN_MS)

    fun isAppOpenAdFresh(loadedAt: Long, now: Long): Boolean = now - loadedAt < APP_OPEN_MAX_AGE_MS

    private fun gapElapsed(last: Long?, now: Long, gap: Long) = last == null || now - last >= gap
}
