# Memory Android Monitor UNG

Terakhir dicocokkan: 2026-11-17. Berlaku untuk `/home/terio-op/monitor-ung-app`. Baca juga [memory proyek](../AGENTS.md), [backend](../server/AGENTS.md), [website](../website/AGENTS.md). Path Kotlin relatif terhadap `app/src/main/java/com/monitorung/app/` kecuali disebut lain.

## Tujuan dan stack

Aplikasi mahasiswa: login, izin, jadwal, monitoring penggunaan ponsel saat kuliah, ringkasan harian, histori, sinkronisasi, profil. Pengumpulan harus hemat baterai, transparan lewat izin/notifikasi, dan tahan koneksi terputus.

| Bagian | Konfigurasi |
| --- | --- |
| Identitas | `com.monitorung.app`; versionCode 34, versionName 0.32.1. Distribusi publik terakhir di website: 0.29.1 (belum diperbarui). |
| Toolchain | Kotlin/Compose compiler 2.4.21, AGP 9.5.1, KSP 2.3.13, Gradle 9.9.1, JVM 17. |
| Android | minSdk 26, targetSdk/compileSdk 37. ABI arm64-v8a, armeabi-v7a, x86_64; split + universal APK. |
| UI | Compose BOM 2026.09.00, Material 3, Navigation Compose, window size classes, biometric. |
| Arsitektur | Hilt 2.60.1, ViewModel, StateFlow/Flow, Coroutines; layer data/domain/presentation. |
| Lokal | Room 2.8.5, SQLCipher 4.20.0, DataStore, EncryptedSharedPreferences/Keystore. |
| Jaringan | Retrofit 3.0, OkHttp 5.5.1, Gson, Paho 1.2.5. |
| Background | WorkManager 2.12, foreground service, Play Services Location. |
| Push | Firebase Messaging 24.1.2 + FCMService; butuh konfigurasi Firebase nyata. |
| Test | JUnit 4.13.2 + Compose/instrumentation test existing. |

Versi dependency dipusatkan di `gradle/libs.versions.toml`; jangan duplikasi nomor versi di build script. Manifest fitur/platform ada di `app/src/main/AndroidManifest.xml`.

CI: `.github/workflows/ci.yml` — `:app:testDevDebugUnitTest` + `:app:lintDevDebug` dengan `-PmqttPassword=ci` (prod buildTypes melempar `GradleException` saat konfigurasi bila properti kosong, walau task yang jalan cuma `dev*`), lalu `:app:cyclonedxBom` (plugin `org.cyclonedx.bom`, reporting-only; Syft tidak bisa membaca APK) yang mengunggah `app/build/reports/cyclonedx/bom.json`. `app/google-services.json` gitignored, jadi CI menyalin `app/google-services.ci.json` (placeholder ter-track) — tanpa itu task `processDevDebugGoogleServices` gagal. `gradle.properties` gitignored: runner bersih tidak punya signing config, jadi `assembleRelease` tidak bisa ditandatangani di CI.

## Peta folder

