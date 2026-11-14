# Memory Android Monitor UNG

Snapshot kode: 2026-11-13. Berlaku untuk `/home/terio-op/monitor-ung-app`. Baca juga [memory proyek](../AGENTS.md), [backend](../server/AGENTS.md), dan [website](../website/AGENTS.md) untuk kontrak bersama. Path Kotlin di bawah relatif terhadap `app/src/main/java/com/monitorung/app/`, kecuali disebut lain.

## Tujuan dan stack

Android adalah aplikasi mahasiswa: masuk, memberikan persetujuan/izin, melihat jadwal, memantau penggunaan ponsel saat kuliah, melihat penggunaan harian dan histori, menyinkronkan data, mengelola profil dan preferensi. Pengumpulan harus tetap hemat baterai, transparan melalui izin/notifikasi, dan tahan koneksi terputus.

| Bagian | Konfigurasi sumber |
| --- | --- |
| Identitas | Namespace/applicationId `com.monitorung.app`; versionCode 31, versionName 0.30.0, build 2026-11-14; distribusi publik terakhir tetap 0.29.1. |
| Toolchain | Kotlin/Compose compiler 2.4.21, AGP 9.5.1 (Kotlin Android bawaan), KSP 2.3.13, Gradle wrapper 9.9.1, Java/JVM 17. |
| Android | minSdk 26, targetSdk/compileSdk 37. ABI arm64-v8a, armeabi-v7a, dan x86_64; split APK dan universal APK. |
| UI | Jetpack Compose BOM 2026.09.00, Material 3, Navigation Compose, window size classes, biometric. |
| Arsitektur | Hilt 2.60.1, ViewModel, StateFlow/Flow, Coroutines; layer data/domain/presentation. |
| Lokal | Room 2.8.5, SQLCipher Android 4.20.0, DataStore, EncryptedSharedPreferences/Android Keystore. |
| Jaringan | Retrofit 3.0, OkHttp 5.5.1, Gson, Eclipse Paho 1.2.5. |
| Background | WorkManager 2.12, foreground service, Google Play Services Location. |
| Push | Firebase Messaging 24.1.2 dan FCMService; konfigurasi Firebase diperlukan. |
| Test | JUnit 4.13.2 dan dependency instrumentasi/Compose test yang sudah ada. |

Versi dependency dipusatkan pada `gradle/libs.versions.toml`; jangan menduplikasi nomor versi dalam build script. Manifest fitur/platform ada di `app/src/main/AndroidManifest.xml`.

## Peta folder dan tanggung jawab

| Path | Tanggung jawab |
| --- | --- |
| `MonitorUNGApplication.kt`, `di/AppModule.kt` | Startup, dependency Hilt, worker factory, HTTP, Room terenkripsi, konfigurasi client. |
| `presentation/MainActivity.kt` | FragmentActivity untuk Compose dan BiometricPrompt; memilih layar berdasarkan AppAccessState. |
| `presentation/navigation/Navigation.kt` | Auth/onboarding, empat tab utama, pager ponsel, rail tablet, overlay profil. |
| `presentation/viewmodel/` | State dan tindakan layar; beberapa ViewModel histori/jadwal juga berada di `HomeViewModel.kt`. |
| `presentation/ui/screens/` | Splash/login, auth biometric, onboarding, home, schedule, history, settings, profile. |
| `presentation/ui/components/` | PaperCard, BubbleNavBar, PaperPill, SpringButton, skeleton, progress, haptic dan komponen bersama lain. |
| `presentation/ui/theme/`, `presentation/ui/util/WindowSize.kt` | Token visual, Typography, varian tema dan adaptasi ukuran. |
| `domain/model/`, `domain/repository/Repositories.kt` | Model/kontrak bisnis, DailyUsageBreakdown dan perhitungan penggunaan per jadwal. |
| `data/repository/` | AuthRepositoryImpl, UsageRepositoryImpl, ScheduleRepositoryImpl, ScheduleRequestRepositoryImpl. |
| `data/local/` | MonitorUNGDatabase, Entities, Daos; penyimpanan dan status sinkronisasi. |
| `data/remote/api/MonitorUNGApi.kt`, `data/remote/dto/Dtos.kt` | Endpoint Retrofit dan serialisasi JSON server. |
| `data/remote/TokenAuthenticator.kt` | Refresh token dan retry request 401. |
| `data/snapshot/UsageSnapshotStore.kt` | Snapshot cumulative UsageStats bersama untuk menghitung delta tanpa duplikasi antarcollector. |
| `data/lock/UnlockTracker.kt`, `receiver/UnlockReceiver.kt` | Counter unlock. |
| `data/push/MqttPushClient.kt`, `data/push/FCMService.kt` | Koneksi/publikasi MQTT, broadcast dan push FCM. |
| `service/SmartMonitoringService.kt`, `service/SmartMonitoringManager.kt` | Foreground lifecycle, sesi kuliah, koleksi screen time/lokasi, notifikasi monitoring. |
| `service/BatteryBasedSotManager.kt` | Akumulasi screen-on/off yang ringan. |
| `service/DataSyncManager.kt` | Sinkronisasi background dengan batas waktu, baterai dan jaringan. |
| `service/NotificationScheduler.kt`, `service/Workers.kt` | Reminder, mulai sesi, retry/sync, koleksi background dan keep-alive. |
| `receiver/BootReceiver.kt`, `receiver/GeofenceReceiver.kt` | Pemulihan setelah boot dan event geofence. |
| `util/SecurePrefs.kt`, `util/AppConstants.kt`, `util/Utils.kt` | Preferensi aman, konstanta perilaku dan utilitas bersama. |

