package com.xmentarik.quiz.game

import com.xmentarik.quiz.data.Question
import com.xmentarik.quiz.data.QuestionSet
import kotlin.random.Random

/** Pure game rules. Every action returns a new [GameState]; invalid actions return the state unchanged. */
class GameEngine(private val random: Random = Random.Default) {

    fun start(set: QuestionSet): GameState {
        require(set.main.size == MoneyLadder.size) { "A game needs ${MoneyLadder.size} questions" }
        return GameState(questions = set.main, reserve = set.reserve)
    }

    fun answer(state: GameState, option: Int): GameState {
        if (state.phase != GamePhase.PLAYING || option !in state.currentQuestion.options.indices ||
            option in state.hiddenOptions
        ) return state

        val correct = option == state.currentQuestion.correctIndex
        return when {
            correct && state.index == state.questions.lastIndex ->
                state.copy(phase = GamePhase.WON, selectedOption = option, prize = MoneyLadder.top)
            correct ->
                state.copy(phase = GamePhase.CORRECT, selectedOption = option, prize = MoneyLadder.banked(state.index + 1))
            else ->
                state.copy(phase = GamePhase.WRONG, selectedOption = option, prize = MoneyLadder.guaranteed(state.index))
        }
    }

    fun nextQuestion(state: GameState): GameState {
        if (state.phase != GamePhase.CORRECT) return state
        return state.copy(index = state.index + 1, phase = GamePhase.PLAYING).clearQuestionAids()
    }

    fun walkAway(state: GameState): GameState {
        if (state.phase != GamePhase.PLAYING) return state
        return state.copy(phase = GamePhase.WALKED_AWAY, prize = MoneyLadder.banked(state.index))
    }

    fun timeUp(state: GameState): GameState {
        if (state.phase != GamePhase.PLAYING) return state
        return state.copy(phase = GamePhase.TIME_UP, prize = MoneyLadder.guaranteed(state.index))
    }

    /**
     * Saves a lost game once (after watching a rewarded ad): play resumes on the same step with a fresh
     * question of the same difficulty, or, without one, the same question minus the wrong answer picked.
     */
    fun secondChance(state: GameState): GameState {
        if (!state.canGetSecondChance) return state
        val question = state.currentQuestion
        val replacement = state.reserve.firstOrNull { it.difficulty == question.difficulty }
        val resumed = state.copy(
            phase = GamePhase.PLAYING,
            prize = MoneyLadder.banked(state.index),
            secondChanceUsed = true,
        )
        return if (replacement != null) {
            resumed.copy(
                questions = state.questions.toMutableList().also { it[state.index] = replacement },
                reserve = state.reserve - replacement,
            ).clearQuestionAids()
        } else {
            resumed.copy(
                selectedOption = null,
                hiddenOptions = state.hiddenOptions + listOfNotNull(state.selectedOption),
            )
        }
    }

    fun use(state: GameState, lifeline: Lifeline): GameState {
        if (!state.canUse(lifeline)) return state
        val used = state.copy(usedLifelines = state.usedLifelines + lifeline)
        val question = state.currentQuestion
        return when (lifeline) {
            Lifeline.FIFTY_FIFTY -> {
                val wrong = question.visibleWrongOptions(state.hiddenOptions).shuffled(random)
                val toHide = wrong.take(wrong.size - 1)
                used.copy(hiddenOptions = state.hiddenOptions + toHide)
            }
            Lifeline.ASK_AUDIENCE -> used.copy(audienceVotes = audienceVotes(question, state.hiddenOptions))
            Lifeline.PHONE_A_FRIEND -> used.copy(friendAdvice = friendAdvice(question, state.hiddenOptions))
            Lifeline.SWITCH_QUESTION -> {
                val replacement = state.reserve.firstOrNull { it.difficulty == question.difficulty }
                    ?: state.reserve.first()
                used.copy(
                    questions = state.questions.toMutableList().also { it[state.index] = replacement },
                    reserve = state.reserve - replacement,
                ).clearQuestionAids()
            }
        }
    }

    /** Simulated audience: the harder the question, the more the votes are spread out. */
    internal fun audienceVotes(question: Question, hidden: Set<Int>): List<Int> {
        val votes = MutableList(question.options.size) { 0 }
        val wrong = question.visibleWrongOptions(hidden)
        val (min, max) = when (question.difficulty) {
            1 -> 55 to 85
            2 -> 40 to 70
            else -> 25 to 55
        }
        // With fewer options left, the crowd agrees more often.
        val bonus = (Question.OPTION_COUNT - 1 - wrong.size) * 10
        val correctShare = if (wrong.isEmpty()) 100 else (random.nextInt(min, max + 1) + bonus).coerceAtMost(95)
        votes[question.correctIndex] = correctShare

        if (wrong.isNotEmpty()) {
            val remaining = 100 - correctShare
            val weights = wrong.map { random.nextInt(1, 101) }
            val shares = weights.map { it * remaining / weights.sum() }.toMutableList()
            shares[0] += remaining - shares.sum()
            wrong.forEachIndexed { i, option -> votes[option] = shares[i] }
        }
        return votes
    }

    /** Simulated friend: usually right on easy questions, often unsure on hard ones. */
    internal fun friendAdvice(question: Question, hidden: Set<Int>): FriendAdvice {
        val accuracy = when (question.difficulty) {
            1 -> 0.9
            2 -> 0.75
            else -> 0.55
        }
        val wrong = question.visibleWrongOptions(hidden)
        val knows = wrong.isEmpty() || random.nextDouble() < accuracy
        return if (knows) {
            FriendAdvice(question.correctIndex, random.nextInt(65, 100))
        } else {
            FriendAdvice(wrong.random(random), random.nextInt(30, 70))
        }
    }

    private fun Question.visibleWrongOptions(hidden: Set<Int>): List<Int> =
        options.indices.filter { it != correctIndex && it !in hidden }

    private fun GameState.clearQuestionAids(): GameState =
        copy(selectedOption = null, hiddenOptions = emptySet(), audienceVotes = null, friendAdvice = null)
}
