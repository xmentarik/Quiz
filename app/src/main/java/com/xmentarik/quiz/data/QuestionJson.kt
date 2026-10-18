package com.xmentarik.quiz.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object QuestionJson {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** Parses a JSON array of questions and drops the malformed ones. */
    fun parseList(raw: String): List<Question> =
        json.decodeFromString(ListSerializer(Question.serializer()), raw).filter { it.isValid }
}
