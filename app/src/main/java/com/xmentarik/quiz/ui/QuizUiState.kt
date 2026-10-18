package com.xmentarik.quiz.ui

import com.xmentarik.quiz.game.GameState
import com.xmentarik.quiz.online.ChallengeResult
import com.xmentarik.quiz.online.LeaderboardEntry

enum class Screen { HOME, GAME, RESULT, LEADERBOARD, CHALLENGES, CHALLENGE_RESULTS }

enum class LifelineDialog { AUDIENCE, FRIEND }

data class QuizUiState(
    val screen: Screen = Screen.HOME,
    val loading: Boolean = false,
    val aiAvailable: Boolean = false,
    val useAi: Boolean = false,
    /** Index into [com.xmentarik.quiz.data.AppLanguage.topics]. */
    val topicIndex: Int = 0,
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
    /** Code of the friends challenge being played, null for a normal game. */
    val activeChallenge: String? = null,
    val online: OnlineUiState = OnlineUiState(),
)

enum class OnlineError { NOT_CONFIGURED, NETWORK, BAD_CODE, CHALLENGE_NOT_FOUND, ALREADY_PLAYED }

data class OnlineUiState(
    val available: Boolean = false,
    val playerName: String? = null,
    val playerId: String? = null,
    /** Screen to open once the player has picked a nickname; non-null shows the nickname dialog. */
    val namePromptFor: Screen? = null,
    val busy: Boolean = false,
    val error: OnlineError? = null,
    val leaderboard: List<LeaderboardEntry> = emptyList(),
    val myRank: Int? = null,
    val codeInput: String = "",
    val myChallenges: List<String> = emptyList(),
    /** Challenge shown on the challenge results screen. */
    val viewedChallenge: String? = null,
    val viewedCreator: String? = null,
    val challengeResults: List<ChallengeResult> = emptyList(),
)
