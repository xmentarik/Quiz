package com.xmentarik.quiz.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xmentarik.quiz.R
import com.xmentarik.quiz.audio.AudioSettings
import com.xmentarik.quiz.data.PlayerStats
import com.xmentarik.quiz.ui.QuizTopics
import com.xmentarik.quiz.ui.QuizUiState
import com.xmentarik.quiz.ui.formatPrize
import com.xmentarik.quiz.ui.theme.QuizColors

@Composable
fun HomeScreen(
    state: QuizUiState,
    stats: PlayerStats,
    audioSettings: AudioSettings,
    onUseAiChange: (Boolean) -> Unit,
    onTopicChange: (String) -> Unit,
    onPlay: () -> Unit,
    onSoundChange: (Boolean) -> Unit,
    onMusicChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("💎", fontSize = 64.sp)
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = QuizColors.Gold,
        )
        Text(
            text = stringResource(R.string.home_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = QuizColors.TextSecondary,
            textAlign = TextAlign.Center,
        )

        StatsCard(stats)
        AiCard(state, onUseAiChange, onTopicChange)
        AudioRow(audioSettings, onSoundChange, onMusicChange)

        Button(
            onClick = onPlay,
            enabled = !state.loading,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.home_loading))
            } else {
                Text(stringResource(R.string.home_play), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun StatsCard(stats: PlayerStats) {
    Card(
        colors = CardDefaults.cardColors(containerColor = QuizColors.OptionBackground),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.home_best_prize, formatPrize(stats.bestPrize)),
                color = QuizColors.Gold,
                fontWeight = FontWeight.Bold,
            )
            Text(stringResource(R.string.home_games_played, stats.gamesPlayed))
            Text(stringResource(R.string.home_million_wins, stats.millionWins))
        }
    }
}

@Composable
private fun AudioRow(
    settings: AudioSettings,
    onSoundChange: (Boolean) -> Unit,
    onMusicChange: (Boolean) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilterChip(
            selected = settings.soundEnabled,
            onClick = { onSoundChange(!settings.soundEnabled) },
            label = { Text((if (settings.soundEnabled) "🔊 " else "🔇 ") + stringResource(R.string.home_sound)) },
        )
        FilterChip(
            selected = settings.musicEnabled,
            onClick = { onMusicChange(!settings.musicEnabled) },
            label = { Text((if (settings.musicEnabled) "🎵 " else "🔇 ") + stringResource(R.string.home_music)) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiCard(
    state: QuizUiState,
    onUseAiChange: (Boolean) -> Unit,
    onTopicChange: (String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = QuizColors.OptionBackground),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("✨ " + stringResource(R.string.home_ai_title), fontWeight = FontWeight.Bold)
                    Text(
                        text = stringResource(
                            if (state.aiAvailable) R.string.home_ai_subtitle else R.string.home_ai_missing_key,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = QuizColors.TextSecondary,
                    )
                }
                Switch(
                    checked = state.useAi,
                    onCheckedChange = onUseAiChange,
                    enabled = state.aiAvailable && !state.loading,
                )
            }
            if (state.useAi) {
                Text(stringResource(R.string.home_topic), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuizTopics.forEach { topic ->
                        FilterChip(
                            selected = topic == state.topic,
                            onClick = { onTopicChange(topic) },
                            label = { Text(topic) },
                            enabled = !state.loading,
                        )
                    }
                }
            }
        }
    }
}