## Alur masuk dan navigasi

`MainActivity` mengamati `LoginViewModel.appAccessState`: `LOADING`, `NEEDS_LOGIN`, `LOCKED`, `UNLOCKED`. Splash menunggu state; auth navigation menangani login/onboarding; state terkunci menampilkan biometric gate; unlocked membuka aplikasi utama.

Password hanya parameter sementara dari composable login ke `login(password: String)` dan repository. Jangan menyimpannya di state persisten, DataStore, SharedPreferences atau log. Auto-login memakai refresh token, bukan password yang disimpan. Jalur offline memulihkan pekerjaan pengingat/monitoring dari jadwal Room, tanpa menunggu fetch jadwal server. Biometric mengikuti preference/setup dan state machine aktual; dokumentasi lama yang menyebut biometric selalu wajib tidak menggambarkan semua cabang kode saat ini.

Empat tab aktif: Beranda (`home`), Jadwal (`schedule`), Histori (`history`), Profil (`settings`, berisi SettingsScreen). Profil detail merupakan overlay terpisah pada ponsel; Back menutup overlay dulu. Phone memakai HorizontalPager dan BubbleNavBar, tablet memakai NavigationRail/NavHost dengan batas lebar konten. Pertahankan state tab dan perilaku Back saat mengubah navigasi.

Alur awal mencakup TermsScreen dan PermissionScreen. Usage access, lokasi dan notifikasi memiliki mekanisme izin Android yang berbeda; jangan menyamakan special access UsageStats dengan runtime permission biasa. Perubahan izin harus mencakup manifest, onboarding, pemeriksaan runtime, dan penjelasan kepada pengguna.

## Koleksi penggunaan dan arti angka

Waktu bisnis adalah WITA (`Asia/Makassar`), bukan timezone perangkat yang kebetulan aktif. `UsageSnapshotStore.WITA` dipakai untuk batas hari. Durasi sumber Android dapat berupa milidetik, sedangkan DTO server memakai menit.

1. Jadwal disinkronkan melalui repository dan dipakai untuk menentukan sesi kuliah aktif. Pengingat dan worker kelas menyiapkan monitoring.
2. `SmartMonitoringManager` melakukan polling lokasi perkiraan saat kelas mulai, setiap 45 menit, serta mendekati akhir kelas. Tidak mendaftarkan geofence Android karena izin yang tersedia hanya `ACCESS_COARSE_LOCATION`.
3. Kedua collector membaca UsageEvents lewat `data/snapshot/ClassUsage.kt`, memotong interval pada waktu mulai/akhir kelas WITA, lalu menyimpan total absolut per `(studentId, logDate, scheduleId)`. Restart tidak mengatribusikan total harian ke kelas kedua; jeda antarkelas tidak masuk log kelas.
4. `replaceDay` mengabaikan snapshot lebih tua dan mengganti ID baris. ACK memakai snapshot utuh di dalam transaksi agar upload lama tidak menandai pembaruan baru sebagai SYNCED, termasuk timestamp yang sama.
5. Sesi selesai pada waktu akhir kelas, membatalkan job miliknya. Callback lama memeriksa identitas sesi sehingga tidak menghentikan kelas berikutnya. `ClassEndWorker` mengoleksi total final melalui WorkManager walau proses timer sudah mati; eksekusi tetap dapat terlambat oleh OS.
6. MQTT dan HTTP sekarang membawa total absolut per kelas. Subscriber MQTT meng-upsert tanggal bisnis dan schedule yang sama, bukan menyimpan baris tiap sampel. `UsageSnapshotStore` tetap tersedia untuk jalur lama, bukan baseline kedua collector kelas.