| Path | Tanggung jawab |
| --- | --- |
| `MonitorUNGApplication.kt`, `di/AppModule.kt` | Startup, Hilt, worker factory, HTTP, Room terenkripsi, konfigurasi client. |
| `presentation/MainActivity.kt` | FragmentActivity untuk Compose + BiometricPrompt; memilih layar dari `AppAccessState`. |
| `presentation/navigation/Navigation.kt` | Auth/onboarding, empat tab, pager ponsel, rail tablet, overlay profil. |
| `presentation/viewmodel/` | State dan aksi layar; sebagian ViewModel jadwal/histori ada di `HomeViewModel.kt`. |
| `presentation/ui/screens/` | Splash/login, auth biometric, onboarding, home, schedule, history, settings, profile. |
| `presentation/ui/components/` | PaperCard, BubbleNavBar, PaperPill, SpringButton, skeleton, progress, haptic. |
| `presentation/ui/theme/` | Token visual, Typography, varian tema, adaptasi ukuran. |
| `domain/model/`, `domain/repository/Repositories.kt` | Model bisnis, `DailyUsageBreakdown`, perhitungan pemakaian per jadwal. |
| `data/repository/` | AuthRepositoryImpl, UsageRepositoryImpl, ScheduleRepositoryImpl, ScheduleRequestRepositoryImpl. |
| `data/local/` | MonitorUNGDatabase, Entities, Daos; status sinkronisasi. |
| `data/remote/api/`, `data/remote/dto/Dtos.kt` | Endpoint Retrofit dan serialisasi JSON. |
| `data/remote/TokenAuthenticator.kt` | Refresh token dan retry 401. |
| `data/snapshot/UsageSnapshotStore.kt` | Snapshot cumulative UsageStats untuk menghitung delta antarcollector. |
| `data/lock/`, `receiver/` | Counter unlock, BootReceiver, GeofenceReceiver. |
| `data/push/MqttPushClient.kt`, `FCMService.kt` | Publikasi MQTT, broadcast, push FCM. |
| `service/SmartMonitoringService.kt`, `SmartMonitoringManager.kt` | Foreground lifecycle, sesi kuliah, koleksi screen time/lokasi. |
| `service/BatteryBasedSotManager.kt` | Akumulasi screen-on/off ringan. |
| `service/DataSyncManager.kt` | Sinkronisasi background dengan batas waktu/baterai/jaringan. |
| `service/NotificationScheduler.kt`, `Workers.kt` | Reminder, mulai sesi, retry/sync, koleksi background, keep-alive. |
| `util/SecurePrefs.kt`, `AppConstants.kt`, `Utils.kt` | Preferensi aman, konstanta, utilitas. |

## Alur masuk dan navigasi

`MainActivity` mengamati `LoginViewModel.appAccessState`: `LOADING`, `NEEDS_LOGIN`, `LOCKED`, `UNLOCKED`. Splash menunggu state; auth navigation menangani login/onboarding; `LOCKED` menampilkan biometric gate; `UNLOCKED` membuka aplikasi utama.

Password hanya parameter sementara dari composable ke repository. Jangan simpan di state persisten, DataStore, SharedPreferences, atau log. Auto-login memakai refresh token. Jalur offline memulihkan pekerjaan dari jadwal Room tanpa menunggu fetch server.

Empat tab: Beranda, Jadwal, Histori, Profil. Ponsel memakai HorizontalPager + BubbleNavBar; tablet memakai NavigationRail/NavHost. Profil detail adalah overlay terpisah; Back menutup overlay dulu. Pertahankan state tab dan perilaku Back.

Alur awal mencakup TermsScreen dan PermissionScreen. Usage access, lokasi, dan notifikasi punya mekanisme izin Android berbeda; jangan samakan special access UsageStats dengan runtime permission. Perubahan izin harus mencakup manifest, onboarding, pemeriksaan runtime, dan penjelasan pengguna.

## Jadwal (perbaikan 2026-11-17)

**Kunci Room harus `users.id` dari auth, bukan `students.id` dari response server.** `schedules.student_id` di backend berisi `students.id`, sedangkan `/auth/login` mengembalikan `student_id` = `users.id`. Semua query Room memakai `users.id`.

- `ScheduleDto.toEntity(ownerId)` dipanggil dengan studentId pemilik dari auth. Jangan kembali memakai `dto.studentId`.
- `AuthRepositoryImpl.fetchProfile()`/`updateProfile()` memakai `dto.id` (users.id). Sebelumnya `dto.username`, yang me-rekey seluruh Room tiap auto-login.
- `getStudentId()` memakai `getCurrentUser().filterNotNull().first()` dengan `withTimeoutOrNull(5_000)`; `first()` polos mengembalikan null saat cold start sebelum DataStore terhidrasi.
- Tab Jadwal **default menampilkan semua hari** (`selectedDay = 0`), urut `day_of_week, start_time`. Chip hari ("Semua" + Sen..Min) hanya **filter**. `getAllSchedules()` membaca `ScheduleDao.getAll()`.
- Chip memakai `LazyRow` + `FilterChip`, bukan `SegmentedButtonRow` — delapan label tidak muat di 320 dp.
- Setiap kartu menampilkan label hari karena daftar lintas hari. Penanda "aktif" memeriksa `dayOfWeek` **dan** jam, memakai WITA; sebelumnya hanya jam sehingga kelas Sabtu tampak aktif di hari kerja.
- Tes: `ScheduleOwnerKeyTest`, `ScheduleDefaultFilterTest`.

