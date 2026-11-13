# Monitor UNG Android — Architecture Notes

## Build Configuration

### Supported ABIs (CPU Architectures)

| ABI | Target Devices | Use Case |
|-----|----------------|----------|
| `arm64-v8a` | Modern phones/tablets (2017+) | Primary — most current Android devices |
| `armeabi-v7a` | Older phones (2011-2017) | Legacy support — 32-bit ARM |

x86 and x86_64 have been **removed** to reduce APK size and complexity.

### APK Output Locations

All builds stored in: `app/build/outputs/apk/project-mati-tanam/`

| File | Build Type | Size | ABI |
|------|-----------|------|-----|
| `app-arm64-v8a-debug.apk` | Debug | ~23 MB | arm64-v8a |
| `app-armeabi-v7a-debug.apk` | Debug | ~22 MB | armeabi-v7a |
| `app-universal-debug.apk` | Debug | ~25 MB | Universal |
| `app-arm64-v8a-rilis-beta.apk` | Release | ~5.8 MB | arm64-v8a |
| `app-armeabi-v7a-rilis-beta.apk` | Release | ~4.4 MB | armeabi-v7a |
| `app-universal-rilis-beta.apk` | Release | ~7.9 MB | Universal |

Release APK ~4-5x smaller due to R8 minification and resource shrinking.

### SDK Levels

| Setting | Value |
|---------|-------|
| `compileSdk` | 34 (Android 14) |
| `targetSdk` | 34 (Android 14) |
| `minSdk` | 26 (Android 8.0 Oreo) |

`minSdk = 26` chosen because:
- Covers ~95% of active Android devices
- All modern encryption APIs available (SQLCipher 4.x)
- Adaptive icons supported
- Background services properly scoped

### Build Variants

```
Flavor  │ API Base URL                        │ Cert Pinning         │ MQTT Auth
────────┼─────────────────────────────────────┼──────────────────────┼─────────────
dev     │ http://10.0.2.2:8000                │ Disabled             │ Empty (backward compat)
prod    │ https://api.monitorterop.my.id/   │ Enabled              │ Shared secret
```

- `debug` — R8 disabled, debug symbols, no signing required
- `release` — R8 enabled, resource shrinking, ProGuard rules applied, signing from `gradle.properties`

**Wave 4 — MQTT auth (added 2026-10-05):** Production broker at `tcp://monitorterop.my.id:1883` is exposed via Cloudflare Tunnel and would be reachable by anyone on the internet. Previously `allow_anonymous=true` meant anyone could subscribe `dashboard/broadcast` (read admin events) or publish fake data to `students/$id/logs` and `students/$id/location`. Now `MQTT_USERNAME` + `MQTT_PASSWORD` are `buildConfigField`s in `app/build.gradle.kts`:

- `dev` flavor: empty (no auth — local broker may still be anonymous)
- `prod` flavor: from `-PmqttUsername=... -PmqttPassword=...` Gradle property, or default from `app/build.gradle.kts` (regenerated on each deploy, must match `MQTT_PASSWORD` in `.env.production`)

`MqttPushClient.kt` reads them via `BuildConfig.MQTT_USERNAME` / `BuildConfig.MQTT_PASSWORD`; paho only sends auth if username is non-empty. Trade-off: APK contains the password plaintext (extractable from decompiled APK). Future hardening: TLS + client certs. See `server/mosquitto/README.md` for rotation procedure (90 days recommended).

## Security Architecture

### Storage Security

| Data | Storage | Encryption |
|------|---------|------------|
| Database | SQLCipher | 256-bit AES, per-install random passphrase |
| DB passphrase | `EncryptedSharedPreferences` | AES-256-GCM + Android Keystore (hardware-backed) |
| Auth tokens | `EncryptedSharedPreferences` | AES-256-SIV (keys) + AES-256-GCM (values) |
| User profile | DataStore Preferences | Non-sensitive |
| Monitoring session | SharedPreferences | Plain — only during active session, auto-cleared |