Ringkasan Beranda mempunyai jalur berbeda: `UsageRepositoryImpl.getDailyUsageBreakdown()` membaca UsageEvents sejak tengah malam WITA, membentuk interval foreground/background, lalu membandingkan dengan window jadwal hari itu lewat `domain/model/DailyUsageMath.kt`.

`DailyUsageBreakdown` berisi `scheduledMinutes`, `outsideScheduleMinutes`, `scheduledClassCount`, `attendedClassCount`, `absentClassCount`. Kehadiran memakai bukti lokasi di kampus/jadwal dalam window; absen dihitung untuk kelas yang sudah selesai. Jangan mengganti angka ini dengan satu SUM app_usage_logs atau menganggap penggunaan ponsel sendiri membuktikan kehadiran. Perubahan pada overlap interval, jadwal tumpang-tindih, atau batas waktu wajib memperbarui test matematika terkait.

## Penyimpanan lokal dan sinkronisasi

Room saat ini versi 5 dengan tiga entity: `AppUsageLogEntity`, `LocationLogEntity`, `ScheduleEntity`. Tabel `pending_logs` pada dokumentasi lama sudah dihapus melalui migrasi 2 ke 3. Antrean pending sekarang ditandai pada baris log.

Migrasi 4 ke 5 mengganti index penggunaan harian menjadi `(student_id, log_date, schedule_id)`. Jangan kembali menggabungkan semua jadwal menjadi satu baris per hari. Perhatikan NULL pada scheduleId dan semantik SQLite saat mengubah uniqueness. Gunakan DAO existing; query pengguna harus berdasarkan studentId dari auth repository, bukan ID global/hardcoded.

SQLCipher memakai passphrase acak per instalasi yang disimpan terenkripsi. Tambahkan migrasi eksplisit saat schema berubah; jangan mengaktifkan destructive fallback yang menghilangkan antrean belum terkirim. Logout mempertahankan log terenkripsi per akun, termasuk pending, sambil menghapus token/state login. Query tetap berdasarkan studentId. `clearAllData(studentId)` hanya untuk hapus data eksplisit: ketiga tabel dibatasi akun; null berarti seluruh data, dan Settings tidak memanggilnya tanpa akun.

Ada dua jalur sinkronisasi yang harus dibedakan:

- `DataSyncManager.syncPendingData()`: otomatis/background, memerlukan internet, menunda jika baterai di bawah 20% dan tidak charging, memeriksa window 22:00 inklusif sampai sebelum 04:00 WITA. Di luar window, antrean menunggu job pukul 22:00 tanpa menghabiskan retry jaringan. Komentar lama menyebut 15%, tetapi guard aktif memakai konstanta 20.
- `UsageRepositoryImpl.syncPendingLogs()`: jalur repository yang juga dipakai fitur sinkronisasi; tidak mempunyai guard window/baterai yang sama. Pemanggil manual berada pada tombol Beranda/Pengaturan; membuka Beranda tidak lagi otomatis mengunggah data. Periksa pemanggil saat mengubah perilaku manual.

Keduanya mengambil maksimal 100 usage dan 100 location pending per batch, mengirim `/api/logs/batch`, lalu mencocokkan `synced_client_ids` dengan namespace `usage:<id>` dan `location:<id>`. Jalur otomatis mengembalikan belum selesai jika masih ada pending agar worker retry; antrean besar yang melewati batas retry diteruskan malam berikutnya. Jangan menandai semua pending sebagai synced hanya berdasarkan HTTP 200. Status kosong/duplicate, retry, pembagian ID antar-tabel dan kegagalan parsial perlu diperhatikan saat mengubah kontrak ACK.

