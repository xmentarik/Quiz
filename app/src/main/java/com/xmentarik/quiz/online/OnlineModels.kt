package com.xmentarik.quiz.online

import com.xmentarik.quiz.data.Question
import com.xmentarik.quiz.data.QuestionJson
import com.xmentarik.quiz.data.QuestionSet
import kotlinx.serialization.Serializable
import kotlin.random.Random

data class LeaderboardEntry(
    val playerId: String,
    val name: String,
    val bestPrize: Int,
    val millionWins: Int,
)

data class ChallengeResult(
    val playerId: String,
    val name: String,
    val prize: Int,
    val correctCount: Int,
    val durationSeconds: Int,
)

/** Everything a friend needs to replay exactly the same game. */
@Serializable
data class ChallengePayload(
    val language: String,
    val main: List<Question>,
    val reserve: List<Question>,
) {
    fun toQuestionSet() = QuestionSet(main, reserve)

    fun encode(): String = QuestionJson.json.encodeToString(serializer(), this)

    companion object {
        fun of(set: QuestionSet, language: String) = ChallengePayload(language, set.main, set.reserve)

        fun decode(raw: String): ChallengePayload = QuestionJson.json.decodeFromString(serializer(), raw)
    }
}

data class Challenge(
    val code: String,
    val creatorName: String,
    val payload: ChallengePayload,
)

object ChallengeCodes {
    const val LENGTH = 6

    /** No 0/O or 1/I, so codes read out loud or typed from a screenshot stay unambiguous. */
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun generate(random: Random = Random.Default): String =
        String(CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] })

    /** Accepts lower case, spaces and dashes ("ab3-k9z"); returns null when it cannot be a code. */
    fun normalize(input: String): String? {
        val code = input.uppercase().filter { !it.isWhitespace() && it != '-' }
        return code.takeIf { it.length == LENGTH && it.all { c -> c in ALPHABET } }
    }
}

object PlayerNames {
    const val MIN_LENGTH = 2
    const val MAX_LENGTH = 20

    /** Trims and collapses spaces; letters (any script), digits, spaces, "_" and "-" only. */
    fun sanitize(raw: String): String? {
        val name = raw.trim().replace(Regex("\\s+"), " ")
        val allowed = name.all { it.isLetterOrDigit() || it == ' ' || it == '_' || it == '-' }
        return name.takeIf { allowed && it.length in MIN_LENGTH..MAX_LENGTH }
    }
}

object Rankings {
    /** Highest prize first, then most correct answers, then the fastest. */
    fun sortResults(results: List<ChallengeResult>): List<ChallengeResult> = results.sortedWith(
        compareByDescending<ChallengeResult> { it.prize }
            .thenByDescending { it.correctCount }
            .thenBy { it.durationSeconds },
    )

    fun sortLeaderboard(entries: List<LeaderboardEntry>): List<LeaderboardEntry> = entries.sortedWith(
        compareByDescending<LeaderboardEntry> { it.bestPrize }.thenByDescending { it.millionWins },
    )
}

/** Online features, implemented with Firebase in the app and with fakes in tests. */
interface OnlineService {
    /** False when the app was built without a Firebase configuration. */
    val isAvailable: Boolean

    suspend fun playerId(): String

    suspend fun submitStats(name: String, bestPrize: Int, gamesPlayed: Int, millionWins: Int)

    suspend fun topPlayers(limit: Int = 50): List<LeaderboardEntry>

    /** 1-based rank of a player with [bestPrize]. */
    suspend fun rankOf(bestPrize: Int): Int

    /** Stores the challenge and returns its code. */
    suspend fun createChallenge(creatorName: String, payload: ChallengePayload): String

    suspend fun loadChallenge(code: String): Challenge?

    suspend fun hasPlayed(code: String): Boolean

    suspend fun submitChallengeResult(code: String, result: ChallengeResult)

    suspend fun challengeResults(code: String): List<ChallengeResult>
}
