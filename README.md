# Monitor UNG — Android App

Aplikasi pemantauan distraksi belajar mahasiswa **Universitas Negeri Gorontalo**.
Menggabungkan screen time, jumlah unlock, jadwal kuliah, dan lokasi kampus untuk menghasilkan skor distraksi per kelas via PCA + K-Means (backend). Data dikumpulkan di HP, disinkronkan otomatis malam hari (22:00–04:00 WITA). apps ini membutuhkan 2 sistem lainnya

- Production: <https://monitorterop.my.id>
- Backend: `../server/` (FastAPI, port 8000) <https://github.com/Ryetodd21/server>
- Android: `../monitor-ung-app/` <https://github.com/Ryetodd21/monitor-ung-app>Ryetodd21/website
---

## Quick Start

```bash
# Clone & masuk folder Android
cd monitor-ung-app

# Debug build (bisa install langsung ke HP via adb)
./gradlew :app:assembleProdDebug

# Production build (butuh kredensial MQTT dari .env.production)
# Jalankan dari root repo agar .env.production terbaca:
cd /home/terio-op
ORG_GRADLE_PROJECT_mqttUsername=monitorung \
ORG_GRADLE_PROJECT_mqttPassword="$(grep '^MQTT_PASSWORD=' .env.production | cut -d= -f2-)" \
./gradlew :monitor-ung-app:app:assembleProdRelease --offline
```

**Output APK:** `app/build/outputs/apk/prod/{debug,release}/` — 4 ABI (arm64, armv7, x86_64, universal).

---

## Arsitektur Singkat

```
presentation/          # Compose UI (Material 3, PaperCard design system)
 ├─ ui/screens/        # Home, History, Schedule, Settings, Profile, Onboarding, Auth
 ├─ ui/components/     # PaperCard, SectionBadge, SpringButton, BubbleNavBar, Skeleton, ProgressRing
 ├─ ui/theme/          # Token warna (PrimaryBlue #2454C6), radius, tipografi, varian Light/Dark/AMOLED
 ├─ navigation/        # Bottom nav (phone) / Rail (tablet) + Profile overlay
 └─ viewmodel/         # HomeViewModel, HistoryViewModel, ScheduleViewModel, SettingsViewModel, ProfileViewModel

domain/
 ├─ model/             # AppUsageLog, DailyUsageBreakdown, TodayAggregate, Gamification (level/XP/streak)
 └─ repository/        # Interfaces: UsageRepository, ScheduleRepository, AuthRepository...

data/
 ├─ local/             # Room (SQLCipher), DAO, Entity (AppUsageLogEntity, LocationLogEntity, ScheduleEntity)
 ├─ remote/            # Retrofit API, DTO, TokenAuthenticator (refresh 401)
 ├─ repository/        # Implementasi repository
 ├─ snapshot/          # UsageSnapshotStore (delta UsageStats), ClassUsage (jendela kelas)
 ├─ push/              # MqttPushClient (HMAC envelope), FCMService
 ├─ lock/              # UnlockTracker (screen on/off counter)
 └─ service/           # SmartMonitoringManager, DataSyncManager, NotificationScheduler, Workers

di/                   # Hilt modules (AppModule, DatabaseModule, NetworkModule, ...)
```

---

## Fitur Utama

| Fitur | Layar / Komponen | Catatan |
|---|---|---|
| **Koleksi pemakaian** | `UsageRepositoryImpl.collectAppUsage()` | Tiap 60 detik di foreground; tanpa kelas aktif → total hari (00:00 WITA). Dengan kelas → window jadwal. |
| **Unlock counter** | `UnlockTracker` (BroadcastReceiver `ACTION_SCREEN_ON`) | Running total harian; reset 00:00 WITA. |
| **Lokasi / Geofence** | `SmartMonitoringManager` + `MqttPushClient` | Polling ~45 menit saat kelas; coarse location only. MQTT envelope HMAC. |
| **Sinkronisasi** | `DataSyncManager` (auto 22:00–04:00 WITA) & `syncNow()` manual | Batch 100 usage + 100 location. `client_id` + `idempotency_key`. Server ACK `synced_client_ids`. |
| **Jadwal kuliah** | `ScheduleRepository` + Room cache | Default: **semua hari**, chip hari sebagai filter. Pull-to-refresh. |
| **Riwayat** | `HistoryScreen` + `HistoryViewModel` | Pull-to-refresh → `collectAppUsage()`. Kartu "Tentang Data Ini" jujur: data lokal **tetap ada** setelah sync, hapus manual di Settings. |
| **Gamifikasi** | `Gamification.kt` (satu sumber) | Level/XP/streak identik di Home & History. XP = menit screen time (cap 120). Level naik 100, 120, 140… Streak = hari berurutan di WITA. |
| **Profil** | `ProfileScreen` (overlay) | Edit nama/email/HP/bio, ganti password, ganti avatar (base64). |
| **Tema** | `Theme.kt` + `Typography.kt` | Light / Dark / AMOLED. PaperCard radius 24dp, border 1dp, elevation 1dp. |

