package com.xmentarik.quiz.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Generates fresh quiz questions with the Gemini REST API (structured JSON output). */
class GeminiQuestionGenerator(
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    suspend fun generate(topic: String, language: AppLanguage, perLevel: Int = DEFAULT_PER_LEVEL): List<Question> =
        withContext(Dispatchers.IO) {
            val connection = URL("$baseUrl/models/$model:generateContent").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 15_000
                connection.readTimeout = 90_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("x-goog-api-key", apiKey)
                connection.outputStream.use { it.write(buildRequest(topic, language, perLevel).toByteArray()) }

                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw IOException("Gemini HTTP $code: ${body.take(300)}")
                parseResponse(body, topic)
            } finally {
                connection.disconnect()
            }
        }

    companion object {
        const val DEFAULT_PER_LEVEL = 6

        internal fun buildPrompt(topic: String, language: AppLanguage, perLevel: Int): String = when (language) {
            AppLanguage.ARABIC -> """
            أنت معدّ أسئلة لبرنامج مسابقات ثقافية على غرار "من سيربح المليون".
            أنشئ ${perLevel * 3} سؤالاً باللغة العربية الفصحى في موضوع: «$topic».
            - $perLevel أسئلة سهلة (difficulty = 1) و $perLevel متوسطة (difficulty = 2) و $perLevel صعبة (difficulty = 3).
            - لكل سؤال 4 خيارات مختلفة وقصيرة، وإجابة صحيحة واحدة فقط لا خلاف عليها.
            - correctIndex هو رقم الخيار الصحيح من 0 إلى 3، ووزّع موقع الإجابة الصحيحة عشوائياً.
            - اجعل الخيارات الخاطئة معقولة ومقنعة.
            - explanation: جملة أو جملتان تشرحان الإجابة الصحيحة بمعلومة مفيدة.
            - تأكد من صحة كل معلومة، وتجنب الأسئلة التي تتغير إجابتها مع الزمن.
            """.trimIndent()
            AppLanguage.FRENCH -> """
            Tu écris les questions d'un jeu télévisé de culture générale du type « Qui veut gagner des millions ? ».
            Rédige ${perLevel * 3} questions en français sur le thème : « $topic ».
            - $perLevel faciles (difficulty = 1), $perLevel moyennes (difficulty = 2) et $perLevel difficiles (difficulty = 3).
            - Chaque question a 4 propositions courtes et différentes, et une seule bonne réponse incontestable.
            - correctIndex est l'indice de la bonne réponse, de 0 à 3 ; varie sa position au hasard.
            - Les mauvaises réponses doivent être plausibles.
            - explanation : une ou deux phrases qui expliquent la bonne réponse avec une information utile.
            - Vérifie l'exactitude de chaque fait et évite les questions dont la réponse change avec le temps.
            """.trimIndent()
            AppLanguage.ENGLISH -> """
            You write the questions for a TV general-knowledge quiz show in the style of "Who Wants to Be a Millionaire".
            Write ${perLevel * 3} questions in English on the topic: "$topic".
            - $perLevel easy (difficulty = 1), $perLevel medium (difficulty = 2) and $perLevel hard (difficulty = 3).
            - Each question has 4 short, distinct options and exactly one indisputable correct answer.
            - correctIndex is the index of the correct option, from 0 to 3; vary its position at random.
            - Make the wrong options plausible.
            - explanation: one or two sentences explaining the correct answer with a useful fact.
            - Check that every fact is accurate and avoid questions whose answer changes over time.
            """.trimIndent()
        }

        internal fun buildRequest(topic: String, language: AppLanguage, perLevel: Int): String {
            val request: JsonObject = buildJsonObject {
                putJsonArray("contents") {
                    addJsonObject {
                        put("role", "user")
                        putJsonArray("parts") { addJsonObject { put("text", buildPrompt(topic, language, perLevel)) } }
                    }
                }
                putJsonObject("generationConfig") {
                    put("temperature", 1.0)
                    put("responseMimeType", "application/json")
                    putJsonObject("responseSchema") {
                        put("type", "ARRAY")
                        putJsonObject("items") {
                            put("type", "OBJECT")
                            putJsonObject("properties") {
                                putJsonObject("text") { put("type", "STRING") }
                                putJsonObject("options") {
                                    put("type", "ARRAY")
                                    putJsonObject("items") { put("type", "STRING") }
                                }
                                putJsonObject("correctIndex") { put("type", "INTEGER") }
                                putJsonObject("difficulty") { put("type", "INTEGER") }
                                putJsonObject("explanation") { put("type", "STRING") }
                            }
                            putJsonArray("required") {
                                listOf("text", "options", "correctIndex", "difficulty", "explanation").forEach { add(it) }
                            }
                        }
                    }
                }
            }
            return request.toString()
        }

        /** Extracts the questions from a `generateContent` response, ignoring "thought" parts. */
        internal fun parseResponse(raw: String, topic: String): List<Question> {
            val parts = QuestionJson.json.parseToJsonElement(raw).jsonObject["candidates"]
                ?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("parts")?.jsonArray
                ?: throw IOException("Gemini response has no content")

            val text = parts
                .map { it.jsonObject }
                .filter { it["thought"]?.jsonPrimitive?.booleanOrNull != true }
                .mapNotNull { it["text"]?.jsonPrimitive?.contentOrNull }
                .joinToString("")
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            return QuestionJson.json.decodeFromString(ListSerializer(Question.serializer()), text)
                .mapIndexed { i, q -> q.copy(id = "ai-$i", category = topic) }
                .filter { it.isValid }
                .distinctBy { it.text.trim() }
        }
    }
}
