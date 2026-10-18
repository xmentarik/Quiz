package com.xmentarik.quiz.online

import android.content.Context
import com.xmentarik.quiz.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

/**
 * Firestore layout (see firestore.rules):
 * - leaderboard/{uid}: name, bestPrize, gamesPlayed, millionWins, updatedAt
 * - challenges/{code}: creatorId, creatorName, language, payload (JSON), createdAt
 * - challenges/{code}/results/{uid}: name, prize, correctCount, durationSeconds, finishedAt
 * - reports/{auto id}: question as shown, reason, comment, reporterId, createdAt (readable from the console only)
 */
class FirebaseOnlineService(context: Context) : OnlineService {
    override val isAvailable: Boolean = FirebaseApp.getApps(context).isNotEmpty()

    private val auth by lazy { Firebase.auth }
    private val db by lazy { Firebase.firestore }

    override suspend fun playerId(): String =
        auth.currentUser?.uid
            ?: auth.signInAnonymously().await().user?.uid
            ?: error("Anonymous sign-in returned no user")

    override suspend fun submitStats(name: String, bestPrize: Int, gamesPlayed: Int, millionWins: Int) {
        val ref = db.collection(LEADERBOARD).document(playerId())
        // Keep the best values ever sent, even after a reinstall resets the local stats.
        db.runTransaction { tx ->
            val old = tx.get(ref)
            tx.set(
                ref,
                mapOf(
                    "name" to name,
                    "bestPrize" to maxOf(bestPrize.toLong(), old.getLong("bestPrize") ?: 0L),
                    "gamesPlayed" to maxOf(gamesPlayed.toLong(), old.getLong("gamesPlayed") ?: 0L),
                    "millionWins" to maxOf(millionWins.toLong(), old.getLong("millionWins") ?: 0L),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
        }.await()
    }

    override suspend fun topPlayers(limit: Int): List<LeaderboardEntry> {
        playerId()
        val snapshot = db.collection(LEADERBOARD)
            .orderBy("bestPrize", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .get()
            .await()
        return Rankings.sortLeaderboard(
            snapshot.documents.map {
                LeaderboardEntry(
                    playerId = it.id,
                    name = it.getString("name").orEmpty(),
                    bestPrize = (it.getLong("bestPrize") ?: 0L).toInt(),
                    millionWins = (it.getLong("millionWins") ?: 0L).toInt(),
                )
            },
        )
    }

    override suspend fun rankOf(bestPrize: Int): Int {
        playerId()
        val better = db.collection(LEADERBOARD)
            .whereGreaterThan("bestPrize", bestPrize.toLong())
            .count()
            .get(AggregateSource.SERVER)
            .await()
            .count
        return better.toInt() + 1
    }

    override suspend fun createChallenge(creatorName: String, payload: ChallengePayload): String {
        val uid = playerId()
        repeat(CODE_ATTEMPTS) {
            val code = ChallengeCodes.generate(Random.Default)
            val ref = db.collection(CHALLENGES).document(code)
            val created = db.runTransaction { tx ->
                if (tx.get(ref).exists()) {
                    false
                } else {
                    tx.set(
                        ref,
                        mapOf(
                            "creatorId" to uid,
                            "creatorName" to creatorName,
                            "language" to payload.language,
                            "payload" to payload.encode(),
                            "createdAt" to FieldValue.serverTimestamp(),
                        ),
                    )
                    true
                }
            }.await()
            if (created) return code
        }
        error("No free challenge code after $CODE_ATTEMPTS attempts")
    }

    override suspend fun loadChallenge(code: String): Challenge? {
        playerId()
        val doc = db.collection(CHALLENGES).document(code).get().await()
        val payload = doc.getString("payload") ?: return null
        return Challenge(code, doc.getString("creatorName").orEmpty(), ChallengePayload.decode(payload))
    }

    override suspend fun hasPlayed(code: String): Boolean =
        results(code).document(playerId()).get().await().exists()

    override suspend fun submitChallengeResult(code: String, result: ChallengeResult) {
        results(code).document(playerId()).set(
            mapOf(
                "name" to result.name,
                "prize" to result.prize.toLong(),
                "correctCount" to result.correctCount.toLong(),
                "durationSeconds" to result.durationSeconds.toLong(),
                "finishedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override suspend fun challengeResults(code: String): List<ChallengeResult> {
        playerId()
        val snapshot = results(code).get().await()
        return Rankings.sortResults(
            snapshot.documents.map {
                ChallengeResult(
                    playerId = it.id,
                    name = it.getString("name").orEmpty(),
                    prize = (it.getLong("prize") ?: 0L).toInt(),
                    correctCount = (it.getLong("correctCount") ?: 0L).toInt(),
                    durationSeconds = (it.getLong("durationSeconds") ?: 0L).toInt(),
                )
            },
        )
    }

    override suspend fun submitReport(report: QuestionReport) {
        val uid = playerId()
        db.collection(REPORTS).add(
            mapOf(
                "questionId" to report.questionId,
                "language" to report.language,
                "text" to report.text,
                "options" to report.options,
                "correctIndex" to report.correctIndex.toLong(),
                "selectedOption" to report.selectedOption?.toLong(),
                "reason" to report.reason.name,
                "comment" to report.comment,
                "appVersion" to BuildConfig.VERSION_NAME,
                "reporterId" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    private fun results(code: String) = db.collection(CHALLENGES).document(code).collection(RESULTS)

    private companion object {
        const val LEADERBOARD = "leaderboard"
        const val CHALLENGES = "challenges"
        const val RESULTS = "results"
        const val REPORTS = "reports"
        const val CODE_ATTEMPTS = 5
    }
}
