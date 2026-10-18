package com.xmentarik.quiz.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLanguageTest {
    @Test
    fun codesRoundTrip() {
        AppLanguage.entries.forEach { assertEquals(it, AppLanguage.fromCode(it.code)) }
        assertEquals("ar", AppLanguage.ARABIC.code)
        assertNull(AppLanguage.fromCode("de"))
    }

    @Test
    fun devicesStartInTheirLanguageOrArabic() {
        assertEquals(AppLanguage.FRENCH, AppLanguage.defaultFor("fr"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.defaultFor("en"))
        assertEquals(AppLanguage.ARABIC, AppLanguage.defaultFor("ar"))
        assertEquals(AppLanguage.ARABIC, AppLanguage.defaultFor("de"))
    }

    @Test
    fun onlyArabicIsRightToLeft() {
        assertEquals(listOf(AppLanguage.ARABIC), AppLanguage.entries.filter { it.isRtl })
        assertEquals(AppLanguage.entries.size, AppLanguage.entries.map { it.nativeName }.toSet().size)
    }

    @Test
    fun everyLanguageHasTheSameTopicsAndFourLabels() {
        AppLanguage.entries.forEach {
            assertEquals(AppLanguage.ARABIC.topics.size, it.topics.size)
            assertEquals(4, it.optionLabels.size)
        }
    }

    @Test
    fun arabicUsesWesternDigits() {
        assertEquals("1,000,000", java.text.NumberFormat.getIntegerInstance(AppLanguage.ARABIC.locale).format(1_000_000))
    }
}