Pemetaan JSON di Dtos.kt harus cocok dengan schema server, termasuk `logs`, `locations`, `client_id`, `log_date`, `recorded_at`, `schedule_id`, `idempotency_key` dan `synced_client_ids`. Pengubahan Kotlin property tanpa memperhatikan `@SerializedName` dapat merusak API meski kompilasi lulus.

## Background, MQTT dan keamanan

- `SmartMonitoringService` harus memanggil Service.startForeground yang nyata; manager menyediakan state/notifikasi. Hentikan receiver, coroutine dan resource melalui `destroy()`/`close()` saat lifecycle berakhir.
- Session preferences mendukung pemulihan service; BootReceiver/keep-alive tidak boleh membangkitkan kembali sesi pengguna yang telah logout.
- NotificationScheduler mencakup reminder pagi 07:00, reminder sync 21:45, target auto-sync 22:00 WITA, reminder kelas dan mulai monitoring. Worker kelas menjadwalkan ulang kejadian minggu berikutnya.
- Background SOT dijadwalkan 4 jam dan keep-alive terpisah. WorkManager tunduk pada constraint/OS; target jam bukan jaminan eksekusi tepat detik.
- `DataSyncManager.scheduleAutoSync()` meneruskan ke NotificationScheduler: one-time work unik per tanggal, initial delay ke 22:00 WITA, constraint jaringan/baterai. AutoSyncWorker menjadwalkan malam berikutnya sebelum mencoba upload, lalu retry terbatas jika gagal. Logout membatalkan seluruh tag sync. Timestamp worker kelas dihitung ulang untuk setiap kejadian; mulai tepat jam kelas, bukan lima menit sebelumnya.
- Topic outgoing: `students/{studentId}/logs` dan `students/{studentId}/location`. MqttPushClient berlangganan `dashboard/broadcast`; broker primary/fallback berasal dari config server.
- Production MQTT menolak downgrade transport tidak aman. Kredensial broker yang masuk APK tetap dapat diekstrak; jangan menganggapnya identitas otorisasi per mahasiswa.
- `MqttPushClient` mempertahankan skema `wss://` untuk WebSocket. Publish telemetri membungkus string JSON asli sebagai `payload`, epoch detik `sent_at`, dan HMAC-SHA256 hex `signature` atas `monitorung-mqtt-v1\n{topic}\n{sent_at}\n{payload}` (UTF-8), memakai refresh token terkini dari penyimpanan terenkripsi sebagai kunci. Token tidak ikut dikirim atau dicetak. Backend menolak pesan tanpa signature, sesi dicabut/kedaluwarsa, atau selisih jam lebih dari 300 detik; HTTP batch tetap memulihkan data pending. APK lama perlu diperbarui untuk telemetri realtime. Signature bukan isolasi baca topik broker.
- TokenAuthenticator mengatur refresh 401 dengan Mutex. Hanya penolakan refresh 401 menghapus token; 429/5xx/gangguan jaringan mempertahankannya. Auto-login mempertahankan exception asli. IOException jaringan (bukan TLS), HTTP 429, dan 5xx mengizinkan cache hanya jika akun/token tersimpan lengkap dan validasi server terakhir belum berusia 24 jam. Login, refresh, dan penyimpanan profil sukses mencatat last_server_validation dalam preferensi terenkripsi; 401/403 pada refresh/profil membatalkan izin offline. Instalasi lama tanpa timestamp harus online sekali. Batas diperiksa saat membuka akses, setelah biometrik, ketika resume, dan oleh timer selama sesi; kedaluwarsa memicu validasi ulang tanpa menghapus antrean. Jam perangkat yang mundur sebelum waktu validasi ditolak. Logging HTTP debug hanya BASIC, release NONE. Perubahan TLS/token perlu mencakup client utama di AppModule dan client refresh terpisah, bukan hanya satu OkHttp builder.
- Password, access/refresh token, passphrase DB, signing key dan token FCM tidak boleh tercetak ke log atau disalin ke memory. Logging HTTP release harus mempertahankan batas privasi existing.
- Firebase SDK/plugin sudah ada, tetapi efektivitas FCM bergantung pada konfigurasi nyata `app/google-services.json` dan backend; komentar placeholder bukan bukti keadaan deployment saat ini.

