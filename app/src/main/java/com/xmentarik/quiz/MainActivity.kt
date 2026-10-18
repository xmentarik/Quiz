package com.xmentarik.quiz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.xmentarik.quiz.audio.AudioDirector
import com.xmentarik.quiz.audio.GameAudio
import com.xmentarik.quiz.ui.QuizApp
import com.xmentarik.quiz.ui.QuizUiState
import com.xmentarik.quiz.ui.QuizViewModel
import com.xmentarik.quiz.ui.theme.QuizTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: QuizViewModel by viewModels { QuizViewModel.Factory }
    private lateinit var audio: GameAudio

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        audio = GameAudio(this)
        playAudioWhileVisible()
        setContent {
            // The whole UI is Arabic, so keep it right-to-left whatever the device language.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                QuizTheme {
                    QuizApp(viewModel)
                }
            }
        }
    }

    override fun onDestroy() {
        audio.release()
        super.onDestroy()
    }

    /** Turns UI state changes into sounds and music; everything stops while the app is in the background. */
    private fun playAudioWhileVisible() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                audio.resume()
                try {
                    var previous: QuizUiState? = null
                    combine(viewModel.state, viewModel.audioSettings, ::Pair).collect { (state, settings) ->
                        audio.applySettings(settings)
                        AudioDirector.cues(previous, state).forEach(audio::play)
                        audio.setMusic(AudioDirector.music(state))
                        previous = state
                    }
                } finally {
                    audio.pause()
                }
            }
        }
    }
}