**Database passphrase generation:**
```kotlin
val random = ByteArray(32)
SecureRandom().nextBytes(random)
// Stored in EncryptedSharedPreferences (AES-256-GCM)
```

### Certificate Pinning

`OkHttp CertificatePinner` — **prod flavor only**, dev has pinning disabled (proxy debugging).

```
HOW TO GET PINS:
  openssl s_client -connect api.monitorterop.my.id:443 \
    -servername api.monitorterop.my.id </dev/null \
    | openssl x509 -noout -pubkey \
    | openssl pkey -pubin -outform der \
    | openssl dgst -sha256 -binary | base64
  → prefix with "sha256/" → PROD_PIN_PRIMARY

  Backup pin (get from server operator or intermediate CA)
  → PROD_PIN_BACKUP

⚠️  Replace PLACEHOLDER values in AppModule.kt before prod release.
```

### HTTP Logging

| Build | Level | Sensitive Headers |
|-------|-------|-------------------|
| Debug | `Level.BODY` | Authorization, Cookie, Token → sanitized to `<redacted>` |
| Release | `Level.NONE` | None logged |

### App Access Gate (Biometric)

```
App Launch
  └─ attemptAutoLogin()
       └─ autoLoginWithSavedCredentials()
            └─ refreshToken() → success
                 └─ resolveAppAccess()
                      ├─ biometricEnabled=true → AppAccessState.LOCKED
                      │    └─ BiometricPrompt → success → UNLOCKED
                      └─ biometricEnabled=false → UNLOCKED (direct)
```

**AppAccessState:**

| State | Trigger | Action |
|-------|---------|--------|
| `LOADING` | App start | Checking credentials |
| `NEEDS_LOGIN` | No saved credentials | Show login screen |
| `LOCKED` | `biometricEnabled=true` + auto-login success | Show BiometricGateScreen |
| `UNLOCKED` | Biometric pass or no biometric enabled | Navigate to HomeScreen |

- `DEVICE_CREDENTIAL` authenticators — supports PIN/pattern/fingerprint fallback
- Toggle in Settings → "Verifikasi Biometric" switch (stores in DataStore)
- Setup dialog appears **after successful login** (prompt to enable)
- `BiometricSetupDialog` composable in `BiometricGateScreen.kt`

### Logout

```
logout()
  └─ database.clearAllData()    → DELETE FROM all tables
  └─ securePrefs.clear()        → wipe tokens, NIM
  └─ DataStore.edit { it.clear() }
  └─ Reset biometricPassed + appAccessState in LoginViewModel
```

---

## Responsive Layout

The app detects device form factor via `WindowSizeClass` and adapts layout accordingly.

### Device Tiers (smallestWidth `sw`)

| Tier | sw Range | Form Factor | Layout |
|------|----------|-------------|--------|
| Compact (Phone) | < 600dp | Phone | Bottom navigation, single-column content |
| Medium (Tablet Portrait) | 600-839dp | Tablet portrait | Side rail navigation, 2-column grid |
| Expanded (Tablet Landscape) | ≥ 840dp | Tablet landscape / desktop | Side rail navigation, 3-column grid, max-width container |

### Resource Directories

- `values/dimens.xml` — phone defaults
- `values-sw600dp/dimens.xml` — tablet portrait
- `values-sw720dp/dimens.xml` — tablet landscape
- `values/integers.xml` — grid columns per form factor

Padding, corner radius, text size, and icon size all scale by form factor.

## Data Collection & Monitoring

### Foreground Service Resilience

**Masalah:** User swipe app → monitoring service di-stop → student bisa tinggalkan kampus tanpa terdeteksi.

**Solusi:** `onTaskRemoved()` + SharedPreferences persistence.