Baris Room lama berkunci `students.id`/username menjadi yatim dan tidak dibaca; tidak dihapus otomatis.

## Koleksi penggunaan dan arti angka

Waktu bisnis WITA (`Asia/Makassar`), bukan timezone perangkat. `UsageSnapshotStore.WITA` dipakai untuk batas hari. Durasi sumber Android milidetik; DTO server menit.

1. Jadwal disinkronkan lewat repository dan dipakai menentukan sesi kuliah aktif.
2. `SmartMonitoringManager` polling lokasi perkiraan saat kelas mulai, tiap 45 menit, dan mendekati akhir. Tidak memakai geofence Android karena izin hanya `ACCESS_COARSE_LOCATION`.
3. Kedua collector membaca UsageEvents lewat `data/snapshot/ClassUsage.kt`, memotong interval pada batas WITA, menyimpan total absolut per `(studentId, logDate, scheduleId)`. Restart tidak mengatribusikan total harian ke kelas kedua.
4. `replaceDay` mengabaikan snapshot lebih tua dan mengganti ID baris. ACK memakai snapshot utuh dalam transaksi agar upload lama tidak menandai pembaruan baru sebagai SYNCED.
5. Sesi selesai pada waktu akhir kelas. Callback lama memeriksa identitas sesi sehingga tidak menghentikan kelas berikutnya. `ClassEndWorker` mengoleksi total final lewat WorkManager.
6. MQTT dan HTTP membawa total absolut per kelas. Subscriber MQTT meng-upsert per tanggal bisnis + schedule.

Ringkasan Beranda punya jalur berbeda: `UsageRepositoryImpl.getDailyUsageBreakdown()` membaca UsageEvents sejak tengah malam WITA, membentuk interval foreground/background, lalu membandingkan dengan window jadwal lewat `domain/model/DailyUsageMath.kt`.

`DailyUsageBreakdown`: `scheduledMinutes`, `outsideScheduleMinutes`, `scheduledClassCount`, `attendedClassCount`, `absentClassCount`. Kehadiran memakai bukti lokasi di kampus/jadwal dalam window; absen dihitung untuk kelas yang sudah selesai. Jangan ganti dengan SUM `app_usage_logs`, dan jangan anggap pemakaian ponsel membuktikan kehadiran. Perubahan overlap/tumpang-tindih/batas waktu wajib memperbarui test matematika.

## Penyimpanan lokal dan sinkronisasi

Room versi 5, tiga entity: `AppUsageLogEntity`, `LocationLogEntity`, `ScheduleEntity`. Tabel `pending_logs` lama sudah dihapus (migrasi 2→3); antrean pending ditandai pada baris log.

Migrasi 4→5 mengganti index harian menjadi `(student_id, log_date, schedule_id)`. Jangan gabungkan semua jadwal jadi satu baris per hari. Perhatikan NULL pada `scheduleId` dan semantik SQLite. Query pengguna harus berdasarkan studentId dari auth repository, bukan ID global.

SQLCipher memakai passphrase acak per instalasi yang disimpan terenkripsi. Tambahkan migrasi eksplisit saat schema berubah; jangan aktifkan destructive fallback yang menghilangkan antrean. Logout mempertahankan log terenkripsi per akun termasuk pending, sambil menghapus token/state login. `clearAllData(studentId)` hanya untuk hapus eksplisit; null berarti seluruh data.

Dua jalur sinkronisasi berbeda:

- `DataSyncManager.syncPendingData()` — otomatis/background; butuh internet; menunda bila baterai <20% dan tidak charging; hanya jalan pada window 22:00 inklusif sampai sebelum 04:00 WITA.
- `UsageRepositoryImpl.syncPendingLogs()` — dipakai tombol sinkronisasi manual; tanpa guard window/baterai. Membuka Beranda tidak otomatis mengunggah.

Keduanya mengambil maks 100 usage + 100 location per batch, mengirim `/api/logs/batch`, lalu mencocokkan `synced_client_ids` dengan namespace `usage:<id>` dan `location:<id>`. Jangan tandai semua pending sebagai synced hanya karena HTTP 200.

