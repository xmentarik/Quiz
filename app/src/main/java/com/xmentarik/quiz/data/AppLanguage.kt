package com.xmentarik.quiz.data

import java.util.Locale

enum class AppLanguage(
    /** BCP 47 tag; Arabic keeps Western digits (1, 2, 3), as used across the Maghreb. */
    private val tag: String,
    val isRtl: Boolean,
    val questionsAsset: String,
    val optionLabels: List<String>,
    val topics: List<String>,
) {
    ARABIC(
        tag = "ar-u-nu-latn",
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
        isRtl = false,
        questionsAsset = "questions_fr.json",
        optionLabels = listOf("A", "B", "C", "D"),
        topics = listOf(
            "Culture générale", "Sciences", "Histoire", "Géographie", "Sport", "Religion et civilisation islamique",
            "Technologie et programmation", "Littérature et langue", "Cinéma et séries", "Le Maghreb",
        ),
    );

    val locale: Locale get() = Locale.forLanguageTag(tag)
    val code: String get() = locale.language

    companion object {
        fun fromCode(code: String?): AppLanguage? = entries.firstOrNull { it.code == code }

        /** French devices start in French; everyone else starts in Arabic. */
        fun defaultFor(systemLanguage: String): AppLanguage = if (systemLanguage == "fr") FRENCH else ARABIC
    }
}