---

## Alur Data (Ringkas)

```
[UsageStatsManager / UsageEvents] ──▶ UsageSnapshotStore (delta per package)
         │
         ▼
[UsageRepositoryImpl.collectAppUsage()]
         │   ├─ with active class ──▶ window = [classStart, min(now, classEnd)]
         │   └─ no active class  ──▶ window = [00:00 WITA, now]
         │
         ▼
[AppUsageLogEntity] ──▶ Room (replaceDay by studentId, logDate, scheduleId)
         │                 scheduleId = null untuk baris harian (tanpa kelas)
         ▼
[getDayTotals (MAX, bukan SUM)] ──▶ HomeViewModel.todayUsage / HistoryViewModel.todayScreenMinutes
         │
         ├─▶ UI (metric cards, LevelProgressCard, StreakIndicator)
         │
         └─▶ DataSyncManager / syncNow() ──▶ POST /api/logs/batch ──▶ Server
                                              │
                                              ▼
                                    [MQTT students/{id}/logs] ──▶ Dashboard Dosen (WebSocket)
```

**Penting:** `getDayTotals` pakai `MAX(total_screen_time_minutes)`. Baris per-kelas dan baris harian (`schedule_id IS NULL`) **sama-sama memuat total hari itu**. `SUM` akan dobel-hitung (diverifikasi SQLite).

---

## Build & Konfigurasi

### Flavor
| Flavor | Suffix | API Base | Certificate Pinning |
|---|---|---|---|
| `dev` | `-dev` | `http://10.0.2.2:8000` | **OFF** |
| `prod` | (none) | `https://monitorterop.my.id` | **ON** |

### Environment wajib (prod)
```bash
# Di .env.production (root repo)
MQTT_USERNAME=monitorung
MQTT_PASSWORD=...           # shared secret broker, sama dengan server mosquitto
```

Gradle melewatkan kredensial lewat `ORG_GRADLE_PROJECT_mqttUsername/Password` → `BuildConfig.MQTT_USERNAME/PASSWORD`. **Tidak pernah hardcoded, tidak pernah dicetak ke log.**

### Signing (release)
`gradle.properties`:
```properties
monitorung.keystore.path=release.keystore.p12
monitorung.keystore.password=...
monitorung.key.alias=monitorung
monitorung.key.password=...
```
Sertifikat: `CN=Monitor UNG, OU=IT, O=Universitas Negeri Gorontalo, L=Gorontalo, ST=Gorontalo, C=ID`
SHA-256: `4e8a8649909cdd699e713b4c1754c8d0a0761d784b12589cfcd2a4006ac29bcf`

---

## Test & Lint

```bash
# Unit test (prod flavor)
./gradlew :app:testProdDebugUnitTest --offline
# → 30 test (termasuk GamificationTest, OfflineAccessTest, ClassUsageWindowTest, ...)

# Lint production
./gradlew :app:lintProdRelease --offline
# → 0 issue
```

### Test Kunci
| File | Apa yang diuji |
|---|---|
| `GamificationTest` | Batas level/XP, cap 120 XP, streak WITA & urutan acak |
| `DailyUsageMathTest` | Pembagian menit scheduled vs outside vs overlapping |
| `ClassUsageWindowTest` | Dua kelas, jeda, restart, pecahan menit, batas akhir |
| `UsageAcknowledgementTest` | ACK snapshot lama, timestamp bertabrakan |
| `UsageSnapshotStoreTest` | Delta, reset, cumulative |
| `ScheduleOwnerKeyTest` | Kunci Room pakai `users.id` (bukan `students.id`) |
| `ScheduleDefaultFilterTest` | Tab jadwal default tanpa filter (semua hari) |
| `MqttSignatureTest` | HMAC envelope, binding topic/payload/waktu/sesi |

---

## Izin Android (Manifest)