Gunakan CoroutineScope milik instance dengan SupervisorJob dan cleanup, bukan GlobalScope. Response body nullable harus menghasilkan kegagalan yang terkendali, bukan `body()!!`. Logout harus mengoordinasikan auth, service, work, snapshot dan data lokal sehingga pengguna berikutnya tidak menerima data sesi lama.

## UI dan UX yang dipertahankan

Visual Android adalah PaperCard yang lembut dan playful, tetap berorientasi informasi mahasiswa. Jangan mengganti menjadi dashboard web editorial atau mengembalikan glassmorphism lama.

- `Theme.kt`: primary biru #2454C6, canvas paper hangat #F6F5F1, semantic teal/amber/fuchsia, palette chart dan pastel, spacing/radius bersama. Gunakan MaterialTheme dan token existing, bukan warna literal per layar.
- ThemeVariant aktif `LIGHT`, `DARK`, `AMOLED`; dynamic color default false. `Typography.kt` memakai FontFamily.Default dengan hirarki 32/28/24 untuk headline, 16/14/12 untuk body. Tidak menggunakan empat font web.
- `PaperCard`: Surface theme-aware, border 1 dp, radius `RadiusPlayful` 24 dp, shadow elevation 1 dp; padding positif termasuk 8 dp diterapkan. Warna/fill primitive lain mengikuti theme.
- Gunakan PaperPill/SectionBadge, SpringButton, BubbleNavBar, SkeletonShimmer, PullToRefreshWrapper, ProgressRing, AnimatedCounter dan feedback haptic yang sudah ada.
- Ada komponen gamifikasi seperti streak, achievement, challenge dan confetti. Keberadaan file bukan bukti semua komponen terpasang di layar; periksa pemanggil sebelum menambah penggunaan.
- Phone/tablet memakai WindowSize dan window size classes; resource ukuran/grid XML lama yang tidak dipakai telah dihapus. Pertahankan inset edge-to-edge, ruang navigasi, layout layar sempit dan pembesaran font.
- Status permission, offline, pending, synced, gagal request dan belum ada jadwal harus terbaca jelas. Loading tidak boleh menyamarkan kegagalan atau angka belum tersedia menjadi nol.
- Icon/tombol butuh contentDescription/semantics yang tepat; status memakai label/ikon selain warna. Jaga touch target, TalkBack, kontras dan feedback operasi.
- BouncySwitch memakai role Switch, checked state, target sentuh minimal 48dp; kontrol biometrik memiliki label aksesibilitas dan barisnya tidak lagi clickable kosong.
- Teks UI Indonesia; periksa `app/src/main/res/values/strings.xml` dan `values-in/strings.xml` saat mengubah string resource. Jangan menambah kalimat teknis backend pada alur mahasiswa jika tidak membantu tindakan pengguna.

## Build, flavor dan pemeriksaan

`dev`: suffix `.dev`, API emulator `http://10.0.2.2:8000`, pinning tidak diaktifkan. `prod`: API `https://monitorterop.my.id`, flag pinning aktif. Konfigurasi pin client utama ada di AppModule; jangan menyalin nilai pin historis dari CLAUDE.md. Emulator `10.0.2.2` bukan IP backend bagi ponsel fisik.

Release memakai R8/resource shrinking dan `app/proguard-rules.pro`. Perubahan DTO/Retrofit/Gson perlu memperhatikan build release; berhasil debug tidak membuktikan aturan reflection/minification benar.

Perintah dari folder `monitor-ung-app/`, setelah JDK/SDK dan property lokal yang diperlukan tersedia:

```bash
./gradlew :app:testDevDebugUnitTest
./gradlew :app:assembleDevDebug
./gradlew :app:lintDevDebug
```

Build script saat ini membaca `mqttPassword` dengan `project.findProperty` di blok flavor prod dan melempar bila kosong saat konfigurasi. Karena semua flavor dikonfigurasi, task dev juga dapat tertahan. Gunakan Gradle property lokal/CI yang disediakan operator; jangan memasukkan secret sungguhan ke contoh/chat. Komentar menyebut local.properties, tetapi mekanisme pembacaan efektif harus diverifikasi sebelum menganggap file itu otomatis memenuhi project property.

