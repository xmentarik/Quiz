package com.xmentarik.quiz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.xmentarik.quiz.ui.QuizApp
import com.xmentarik.quiz.ui.QuizViewModel
import com.xmentarik.quiz.ui.theme.QuizTheme

class MainActivity : ComponentActivity() {
    private val viewModel: QuizViewModel by viewModels { QuizViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // The whole UI is Arabic, so keep it right-to-left whatever the device language.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                QuizTheme {
                    QuizApp(viewModel)
                }
            }
        }
    }
}
