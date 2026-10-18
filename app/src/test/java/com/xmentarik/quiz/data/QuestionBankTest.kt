package com.xmentarik.quiz.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class QuestionBankTest {
    private fun bank(language: AppLanguage): List<Question> =
        QuestionJson.parseList(File("src/main/assets/${language.questionsAsset}").readText())

    private val bundled: List<Question> by lazy { bank(AppLanguage.ARABIC) }

    @Test
    fun everyBundledBankIsValidAndUnique() {
        AppLanguage.entries.forEach { language ->
            val questions = bank(language)
            assertEquals(language.name, 60, questions.size)
            assertEquals(questions.size, questions.map { it.id }.toSet().size)
            assertEquals(questions.size, questions.map { it.text }.toSet().size)
            (1..3).forEach { level -> assertEquals(20, questions.count { it.difficulty == level }) }
        }
    }

    @Test
    fun translationsMatchTheArabicBank() {
        val french = bank(AppLanguage.FRENCH).associateBy { it.id }
        bundled.forEach { arabic ->
            val translated = french.getValue(arabic.id)
            assertEquals(arabic.id, arabic.correctIndex, translated.correctIndex)
            assertEquals(arabic.id, arabic.difficulty, translated.difficulty)
            assertTrue(arabic.id, translated.explanation.isNotBlank())
        }
    }

    @Test
    fun builtSetIsOrderedByDifficultyAndHasReserves() {
        repeat(50) { seed ->
            val set = QuestionSetBuilder.build(bundled, Random(seed))
            assertEquals(15, set.main.size)
            assertEquals(List(5) { 1 } + List(5) { 2 } + List(5) { 3 }, set.main.map { it.difficulty })
            assertEquals(3, set.reserve.size)
            assertEquals(18, (set.main + set.reserve).map { it.id }.toSet().size)
            assertTrue((set.main + set.reserve).all { it.isValid })
        }
    }

    @Test
    fun shufflingKeepsTheCorrectAnswer() {
        val original = bundled.first()
        repeat(20) { seed ->
            val shuffled = original.withShuffledOptions(Random(seed))
            assertEquals(original.options[original.correctIndex], shuffled.options[shuffled.correctIndex])
            assertEquals(original.options.toSet(), shuffled.options.toSet())
        }
    }

    @Test
    fun shortPoolIsToppedUpFromFallback() {
        val aiPool = bundled.filter { it.difficulty == 3 }.take(2).map { it.copy(id = "ai-${it.id}") }
        val set = QuestionSetBuilder.build(aiPool, Random(1), fallback = bundled)
        assertEquals(15, set.main.size)
        assertTrue(set.main.count { it.id.startsWith("ai-") } == 2)
        assertEquals(15, set.main.map { it.text }.toSet().size)
    }

    @Test
    fun invalidQuestionsAreDropped() {
        val raw = """
            [
              {"text":"ok","options":["a","b","c","d"],"correctIndex":0,"difficulty":1},
              {"text":"three options","options":["a","b","c"],"correctIndex":0,"difficulty":1},
              {"text":"bad index","options":["a","b","c","d"],"correctIndex":4,"difficulty":1},
              {"text":"duplicate options","options":["a","a","c","d"],"correctIndex":0,"difficulty":1},
              {"text":"bad level","options":["a","b","c","d"],"correctIndex":0,"difficulty":7}
            ]
        """.trimIndent()
        assertEquals(listOf("ok"), QuestionJson.parseList(raw).map { it.text })
    }
}
