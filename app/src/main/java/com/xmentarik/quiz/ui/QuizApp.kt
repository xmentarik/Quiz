package com.xmentarik.quiz.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xmentarik.quiz.ads.AdsManager
import com.xmentarik.quiz.ads.BannerAd
import com.xmentarik.quiz.ads.NativeAdCard
import com.xmentarik.quiz.store.StoreLinks
import kotlinx.coroutines.delay
import com.xmentarik.quiz.ui.game.GameScreen
import com.xmentarik.quiz.ui.home.HomeScreen
import com.xmentarik.quiz.ui.online.ChallengeResultsScreen
import com.xmentarik.quiz.ui.online.ChallengesScreen
import com.xmentarik.quiz.ui.online.LeaderboardScreen
import com.xmentarik.quiz.ui.online.NamePromptDialog
import com.xmentarik.quiz.ui.result.ResultScreen
import com.xmentarik.quiz.ui.theme.QuizColors

/** Screens that show a banner at the bottom; never the game screen, where it would sit next to the answers. */
private val BannerScreens = setOf(Screen.HOME, Screen.LEADERBOARD, Screen.CHALLENGES, Screen.CHALLENGE_RESULTS)

@Composable
fun QuizApp(viewModel: QuizViewModel, ads: AdsManager) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val feedbackSettings by viewModel.feedbackSettings.collectAsStateWithLifecycle()
    val adsReady by ads.ready.collectAsStateWithLifecycle()
    val rewardedReady by ads.rewardedReady.collectAsStateWithLifecycle()
    val privacyOptionsRequired by ads.privacyOptionsRequired.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()

    // Google Play's review sheet, right after a good game (see ReviewPolicy); Google may still skip it.
    LaunchedEffect(state.reviewRequested) {
        if (!state.reviewRequested) return@LaunchedEffect
        delay(1_500)
        activity?.let(StoreLinks::requestInAppReview)
        viewModel.consumeReviewRequest()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(QuizColors.Background)
            .safeDrawingPadding(),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(targetState = state.screen, label = "screen") { screen ->
                val game = state.game
                when {
                    screen == Screen.GAME && game != null -> GameScreen(
                        state = state,
                        game = game,
                        onSelect = viewModel::selectOption,
                        onConfirm = viewModel::confirmAnswer,
                        onLifeline = viewModel::useLifeline,
                        onDismissDialog = viewModel::dismissDialog,
                        onRequestWalkAway = viewModel::requestWalkAway,
                        onCancelWalkAway = viewModel::cancelWalkAway,
                        onConfirmWalkAway = viewModel::confirmWalkAway,
                        onNext = viewModel::nextQuestion,
                        // An interstitial may play first, at most every second game.
                        onShowResult = {
                            if (activity != null) ads.onGameFinished(activity, viewModel::showResult) else viewModel.showResult()
                        },
                        onSecondChance = if (rewardedReady && activity != null && viewModel.canOfferSecondChance) {
                            { ads.showRewarded(activity, viewModel::useSecondChance) }
                        } else {
                            null
                        },
                        onDismissAiNotice = viewModel::dismissAiNotice,
                        onOpenReport = viewModel::openReport,
                        onSubmitReport = viewModel::submitReport,
                        onReportedByEmail = viewModel::markReported,
                        onDismissReport = viewModel::dismissReport,
                    )
                    screen == Screen.RESULT && game != null -> ResultScreen(
                        game = game,
                        stats = stats,
                        newRecord = state.newRecord,
                        loading = state.loading,
                        challengeCode = state.activeChallenge,
                        adSlot = { if (adsReady) NativeAdCard() },
                        onPlayAgain = viewModel::startGame,
                        onChallengeRanking = viewModel::openChallengeResults,
                        onHome = viewModel::goHome,
                    )
                    screen == Screen.LEADERBOARD -> LeaderboardScreen(
                        online = state.online,
                        onRefresh = viewModel::refreshLeaderboard,
                        onBack = viewModel::backFromOnline,
                    )
                    screen == Screen.CHALLENGES -> ChallengesScreen(
                        online = state.online,
                        onCodeChange = viewModel::setChallengeCodeInput,
                        onCreate = viewModel::createChallenge,
                        onJoin = viewModel::joinChallenge,
                        onOpenChallenge = viewModel::openChallengeResults,
                        onBack = viewModel::backFromOnline,
                    )
                    screen == Screen.CHALLENGE_RESULTS -> ChallengeResultsScreen(
                        online = state.online,
                        onRefresh = { state.online.viewedChallenge?.let(viewModel::openChallengeResults) },
                        onBack = viewModel::backFromOnline,
                    )
                    else -> HomeScreen(
                        state = state,
                        stats = stats,
                        feedbackSettings = feedbackSettings,
                        onUseAiChange = viewModel::setUseAi,
                        onTopicChange = viewModel::setTopic,
                        onPlay = viewModel::startGame,
                        onSoundChange = viewModel::setSoundEnabled,
                        onMusicChange = viewModel::setMusicEnabled,
                        onVibrationChange = viewModel::setVibrationEnabled,
                        onLanguageChange = viewModel::setLanguage,
                        onLeaderboard = viewModel::openLeaderboard,
                        onChallenges = viewModel::openChallenges,
                        onPrivacyOptions = if (privacyOptionsRequired && activity != null) {
                            { ads.showPrivacyOptions(activity) }
                        } else {
                            null
                        },
                    )
                }
            }
        }

        if (adsReady && state.screen in BannerScreens) BannerAd()

        if (state.online.namePromptFor != null) {
            NamePromptDialog(onSubmit = viewModel::submitPlayerName, onDismiss = viewModel::dismissNamePrompt)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