| Izin | Jenis | Digunakan untuk |
|---|---|---|
| `PACKAGE_USAGE_STATS` | Special access (Settings → Apps → Special access) | `UsageStatsManager` — **wajib** untuk koleksi screen time |
| `ACCESS_COARSE_LOCATION` | Runtime | Geofence / lokasi kelas |
| `ACCESS_FINE_LOCATION` | Runtime | Opsional, akurasi lebih baik |
| `POST_NOTIFICATIONS` | Runtime (Android 13+) | Notifikasi reminder, foreground service |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` | Manifest | `SmartMonitoringService` |
| `RECEIVE_BOOT_COMPLETED` | Manifest | `BootReceiver` restart service |
| `SCHEDULE_EXACT_ALARM` | Runtime (Android 12+) | `NotificationScheduler` reminder kelas |
| `USE_EXACT_ALARM` | Normal | WorkManager exact timing |

**Onboarding** (`PermissionScreen`, `TermsScreen`) memandu pengguna memberi izin Usage Access & Lokasi. Tanpa Usage Access, koleksi **tidak bisa jalan sama sekali**.

---

## Penanganan Error (UI)

| Skenario | Tampilan |
|---|---|
| Izin Usage Access belum diberikan | Kartu error merah di Beranda: "PACKAGE_USAGE_STATS permission not granted" + tombol **Coba lagi** → buka Settings Usage Access |
| Tidak ada kelas aktif | Koleksi tetap jalan (total hari). Tidak ada error. |
| Koleksi gagal (exception) | Kartu error di Beranda & Histori dengan pesan asli + tombol **Coba lagi** |
| Pull-to-refresh Histori gagal | Kartu error di atas daftar + tombol **Coba lagi** |
| Offline (API tidak dijangkau) | Badge "Offline" di header Beranda; sinkron manual tetap bisa dicoba |

---

## Konvensi Kode

- **PaperCard** = satu-satunya primitif kartu. `Surface` hanya untuk elemen dekoratif (ikon bulat, badge, step dots).
- **SectionBadge** tidak memaksa lebar penuh; pemanggil beri `modifier` (wajib di `ProfileForm` agar ikon Edit tidak terdorong).
- **Warna/radius** pakai token `Theme.kt` / `Typography.kt`. **Tidak** ada literal `Color(0x...)`, `Color.Gray`, `RoundedCornerShape(4.dp)` di layar.
- **Waktu bisnis = WITA** (`Asia/Makassar`). Gunakan `UsageSnapshotStore.WITA` (TimeZone) atau `ZoneId.of("Asia/Makassar")`. Jangan `Calendar.getInstance()` / `LocalDate.now()` tanpa zone.
- **Kunci Room = `users.id`** (dari auth), bukan `students.id`. Lihat `ScheduleOwnerKeyTest`.
- **Tidak ada `global` scope**. Semua coroutine di `viewModelScope` / `lifecycleScope` / instance `CoroutineScope` dengan `SupervisorJob`.

---

## Troubleshooting Cepat

| Gejala | Penyebab Umum | Cek |
|---|---|---|
| Beranda kosong "0 menit" setelah pakai HP | Izin Usage Access belum diberikan | Buka Settings → Apps → Special access → Usage access → Monitor UNG → **Allow** |
| Angka screen time dobel / aneh | Migrasi lama tinggalkan baris ganda per hari | `getDayTotals` sudah pakai MAX. Baris lama akan terabaikan. |
| Pull-to-refresh Histori tidak berubah | Sebelumnya hanya `delay()` tanpa koleksi | Sudah diperbaiki: memanggil `collectAppUsage()`. |
| Level/XP Beranda ≠ Histori | Rumus beda per layar | Sudah disatukan ke `Gamification.kt`. |
| Notifikasi tidak muncul | Izin POST_NOTIFICATIONS / exact alarm | Cek Settings → Notifications & Alarms. |
| Build prod gagal "MQTT_PASSWORD is required" | Env tidak dilewatkan | Jalankan dari root repo dengan `ORG_GRADLE_PROJECT_mqttPassword=...` |

---

## Rilis Terbaru

| Versi | VersionCode | Tanggal | Catatan |
|---|---|---|---|
| **0.32.0** | 33 | 2026-11-17 | Overhaul PaperCard, hapus "Kurangi Screen Time", fix Edit profil, teks "Tentang Data Ini" jujur, bug fungsional A–D. **Belum memuat fix koleksi 10-menit.** |
| **0.32.1 (upcoming)** | 34 | — | Fix: koleksi tanpa kelas aktif, polling 60dtk, error UI, izin via AppOps, MAX agregasi. |

**Folder rilis lokal:** `~/Downloads/MonitorUNG-0.32.0/` (4 ABI + `SHA256SUMS.txt`)

---

## Link Terkait

- **Backend (FastAPI):** `../server/` — API, ML, MQTT broker config
- **Website (Next.js):** `../website/` — Dashboard dosen/admin, publik
- **Memory proyek:** `../AGENTS.md` (root), `AGENTS.md` (folder ini), `../server/AGENTS.md`, `../website/AGENTS.md`
- **Deploy VPS:** `../DEPLOYMENT.md`, `docker-compose.yml`, `compose.vps.yml` di `/opt/monitorung`

---

## Lisensi

Internal Universitas Negeri Gorontalo. Tidak untuk distribusi publik tanpa izin.
