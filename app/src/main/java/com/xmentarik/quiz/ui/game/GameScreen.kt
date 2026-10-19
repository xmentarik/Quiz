package com.xmentarik.quiz.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xmentarik.quiz.BuildConfig
import com.xmentarik.quiz.R
import com.xmentarik.quiz.game.GamePhase
import com.xmentarik.quiz.game.GameState
import com.xmentarik.quiz.game.Lifeline
import com.xmentarik.quiz.game.MoneyLadder
import com.xmentarik.quiz.online.ReportReason
import com.xmentarik.quiz.ui.LifelineDialog
import com.xmentarik.quiz.ui.LocalAppLanguage
import com.xmentarik.quiz.ui.QuizUiState
import com.xmentarik.quiz.ui.formatPrize

@Composable
fun GameScreen(
    state: QuizUiState,
    game: GameState,
    onSelect: (Int) -> Unit,
    onConfirm: () -> Unit,
    onLifeline: (Lifeline) -> Unit,
    onDismissDialog: () -> Unit,
    onRequestWalkAway: () -> Unit,
    onCancelWalkAway: () -> Unit,
    onConfirmWalkAway: () -> Unit,
    onNext: () -> Unit,
    onShowResult: () -> Unit,
    /** Non-null when a rewarded ad can save the game. */
    onSecondChance: (() -> Unit)?,
    onOpenReport: () -> Unit,
    onSubmitReport: (ReportReason, String) -> Unit,
    onReportedByEmail: (String) -> Unit,
    onDismissReport: () -> Unit,
) {
    BackHandler(enabled = game.phase == GamePhase.PLAYING) { onRequestWalkAway() }
    var showLadder by rememberSaveable { mutableStateOf(false) }
    val question = game.currentQuestion
    val labels = LocalAppLanguage.current.optionLabels
    val banked = formatPrize(MoneyLadder.banked(game.index))
    val canReport = state.online.available || BuildConfig.REPORT_EMAIL.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {

        GameHeader(game = game, secondsLeft = state.secondsLeft, onLadderClick = { showLadder = true })
        LifelineRow(game = game, enabled = state.lockedOption == null, onLifeline = onLifeline)
        QuestionCard(question.text)

        question.options.forEachIndexed { index, text ->
            AnswerOption(
                label = labels[index],
                text = text,
                status = optionStatus(index, state, game),
                onClick = { onSelect(index) },
            )
        }

        when {
            game.phase != GamePhase.PLAYING -> RevealPanel(
                game = game,
                canReport = canReport,
                reported = question.id in state.reportedQuestions,
                onReport = onOpenReport,
                onSecondChance = onSecondChance,
                onNext = onNext,
                onShowResult = onShowResult,
            )
            state.lockedOption == null -> {
                if (state.pendingOption != null) {
                    Button(
                        onClick = onConfirm,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) { Text(stringResource(R.string.game_final_answer)) }
                }
                OutlinedButton(
                    onClick = onRequestWalkAway,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.game_walk_away, banked)) }
            }
        }
    }

    if (showLadder) MoneyLadderSheet(currentIndex = game.index, onDismiss = { showLadder = false })

    when (state.dialog) {
        LifelineDialog.AUDIENCE -> game.audienceVotes?.let { AudienceDialog(it, game.hiddenOptions, onDismissDialog) }
        LifelineDialog.FRIEND -> game.friendAdvice?.let { FriendDialog(it, question, onDismissDialog) }
        null -> Unit
    }

    if (state.reportOpen) {
        ReportDialog(
            question = question,
            selectedOption = game.selectedOption,
            status = state.reportStatus,
            canSendOnline = state.online.available,
            reportEmail = BuildConfig.REPORT_EMAIL,
            onSubmit = onSubmitReport,
            onSentByEmail = { onReportedByEmail(question.id) },
            onDismiss = onDismissReport,
        )
    }

    if (state.confirmWalkAway) {
        AlertDialog(
            onDismissRequest = onCancelWalkAway,
            title = { Text(stringResource(R.string.game_walk_away_title)) },
            text = { Text(stringResource(R.string.game_walk_away_body, banked)) },
            confirmButton = {
                TextButton(onClick = onConfirmWalkAway) { Text(stringResource(R.string.game_confirm_walk_away)) }
            },
            dismissButton = {
                TextButton(onClick = onCancelWalkAway) { Text(stringResource(R.string.game_keep_playing)) }
            },
        )
    }
}

private fun optionStatus(index: Int, state: QuizUiState, game: GameState): OptionStatus = when {
    index in game.hiddenOptions -> OptionStatus.HIDDEN
    game.phase != GamePhase.PLAYING -> when (index) {
        game.currentQuestion.correctIndex -> OptionStatus.CORRECT
        game.selectedOption -> OptionStatus.WRONG
        else -> OptionStatus.DIMMED
    }
    state.lockedOption == index -> OptionStatus.LOCKED
    state.pendingOption == index -> OptionStatus.PENDING
    state.lockedOption != null -> OptionStatus.DIMMED
    else -> OptionStatus.NORMAL
}
