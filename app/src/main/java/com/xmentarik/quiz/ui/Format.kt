package com.xmentarik.quiz.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.xmentarik.quiz.data.AppLanguage
import java.text.NumberFormat
import java.util.Locale

/** Language the activity was created with; the activity is recreated when it changes. */
val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.ARABIC }

/** Uses the app language set as default locale: "1,000,000" in Arabic, "1 000 000" in French. */
fun formatPrize(amount: Int): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(amount)
