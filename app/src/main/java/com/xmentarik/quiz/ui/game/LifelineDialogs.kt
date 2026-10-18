package com.xmentarik.quiz.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xmentarik.quiz.R
import com.xmentarik.quiz.data.Question
import com.xmentarik.quiz.game.FriendAdvice
import com.xmentarik.quiz.game.MoneyLadder
import com.xmentarik.quiz.ui.LocalAppLanguage
import com.xmentarik.quiz.ui.formatPrize
import com.xmentarik.quiz.ui.theme.QuizColors

@Composable
fun AudienceDialog(votes: List<Int>, hidden: Set<Int>, onDismiss: () -> Unit) {
    val labels = LocalAppLanguage.current.optionLabels
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
        title = { Text("👥 " + stringResource(R.string.audience_title)) },
        text = {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
            ) {
                votes.forEachIndexed { index, percent ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier
                            .fillMaxHeight()
                            .alpha(if (index in hidden) 0.3f else 1f),
                    ) {
                        Text("$percent%", style = MaterialTheme.typography.labelMedium)
                        Box(
                            Modifier
                                .width(32.dp)
                                .height((140 * percent / 100).coerceAtLeast(2).dp)
                                .background(QuizColors.Gold, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
                        )
                        Text(labels[index], fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
    )
}

@Composable
fun FriendDialog(advice: FriendAdvice, question: Question, onDismiss: () -> Unit) {
    val labels = LocalAppLanguage.current.optionLabels
    val answer = "${labels[advice.optionIndex]}: ${question.options[advice.optionIndex]}"
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
        title = { Text("📞 " + stringResource(R.string.friend_title)) },
        text = {
            Text(
                stringResource(
                    R.string.friend_says,
                    answer,
                    stringResource(R.string.friend_confidence, advice.confidence),
                ),
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyLadderSheet(currentIndex: Int, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = QuizColors.DeepPurple) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.game_ladder),
                style = MaterialTheme.typography.titleMedium,
                color = QuizColors.Gold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            for (index in MoneyLadder.amounts.indices.reversed()) {
                val isCurrent = index == currentIndex
                val passed = index < currentIndex
                val safe = MoneyLadder.isSafeHaven(index)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isCurrent) QuizColors.Gold else Color.Transparent,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        val textColor = when {
                            isCurrent -> QuizColors.NightBlue
                            passed -> QuizColors.Correct
                            safe -> QuizColors.Gold
                            else -> Color.White
                        }
                        Text("${index + 1}", color = textColor, modifier = Modifier.width(32.dp))
                        Text(
                            text = formatPrize(MoneyLadder.amounts[index]),
                            color = textColor,
                            fontWeight = if (safe || isCurrent) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f),
                        )
                        if (safe) Text(stringResource(R.string.game_safe_haven), color = textColor, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
