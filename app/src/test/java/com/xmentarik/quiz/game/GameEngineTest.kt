package com.xmentarik.quiz.game

import com.xmentarik.quiz.data.Question
import com.xmentarik.quiz.data.QuestionSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameEngineTest {
    private val engine = GameEngine(Random(42))

    private fun question(n: Int, difficulty: Int = 1 + n / 5) = Question(
        id = "q$n",
        text = "Question $n",
        options = listOf("A$n", "B$n", "C$n", "D$n"),
        correctIndex = n % 4,
        difficulty = difficulty,
    )

    private fun newGame(): GameState = engine.start(
        QuestionSet(main = (0 until 15).map { question(it) }, reserve = listOf(question(100, difficulty = 1))),
    )

    private fun GameState.answerCorrectly() = engine.answer(this, currentQuestion.correctIndex)
    private fun GameState.answerWrong() = engine.answer(this, (currentQuestion.correctIndex + 1) % 4)

    private fun advance(correctAnswers: Int): GameState {
        var state = newGame()
        repeat(correctAnswers) { state = engine.nextQuestion(state.answerCorrectly()) }
        return state
    }

    @Test
    fun ladderHasFifteenStepsUpToAMillion() {
        assertEquals(15, MoneyLadder.size)
        assertEquals(1_000_000, MoneyLadder.top)
        assertEquals(MoneyLadder.amounts.sorted(), MoneyLadder.amounts)
    }

    @Test
    fun guaranteedPrizeFollowsSafeHavens() {
        assertEquals(0, MoneyLadder.guaranteed(0))
        assertEquals(0, MoneyLadder.guaranteed(4))
        assertEquals(1_000, MoneyLadder.guaranteed(5))
        assertEquals(1_000, MoneyLadder.guaranteed(9))
        assertEquals(32_000, MoneyLadder.guaranteed(10))
        assertEquals(32_000, MoneyLadder.guaranteed(14))
    }

    @Test
    fun correctAnswerBanksTheStepPrize() {
        val state = newGame().answerCorrectly()
        assertEquals(GamePhase.CORRECT, state.phase)
        assertEquals(100, state.prize)
        assertEquals(1, state.correctCount)
    }

    @Test
    fun wrongAnswerFallsBackToSafeHaven() {
        val state = advance(7).answerWrong()
        assertEquals(GamePhase.WRONG, state.phase)
        assertEquals(1_000, state.prize)
        assertEquals(7, state.correctCount)
    }

    @Test
    fun walkingAwayKeepsTheBankedPrize() {
        val state = engine.walkAway(advance(7))
        assertEquals(GamePhase.WALKED_AWAY, state.phase)
        assertEquals(4_000, state.prize)
    }

    @Test
    fun timeUpBehavesLikeAWrongAnswer() {
        val state = engine.timeUp(advance(11))
        assertEquals(GamePhase.TIME_UP, state.phase)
        assertEquals(32_000, state.prize)
    }

    @Test
    fun answeringAllFifteenWinsTheMillion() {
        val state = advance(14).answerCorrectly()
        assertEquals(GamePhase.WON, state.phase)
        assertEquals(1_000_000, state.prize)
        assertEquals(15, state.correctCount)
    }

    @Test
    fun actionsAfterTheGameEndsAreIgnored() {
        val over = newGame().answerWrong()
        assertSame(over, engine.answer(over, 0))
        assertSame(over, engine.nextQuestion(over))
        assertSame(over, engine.walkAway(over))
        assertSame(over, engine.use(over, Lifeline.FIFTY_FIFTY))
    }

    @Test
    fun fiftyFiftyHidesTwoWrongOptionsOnce() {
        val state = engine.use(newGame(), Lifeline.FIFTY_FIFTY)
        assertEquals(2, state.hiddenOptions.size)
        assertFalse(state.currentQuestion.correctIndex in state.hiddenOptions)
        assertFalse(state.canUse(Lifeline.FIFTY_FIFTY))
        assertSame(state, engine.use(state, Lifeline.FIFTY_FIFTY))
        assertSame(state, engine.answer(state, state.hiddenOptions.first()))
    }

    @Test
    fun audienceVotesSumToHundredAndSkipHiddenOptions() {
        repeat(200) { seed ->
            val seeded = GameEngine(Random(seed))
            val state = seeded.use(seeded.use(newGame(), Lifeline.FIFTY_FIFTY), Lifeline.ASK_AUDIENCE)
            val votes = state.audienceVotes!!
            assertEquals(100, votes.sum())
            assertTrue(votes.all { it >= 0 })
            state.hiddenOptions.forEach { assertEquals(0, votes[it]) }
        }
    }

    @Test
    fun friendAlwaysPointsAtAVisibleOption() {
        repeat(200) { seed ->
            val seeded = GameEngine(Random(seed))
            val state = seeded.use(seeded.use(advance(12), Lifeline.FIFTY_FIFTY), Lifeline.PHONE_A_FRIEND)
            val advice = state.friendAdvice!!
            assertFalse(advice.optionIndex in state.hiddenOptions)
            assertTrue(advice.confidence in 0..100)
        }
    }

    @Test
    fun switchQuestionReplacesCurrentQuestionAndResetsAids() {
        val withFifty = engine.use(newGame(), Lifeline.FIFTY_FIFTY)
        val switched = engine.use(withFifty, Lifeline.SWITCH_QUESTION)
        assertEquals("q100", switched.currentQuestion.id)
        assertTrue(switched.hiddenOptions.isEmpty())
        assertTrue(switched.reserve.isEmpty())
        assertFalse(switched.canUse(Lifeline.SWITCH_QUESTION))
        assertNotEquals(withFifty.currentQuestion, switched.currentQuestion)
    }

    @Test
    fun secondChanceResumesTheGameOnceWithAFreshQuestion() {
        val lost = advance(3).answerWrong()
        assertTrue(lost.canGetSecondChance)
        val saved = engine.secondChance(lost)
        assertEquals(GamePhase.PLAYING, saved.phase)
        assertEquals(3, saved.index)
        assertEquals(MoneyLadder.banked(3), saved.prize)
        assertEquals("q100", saved.currentQuestion.id) // the easy spare question
        assertTrue(saved.reserve.isEmpty())
        // Only once per game.
        val lostAgain = saved.answerWrong()
        assertFalse(lostAgain.canGetSecondChance)
        assertSame(lostAgain, engine.secondChance(lostAgain))
    }

    @Test
    fun secondChanceWithoutASpareRemovesTheWrongAnswer() {
        val lost = advance(12).answerWrong() // no hard spare question in this game
        val saved = engine.secondChance(lost)
        assertEquals(lost.currentQuestion, saved.currentQuestion)
        assertTrue(lost.selectedOption!! in saved.hiddenOptions)
        assertEquals(null, saved.selectedOption)
        assertEquals(GamePhase.PLAYING, saved.phase)
    }

    @Test
    fun secondChanceIsNotOfferedAfterWinningOrWalkingAway() {
        assertFalse(advance(14).answerCorrectly().canGetSecondChance)
        assertFalse(engine.walkAway(advance(3)).canGetSecondChance)
        assertTrue(engine.timeUp(advance(3)).canGetSecondChance)
    }

    @Test
    fun timeLimitsMatchTheThreeStages() {
        assertEquals(30, TimeLimits.secondsFor(0))
        assertEquals(45, TimeLimits.secondsFor(5))
        assertEquals(null, TimeLimits.secondsFor(10))
    }
}