Konfigurasi signing memakai property `monitorung.keystore.path`, `monitorung.keystore.password`, `monitorung.key.alias`, `monitorung.key.password`; jangan membuka/menulis ulang keystore sebagai bagian tugas biasa. Build baru tidak berarti APK sudah didistribusikan atau diinstal.

Rilis 0.29.0 dibangun dengan `:app:assembleProdRelease :app:bundleProdRelease :app:testProdReleaseUnitTest`. Property MQTT dapat diteruskan melalui environment `ORG_GRADLE_PROJECT_mqttPassword` dan `ORG_GRADLE_PROJECT_mqttUsername` dari konfigurasi production, tanpa mencetak nilainya atau menaruhnya pada argumen proses. APK ada di `app/build/outputs/apk/prod/release/` (arm64, armv7, universal); AAB di `app/build/outputs/bundle/prodRelease/`. Signature APK/AAB dan 20 unit test release diverifikasi pada 2026-11-13; pin cocok dengan rantai sertifikat HTTPS saat pemeriksaan.

Test existing yang perlu digunakan sesuai perubahan:

- `app/src/test/java/com/monitorung/app/data/push/MqttSignatureTest.kt`: kecocokan signature backend serta pengikatan topik, payload, waktu, dan sesi.
- `app/src/test/java/com/monitorung/app/data/snapshot/ClassUsageWindowTest.kt`: dua kelas, jeda, rekonstruksi setelah restart, pecahan menit, batas akhir.
- `app/src/test/java/com/monitorung/app/data/snapshot/UsageAcknowledgementTest.kt`: ACK snapshot lama dan timestamp bertabrakan.
- `app/src/test/java/com/monitorung/app/data/snapshot/UsageSnapshotStoreTest.kt`: delta, reset, cumulative usage.
- `app/src/test/java/com/monitorung/app/domain/model/DailyUsageMathTest.kt`: pembagian pemakaian terhadap jadwal.
- `app/src/test/java/com/monitorung/app/service/NextClassOccurrenceTest.kt`: penentuan jadwal berikutnya.

Untuk perubahan nontrivial, tambahkan kasus terfokus pada suite yang relevan. Perubahan izin/background/GPS/baterai memerlukan perangkat/emulator karena unit test tidak membuktikan perilaku OEM, Doze, geofence atau foreground service nyata.

## Rilis production 2026-11-14

Keempat unduhan APK publik dan SHA256SUMS.txt diverifikasi 200 dengan checksum sama seperti artefak lokal. Probe browser unduhan universal macet; verifikasi universal/x86_64 diselesaikan melalui curl. Halaman publik dan /api/status 200; POST refresh tanpa sesi/body 401.

APK prodRelease 0.29.1 (versionCode 30) dibangun setelah clean, memakai R8 dan signing certificate yang sama dengan APK production sebelumnya. Universal, arm64-v8a, armeabi-v7a, dan x86_64 tersedia di `website/public/downloads/monitor-ung-0.29.1-{abi}.apk`; daftar SHA-256 di `website/public/downloads/SHA256SUMS.txt`. Salinan rilis dan APK lama di `/home/terio-op/releases/20261114-production/` (direktori privat, jangan distribusikan backup DB di dalamnya).

Task toolchain terbaru: `:app:assembleProdRelease :app:testProdDebugUnitTest :app:lintProdRelease`; `testProdReleaseUnitTest` tidak tersedia. 25 unit test flavor prod debug lulus, lint release 0 temuan. Semua APK lulus verifikasi signature, kesamaan signer lama, non-debuggable, version/application ID, zipalign 16 KB dan alignment ELF 64-bit. Pin TLS cocok dengan rantai sertifikat production pada waktu rilis. Log `/tmp/monitorung-prod30-android.log`. Tidak ada perangkat terhubung: instalasi/upgrade, SQLCipher dengan data pengguna lama, biometrik dan monitoring background belum diuji pada perangkat nyata. Unit test debug bukan eksekusi runtime APK hasil R8.

## Batas verifikasi memory

Perbaikan audit menjalankan unit test, lint dan assemble DevDebug. Tidak ada perangkat terhubung; TalkBack, akurasi lokasi perkiraan, Doze/OEM, Firebase dan broker nyata belum diuji. UsageEvents hanya mengikuti riwayat yang tersedia di perangkat; data historis salah sebelum perbaikan tidak dibersihkan otomatis. `CLAUDE.md` dan `ARCHITECTURE.md` merupakan referensi sejarah yang dapat berbeda dari schema, pin, visual dan lifecycle terkini. Perbarui memory ketika semantik koleksi, sinkronisasi, schema, izin, navigasi, flavor atau kontrak API berubah.

