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

    private val localCache = mutableMapOf<AppLanguage, List<Question>>()

    private suspend fun localQuestions(language: AppLanguage): List<Question> = localCache[language]
        ?: withContext(Dispatchers.IO) {
            context.assets.open(language.questionsAsset).bufferedReader().use { QuestionJson.parseList(it.readText()) }
        }.also { localCache[language] = it }

    /** Builds a game from AI questions when requested, falling back to the bundled bank on any failure. */
    suspend fun loadGame(
        useAi: Boolean,
        topic: String,
        language: AppLanguage,
        recentlySeen: Set<String> = emptySet(),
        random: Random = Random.Default,
    ): LoadedGame {
        val local = localQuestions(language)
        if (!useAi || !aiAvailable) {
            return LoadedGame(QuestionSetBuilder.build(local, random, recentlySeen = recentlySeen), QuestionSource.LOCAL)
        }

        return try {
            val generated = generator.generate(topic, language)
            LoadedGame(QuestionSetBuilder.build(generated, random, fallback = local, recentlySeen = recentlySeen), QuestionSource.AI)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadedGame(
                QuestionSetBuilder.build(local, random, recentlySeen = recentlySeen),
                QuestionSource.LOCAL_AFTER_AI_FAILURE,
            )
        }
    }
}
