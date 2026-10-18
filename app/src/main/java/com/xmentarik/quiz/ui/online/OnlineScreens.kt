package com.xmentarik.quiz.ui.online

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xmentarik.quiz.R
import com.xmentarik.quiz.online.PlayerNames
import com.xmentarik.quiz.store.StoreLinks
import com.xmentarik.quiz.ui.OnlineError
import com.xmentarik.quiz.ui.OnlineUiState
import com.xmentarik.quiz.ui.formatDuration
import com.xmentarik.quiz.ui.formatPrize
import com.xmentarik.quiz.ui.theme.QuizColors

@Composable
fun LeaderboardScreen(online: OnlineUiState, onRefresh: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    OnlineScaffold(
        title = "🏆 " + stringResource(R.string.leaderboard_title),
        online = online,
        onBack = onBack,
        onRefresh = onRefresh,
    ) {
        if (online.myRank != null) {
            item {
                Text(
                    text = stringResource(R.string.leaderboard_my_rank, online.myRank),
                    style = MaterialTheme.typography.titleMedium,
                    color = QuizColors.Gold,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (!online.busy && online.error == null && online.leaderboard.isEmpty()) {
            item { Text(stringResource(R.string.leaderboard_empty), color = QuizColors.TextSecondary) }
        }
        itemsIndexed(online.leaderboard, key = { _, entry -> entry.playerId }) { index, entry ->
            RankRow(
                rank = index + 1,
                name = entry.name,
                detail = if (entry.millionWins > 0) "💎 × ${entry.millionWins}" else null,
                prize = entry.bestPrize,
                isMe = entry.playerId == online.playerId,
            )
        }
    }
}

@Composable
fun ChallengesScreen(
    online: OnlineUiState,
    onCodeChange: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onOpenChallenge: (String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    OnlineScaffold(title = "⚔️ " + stringResource(R.string.challenge_title), online = online, onBack = onBack) {
        item {
            OnlineCard {
                Text(stringResource(R.string.challenge_create_title), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.challenge_create_body), color = QuizColors.TextSecondary)
                Button(
                    onClick = onCreate,
                    enabled = !online.busy && online.available,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.challenge_create)) }
            }
        }
        item {
            OnlineCard {
                Text(stringResource(R.string.challenge_join_title), fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = online.codeInput,
                    onValueChange = onCodeChange,
                    label = { Text(stringResource(R.string.challenge_code_label)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = onJoin,
                    enabled = !online.busy && online.available && online.codeInput.isNotBlank(),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.challenge_join)) }
            }
        }
        if (online.myChallenges.isNotEmpty()) {
            item { Text(stringResource(R.string.challenge_mine), style = MaterialTheme.typography.titleMedium) }
            items(online.myChallenges, key = { it }) { code ->
                Surface(
                    onClick = { onOpenChallenge(code) },
                    enabled = !online.busy,
                    shape = RoundedCornerShape(12.dp),
                    color = QuizColors.OptionBackground,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
                        Text(code, fontFamily = FontFamily.Monospace, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        Text("🏁", fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ChallengeResultsScreen(online: OnlineUiState, onRefresh: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val code = online.viewedChallenge ?: return
    val context = LocalContext.current
    val invite = stringResource(R.string.challenge_invite, code) + "\n" + StoreLinks.APP_URL
    OnlineScaffold(
        title = "⚔️ " + stringResource(R.string.challenge_results_title, code),
        online = online,
        onBack = onBack,
        onRefresh = onRefresh,
    ) {
        online.viewedCreator?.let { creator ->
            item { Text(stringResource(R.string.challenge_by, creator), color = QuizColors.TextSecondary) }
        }
        item {
            OutlinedButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, invite)
                    }
                    context.startActivity(Intent.createChooser(send, null))
                },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("📨 " + stringResource(R.string.challenge_share)) }
        }
        if (!online.busy && online.challengeResults.isEmpty()) {
            item { Text(stringResource(R.string.challenge_no_results), color = QuizColors.TextSecondary) }
        }
        itemsIndexed(online.challengeResults, key = { _, result -> result.playerId }) { index, result ->
            RankRow(
                rank = index + 1,
                name = result.name,
                detail = stringResource(
                    R.string.challenge_correct_time,
                    result.correctCount,
                    formatDuration(result.durationSeconds),
                ),
                prize = result.prize,
                isMe = result.playerId == online.playerId,
            )
        }
    }
}

@Composable
fun NamePromptDialog(onSubmit: (String) -> Boolean, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.name_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.take(PlayerNames.MAX_LENGTH + 5)
                    invalid = false
                },
                singleLine = true,
                isError = invalid,
                supportingText = {
                    Text(stringResource(if (invalid) R.string.name_invalid else R.string.name_hint))
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
        },
        confirmButton = {
            TextButton(onClick = { invalid = !onSubmit(name) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun OnlineScaffold(
    title: String,
    online: OnlineUiState,
    onBack: () -> Unit,
    onRefresh: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = QuizColors.Gold,
                modifier = Modifier.weight(1f),
            )
            if (onRefresh != null) {
                TextButton(onClick = onRefresh, enabled = !online.busy && online.available) {
                    Text(stringResource(R.string.refresh))
                }
            }
        }
        if (online.busy) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(32.dp).padding(4.dp))
            }
        }
        online.error?.let { ErrorBanner(it) }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
            content = content,
        )
    }
}

@Composable
private fun ErrorBanner(error: OnlineError) {
    val message = when (error) {
        OnlineError.NOT_CONFIGURED -> R.string.error_not_configured
        OnlineError.NETWORK -> R.string.error_network
        OnlineError.BAD_CODE -> R.string.error_bad_code
        OnlineError.CHALLENGE_NOT_FOUND -> R.string.error_not_found
        OnlineError.ALREADY_PLAYED -> R.string.error_already_played
    }
    val isInfo = error == OnlineError.ALREADY_PLAYED
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = (if (isInfo) QuizColors.Selected else QuizColors.Wrong).copy(alpha = 0.2f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Text(stringResource(message), modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun OnlineCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = QuizColors.OptionBackground,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun RankRow(rank: Int, name: String, detail: String?, prize: Int, isMe: Boolean) {
    val medal = when (rank) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> "#$rank"
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isMe) QuizColors.Gold.copy(alpha = 0.18f) else QuizColors.OptionBackground,
        border = if (isMe) BorderStroke(1.5.dp, QuizColors.Gold) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            Text(medal, fontSize = 18.sp, modifier = Modifier.width(44.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (isMe) "$name ${stringResource(R.string.leaderboard_you)}" else name,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = QuizColors.TextSecondary)
                }
            }
            Text(formatPrize(prize), color = QuizColors.Gold, fontWeight = FontWeight.Bold)
        }
    }
}