Pemetaan JSON di `Dtos.kt` harus cocok dengan schema server: `logs`, `locations`, `client_id`, `log_date`, `recorded_at`, `schedule_id`, `idempotency_key`, `synced_client_ids`. Perubahan property Kotlin tanpa `@SerializedName` bisa merusak API meski kompilasi lulus.

**Perbaikan koleksi 2026-11-18 (data tidak terbaca setelah 10 menit pemakaian):** ada empat penyebab, semuanya sudah diperbaiki.

- `collectAppUsage()` dulu langsung gagal `"No active class schedule"` bila tidak ada kelas berjalan, jadi Beranda kosong di luar jam kuliah. Sekarang tanpa kelas aktif ia mengoleksi pemakaian sejak 00:00 WITA sampai sekarang dan menulis baris `scheduleId = null`; dengan kelas aktif tetap memakai jendela kelas seperti sebelumnya.
- Koleksi dulu hanya sekali saat layar dibuka. `HomeScreen` kini memanggil `viewModel.refreshUsage()` tiap 60 detik selama `RESUMED` (senyap, tanpa mengedipkan skeleton/loading).
- `HomeUiState.error` tidak pernah dirender, sehingga kegagalan izin/kelas tampak seperti "tidak ada data". Sekarang ada kartu error + tombol "Coba lagi" di Beranda; Histori juga menampilkan error dengan retry.
- `hasUsageStatsPermission()` dulu menyimpulkan "izin tidak ada" dari `queryUsageStats` kosong — salah pada perangkat baru. Sekarang memakai `AppOpsManager.OPSTR_GET_USAGE_STATS` (MODE_ALLOWED), dengan fallback query hanya untuk MODE_DEFAULT.
- `AppUsageLogDao.getDayTotals` memakai **MAX, bukan SUM**. Baris per-kelas dan baris harian (`schedule_id = NULL`) sama-sama memuat total hari itu; SUM menghitung dua kali (diverifikasi dengan SQLite: 30+45 menjadi 75, seharusnya 45). Jangan kembalikan ke SUM.

## Background, MQTT, keamanan

- `SmartMonitoringService` wajib memanggil `startForeground` nyata; manager menyediakan state/notifikasi. Hentikan receiver, coroutine, resource via `destroy()`/`close()`.
- Session preferences mendukung pemulihan service; BootReceiver/keep-alive tidak boleh membangkitkan sesi pengguna yang sudah logout.
- NotificationScheduler: reminder pagi 07:00, sync 21:45, auto-sync 22:00 WITA, reminder kelas, mulai monitoring. Worker kelas menjadwalkan ulang minggu berikutnya, mulai tepat jam kelas.
- Background SOT tiap 4 jam + keep-alive terpisah. WorkManager tunduk pada constraint/OS; jam target bukan jaminan.
- Topic keluar: `students/{studentId}/logs` dan `students/{studentId}/location`. `MqttPushClient` berlangganan `dashboard/broadcast`. Broker primary/fallback dari config server.
- Production menolak downgrade transport tidak aman. Kredensial broker di APK bisa diekstrak; bukan identitas otorisasi per mahasiswa.
- Telemetri MQTT memakai envelope HMAC: `monitorung-mqtt-v1\n{topic}\n{sent_at}\n{payload}`, kunci refresh token terenkripsi. Server menolak pesan tanpa signature, sesi dicabut, atau selisih jam >300 detik. HTTP batch tetap memulihkan data pending. Signature bukan isolasi baca topik.
- `TokenAuthenticator` refresh 401 dengan Mutex. Hanya penolakan refresh 401 yang menghapus token; 429/5xx/jaringan mempertahankannya. Offline cache hanya bila akun/token lengkap dan validasi server <24 jam. Instalasi lama tanpa timestamp harus online sekali. Logging HTTP debug hanya BASIC, release NONE.
- Password, token, passphrase DB, signing key, token FCM tidak boleh tercetak ke log atau disalin ke memory.
- Firebase SDK/plugin ada, tetapi efektivitas FCM bergantung `app/google-services.json` nyata dan backend.

Gunakan `CoroutineScope` milik instance dengan SupervisorJob dan cleanup, bukan GlobalScope. Response body nullable harus menghasilkan kegagalan terkendali, bukan `body()!!`.

## UI dan UX yang dipertahankan

