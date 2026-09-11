package com.example.kanjipractice.domain.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StudySettingsTest {

    @Test
    fun theAllowanceIsWhatIsLeftOfTheDailyLimit() {
        assertEquals(15, StudySettings(dailyNewLimit = 20, introducedToday = 5).remainingNewAllowance)
    }

    @Test
    fun theAllowanceIsNeverNegative() {
        // A user who lowers the limit mid-day must not see "-7 new cards".
        val settings = StudySettings(dailyNewLimit = 10, introducedToday = 17)
        assertEquals(0, settings.remainingNewAllowance)
    }

    @Test
    fun aLimitOfZeroMeansNoNewCardsAtAll() {
        assertEquals(0, StudySettings(dailyNewLimit = 0).remainingNewAllowance)
    }

    @Test
    fun theDefaultIsASensibleDayOfNewCards() {
        val settings = StudySettings()
        assertTrue(settings.dailyNewLimit in 5..50)
        assertEquals(settings.dailyNewLimit, settings.remainingNewAllowance)
    }
}
