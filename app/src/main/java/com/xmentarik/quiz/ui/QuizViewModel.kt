package com.xmentarik.quiz.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xmentarik.quiz.QuizApplication
import com.xmentarik.quiz.audio.FeedbackSettings
import com.xmentarik.quiz.data.AppLanguage
import com.xmentarik.quiz.data.LoadedGame
import com.xmentarik.quiz.data.QuestionSet
import com.xmentarik.quiz.data.PlayerStats
import com.xmentarik.quiz.data.QuestionRepository
import com.xmentarik.quiz.data.QuestionSource
import com.xmentarik.quiz.data.SettingsStore
import com.xmentarik.quiz.data.StatsStore
import com.xmentarik.quiz.game.GameEngine
import com.xmentarik.quiz.game.GamePhase
import com.xmentarik.quiz.game.Lifeline
import com.xmentarik.quiz.game.MoneyLadder
import com.xmentarik.quiz.game.TimeLimits
import com.xmentarik.quiz.online.ChallengeCodes
import com.xmentarik.quiz.online.ChallengePayload
import com.xmentarik.quiz.online.ChallengeResult
import com.xmentarik.quiz.online.OnlineService
import com.xmentarik.quiz.online.PlayerNames
import com.xmentarik.quiz.online.QuestionReport
import com.xmentarik.quiz.online.ReportReason
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class QuizViewModel(
    private val repository: QuestionRepository,
    private val statsStore: StatsStore,
    private val settingsStore: SettingsStore,
    private val online: OnlineService,
    private val engine: GameEngine = GameEngine(),
) : ViewModel() {

    private val _state = MutableStateFlow(
        QuizUiState(
            aiAvailable = repository.aiAvailable,
            // The bundled bank is large enough; AI questions are opt-in to save the Gemini quota.
            useAi = false,
            online = OnlineUiState(
                available = online.isAvailable,
                playerName = settingsStore.playerName,
                myChallenges = settingsStore.myChallenges,
            ),
        ),
    )
    val state: StateFlow<QuizUiState> = _state.asStateFlow()
    val stats: StateFlow<PlayerStats> = statsStore.stats
    val feedbackSettings: StateFlow<FeedbackSettings> = settingsStore.feedback
    val language: StateFlow<AppLanguage> = settingsStore.language

    private var timerJob: Job? = null
    private var loadJob: Job? = null
    private var gameRecorded = false
    private var gameStartedAt = 0L
    /** Kept until the server accepts it, so a network failure can be retried from the results screen. */
    private var pendingChallengeResult: Pair<String, ChallengeResult>? = null

    fun setUseAi(enabled: Boolean) = _state.update { it.copy(useAi = enabled && it.aiAvailable) }

    fun setTopic(index: Int?) = _state.update { it.copy(topicIndex = index) }

    fun setSoundEnabled(enabled: Boolean) =
        settingsStore.updateFeedback(feedbackSettings.value.copy(soundEnabled = enabled))

    fun setMusicEnabled(enabled: Boolean) =
        settingsStore.updateFeedback(feedbackSettings.value.copy(musicEnabled = enabled))

    fun setVibrationEnabled(enabled: Boolean) =
        settingsStore.updateFeedback(feedbackSettings.value.copy(vibrationEnabled = enabled))

    /** Only offered on the home screen; the activity recreates itself in the new language. */
    fun setLanguage(language: AppLanguage) {
        if (_state.value.screen == Screen.HOME && !_state.value.loading) settingsStore.updateLanguage(language)
    }

    fun startGame() {
        if (_state.value.loading) return
        stopTimer()
        _state.update { it.copy(loading = true) }
        loadJob = viewModelScope.launch {
            try {
                val loaded = loadQuestions()
                beginGame(loaded.questions, challenge = null, aiFallback = loaded.source == QuestionSource.LOCAL_AFTER_AI_FAILURE)
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    private suspend fun loadQuestions(): LoadedGame {
        val current = _state.value
        val topic = current.topicIndex?.takeIf { it in language.value.topics.indices }
        return repository.loadGame(current.useAi, topic, language.value, settingsStore.recentQuestionIds.toSet())
    }

    private fun beginGame(questions: QuestionSet, challenge: String?, aiFallback: Boolean = false) {
        // AI questions get fresh ids each time, so only bundled ones are worth remembering.
        settingsStore.rememberQuestions(questions.main.map { it.id }.filter { !it.startsWith("ai-") })
        gameRecorded = false
        gameStartedAt = System.nanoTime()
        _state.update {
            it.copy(
                screen = Screen.GAME,
                game = engine.start(questions),
                pendingOption = null,
                lockedOption = null,
                dialog = null,
                confirmWalkAway = false,
                newRecord = false,
                aiFallbackNotice = aiFallback,
                activeChallenge = challenge,
                reportOpen = false,
                reportStatus = null,
            )
        }
        startTimer()
    }

    // ---------------------------------------------------------------- online: navigation and nickname

    fun openLeaderboard() = openOnline(Screen.LEADERBOARD)

    fun openChallenges() = openOnline(Screen.CHALLENGES)

    /** Online screens need a nickname first; without Firebase they open and explain the setup. */
    private fun openOnline(screen: Screen) {
        val current = _state.value.online
        when {
            !current.available -> updateOnline(screen = screen) { it.copy(error = OnlineError.NOT_CONFIGURED) }
            current.playerName == null -> updateOnline { it.copy(namePromptFor = screen) }
            else -> {
                updateOnline(screen = screen) { it.copy(error = null) }
                if (screen == Screen.LEADERBOARD) refreshLeaderboard()
            }
        }
    }

    fun submitPlayerName(raw: String): Boolean {
        val name = PlayerNames.sanitize(raw) ?: return false
        settingsStore.updatePlayerName(name)
        val destination = _state.value.online.namePromptFor
        updateOnline { it.copy(playerName = name, namePromptFor = null) }
        destination?.let(::openOnline)
        return true
    }

    fun dismissNamePrompt() = updateOnline { it.copy(namePromptFor = null) }

    fun setChallengeCodeInput(input: String) = updateOnline { it.copy(codeInput = input.take(12), error = null) }

    fun backFromOnline() {
        if (_state.value.screen == Screen.CHALLENGE_RESULTS) {
            updateOnline(screen = Screen.CHALLENGES) { it.copy(error = null) }
        } else {
            goHome()
        }
    }

    // ---------------------------------------------------------------- online: leaderboard

    fun refreshLeaderboard() = onlineCall {
        val name = it.playerName ?: return@onlineCall
        val stats = statsStore.stats.value
        online.submitStats(name, stats.bestPrize, stats.gamesPlayed, stats.millionWins)
        val top = online.topPlayers()
        val rank = online.rankOf(stats.bestPrize)
        val id = online.playerId()
        updateOnline { state -> state.copy(leaderboard = top, myRank = rank, playerId = id) }
    }

    // ---------------------------------------------------------------- online: friends challenges

    /** Builds a game with the current settings, publishes it and starts playing it. */
    fun createChallenge() = onlineCall { current ->
        val name = current.playerName ?: return@onlineCall
        val loaded = loadQuestions()
        val code = online.createChallenge(name, ChallengePayload.of(loaded.questions, language.value.code))
        updateOnline { it.copy(myChallenges = settingsStore.rememberChallenge(code)) }
        beginGame(loaded.questions, challenge = code)
    }

    fun joinChallenge() {
        val code = ChallengeCodes.normalize(_state.value.online.codeInput)
        if (code == null) {
            updateOnline { it.copy(error = OnlineError.BAD_CODE) }
            return
        }
        onlineCall {
            val challenge = online.loadChallenge(code)
            when {
                challenge == null -> updateOnline { it.copy(error = OnlineError.CHALLENGE_NOT_FOUND) }
                online.hasPlayed(code) -> {
                    updateOnline { it.copy(myChallenges = settingsStore.rememberChallenge(code)) }
                    showChallengeResults(code, notice = OnlineError.ALREADY_PLAYED)
                }
                else -> {
                    updateOnline { it.copy(myChallenges = settingsStore.rememberChallenge(code), codeInput = "") }
                    beginGame(challenge.payload.toQuestionSet(), challenge = code)
                }
            }
        }
    }

    fun openChallengeResults(code: String) = onlineCall { showChallengeResults(code) }

    private suspend fun showChallengeResults(code: String, notice: OnlineError? = null) {
        pendingChallengeResult?.takeIf { it.first == code }?.let { (pendingCode, result) ->
            online.submitChallengeResult(pendingCode, result)
            pendingChallengeResult = null
        }
        val challenge = online.loadChallenge(code)
        val results = online.challengeResults(code)
        val id = online.playerId()
        updateOnline(screen = Screen.CHALLENGE_RESULTS) {
            it.copy(
                viewedChallenge = code,
                viewedCreator = challenge?.creatorName,
                challengeResults = results,
                playerId = id,
                error = notice,
            )
        }
    }

    /** Runs a network action with a busy flag; any failure becomes a NETWORK error on screen. */
    private fun onlineCall(block: suspend (OnlineUiState) -> Unit) {
        val current = _state.value.online
        if (current.busy || !current.available) return
        updateOnline { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block(_state.value.online)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateOnline { it.copy(error = OnlineError.NETWORK) }
            } finally {
                updateOnline { it.copy(busy = false) }
            }
        }
    }

    private fun updateOnline(screen: Screen? = null, transform: (OnlineUiState) -> OnlineUiState) =
        _state.update { it.copy(screen = screen ?: it.screen, online = transform(it.online)) }

    /** Fire-and-forget uploads at the end of a game; failures are retried later. */
    private fun publishOnline(prize: Int, correctCount: Int) {
        val current = _state.value
        val name = current.online.playerName ?: return
        if (!current.online.available) return
        val stats = statsStore.stats.value
        current.activeChallenge?.let { code ->
            val seconds = ((System.nanoTime() - gameStartedAt) / 1_000_000_000L).toInt()
            pendingChallengeResult = code to ChallengeResult("", name, prize, correctCount, seconds)
        }
        viewModelScope.launch {
            runCatching { online.submitStats(name, stats.bestPrize, stats.gamesPlayed, stats.millionWins) }
            pendingChallengeResult?.let { (code, result) ->
                runCatching { online.submitChallengeResult(code, result) }.onSuccess { pendingChallengeResult = null }
            }
        }
    }

    fun selectOption(option: Int) {
        val current = _state.value
        val game = current.game ?: return
        if (game.phase != GamePhase.PLAYING || current.lockedOption != null || option in game.hiddenOptions) return
        _state.update { it.copy(pendingOption = option) }
    }

    fun confirmAnswer() {
        val current = _state.value
        val option = current.pendingOption ?: return
        if (current.lockedOption != null) return
        stopTimer()
        _state.update { it.copy(lockedOption = option, pendingOption = null) }
        viewModelScope.launch {
            delay(REVEAL_DELAY_MS)
            _state.update { it.copy(game = it.game?.let { game -> engine.answer(game, option) }, lockedOption = null) }
            recordIfOver()
        }
    }

    fun useLifeline(lifeline: Lifeline) {
        val current = _state.value
        val game = current.game ?: return
        if (current.lockedOption != null || !game.canUse(lifeline)) return
        val updated = engine.use(game, lifeline)
        _state.update {
            it.copy(
                game = updated,
                pendingOption = it.pendingOption?.takeIf { option ->
                    lifeline != Lifeline.SWITCH_QUESTION && option !in updated.hiddenOptions
                },
                dialog = when (lifeline) {
                    Lifeline.ASK_AUDIENCE -> LifelineDialog.AUDIENCE
                    Lifeline.PHONE_A_FRIEND -> LifelineDialog.FRIEND
                    else -> null
                },
            )
        }
        if (lifeline == Lifeline.SWITCH_QUESTION) startTimer()
    }

    fun dismissDialog() = _state.update { it.copy(dialog = null) }

    fun requestWalkAway() {
        val current = _state.value
        if (current.game?.phase != GamePhase.PLAYING || current.lockedOption != null) return
        _state.update { it.copy(confirmWalkAway = true) }
    }

    fun cancelWalkAway() = _state.update { it.copy(confirmWalkAway = false) }

    fun confirmWalkAway() {
        stopTimer()
        _state.update {
            it.copy(game = it.game?.let(engine::walkAway), confirmWalkAway = false, pendingOption = null)
        }
        recordIfOver()
    }

    fun nextQuestion() {
        _state.update { it.copy(game = it.game?.let(engine::nextQuestion), reportOpen = false, reportStatus = null) }
        startTimer()
    }

    fun showResult() = _state.update { it.copy(screen = Screen.RESULT, reportOpen = false) }

    // ---------------------------------------------------------------- question reports

    fun openReport() = _state.update { it.copy(reportOpen = true, reportStatus = null) }

    fun dismissReport() = _state.update { it.copy(reportOpen = false, reportStatus = null) }

    /** Sends the report to Firestore; the dialog stays open with an error if it fails. */
    fun submitReport(reason: ReportReason, comment: String) {
        val current = _state.value
        val game = current.game ?: return
        if (current.reportStatus == ReportStatus.SENDING || !current.online.available) return
        val question = game.currentQuestion
        val report = QuestionReport(
            questionId = question.id,
            language = language.value.code,
            text = question.text,
            options = question.options,
            correctIndex = question.correctIndex,
            selectedOption = game.selectedOption,
            reason = reason,
            comment = QuestionReport.cleanComment(comment),
        )
        _state.update { it.copy(reportStatus = ReportStatus.SENDING) }
        viewModelScope.launch {
            try {
                online.submitReport(report)
                markReported(question.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(reportStatus = ReportStatus.FAILED) }
            }
        }
    }

    /** Called once the report went out another way (the email app). */
    fun markReported(questionId: String) = _state.update {
        it.copy(reportOpen = false, reportStatus = null, reportedQuestions = it.reportedQuestions + questionId)
    }

    fun dismissAiNotice() = _state.update { it.copy(aiFallbackNotice = false) }

    fun goHome() {
        stopTimer()
        loadJob?.cancel()
        _state.update {
            it.copy(screen = Screen.HOME, game = null, loading = false, dialog = null, activeChallenge = null)
        }
    }

    private fun startTimer() {
        stopTimer()
        val game = _state.value.game ?: return
        val limit = TimeLimits.secondsFor(game.index)
        _state.update { it.copy(secondsLeft = limit) }
        if (limit == null) return
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                val current = _state.value
                // The clock pauses while a lifeline result or the walk-away prompt is open.
                if (current.dialog != null || current.confirmWalkAway) continue
                val left = (current.secondsLeft ?: return@launch) - 1
                if (left <= 0) {
                    _state.update {
                        it.copy(secondsLeft = 0, pendingOption = null, game = it.game?.let(engine::timeUp))
                    }
                    recordIfOver()
                    return@launch
                }
                _state.update { it.copy(secondsLeft = left) }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun recordIfOver() {
        val game = _state.value.game ?: return
        if (!game.phase.isOver || gameRecorded) return
        gameRecorded = true
        val record = statsStore.recordGame(game.prize, wonMillion = game.prize == MoneyLadder.top)
        _state.update { it.copy(newRecord = record) }
        publishOnline(game.prize, game.correctCount)
    }

    companion object {
        const val REVEAL_DELAY_MS = 1_500L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as QuizApplication
                QuizViewModel(app.questionRepository, app.statsStore, app.settingsStore, app.onlineService)
            }
        }
    }
}
