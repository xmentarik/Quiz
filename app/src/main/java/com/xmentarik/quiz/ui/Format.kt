package com.xmentarik.quiz.ui

import java.text.NumberFormat
import java.util.Locale

val OptionLabels = listOf("أ", "ب", "ج", "د")

fun formatPrize(amount: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(amount)
