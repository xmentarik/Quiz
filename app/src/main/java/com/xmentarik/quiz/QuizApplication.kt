package com.xmentarik.quiz

import android.app.Application
import com.xmentarik.quiz.data.GeminiQuestionGenerator
import com.xmentarik.quiz.data.QuestionRepository
import com.xmentarik.quiz.data.SettingsStore
import com.xmentarik.quiz.data.StatsStore
import com.xmentarik.quiz.online.FirebaseOnlineService
import com.xmentarik.quiz.online.OnlineService

class QuizApplication : Application() {
    val questionRepository: QuestionRepository by lazy {
        QuestionRepository(this, GeminiQuestionGenerator(BuildConfig.GEMINI_API_KEY, BuildConfig.GEMINI_MODEL))
    }
    val statsStore: StatsStore by lazy { StatsStore(this) }
    val settingsStore: SettingsStore by lazy { SettingsStore(this) }
    val onlineService: OnlineService by lazy { FirebaseOnlineService(this) }
}
