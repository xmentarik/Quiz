package com.xmentarik.quiz.data

import kotlin.random.Random

/**
 * Picks the questions of a game: 5 easy, 5 medium and 5 hard, in that order,
 * plus one spare per level for the "switch question" lifeline.
 */
object QuestionSetBuilder {
    const val PER_LEVEL = 5
    const val RESERVE_PER_LEVEL = 1

    /**
     * Uses [pool] first and tops up each level from [fallback] when [pool] is short
     * (for example when the AI returned too few valid questions).
     */
    fun build(
        pool: List<Question>,
        random: Random = Random.Default,
        fallback: List<Question> = emptyList(),
    ): QuestionSet {
        val main = mutableListOf<Question>()
        val reserve = mutableListOf<Question>()
        val seenTexts = mutableSetOf<String>()

        for (level in Question.MIN_DIFFICULTY..Question.MAX_DIFFICULTY) {
            val candidates = (pool.levelShuffled(level, random) + fallback.levelShuffled(level, random))
                .filter { seenTexts.add(it.text.trim()) }
            check(candidates.size >= PER_LEVEL) {
                "Not enough questions for difficulty $level: ${candidates.size} < $PER_LEVEL"
            }
            main += candidates.take(PER_LEVEL).map { it.withShuffledOptions(random) }
            reserve += candidates.drop(PER_LEVEL).take(RESERVE_PER_LEVEL).map { it.withShuffledOptions(random) }
        }
        return QuestionSet(main, reserve)
    }

    private fun List<Question>.levelShuffled(level: Int, random: Random) =
        filter { it.isValid && it.difficulty == level }.shuffled(random)
}