Visual Android: PaperCard lembut dan playful, berorientasi informasi mahasiswa. Jangan ganti menjadi dashboard web editorial atau mengembalikan glassmorphism lama.

- `Theme.kt`: primary biru #2454C6, canvas paper hangat #F6F5F1, semantic teal/amber/fuchsia, palette chart. Gunakan MaterialTheme dan token existing, bukan warna literal per layar.
- ThemeVariant: `LIGHT`, `DARK`, `AMOLED`; dynamic color default false. Typography memakai FontFamily.Default, headline 32/28/24, body 16/14/12. Tidak memakai empat font web.
- `PaperCard`: satu-satunya primitif kartu (Semua kartu/banner/callout layar memakainya). Surface theme-aware, border 1 dp, radius `RadiusPlayful` (24 dp), elevation 1 dp. Parameter opsional `containerColor`/`borderColor` (default token tema) dipakai untuk callout berwarna; backward-compatible. `GradientCard`, `BubbleNavBar` membungkus `PaperCard`. `Surface` langsung tersisa hanya untuk elemen dekoratif (ikon lingkaran, badge/chip, step dots, catatan inset), bukan kartu.
- Pakai PaperPill/SectionBadge, SpringButton, BubbleNavBar, SkeletonShimmer, PullToRefreshWrapper, ProgressRing, AnimatedCounter, haptic existing. `SectionBadge` hanya versi `components/SectionBadge.kt` (Settings dulu punya salinan lokal, sudah dihapus). `SectionBadge` **tidak memaksa lebar penuh**: pemanggil yang menentukan lewat `modifier` (wajib di `ProfileForm` agar ikon Edit tidak terdorong keluar layar).
- `DailyChallengeWidget` dan semua string `challenge_*` sudah dihapus. Tidak ada lagi kartu "Kurangi Screen Time"/tantangan harian; jangan menambahkannya kembali tanpa data progres nyata.
- Warna/radius layar memakai token `Theme.kt`, bukan literal `Color(0x)`/`Color.Gray` maupun angka dp. `Typography.kt` mendefinisikan `titleSmall`/`labelSmall` (sebelumnya jatuh ke default Material3). Konversi kartu 2026-11-17: Permission, Terms, History, Schedule, Home, Login, BiometricGate, DailyChallengeWidget. Verifikasi: `testProdDebugUnitTest` 27/27, `lintProdRelease` 0 issue. Belum diuji runtime/perangkat nyata.
- Ada komponen gamifikasi (streak, achievement, confetti). Keberadaan file bukan bukti terpasang; periksa pemanggil.
- Level/XP/streak dihitung terpusat oleh `domain/model/Gamification.kt` (WITA, bukan timezone perangkat). Beranda dan Histori memakai sumber XP yang sama (`getTodayAggregate`) dan daftar 30 log yang sama, sehingga angka level identik. `LevelProgressCard` menerima XP di level berjalan + sisa XP, bukan XP kumulatif. Jangan membuat rumus lokal baru di layar.
- Pull-to-refresh Histori benar-benar memanggil `collectAppUsage()`; kegagalan ditampilkan dengan tombol retry. Teks `history_about_*` sudah jujur: salinan lokal tetap ada setelah sinkron (hanya ditandai SYNCED) dan penghapusan manual ada di Pengaturan. Jangan menjanjikan penghapusan otomatis.
- Phone/tablet memakai WindowSize classes. Pertahankan inset edge-to-edge, ruang navigasi, layout layar sempit, pembesaran font.
- Status permission, offline, pending, synced, gagal, belum ada jadwal harus terbaca jelas. Loading tidak boleh menyamarkan kegagalan atau angka belum tersedia menjadi nol.
- Icon/tombol butuh contentDescription/semantics; status memakai label/ikon selain warna. Jaga touch target, TalkBack, kontras.
- `BouncySwitch` memakai role Switch, checked state, target ≥48 dp.
- Teks UI Indonesia; periksa `values/strings.xml` dan `values-in/strings.xml`.

## Build dan pemeriksaan

`dev`: suffix `.dev`, API emulator `http://10.0.2.2:8000`, pinning mati. `prod`: API `https://monitorterop.my.id`, pinning aktif (konfigurasi di `AppModule`; jangan salin nilai pin historis dari `CLAUDE.md`). Emulator `10.0.2.2` bukan IP backend untuk ponsel fisik.