## Akses offline dan verifikasi biometrik 2026-11-13

Kebijakan pengguna: akses offline maksimum 24 jam sejak validasi server terakhir; sinkronisasi otomatis tetap 22:00 sampai sebelum 04:00 WITA dan manual melalui tombol. Tidak ada perubahan backend/Cloudflare, schema Room, ACK, maupun penghapusan antrean.

BiometricGateScreen memakai PaperCard, warna MaterialTheme, layout scroll dengan safe area dan lebar maksimum 480dp, tombol minimal 48dp, teks Indonesia, serta error live-region. Animasi pulse/gradient berulang dihapus. Dialog sistem menyediakan hasil sukses/batal/gagal; tombol mencegah prompt ganda dan prompt dibatalkan saat layar ditutup. Sensor/activity tidak tersedia tidak lagi membuka aplikasi. Android 11+ memakai BIOMETRIC_STRONG atau DEVICE_CREDENTIAL; Android 8-10 memakai BIOMETRIC_STRONG dengan tombol Batal, karena kombinasi strong + credential tidak didukung Android 9/10. Login akun tetap tersedia sebagai alternatif.

Indikator server Beranda memakai Retrofit existing (termasuk konfigurasi TLS), diperiksa setiap 30 detik selama layar resumed. Tidak menyimpulkan jaringan tersedia berarti API sehat. Sinkronisasi otomatis kini memperbarui timestamp terakhir pada penyimpanan yang sama dengan jalur manual.

Pemeriksaan OfflineAccessTest mencakup batas tepat 24 jam, timestamp kosong, jam mundur, gangguan jaringan/429/5xx, serta penolakan 401/403/TLS. Hasil akhir: 24 unit test lulus, assembleDevDebug berhasil, lint 0 error dan 231 warning yang sama dengan laporan baseline Git (tidak ada warning baru). Log: /tmp/monitor-ung-offline-verified.log. Build/test/lint DevDebug dilakukan dengan property MQTT dummy khusus konfigurasi dev; bukan APK production. Tidak ada perangkat/emulator terhubung: sensor, TalkBack, ukuran font besar, orientasi, tampilan light/dark/AMOLED, dan pemulihan server nyata belum diverifikasi. Batas waktu lokal memakai jam perangkat, bukan sumber waktu server tepercaya yang tahan manipulasi.

## Perbaikan 231 warning lint Android

Resource yang tidak digunakan dihapus lintas qualifier; terjemahan Indonesia memakai values-in sesuai Android. Label kuantitas memakai plurals (Indonesia hanya other), typo tipografi diperbaiki, adaptive icon memakai monochrome dan qualifier minimum SDK yang sesuai. Haptic API 30 memiliki fallback Android 8-10; pemeriksaan API 26 yang selalu benar dihapus. Compose memakai PullToRefreshBox, PrimaryTabRow, menu anchor bertipe, locale/resource observabel, dan LocalActivity/parameter window size tanpa cast LocalContext.

Toolchain/dependency di tabel diperbarui bersama. AGP 9 menyediakan Kotlin Android; plugin Compose tetap eksplisit. Library SQLCipher lama android-database-sqlcipher diganti sqlcipher-android, SupportOpenHelperFactory dan System.loadLibrary, dengan nama DB, passphrase byte-array, schema versi 5 dan migrasi existing tetap. Tidak ada rekey/reset data. Fallback destruktif saat downgrade dihapus agar antrean tidak terhapus jika APK lama tidak mendukung schema. Native SQLCipher arm64/x86_64 diperiksa: semua segmen LOAD beralignment 16384 byte.

device_id pada batch HTTP kini UUID acak per instalasi di filesDir melalui AtomicFile; tidak memakai ANDROID_ID. Kedua jalur upload memakai helper yang sama, logout mempertahankannya, uninstall menghapusnya. Fingerprint login existing belum diubah. Aturan backup/transfer mengecualikan seluruh domain penyimpanan; fullBackupContent dan allowBackup false. EncryptedSharedPreferences tetap dipakai melalui SecurePrefs existing agar format token/passphrase tidak berubah, meskipun API tersebut deprecated pada library terbaru.

