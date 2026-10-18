package com.xmentarik.quiz.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xmentarik.quiz.QuizApplication
import com.xmentarik.quiz.audio.AudioSettings
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
    private val engine: GameEngine = GameEngine(),
) : ViewModel() {

    private val _state = MutableStateFlow(
        QuizUiState(aiAvailable = repository.aiAvailable, useAi = repository.aiAvailable),
    )
    val state: StateFlow<QuizUiState> = _state.asStateFlow()
    val stats: StateFlow<PlayerStats> = statsStore.stats
    val audioSettings: StateFlow<AudioSettings> = settingsStore.audio

    private var timerJob: Job? = null
    private var loadJob: Job? = null
    private var gameRecorded = false

    fun setUseAi(enabled: Boolean) = _state.update { it.copy(useAi = enabled && it.aiAvailable) }

    fun setTopic(topic: String) = _state.update { it.copy(topic = topic) }

    fun setSoundEnabled(enabled: Boolean) =
        settingsStore.updateAudio(audioSettings.value.copy(soundEnabled = enabled))

    fun setMusicEnabled(enabled: Boolean) =
        settingsStore.updateAudio(audioSettings.value.copy(musicEnabled = enabled))

    fun startGame() {
        if (_state.value.loading) return
        stopTimer()
        _state.update { it.copy(loading = true) }
        loadJob = viewModelScope.launch {
            try {
                val current = _state.value
                val loaded = repository.loadGame(current.useAi, current.topic)
                gameRecorded = false
                _state.update {
                    it.copy(
                        screen = Screen.GAME,
                        game = engine.start(loaded.questions),
                        pendingOption = null,
                        lockedOption = null,
                        dialog = null,
                        confirmWalkAway = false,
                        newRecord = false,
                        aiFallbackNotice = loaded.source == QuestionSource.LOCAL_AFTER_AI_FAILURE,
                    )
                }
                startTimer()
            } finally {
                _state.update { it.copy(loading = false) }
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
        _state.update { it.copy(game = it.game?.let(engine::nextQuestion)) }
        startTimer()
    }

    fun showResult() = _state.update { it.copy(screen = Screen.RESULT) }

    fun dismissAiNotice() = _state.update { it.copy(aiFallbackNotice = false) }

    fun goHome() {
        stopTimer()
        loadJob?.cancel()
        _state.update { it.copy(screen = Screen.HOME, game = null, loading = false, dialog = null) }
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
    }

    companion object {
        const val REVEAL_DELAY_MS = 1_500L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as QuizApplication
                QuizViewModel(app.questionRepository, app.statsStore, app.settingsStore)
            }
        }
    }
}
