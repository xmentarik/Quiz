package com.xmentarik.quiz.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerStats(
    val bestPrize: Int = 0,
    val gamesPlayed: Int = 0,
    val millionWins: Int = 0,
)

class StatsStore(context: Context) {
    private val prefs = context.getSharedPreferences("player_stats", Context.MODE_PRIVATE)
    private val _stats = MutableStateFlow(read())
    val stats: StateFlow<PlayerStats> = _stats.asStateFlow()

    /** Saves a finished game and returns true when it set a new record. */
    fun recordGame(prize: Int, wonMillion: Boolean): Boolean {
        val current = _stats.value
        val updated = PlayerStats(
            bestPrize = maxOf(current.bestPrize, prize),
            gamesPlayed = current.gamesPlayed + 1,
            millionWins = current.millionWins + if (wonMillion) 1 else 0,
        )
        prefs.edit {
            putInt(KEY_BEST, updated.bestPrize)
            putInt(KEY_GAMES, updated.gamesPlayed)
            putInt(KEY_MILLIONS, updated.millionWins)
        }
        _stats.value = updated
        return prize > current.bestPrize
    }

    private fun read() = PlayerStats(
        bestPrize = prefs.getInt(KEY_BEST, 0),
        gamesPlayed = prefs.getInt(KEY_GAMES, 0),
        millionWins = prefs.getInt(KEY_MILLIONS, 0),
    )

    private companion object {
        const val KEY_BEST = "best_prize"
        const val KEY_GAMES = "games_played"
        const val KEY_MILLIONS = "million_wins"
    }
}