```kotlin
// SmartMonitoringService.kt
override fun onTaskRemoved(rootIntent: Intent?) {
    val restartIntent = Intent(this, SmartMonitoringService::class.java).apply {
        action = ACTION_START
        putExtra(EXTRA_FROM_RESTART, true)
    }
    val pendingIntent = PendingIntent.getService(
        this, 1, restartIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    startForegroundService(pendingIntent)
}

// onStartCommand() restores session from SharedPreferences
val saved = getSavedSession()  // → (sid, state, start_ts, end_ts, course, room, dow, ...)
if (saved != null) resumeMonitoring(saved)
```

**SharedPreferences keys** (`monitor_session_prefs`): `sid`, `state`, `end_ts`, `start_ts`, `course`, `room`, `dow`, `start_time`, `end_time`.

### Dual-Mode Screen Time Collection

**Mode 1 — During Class Hours (SmartMonitoringManager)**
- Triggered 5 minutes before class starts via `ClassStartWorker`
- Collects SOT every **45 minutes** during class via `checkLocationDuringClass()`
- Also collects on-demand when user taps "Kumpulkan Data" button
- Queries full app list via `UsageStatsManager` (sosmed, games, other)
- Includes GPS location check (on campus vs off campus)
- `usedDuringClass = true`, `scheduleId` populated
- Battery-aware: skips GPS location check if battery < 15% and not charging

**Mode 2 — Outside Class Hours (BackgroundSotWorker + BatteryBasedSotManager)**
- Runs via `PeriodicWorkRequest` every **4 hours** when NOT in class
- Lightweight: tracks only `ACTION_SCREEN_ON/OFF` broadcasts + sosmed/game app usage
- No GPS location collection (battery efficient)
- `usedDuringClass = false`, `scheduleId = null`
- Checks: skip if currently in class, skip if battery < 15% and not charging

### Background Sync Windows

