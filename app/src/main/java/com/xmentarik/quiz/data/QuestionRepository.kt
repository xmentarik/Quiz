package com.xmentarik.quiz.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

enum class QuestionSource { LOCAL, AI, LOCAL_AFTER_AI_FAILURE }

data class LoadedGame(val questions: QuestionSet, val source: QuestionSource)

class QuestionRepository(
    private val context: Context,
    private val generator: GeminiQuestionGenerator,
) {
    val aiAvailable: Boolean get() = generator.isConfigured

    private var localCache: List<Question>? = null

    private suspend fun localQuestions(): List<Question> = localCache ?: withContext(Dispatchers.IO) {
        context.assets.open(LOCAL_QUESTIONS_ASSET).bufferedReader().use { QuestionJson.parseList(it.readText()) }
    }.also { localCache = it }

    /** Builds a game from AI questions when requested, falling back to the bundled bank on any failure. */
    suspend fun loadGame(useAi: Boolean, topic: String, random: Random = Random.Default): LoadedGame {
        val local = localQuestions()
        if (!useAi || !aiAvailable) return LoadedGame(QuestionSetBuilder.build(local, random), QuestionSource.LOCAL)

        return try {
            val generated = generator.generate(topic)
            LoadedGame(QuestionSetBuilder.build(generated, random, fallback = local), QuestionSource.AI)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadedGame(QuestionSetBuilder.build(local, random), QuestionSource.LOCAL_AFTER_AI_FAILURE)
        }
    }

    companion object {
        const val LOCAL_QUESTIONS_ASSET = "questions_ar.json"
    }
}
