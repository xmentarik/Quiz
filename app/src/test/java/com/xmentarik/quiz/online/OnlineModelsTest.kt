package com.xmentarik.quiz.online

import com.xmentarik.quiz.data.QuestionJson
import com.xmentarik.quiz.data.QuestionSetBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class OnlineModelsTest {

    @Test
    fun generatedCodesUseTheUnambiguousAlphabet() {
        val rulesPattern = Regex("^[A-HJ-NP-Z2-9]{6}$") // same pattern as firestore.rules
        repeat(500) { seed ->
            val code = ChallengeCodes.generate(Random(seed))
            assertTrue(code, rulesPattern.matches(code))
            assertEquals(code, ChallengeCodes.normalize(code))
        }
        assertTrue(ChallengeCodes.ALPHABET.none { it in "01IO" })
    }

    @Test
    fun codesAreNormalisedFromWhatPeopleType() {
        assertEquals("AB3K9Z", ChallengeCodes.normalize(" ab3-k9z "))
        assertEquals("AB3K9Z", ChallengeCodes.normalize("AB3 K9Z"))
        assertNull(ChallengeCodes.normalize("AB3K9"))
        assertNull(ChallengeCodes.normalize("AB3K9ZZ"))
        assertNull(ChallengeCodes.normalize("AB0K9Z")) // zero is not in the alphabet
    }

    @Test
    fun playerNamesAreCleanedUp() {
        assertEquals("Nawal", PlayerNames.sanitize("  Nawal "))
        assertEquals("أمين المغربي", PlayerNames.sanitize("أمين   المغربي"))
        assertEquals("Léa_92", PlayerNames.sanitize("Léa_92"))
        assertNull(PlayerNames.sanitize("A"))
        assertNull(PlayerNames.sanitize("x".repeat(21)))
        assertNull(PlayerNames.sanitize("<script>"))
    }

    @Test
    fun challengeRankingUsesPrizeThenAnswersThenTime() {
        val results = listOf(
            ChallengeResult("slow", "Slow", 32_000, 10, 300),
            ChallengeResult("fast", "Fast", 32_000, 10, 120),
            ChallengeResult("best", "Best", 64_000, 11, 500),
            ChallengeResult("more", "More", 32_000, 11, 400),
        )
        assertEquals(listOf("best", "more", "fast", "slow"), Rankings.sortResults(results).map { it.playerId })
    }

    @Test
    fun payloadRoundTripsTheExactGame() {
        val bank = QuestionJson.parseList(File("src/main/assets/questions_fr.json").readText())
        val set = QuestionSetBuilder.build(bank, Random(9))
        val decoded = ChallengePayload.decode(ChallengePayload.of(set, "fr").encode())
        assertEquals("fr", decoded.language)
        assertEquals(set, decoded.toQuestionSet())
        // Well under the 200 000 characters allowed by the security rules.
        assertTrue(ChallengePayload.of(set, "fr").encode().length < 50_000)
    }
}
