package com.monitorung.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class GamificationTest {
    @Test fun levelBoundariesCarryOverXp() {
        assertEquals(Gamification.LevelState(1, 0, 100), Gamification.levelState(0))
        assertEquals(Gamification.LevelState(1, 99, 1), Gamification.levelState(99))
        assertEquals(Gamification.LevelState(2, 0, 120), Gamification.levelState(100))
        assertEquals(Gamification.LevelState(2, 119, 1), Gamification.levelState(219))
        assertEquals(Gamification.LevelState(3, 0, 140), Gamification.levelState(220))
    }

    @Test fun xpIsCappedAndNeverNegative() {
        assertEquals(0, Gamification.xpFromScreenMinutes(-5))
        assertEquals(60, Gamification.xpFromScreenMinutes(60))
        assertEquals(120, Gamification.xpFromScreenMinutes(999))
    }

    @Test fun streakUsesWitaDayAndIgnoresOrder() {
        val today = LocalDate.now(Gamification.WITA)
        val day = { offset: Long -> today.minusDays(offset).toString() }
        assertEquals(0, Gamification.streakDays(emptyList()))
        assertEquals(3, Gamification.streakDays(listOf(day(2), day(0), day(1))))
        assertEquals(0, Gamification.streakDays(listOf(day(1), day(2))))
        assertEquals(0, Gamification.streakDays(listOf(today.plusDays(1).toString())))
    }
}
