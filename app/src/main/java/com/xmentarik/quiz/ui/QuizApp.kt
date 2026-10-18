package com.xmentarik.quiz.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xmentarik.quiz.ui.game.GameScreen
import com.xmentarik.quiz.ui.home.HomeScreen
import com.xmentarik.quiz.ui.result.ResultScreen
import com.xmentarik.quiz.ui.theme.QuizColors

@Composable
fun QuizApp(viewModel: QuizViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    Box(
        Modifier
            .fillMaxSize()
            .background(QuizColors.Background)
            .safeDrawingPadding(),
    ) {
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
                    onShowResult = viewModel::showResult,
                    onDismissAiNotice = viewModel::dismissAiNotice,
                )
                screen == Screen.RESULT && game != null -> ResultScreen(
                    game = game,
                    stats = stats,
                    newRecord = state.newRecord,
                    loading = state.loading,
                    onPlayAgain = viewModel::startGame,
                    onHome = viewModel::goHome,
                )
                else -> HomeScreen(
                    state = state,
                    stats = stats,
                    onUseAiChange = viewModel::setUseAi,
                    onTopicChange = viewModel::setTopic,
                    onPlay = viewModel::startGame,
                )
            }
        }
    }
}
