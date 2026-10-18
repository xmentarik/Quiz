package com.xmentarik.quiz.data

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiQuestionGeneratorTest {

    @Test
    fun requestAsksForStructuredJson() {
        val request = Json.parseToJsonElement(GeminiQuestionGenerator.buildRequest("علوم", 6)).jsonObject
        val config = request["generationConfig"]!!.jsonObject
        assertEquals("application/json", config["responseMimeType"]!!.jsonPrimitive.content)
        assertEquals("ARRAY", config["responseSchema"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        val prompt = request["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray[0]
            .jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(prompt.contains("علوم"))
        assertTrue(prompt.contains("18"))
    }

    @Test
    fun parsesTextPartsAndSkipsThoughts() {
        val questions = """[{"text":"س1","options":["أ","ب","ج","د"],"correctIndex":1,"difficulty":1,"explanation":"ش"},""" +
            """{"text":"س2","options":["أ","ب","ج"],"correctIndex":1,"difficulty":2,"explanation":"ش"}]"""
        val response = """
            {"candidates":[{"content":{"role":"model","parts":[
              {"text":"thinking…","thought":true},
              {"text":${Json.encodeToString(String.serializer(), "```json\n$questions\n```")}}
            ]}}]}
        """.trimIndent()

        val parsed = GeminiQuestionGenerator.parseResponse(response, "علوم")
        assertEquals(1, parsed.size)
        assertEquals("س1", parsed[0].text)
        assertEquals("علوم", parsed[0].category)
        assertTrue(parsed[0].id.startsWith("ai-"))
    }

    @Test(expected = java.io.IOException::class)
    fun missingCandidatesIsAnError() {
        GeminiQuestionGenerator.parseResponse("""{"promptFeedback":{"blockReason":"SAFETY"}}""", "x")
    }
}
