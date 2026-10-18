package com.xmentarik.quiz.data

import java.util.Locale

enum class AppLanguage(
    /** BCP 47 tag; Arabic keeps Western digits (1, 2, 3), as used across the Maghreb. */
    private val tag: String,
    /** Name written in the language itself, for the language picker. */
    val nativeName: String,
    val isRtl: Boolean,
    val questionsAsset: String,
    val optionLabels: List<String>,
    val topics: List<String>,
) {
    ARABIC(
        tag = "ar-u-nu-latn",
        nativeName = "العربية",
        isRtl = true,
        questionsAsset = "questions_ar.json",
        optionLabels = listOf("أ", "ب", "ج", "د"),
        topics = listOf(
            "ثقافة عامة", "علوم", "تاريخ", "جغرافيا", "رياضة", "دين وحضارة إسلامية",
            "تقنية وبرمجة", "أدب ولغة عربية", "أفلام ومسلسلات", "المغرب العربي",
        ),
    ),
    FRENCH(
        tag = "fr",
        nativeName = "Français",
        isRtl = false,
        questionsAsset = "questions_fr.json",
        optionLabels = listOf("A", "B", "C", "D"),
        topics = listOf(
            "Culture générale", "Sciences", "Histoire", "Géographie", "Sport", "Religion et civilisation islamique",
            "Technologie et programmation", "Littérature et langue", "Cinéma et séries", "Le Maghreb",
        ),
    ),
    ENGLISH(
        tag = "en",
        nativeName = "English",
        isRtl = false,
        questionsAsset = "questions_en.json",
        optionLabels = listOf("A", "B", "C", "D"),
        topics = listOf(
            "General knowledge", "Science", "History", "Geography", "Sport", "Religion and Islamic civilisation",
            "Technology and programming", "Literature and language", "Film and TV", "The Maghreb",
        ),
    );

    val locale: Locale get() = Locale.forLanguageTag(tag)
    val code: String get() = locale.language

    companion object {
        fun fromCode(code: String?): AppLanguage? = entries.firstOrNull { it.code == code }

        /** French and English devices start in their language; everyone else starts in Arabic. */
        fun defaultFor(systemLanguage: String): AppLanguage = fromCode(systemLanguage) ?: ARABIC
    }
}
