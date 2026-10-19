package com.xmentarik.quiz.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** The bundled question banks (one JSON file per language in assets/). */
class QuestionRepository(private val context: Context) {
    private val localCache = mutableMapOf<AppLanguage, List<Question>>()

    private suspend fun localQuestions(language: AppLanguage): List<Question> = localCache[language]
        ?: withContext(Dispatchers.IO) {
            context.assets.open(language.questionsAsset).bufferedReader().use { QuestionJson.parseList(it.readText()) }
        }.also { localCache[language] = it }

    /** [topic] is an index into [AppLanguage.topics]; null means all topics. */
    suspend fun loadGame(
        topic: Int?,
        language: AppLanguage,
        recentlySeen: Set<String> = emptySet(),
        random: Random = Random.Default,
    ): QuestionSet = QuestionSetBuilder.buildForTopic(localQuestions(language), topic, random, recentlySeen)
}
