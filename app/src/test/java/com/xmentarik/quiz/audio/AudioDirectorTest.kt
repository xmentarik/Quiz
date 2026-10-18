package com.xmentarik.quiz.audio

import com.xmentarik.quiz.data.Question
import com.xmentarik.quiz.data.QuestionSet
import com.xmentarik.quiz.game.GameEngine
import com.xmentarik.quiz.game.GameState
import com.xmentarik.quiz.game.Lifeline
import com.xmentarik.quiz.ui.QuizUiState
import com.xmentarik.quiz.ui.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AudioDirectorTest {
    private val engine = GameEngine(Random(3))

    private val game: GameState = engine.start(
        QuestionSet(
            main = (0 until 15).map {
                Question(id = "q$it", text = "Q$it", options = listOf("a", "b", "c", "d"), correctIndex = 0, difficulty = 1 + it / 5)
            },
            reserve = emptyList(),
        ),
    )

    private val playing = QuizUiState(screen = Screen.GAME, game = game, secondsLeft = 30)

    private fun GameState.advancedTo(index: Int): GameState {
        var state = this
        repeat(index) { state = engine.nextQuestion(engine.answer(state, 0)) }
        return state
    }

    @Test
    fun firstObservationIsSilent() {
        assertEquals(emptyList<SoundCue>(), AudioDirector.cues(null, playing))
    }

    @Test
    fun enteringTheGamePlaysTheStartSting() {
        assertEquals(listOf(SoundCue.GAME_START), AudioDirector.cues(QuizUiState(), playing))
        assertEquals(
            listOf(SoundCue.GAME_START),
            AudioDirector.cues(playing.copy(screen = Screen.RESULT), playing),
        )
    }

    @Test
    fun selectingLockingAndRevealingAnAnswer() {
        val selected = playing.copy(pendingOption = 2)
        assertEquals(listOf(SoundCue.SELECT), AudioDirector.cues(playing, selected))

        val locked = selected.copy(pendingOption = null, lockedOption = 0)
        assertEquals(listOf(SoundCue.FINAL_ANSWER), AudioDirector.cues(selected, locked))

        val correct = locked.copy(lockedOption = null, game = engine.answer(game, 0))
        assertEquals(listOf(SoundCue.CORRECT), AudioDirector.cues(locked, correct))

        val wrong = locked.copy(lockedOption = null, game = engine.answer(game, 1))
        assertEquals(listOf(SoundCue.WRONG), AudioDirector.cues(locked, wrong))
    }

    @Test
    fun safeHavensWinsAndOtherEndings() {
        val atFive = playing.copy(game = game.advancedTo(4))
        assertEquals(
            listOf(SoundCue.MILESTONE),
            AudioDirector.cues(atFive, atFive.copy(game = engine.answer(atFive.game!!, 0))),
        )

        val last = playing.copy(game = game.advancedTo(14))
        assertEquals(listOf(SoundCue.WIN), AudioDirector.cues(last, last.copy(game = engine.answer(last.game!!, 0))))

        assertEquals(listOf(SoundCue.TIME_UP), AudioDirector.cues(playing, playing.copy(game = engine.timeUp(game))))
        assertEquals(listOf(SoundCue.WALK_AWAY), AudioDirector.cues(playing, playing.copy(game = engine.walkAway(game))))
    }

    @Test
    fun secondChancePlaysTheLifelineSoundAndBringsTheMusicBack() {
        val lost = playing.copy(game = engine.answer(game, 1))
        val saved = lost.copy(game = engine.secondChance(lost.game!!))
        assertEquals(listOf(SoundCue.LIFELINE), AudioDirector.cues(lost, saved))
        assertEquals(MusicTrack.CALM, AudioDirector.music(saved))
    }

    @Test
    fun lifelinesAndNextQuestion() {
        val fifty = playing.copy(game = engine.use(game, Lifeline.FIFTY_FIFTY))
        assertEquals(listOf(SoundCue.LIFELINE), AudioDirector.cues(playing, fifty))

        val correct = playing.copy(game = engine.answer(game, 0))
        val next = correct.copy(game = engine.nextQuestion(correct.game!!), secondsLeft = 30)
        assertEquals(listOf(SoundCue.NEXT_QUESTION), AudioDirector.cues(correct, next))
    }

    @Test
    fun clockTicksOnlyInTheLastTenSeconds() {
        assertEquals(emptyList<SoundCue>(), AudioDirector.cues(playing.copy(secondsLeft = 12), playing.copy(secondsLeft = 11)))
        assertEquals(listOf(SoundCue.TICK), AudioDirector.cues(playing.copy(secondsLeft = 11), playing.copy(secondsLeft = 10)))
        assertEquals(listOf(SoundCue.TICK), AudioDirector.cues(playing.copy(secondsLeft = 2), playing.copy(secondsLeft = 1)))
        // A new question resets the clock upwards: no tick.
        assertTrue(SoundCue.TICK !in AudioDirector.cues(playing.copy(secondsLeft = 3), playing.copy(secondsLeft = 30)))
    }

    @Test
    fun musicFollowsTheStageAndGoesQuietForSuspense() {
        assertEquals(MusicTrack.MENU, AudioDirector.music(QuizUiState()))
        assertEquals(MusicTrack.CALM, AudioDirector.music(playing))
        assertEquals(MusicTrack.TENSE, AudioDirector.music(playing.copy(game = game.advancedTo(5))))
        assertEquals(MusicTrack.INTENSE, AudioDirector.music(playing.copy(game = game.advancedTo(10))))
        assertNull(AudioDirector.music(playing.copy(lockedOption = 1)))
        assertNull(AudioDirector.music(playing.copy(game = engine.answer(game, 0))))
        assertNull(AudioDirector.music(playing.copy(screen = Screen.RESULT)))
    }
}
