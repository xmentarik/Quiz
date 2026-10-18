package com.xmentarik.quiz

import android.app.Application
import com.xmentarik.quiz.ads.AdsManager
import com.xmentarik.quiz.data.GeminiQuestionGenerator
import com.xmentarik.quiz.data.QuestionRepository
import com.xmentarik.quiz.data.SettingsStore
import com.xmentarik.quiz.data.StatsStore
import com.xmentarik.quiz.online.FirebaseOnlineService
import com.xmentarik.quiz.online.OnlineService

class QuizApplication : Application() {
    /** Created at start-up so it can watch the app going to the background (app-open ads). */
    lateinit var ads: AdsManager
        private set

    override fun onCreate() {
        super.onCreate()
        ads = AdsManager(this)
    }

    val questionRepository: QuestionRepository by lazy {
        QuestionRepository(this, GeminiQuestionGenerator(BuildConfig.GEMINI_API_KEY, BuildConfig.GEMINI_MODEL))
    }
    val statsStore: StatsStore by lazy { StatsStore(this) }
    val settingsStore: SettingsStore by lazy { SettingsStore(this) }
    val onlineService: OnlineService by lazy { FirebaseOnlineService(this) }
}