Tes baru: BackupRulesTest (unit), InstallationIdTest dan EncryptedStorageTest (instrumentasi). Instrumentasi memeriksa UUID stabil, pembukaan ulang data terenkripsi dengan kunci biner, serta preferensi terenkripsi. Belum ada perangkat/emulator terhubung; kompilasi instrumentasi bukan bukti tes runtime atau migrasi database pengguna nyata. Upgrade target SDK membutuhkan pemeriksaan monitoring/background, biometrik, dan tampilan pada perangkat sebelum distribusi production.

Verifikasi akhir perbaikan warning: lintDevDebug 0 error/0 warning (baseline 231 warning), 25 unit test lulus, assembleDevDebug dan assembleDevRelease dengan R8 berhasil, compileDevDebugAndroidTestKotlin berhasil. APK universal DevRelease lulus apksigner verify, zipalign -P 16 dan pemeriksaan ELF seluruh library 64-bit. Log: /tmp/monitor-ung-warning-final.log; laporan: app/build/reports/lint-results-devDebug.html. Gradle wrapper diperbarui melalui task wrapper; checksum distribusi dipertahankan. Tidak menonaktifkan aturan lint atau membuat baseline pengecualian. Compiler masih mengeluarkan 9 deprecation unik di SecurePrefs untuk API enkripsi legacy; warning merger manifest unit-test terkait penghapusan WorkManagerInitializer dan informasi stripping native juga terpisah dari lint. APK belum didistribusikan; belum menjalankan instrumentasi pada perangkat.

## PaperCard dan APK 0.30.0 (31), 2026-11-14

Pengguna memilih memperhalus PaperCard existing. Ikon launcher baru vector/adaptive berupa kartu belajar bertumpuk, grafik dan centang; layer monochrome terpisah. Login/splash memakai foreground ikon yang sama. Surface lebih hangat, border ringan, CTA biru/teal dengan kontras lebih kuat; angka metrik memakai onSurface. Ringkasan penggunaan dipindahkan sebelum gamifikasi tanpa mengubah perhitungan data.

StaggeredEntrance selalu menyusun layout, lalu fade/geser 12dp; delay dibatasi 120ms dalam tween Compose agar mengikuti skala animasi sistem. Tombol memakai primitive Button untuk ripple/disabled semantics dan target minimal 48dp, spring kritis hanya pada isi (skala 0.98). Tab tidak membesar; selectable Role.Tab, label 12sp dan badge bersemantik. GradientCard mengikuti tinggi konten, wash 12% di atas surface tema, tanpa aksi klik palsu. Counter pertama menampilkan nilai nyata, perubahan berikutnya 320ms tanpa overshoot; ring 420ms dengan progress semantics. Login menghapus floating/confetti serta penundaan navigasi, mempertahankan label saat loading, live-region error, safe area/IME dan lebar maksimum 480dp. CTA tinggi minimum menggantikan tinggi tetap agar font besar dapat membesar.

Build prodRelease signed universal/arm64-v8a/armeabi-v7a/x86_64 tersedia lokal di `/home/terio-op/Downloads/MonitorUNG-0.30.0/`, bersama SHA256SUMS.txt dan icon-preview.png. Belum dipublikasikan ke website/VPS. Signer identik 0.29.1, non-debuggable, applicationId/version dan zipalign 16 KB lulus. Pin TLS cocok dengan rantai domain publik. 25 unit test prodDebug lulus; lintProdRelease 0 temuan; 7 tes baru PaperCardUiTest dikompilasi melalui compileDevDebugAndroidTestKotlin, belum dieksekusi. Tes mencakup padding, reservasi layout animasi, wrapping gradient/non-clickable, nilai counter awal, disabled button, navigasi dan kontras tiga tema. Log `/tmp/monitorung-paper31-final.log`.

Pengguna akan menguji APK sendiri dan meminta image emulator dihapus. Unduhan image Android 36 dibatalkan/dihapus, paket emulator yang baru dipasang juga dihapus. Tidak ada klaim pemeriksaan runtime UI/TalkBack/font besar/animasi pada perangkat. Keystore, database lokal, izin, API, koleksi/sinkronisasi dan logic monitoring tidak diubah.