Release memakai R8/resource shrinking + `app/proguard-rules.pro`. Perubahan DTO/Retrofit/Gson harus diuji pada build release; debug yang berhasil tidak membuktikan aturan reflection benar.

Dari `monitor-ung-app/`:

```bash
./gradlew :app:testProdDebugUnitTest
./gradlew :app:assembleProdRelease
./gradlew :app:lintProdRelease
```

Task yang **tidak ada**: `testProdReleaseUnitTest`, `testDebugUnitTest` (ambigu; pakai nama flavor). Build script membaca `mqttPassword` via `project.findProperty` dan melempar bila kosong, sehingga semua task bisa tertahan. Teruskan lewat environment dari konfigurasi production, tanpa mencetak nilai atau menaruhnya di argumen proses:

```bash
ORG_GRADLE_PROJECT_mqttPassword=... ORG_GRADLE_PROJECT_mqttUsername=... ./gradlew ...
```

Signing memakai property `monitorung.keystore.path`, `.password`, `monitorung.key.alias`, `.password` di `gradle.properties`. Jangan buka/tulis ulang keystore sebagai bagian tugas biasa.

APK: `app/build/outputs/apk/prod/release/` (arm64, armv7, x86_64, universal). AAB: `app/build/outputs/bundle/prodRelease/`. `apksigner`/`aapt2` ada di `/home/terio-op/.android/build-tools/36.0.0/`.

Test yang relevan:

- `data/push/MqttSignatureTest.kt` — signature backend, pengikatan topik/payload/waktu/sesi.
- `data/snapshot/ClassUsageWindowTest.kt` — dua kelas, jeda, restart, pecahan menit, batas akhir.
- `data/snapshot/UsageAcknowledgementTest.kt` — ACK snapshot lama, timestamp bertabrakan.
- `data/snapshot/UsageSnapshotStoreTest.kt` — delta, reset, cumulative.
- `domain/model/DailyUsageMathTest.kt` — pembagian pemakaian terhadap jadwal.
- `service/NextClassOccurrenceTest.kt` — jadwal berikutnya.
- `data/repository/ScheduleOwnerKeyTest.kt` — kunci Room memakai users.id.
- `presentation/viewmodel/ScheduleDefaultFilterTest.kt` — tab jadwal default tanpa filter.
- `domain/model/GamificationTest.kt` — batas level/XP, batas 120 XP, streak WITA dan urutan acak.

Perubahan izin/background/GPS/baterai butuh perangkat nyata; unit test tidak membuktikan perilaku OEM, Doze, geofence, atau foreground service.

## Status rilis

APK **0.32.0 (versionCode 33)** signed dengan sertifikat yang sama (`SHA-256 4e8a8649…`), empat ABI + `SHA256SUMS.txt` di `/home/terio-op/Downloads/MonitorUNG-0.32.0/`. Folder lama `MonitorUNG-0.30.1/` (versionCode 32) dibiarkan utuh. Build 0.32.0 memuat overhaul PaperCard dan perbaikan A–D (hapus tantangan "Kurangi Screen Time", tombol Edit profil, teks "Tentang Data Ini" yang jujur, lima bug fungsional). Verifikasi: `testProdDebugUnitTest` 30 test 0 gagal, `lintProdRelease` 0 issue, `apksigner` cocok di keempat APK, `sha256sum -c` OK. Build prod memakai kredensial MQTT asli dari `.env.production` (via env, bukan placeholder). **Belum dipublikasikan** ke `website/public/downloads/`; pengguna memasang manual. Unduhan publik di website masih 0.29.1 dan direktori `/downloads/` di VPS saat ini kosong.

Tidak ada perangkat/emulator terhubung; instalasi, upgrade, SQLCipher dengan data lama, biometrik, monitoring background, TalkBack, dan font besar belum diuji pada perangkat nyata. Unit test debug bukan eksekusi runtime APK hasil R8.

## Batas memory

Disusun dari pembacaan kode dan build, bukan pengujian runtime. `CLAUDE.md` dan `ARCHITECTURE.md` memuat riwayat panjang dan sebagian usang; cari bagian relevan saja dan utamakan kode.