| Time (WITA) | Action |
|-------------|--------|
| 07:00 | Morning notification (today's schedule) |
| 2h before class | Class reminder notification |
| 5 min before class | Start monitoring (foreground service + location check) |
| During class | Location check every 45 min + SOT collection |
| 5 min before class ends | Final location check, stop monitoring |
| Every 4h (outside class) | Background SOT collection (BackgroundSotWorker) |
| 21:45 | Sync reminder (15 min until auto-sync) |
| 22:00 - 04:00 | Allowed sync window for server offload |
| 22:00 | Auto-sync (if enabled, battery-aware) |

### Data Lifecycle

- Data is stored **locally** in encrypted Room database (SQLCipher)
- Synced to server during **22:00-04:00 WITA** window only
- Auto-deleted from local DB after successful sync (server is the source of truth)
- **No manual delete** — users cannot delete history manually
- History screen shows synced/pending status with badges per day
- Data cleared only via: (a) successful sync to server, or (b) app data reset / logout

## Battery Optimization Strategy

The app is designed to be battery-friendly without removing monitoring functionality.

### Principles

1. **No battery optimization exemption requested** — the app does NOT ask users to whitelist it from Android battery optimization
2. **Location GPS only during class** — GPS is active only when class monitoring runs (5 min before to 5 min before end)
3. **Battery-aware WorkManager** — all background work respects `setRequiresBatteryNotLow(true)`
4. **Smart deferral** — sync and background SOT skip when battery < 15% and not charging
5. **Targeted queries** — only sosmed + games apps queried via UsageStatsManager, not full app list
6. **Location skipped on low battery** — during class monitoring, if battery < 15%, SOT collection continues but GPS check is skipped

### WorkManager Battery Constraints

| Worker | Battery Constraint | Notes |
|--------|-------------------|-------|
| `BackgroundSotWorker` | `setRequiresBatteryNotLow(true)` | Skipped when system battery saver active |
| `AutoSyncWorker` | `setRequiresBatteryNotLow(true)` | Sync deferred if battery low |
| `DataSyncWorker` (network) | `setRequiresBatteryNotLow(true)` | Battery-deferred variant |
| Class workers | None | Only run at scheduled class times |

## Performance Optimizations

### Build-Time

- `isMinifyEnabled = true` + `isShrinkResources = true` — removes unused code & resources (release only)
- ABI splits — users download only the APK variant for their device
- `useLegacyPackaging = false` — modern JNI packaging with page-aligned mapping
- ProGuard rules for Hilt, Retrofit, Room, SQLCipher, Tink, Paho MQTT

### Runtime

- **Battery-based screen time tracking** (`BatteryBasedSotManager`): uses `ACTION_SCREEN_ON/OFF` broadcasts instead of scanning all apps — minimal CPU/battery cost
- **Targeted app usage collection**: only social media + game packages queried (allowlist via `AppCategorizer`), not the entire app list
- **Smart monitoring state machine**: location checks every 45 minutes during class, not real-time. No location services when idle.
- **WorkManager scheduling**: alarms use `OneTimeWorkRequest` with exact delays instead of continuous background polling. Battery-friendly.
- **Foreground service only when active**: `SmartMonitoringService` only runs during class windows (5 min before → 5 min before end).
- **3-tier theme** (`LIGHT/DARK/AMOLED`): AMOLED = true black `#000000` for OLED battery savings.
- **DataStore instead of SharedPreferences**: asynchronous, coroutine-friendly, no ANR risk.
- **EncryptedSharedPreferences only for secrets** (tokens, passphrase), regular DataStore for non-sensitive prefs.

### Network

- OkHttp connection pool — reuses TCP connections
- `LoggingInterceptor` set to `BODY` only in debug, `NONE` in release
- Retry/backoff handled by WorkManager (`Result.retry()`)
- Offline queue: pending logs persist in Room until next sync window (22:00-04:00 WITA)
- Batch sync with idempotency keys (`deviceId` + `idempotencyKey`) for deduplication

## Database Schema

### AppUsageLogEntity (v2)

```sql
app_usage_logs (
  id PRIMARY KEY,
  student_id,
  log_date,           -- yyyy-MM-dd
  log_timestamp,
  total_screen_time_minutes,
  social_media_time_minutes,
  game_time_minutes,
  other_app_time_minutes,  -- NEW v2
  unlock_count,
  used_during_class,
  schedule_id,            -- NEW v2: class schedule reference
  device_id,             -- NEW v2: Android device ID
  idempotency_key,       -- NEW v2: deduplication key
  sync_status            -- PENDING | SYNCED
)
```

### LocationLogEntity (v2)

```sql
location_logs (
  id PRIMARY KEY,
  student_id,
  recorded_at,          -- NEW v2: ISO timestamp
  log_timestamp,
  latitude,             -- NEW v2
  longitude,            -- NEW v2
  campus_id,           -- NEW v2: campus identifier
  is_on_campus,
  matches_schedule,
  schedule_id,         -- NEW v2: class schedule reference
  idempotency_key,     -- NEW v2: deduplication key
  sync_status
)
```

Room database version bumped to **2** with fallbackToDestructiveMigration().

## Onboarding Flow

First-install flow: **Splash → Login → Terms → Permissions**

1. **TermsScreen** — Must scroll to bottom before checkbox enables
2. **PermissionScreen** — 3 required permissions:
   - Usage Access (UsageStats)
   - Location (coarse)
   - Notifications
   - **No battery optimization exemption** — explained that app is battery-efficient

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| Compose BOM | 2024.06.00 | UI framework |
| Compose Material3 Window Size | latest | Responsive layouts |
| Hilt | 2.51.1 | DI |
| Room | 2.6.1 | Local DB |
| SQLCipher | 4.5.4 | Encrypted DB |
| Retrofit | 2.9.0 | REST client |
| OkHttp | 4.12.0 | HTTP client |
| WorkManager | 2.9.0 | Background scheduling |
| FusedLocation | 21.3.0 | Location services |
| Paho MQTT | 1.2.5 | MQTT broker (future use) |
| Coroutines | 1.8.1 | Async |
| DataStore | 1.1.1 | Prefs storage |
| SplashScreen | 1.0.1 | Splash API |
| Biometric | 1.1.0 | Fingerprint/PIN/pattern gate |
| Security Crypto | 1.1.0-alpha06 | EncryptedSharedPreferences |
| Tink | 1.12.0 | Additional encryption primitives |
| Accompanist Permissions | 0.34.0 | Runtime permission handling |

## UI Theme

3 variants:
- `LIGHT` — white surfaces, blue primary
- `DARK` — slate dark surfaces, light blue primary
- `AMOLED` — true black surfaces (`#000000`), battery-saving on OLED

All variants use:
- Primary: `#3B82F6` (blue with WCAG AA contrast compliance)
- Glass morphism: blurred semi-transparent surfaces with subtle border
- Telegram-style compact bottom navigation (phone)
- NavigationRail (tablet)
- Color tokens with legacy aliases for backward compatibility

### Theme Color Palette (WCAG AA Compliant)

| Token | Light Mode | Dark Mode | Usage |
|-------|-----------|-----------|-------|
| Primary | `#3B82F6` | `#93C5FD` | Brand actions |
| Success | `#16A34A` | `#22C55E` | Sync done, granted |
| Warning | `#D97706` | `#FBBF24` | Pending, outside class |
| Error | `#DC2626` | `#FCA5A5` | Errors |
| Text Primary | `#0F172A` (14.1:1) | `#F8FAFC` (16.1:1) | Body text |
| Text Secondary | `#475569` (6.5:1) | `#CBD5E1` (9.1:1) | Supporting text |

## All Fixed Issues (2026-09-22)

### Security
- [x] Token in SharedPreferences → `EncryptedSharedPreferences` (AES-256-GCM + Android Keystore)
- [x] SQLCipher passphrase weak/hardcoded → per-install random 32-byte via `SecureRandom`
- [x] HTTP log expose tokens in release → `Level.NONE` in release builds
- [x] Logout tidak clear DB → `database.clearAllData()` on logout
- [x] Auto-login tanpa consent → biometric gate + post-login setup dialog
- [x] Certificate pinning tidak ada → `CertificatePinner` per build flavor
- [x] Password di SharedPrefs → removed, auto-login via refresh token only
- [x] Password in ViewModel StateFlow → local composable state only

### Stability
- [x] Foreground service bisa di-stop swipe → `onTaskRemoved()` + SharedPreferences persistence
- [x] Unbounded CoroutineScope → `SupervisorJob` + `close()`/`destroy()`
- [x] BroadcastReceiver leak → `receiversRegistered` guard + `destroy()`
- [x] Singleton never destroyed → `SmartMonitoringService.onDestroy()` → `destroy()`

### Data / API
- [x] `studentId=""` hardcoded → `authRepository.getCurrentUser()`
- [x] `response.body()!!` crash → safe null + `Result.failure()`
- [x] MQTT hardcoded emulator → in-memory cache + DataStore fallback
- [x] Notification channel ID mismatch → `R.string.channel_*` constants

### Code Quality
- [x] SOT 15-min window vs 45-min interval → `SOT_CHECK_INTERVAL_MS = 45 min`
- [x] KSP NonExistentClass error → `MonitoringSessionStore` removed, SharedPreferences inline
- [x] RoomDatabase.clearAllTables override conflict → renamed to `clearAllData()`
- [x] Gradle variable shadowing → rename `keystorePassword` → `keystorePw`, `keyAlias` → `keyAliasName`, `keyPassword` → `keyPw`
- [x] Companion object constants outside block → nested properly in `SmartMonitoringManager`
- [x] "Gunakan akun lain" clears credentials → navigates to login without clearing
- [x] NIM not pre-filled after auto-login → `nim = credentials.first` on success
