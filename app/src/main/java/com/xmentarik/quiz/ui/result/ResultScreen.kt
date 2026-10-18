package com.xmentarik.quiz.ui.result

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xmentarik.quiz.R
import com.xmentarik.quiz.data.PlayerStats
import com.xmentarik.quiz.game.GamePhase
import com.xmentarik.quiz.game.GameState
import com.xmentarik.quiz.game.MoneyLadder
import com.xmentarik.quiz.ui.formatPrize
import com.xmentarik.quiz.ui.theme.QuizColors

@Composable
fun ResultScreen(
    game: GameState,
    stats: PlayerStats,
    newRecord: Boolean,
    loading: Boolean,
    challengeCode: String?,
    onPlayAgain: () -> Unit,
    onChallengeRanking: (String) -> Unit,
    onHome: () -> Unit,
) {
    BackHandler(onBack = onHome)
    val context = LocalContext.current
    val prize = formatPrize(game.prize)
    val shareText = stringResource(R.string.result_share_text, prize)
    val (emoji, title) = when (game.phase) {
        GamePhase.WON -> "🏆" to R.string.result_won
        GamePhase.WALKED_AWAY -> "💼" to R.string.result_walked_away
        else -> "💔" to R.string.result_lost
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Text(emoji, fontSize = 72.sp)
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = QuizColors.Gold,
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = QuizColors.OptionBackground),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Text(stringResource(R.string.result_prize_label), color = QuizColors.TextSecondary)
                Text(
                    text = stringResource(R.string.points, prize),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = QuizColors.Gold,
                )
                Text(stringResource(R.string.result_correct_answers, game.correctCount, MoneyLadder.size))
                Text(
                    text = stringResource(R.string.home_best_prize, formatPrize(stats.bestPrize)),
                    color = QuizColors.TextSecondary,
                )
            }
        }

        if (newRecord) {
            Text("🎉 " + stringResource(R.string.result_new_record), color = QuizColors.Gold, fontWeight = FontWeight.Bold)
        }

        if (challengeCode != null) {
            Button(
                onClick = { onChallengeRanking(challengeCode) },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text("⚔️ " + stringResource(R.string.result_challenge_ranking)) }
        }

        Button(
            onClick = onPlayAgain,
            enabled = !loading,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(R.string.result_play_again))
            }
        }
        OutlinedButton(
            onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(Intent.createChooser(send, null))
            },
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.result_share)) }
        TextButton(onClick = onHome) { Text(stringResource(R.string.result_home)) }
    }
}
