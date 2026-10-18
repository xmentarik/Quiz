package com.xmentarik.quiz.ui.game

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xmentarik.quiz.R
import com.xmentarik.quiz.game.GamePhase
import com.xmentarik.quiz.game.GameState
import com.xmentarik.quiz.game.Lifeline
import com.xmentarik.quiz.game.MoneyLadder
import com.xmentarik.quiz.game.TimeLimits
import com.xmentarik.quiz.ui.formatPrize
import com.xmentarik.quiz.ui.theme.QuizColors

enum class OptionStatus { NORMAL, PENDING, LOCKED, CORRECT, WRONG, DIMMED, HIDDEN }

@Composable
fun GameHeader(game: GameState, secondsLeft: Int?, onLadderClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.game_question_of, game.index + 1, MoneyLadder.size),
                style = MaterialTheme.typography.labelLarge,
                color = QuizColors.TextSecondary,
            )
            Text(
                text = stringResource(R.string.game_for_prize, formatPrize(MoneyLadder.amounts[game.index])),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = QuizColors.Gold,
            )
        }
        TimerBadge(secondsLeft = secondsLeft, limit = TimeLimits.secondsFor(game.index))
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onLadderClick) { Text("🏆", fontSize = 24.sp) }
    }
}

@Composable
private fun TimerBadge(secondsLeft: Int?, limit: Int?) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(52.dp)) {
        if (secondsLeft != null && limit != null) {
            val urgent = secondsLeft <= 10
            CircularProgressIndicator(
                progress = { secondsLeft / limit.toFloat() },
                color = if (urgent) QuizColors.Wrong else QuizColors.Gold,
                trackColor = QuizColors.OptionBackground,
                modifier = Modifier.size(52.dp),
            )
            Text("$secondsLeft", fontWeight = FontWeight.Bold, color = if (urgent) QuizColors.Wrong else Color.White)
        } else {
            Text("∞", fontSize = 24.sp, color = QuizColors.GoldLight)
        }
    }
}

@Composable
fun LifelineRow(game: GameState, enabled: Boolean, onLifeline: (Lifeline) -> Unit) {
    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
        Lifeline.entries.forEach { lifeline ->
            val (icon, label) = when (lifeline) {
                Lifeline.FIFTY_FIFTY -> "½" to R.string.lifeline_fifty
                Lifeline.ASK_AUDIENCE -> "👥" to R.string.lifeline_audience
                Lifeline.PHONE_A_FRIEND -> "📞" to R.string.lifeline_friend
                Lifeline.SWITCH_QUESTION -> "🔄" to R.string.lifeline_switch
            }
            val used = lifeline in game.usedLifelines
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(if (used) 0.3f else 1f),
            ) {
                Surface(
                    onClick = { onLifeline(lifeline) },
                    enabled = enabled && game.canUse(lifeline),
                    shape = CircleShape,
                    color = QuizColors.OptionBackground,
                    border = BorderStroke(2.dp, if (used) QuizColors.Wrong else QuizColors.OptionBorder),
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(if (used) "✕" else icon, fontSize = 22.sp, color = Color.White)
                    }
                }
                Text(stringResource(label), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun QuestionCard(text: String) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = QuizColors.OptionBackground,
        border = BorderStroke(1.5.dp, QuizColors.Gold),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(20.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                color = Color.White,
            )
        }
    }
}

@Composable
fun AnswerOption(label: String, text: String, status: OptionStatus, onClick: () -> Unit) {
    val container by animateColorAsState(
        targetValue = when (status) {
            OptionStatus.PENDING -> QuizColors.Selected.copy(alpha = 0.35f)
            OptionStatus.LOCKED -> QuizColors.Selected
            OptionStatus.CORRECT -> QuizColors.Correct
            OptionStatus.WRONG -> QuizColors.Wrong
            else -> QuizColors.OptionBackground
        },
        label = "optionColor",
    )
    val border = when (status) {
        OptionStatus.PENDING, OptionStatus.LOCKED -> QuizColors.Selected
        OptionStatus.CORRECT -> QuizColors.Correct
        OptionStatus.WRONG -> QuizColors.Wrong
        else -> QuizColors.OptionBorder
    }
    val blink = if (status == OptionStatus.LOCKED) {
        val transition = rememberInfiniteTransition(label = "locked")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.55f,
            animationSpec = infiniteRepeatable(tween(400), RepeatMode.Reverse),
            label = "lockedAlpha",
        ).value
    } else {
        1f
    }
    val alpha = when (status) {
        OptionStatus.HIDDEN -> 0.25f
        OptionStatus.DIMMED -> 0.6f
        else -> 1f
    }

    Surface(
        onClick = onClick,
        enabled = status == OptionStatus.NORMAL || status == OptionStatus.PENDING,
        shape = RoundedCornerShape(50),
        color = container,
        border = BorderStroke(2.dp, border),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha * blink),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Text("$label:", color = QuizColors.Gold, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (status == OptionStatus.HIDDEN) "" else text,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
            )
        }
    }
}

@Composable
fun RevealPanel(
    game: GameState,
    canReport: Boolean,
    reported: Boolean,
    onReport: () -> Unit,
    onSecondChance: (() -> Unit)?,
    onNext: () -> Unit,
    onShowResult: () -> Unit,
) {
    val (title, color) = when (game.phase) {
        GamePhase.CORRECT, GamePhase.WON -> R.string.game_correct to QuizColors.Correct
        GamePhase.TIME_UP -> R.string.game_time_up to QuizColors.Wrong
        GamePhase.WALKED_AWAY -> R.string.result_walked_away to QuizColors.Gold
        else -> R.string.game_wrong to QuizColors.Wrong
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = QuizColors.OptionBackground,
        border = BorderStroke(1.5.dp, color),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(title), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            val explanation = game.currentQuestion.explanation
            if (explanation.isNotBlank()) Text("💡 $explanation", color = QuizColors.TextSecondary)
            when {
                reported -> Text(
                    "✓ " + stringResource(R.string.report_sent),
                    style = MaterialTheme.typography.bodySmall,
                    color = QuizColors.Correct,
                )
                canReport -> TextButton(onClick = onReport) {
                    Text("🚩 " + stringResource(R.string.report_button), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (onSecondChance != null) {
                Button(
                    onClick = onSecondChance,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = QuizColors.Correct, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("📺 " + stringResource(R.string.second_chance)) }
            }
            Button(
                onClick = if (game.phase == GamePhase.CORRECT) onNext else onShowResult,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (game.phase == GamePhase.CORRECT) R.string.game_next else R.string.game_see_result))
            }
        }
    }
}

@Composable
fun NoticeBanner(text: String, onDismiss: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = QuizColors.Selected.copy(alpha = 0.2f)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        }
    }
}
