package com.xmentarik.quiz

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.xmentarik.quiz.audio.AudioDirector
import com.xmentarik.quiz.audio.GameAudio
import com.xmentarik.quiz.audio.GameHaptics
import com.xmentarik.quiz.data.AppLanguage
import com.xmentarik.quiz.ui.LocalAppLanguage
import com.xmentarik.quiz.ui.QuizApp
import com.xmentarik.quiz.ui.QuizUiState
import com.xmentarik.quiz.ui.QuizViewModel
import com.xmentarik.quiz.ui.theme.QuizTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: QuizViewModel by viewModels { QuizViewModel.Factory }
    private lateinit var audio: GameAudio
    private lateinit var haptics: GameHaptics
    private lateinit var appliedLanguage: AppLanguage

    override fun attachBaseContext(newBase: Context) {
        appliedLanguage = (newBase.applicationContext as QuizApplication).settingsStore.language.value
        super.attachBaseContext(newBase.withLanguage(appliedLanguage))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        audio = GameAudio(this)
        haptics = GameHaptics(this)
        playFeedbackWhileVisible()
        recreateOnLanguageChange()
        setContent {
            val direction = if (appliedLanguage.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(
                LocalLayoutDirection provides direction,
                LocalAppLanguage provides appliedLanguage,
            ) {
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

    /** Turns UI state changes into sounds, music and vibrations; everything stops in the background. */
    private fun playFeedbackWhileVisible() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                audio.resume()
                try {
                    var previous: QuizUiState? = null
                    combine(viewModel.state, viewModel.feedbackSettings, ::Pair).collect { (state, settings) ->
                        audio.applySettings(settings)
                        haptics.enabled = settings.vibrationEnabled
                        AudioDirector.cues(previous, state).forEach { cue ->
                            audio.play(cue)
                            haptics.play(cue)
                        }
                        audio.setMusic(AudioDirector.music(state))
                        previous = state
                    }
                } finally {
                    audio.pause()
                    haptics.cancel()
                }
            }
        }
    }

    private fun recreateOnLanguageChange() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.language.collect { if (it != appliedLanguage) recreate() }
            }
        }
    }
}

private fun Context.withLanguage(language: AppLanguage): Context {
    val locale = language.locale
    Locale.setDefault(locale)
    val config = Configuration(resources.configuration).apply {
        setLocale(locale)
        setLayoutDirection(locale)
    }
    return createConfigurationContext(config)
}
