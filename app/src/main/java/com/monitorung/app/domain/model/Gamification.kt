package com.monitorung.app.domain.model

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Satu-satunya sumber aturan gamifikasi (level, XP, streak) untuk Beranda dan
 * Histori. Sebelumnya tiap layar punya rumus sendiri sehingga angka level yang
 * sama bisa berbeda. Semua perhitungan hari memakai WITA, bukan timezone perangkat.
 */
object Gamification {
    val WITA: ZoneId = ZoneId.of("Asia/Makassar")

    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** XP harian dari screen time: 1 XP per menit, maksimal 120 XP. */
    fun xpFromScreenMinutes(minutes: Int): Int = minutes.coerceIn(0, 120)

    /** Total XP yang dibutuhkan untuk menuntaskan satu level. */
    fun xpForLevel(level: Int): Int = 100 + (level - 1) * 20

    data class LevelState(
        val level: Int,
        /** XP yang sudah terkumpul di dalam level berjalan. */
        val currentXp: Int,
        /** Sisa XP menuju level berikutnya. */
        val xpToNextLevel: Int,
    )

    fun levelState(totalXp: Int): LevelState {
        var level = 1
        var remaining = totalXp.coerceAtLeast(0)
        while (remaining >= xpForLevel(level)) {
            remaining -= xpForLevel(level)
            level++
        }
        return LevelState(level, remaining, xpForLevel(level) - remaining)
    }

    /**
     * Hitung streak dari tanggal berurutan (yyyy-MM-dd) mundur dari hari ini
     * menurut WITA. Urutan tidak penting; status sinkronisasi tidak dihitung —
     * satu baris pending tidak boleh memutus streak.
     */
    fun streakDays(dates: Collection<String>): Int {
        if (dates.isEmpty()) return 0
        val available = dates.toSet()
        var day = LocalDate.now(WITA)
        var streak = 0
        while (available.contains(day.format(DATE_FORMAT))) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }
}
