package com.monitorung.app.domain.model

import java.time.LocalTime

/**
 * Isi form "Ajukan Jadwal" sebelum dikirim ke `POST /api/schedule-requests`.
 *
 * Sebelumnya form meneruskan sepuluh argumen posisional ke ViewModel, sehingga
 * mudah tertukar dan tidak ada satu tempat pun yang memeriksa kontrak server.
 * Objek ini memusatkan aturan yang sama dengan
 * `server/app/schemas/schedule_request.py:ScheduleRequestCreate`:
 *
 * - `day_of_week` wajib 1..7 (ISO). Tab Jadwal memakai 0 untuk "Semua", dan
 *   nilai itu dulu ikut terkirim sehingga server membalas HTTP 422.
 * - `start_time` / `end_time` wajib `HH:MM` 24 jam.
 * - `description` tidak boleh kosong (kolom `Text` non-nullable di server).
 */
data class ScheduleRequestDraft(
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val room: String? = null,
    val subjectId: String? = null,
    val lecturerId: String? = null,
    val subjectNameRaw: String? = null,
    val lecturerNameRaw: String? = null,
    val campusId: String? = null,
    val description: String = "Request jadwal baru",
) {

    /** Null bila valid; berisi pesan siap tampil bila tidak. */
    fun validationError(): String? = when {
        dayOfWeek !in ScheduleRequestDefaults.VALID_DAYS ->
            "Pilih hari kuliah terlebih dahulu."
        !ScheduleRequestDefaults.isValidTime(startTime) ->
            "Jam mulai harus dalam format HH:MM."
        !ScheduleRequestDefaults.isValidTime(endTime) ->
            "Jam selesai harus dalam format HH:MM."
        !ScheduleRequestDefaults.isValidTimeRange(startTime, endTime) ->
            "Jam selesai harus lebih lambat dari jam mulai."
        subjectId.isNullOrBlank() && subjectNameRaw.isNullOrBlank() ->
            "Mata kuliah wajib diisi."
        description.isBlank() ->
            "Deskripsi request tidak boleh kosong."
        else -> null
    }

    fun isValid(): Boolean = validationError() == null
}

/**
 * Aturan murni untuk form request jadwal. Dipisah dari Compose dan Retrofit
 * supaya bisa diuji tanpa perangkat maupun server.
 */
object ScheduleRequestDefaults {

    /** ISO-8601: 1 = Senin ... 7 = Minggu, sama dengan `DayOfWeek.value`. */
    val VALID_DAYS: IntRange = 1..7

    private val TIME_PATTERN = Regex("""^\d{2}:\d{2}$""")

    /**
     * Filter hari di tab Jadwal memakai 0 untuk "Semua", sedangkan server hanya
     * menerima 1..7. Hari yang tidak valid diganti dengan [today] supaya form
     * selalu terkirim dengan hari konkret yang terlihat terpilih di UI.
     */
    fun normalizeDayOfWeek(preferred: Int, today: Int): Int = when {
        preferred in VALID_DAYS -> preferred
        today in VALID_DAYS -> today
        else -> 1
    }

    /** Format `HH:MM` 24 jam, sama dengan regex di schema server. */
    fun isValidTime(value: String): Boolean {
        if (!TIME_PATTERN.matches(value)) return false
        val (hour, minute) = value.split(":").map { it.toInt() }
        return hour in 0..23 && minute in 0..59
    }

    /** Server tidak memeriksa urutan jam, tapi jadwal dengan akhir <= mulai tidak masuk akal. */
    fun isValidTimeRange(startTime: String, endTime: String): Boolean {
        if (!isValidTime(startTime) || !isValidTime(endTime)) return false
        return LocalTime.parse(endTime).isAfter(LocalTime.parse(startTime))
    }

    /**
     * Jam mulai default: pembulatan ke atas ke kelipatan 30 menit berikutnya,
     * selalu `HH:MM` dua digit (server menolak "8:30").
     */
    fun defaultStartTime(now: LocalTime): String {
        val rounded = now.withSecond(0).withNano(0)
        val totalMinutes = rounded.hour * 60 + rounded.minute
        val nextSlot = ((totalMinutes / 30) + 1) * 30
        val normalized = nextSlot % (24 * 60)
        return formatTime(normalized / 60, normalized % 60)
    }

    /** Jam selesai default: dua jam setelah jam mulai, dibungkus dalam 24 jam. */
    fun defaultEndTime(startTime: String): String {
        if (!isValidTime(startTime)) return "10:00"
        val (hour, minute) = startTime.split(":").map { it.toInt() }
        val totalMinutes = (hour * 60 + minute + 120) % (24 * 60)
        return formatTime(totalMinutes / 60, totalMinutes % 60)
    }

    private fun formatTime(hour: Int, minute: Int): String =
        "%02d:%02d".format(hour, minute)
}
