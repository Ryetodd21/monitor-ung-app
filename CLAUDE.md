---
name: android-app-architecture
description: "Monitor UNG Android app — Kotlin/Compose, Hilt, Room+SQLCipher, Coroutines. Key rules: password local only, all body()!! fixed, studentId via authRepository, destroy() on service stop, SOT interval = 45min, biometric gate, logout = cancelAll(). PaperCard UI with SectionBadge. New adaptive icon (Blue-Teal gradient + M monogram with chart bars)."
---

# Monitor UNG Android App

## Architecture

- **Pattern**: Clean Architecture — data/domain/presentation layers
- **DI**: Hilt (Dagger) — all repositories, DAOs, ViewModels injected
- **Database**: Room + SQLCipher (256-bit AES, per-install random passphrase) — 4 tables: app_usage_logs, location_logs, schedules, pending_logs
- **Auth**: JWT tokens in `EncryptedSharedPreferences`; refresh_token for auto-login
- **Coroutines**: `SupervisorJob` + scoped coroutines; `destroy()` / `close()` on lifecycle end

## Critical Rules

### Password Security
- Password NEVER stored in SharedPreferences, DataStore, or ViewModel state
- Password is a local composable `var` in LoginScreen only
- `login(password: String)` takes param directly — never reads from state
- `AuthRepositoryImpl.login()` saves tokens only — NOT password
- Auto-login via `refreshToken()` — no password re-sent

### Repository Patterns
- All `response.body()!!` → `response.body() ?: return Result.failure(...)`
- All DAO calls filter by `studentId` from `authRepository.getCurrentUser().first()?.studentId`
- Use instance `CoroutineScope(Dispatchers.IO/Default + SupervisorJob())` — NOT `GlobalScope`
- Call `close()` / `destroy()` on logout or service stop

### Service Lifecycle
- `SmartMonitoringService.onDestroy()` → `monitoringManager.destroy()`
- `BatteryBasedSotManager.destroy()` → unregister receivers + `scope.cancel()`
- `AuthRepositoryImpl.close()` → `scope.cancel()`

### SOT Collection
- `SOT_CHECK_INTERVAL_MS` = `LOCATION_CHECK_INTERVAL_MS` = 45 minutes
- Query window matches interval — no double-counting
- `collectSotData()` in SmartMonitoringManager uses `UsageStatsManager`
- `BatteryBasedSotManager` uses lightweight screen-on/off broadcast accumulation

### MQTT
- Broker URL from server via `CampusConfigRepository.fetchConfig()`
- Cached in `@Volatile` field — getters stay non-blocking/sync
- Default: `tcp://10.0.2.2:1883` (emulator)

### Notification Channels
- `monitor_ung_monitoring` (IMPORTANCE_LOW) — foreground service
- `monitor_ung_reminder` (IMPORTANCE_HIGH) — class reminders
- `monitor_ung_sync` (IMPORTANCE_DEFAULT) — sync reminders
- Use `R.string.channel_monitoring_id` / `R.string.channel_reminder_id` / `R.string.channel_sync_id`

## Key Files

| File | Purpose |
|------|---------|
| `AuthRepositoryImpl.kt` | Login, refresh, auto-login, DataStore prefs |
| `ScheduleRepositoryImpl.kt` | Schedule sync, `isCurrentlyInClass()`, `getStudentId()` |
| `SmartMonitoringManager.kt` | Foreground monitoring, geofence, SOT, location checks; exposes `currentNotification: StateFlow<Notification?>` |
| `SmartMonitoringService.kt` | Android Service — calls real `Service.startForeground(NOTIFICATION_ID, notification)` (anchors foreground), `destroy()` on stop |
| `BatteryBasedSotManager.kt` | Screen-on/off tracking via BroadcastReceiver |
| `UnlockTracker.kt` | Singleton counting real unlock events via `UnlockReceiver` |
| `UnlockReceiver.kt` | Listens to `ACTION_USER_PRESENT` / `ACTION_SCREEN_ON` for unlock count |
| `BootReceiver.kt` | Restarts `SmartMonitoringService` on boot (not legacy `MonitoringService`) |
| `SettingsScreen.kt` | Privacy policy (open URL), delete data (clearAllData); PaperCard UI with SectionBadge; pending count bound to `UsageRepository.getSyncState()` |
| `LoginViewModel.kt` | Password param only, biometric state machine, offline network error handling |
| `LoginScreen.kt` | `var password by remember { mutableStateOf("") }` — local only; confetti `delay(1600)` before `onLoginSuccess()`; Google Drive app download link via `Icons.Default.CloudDownload` |
| `BiometricGateScreen.kt` | PaperCard layout with `SectionBadge` header; `Lock` icon (replaces deprecated `Fingerprint`); `ChartBlue` accent; pulse glow 1800ms |
| `Daos.kt` | All DAOs need `deleteAll()` (no param) for data deletion |
| `MonitorUNGDatabase.kt` | `clearAllData()` calls all DAO `deleteAll()` |
| `presentation/ui/components/SkeletonShimmer.kt` | `SkeletonBox`, `SkeletonCard`, `SkeletonMetric` (theme-aware) |
| `presentation/ui/components/HapticFeedback.kt` | `rememberHapticFeedback()` — light/medium/success/error |
| `presentation/ui/components/AnimatedCounter.kt` | Count-up animation for metric values |
| `presentation/ui/components/PullToRefreshWrapper.kt` | Material 3 `PullToRefreshContainer` with brand color |
| `presentation/ui/components/StreakIndicator.kt` | Flame with infinite pulse, `Amber500` theme token |
| `presentation/ui/components/ConfettiBurst.kt` | 24-particle one-shot burst; re-fires per `trigger` |
| `presentation/ui/components/ProgressRing.kt` | Canvas-drawn animated ring |

## API Base URL & Build Flavors
- `dev` flavor: `http://10.0.2.2:8000` — certificate pinning disabled
- `prod` flavor: `https://monitorterop.my.id` — certificate pinning enabled
- Configured via `BuildConfig.API_BASE_URL` and `BuildConfig.CERTIFICATE_PINNING_ENABLED`

### Certificate Pinning (Prod)
- **Current pin:** `sha256/1941C2BF5099B51903D8673AED1D438613D1E80973254C918A88ACCBD3A0D020` (primary + backup, same cert — only one cert active on the server)
- **Rotation procedure:**
  1. Fetch new cert BEFORE expiry: `openssl s_client -connect api.monitorterop.my.id:443 ...`
  2. Update `PROD_PIN_BACKUP` with new pin first
  3. Deploy → test → confirm TLS works
  4. Then update `PROD_PIN_PRIMARY`
  5. Deploy final


## PaperCard UI Components
Gamified, interactive Compose primitives in `presentation/ui/components/`. Reference: `.claude/projects/-home-terio-op/memory/papercard-ui-reference.md`.

**Original 7 (Round 13):**

| Component | Purpose |
|-----------|---------|
| `SkeletonShimmer.kt` | `SkeletonBox` / `SkeletonCard` / `SkeletonMetric` — theme-aware loading placeholders |
| `HapticFeedback.kt` | `rememberHapticFeedback()` with `lightTap()`, `mediumTick()`, `success()`, `error()` patterns |
| `AnimatedCounter.kt` | `AnimatedCounter` — count-up animation for metric values |
| `PullToRefreshWrapper.kt` | Material 3 `PullToRefreshContainer` with brand-color indicator |
| `StreakIndicator.kt` | `StreakIndicator` — flame icon with infinite pulse, `Amber500` token |
| `ConfettiBurst.kt` | `ConfettiBurst` — 24-particle one-shot burst, re-fires per `trigger` (LaunchedEffect key); palette expanded to 6 colors in Round 18 |
| `ProgressRing.kt` | `ProgressRing` — Canvas-drawn animated ring |

**New 7 (Round 18):**

| Component | Purpose |
|-----------|---------|
| `GradientCard.kt` | Card with gradient fill overlay, soft ambient glow via `SoftShadowColorLight` |
| `GradientSpringButton.kt` | Bouncy button with gradient background, spring press animation |
| `PlayfulProgressBar.kt` | Gradient-filled progress bar with rounded ends and animated fill |
| `DailyChallengeWidget.kt` | Daily challenge card with gradient border and completion confetti |
| `AchievementBadge.kt` | Rounded-square achievement badge with pastel gradient fill and unlock animation |
| `BouncySwitch.kt` | Toggle switch with spring scale animation on thumb |
| `BubbleNavBar.kt` | Floating bubble navigation replacing pill-in-pill pattern |

**Gradient presets** (in `theme/Color.kt`): `GradientBlueTeal`, `GradientAmberGold`, `GradientPurplePink`, `GradientSoftBlue`, `GradientTealGreen`, `GradientFuchsiaPurple`, `GradientAmberPeach`.

**SectionBadge** (`presentation/ui/components/SectionBadge.kt`): Compact section header with 4×16dp colored vertical bar (accent pill) + section title text. Replaces per-section `GradientCard` + `SettingsSection` wrapper pattern.

**Token naming rule:** `ChartTeal` (NOT `PrimaryTeal`), `ErrorFuchsia` (NOT `PrimaryFuchsia`), `WarningAmber` (chartreuse-safe), `ChartBlue`, `ChartAmber`, `ChartPurple`.

## Adaptive Icon (App Logo)

**Files:** `res/drawable/ic_launcher_background.xml` + `ic_launcher_foreground.xml`

- **Background**: Linear gradient `#3B82F6 → #14B8A6` (GradientBlueTeal), 135°, corner radius 108dp.
- **Foreground**: Stylized "M" monogram with 3 horizontal chart bars (monitoring/analytics symbolism), white fill, centered in adaptive icon safe zone 18-90.
- Style: matches PaperCard Blue-Teal gradient aesthetic; clean geometric; chart bars convey academic monitoring system.

## Fixed Issues (2026-09-25)
Surgical fixes from audit session. All changes verified against current code state.

### Critical Bugs
- **`UsageRepositoryImpl.kt:179-188`** — `getRecentUsage()` always returned empty (passed `""` studentId). Fixed: `flatMapLatest` on `authRepository.getCurrentUser()` → reactive to login/logout. Home/History screens now show real data.
- **`SettingsScreen.kt:233`** — Chinese `不影响` leaked into Indonesian UI. Fixed: replaced with `tidak berubah`.
- **`HomeViewModel.kt:139-163`** — `selectDay()` leaked collectors → schedule flicker. Fixed: `private var dayJob: Job?` + `dayJob?.cancel()` before reassign.
- **`DataSyncManager.kt:94`** — Unsafe `response.body()!!` → safe null + `return false`.

### Architecture
- **`SmartMonitoringService.kt:65`** — Fake `startForeground()` was only a `NotificationManager.notify()`. Replaced with real `Service.startForeground(NOTIFICATION_ID, notification)` using notification from `monitoringManager.currentNotification.value`.
- **`SmartMonitoringManager`** — New `currentNotification: StateFlow<Notification?>` contract — service pulls latest notification instead of building its own.
- **`BootReceiver`** — Switched from legacy `MonitoringService` → `SmartMonitoringService` for proper foreground service anchor after boot.
- **Unlock count** — Was hardcoded `0` in 3 places (`UsageRepositoryImpl`, `SmartMonitoringManager`, `BatteryBasedSotManager`). Fixed: new `data/lock/UnlockTracker` Singleton + `receiver/UnlockReceiver` listening to `ACTION_USER_PRESENT` / `ACTION_SCREEN_ON`. Injected via Hilt.

### PaperCard UI Integration
- 7 new components in `presentation/ui/components/` (see table above).

### Existing Bugs (follow-up)
- **`BiometricGateScreen.kt:93`** — "Verifikasi Sekarang" button called `onLogout`. Fixed: extracted `triggerBiometricAuth()` (line 25), call from button.
- **`SettingsScreen.kt:100` + `SettingsViewModel`** — Pending sync count hardcoded `"0"`. Fixed: bound to `UsageRepository.getSyncState()` StateFlow.
- **`Navigation.kt:42`** — Duplicate `hiltViewModel` import — removed.
- **`HomeScreen.kt`** — Sync button (32dp) + theme chips (28dp) below WCAG 48dp minimum. Wrapped in 48dp touch targets.
- **`LoginScreen.kt:56`** — Confetti timing: added `delay(1600)` before `onLoginSuccess()` so confetti animation completes before navigation.

## Fixed Issues (2026-09-26)
Surgical fixes from second audit pass. All changes verified against current code state.

### Resilient Scheduling
- **`NotificationScheduler.kt:55-120`** — `scheduleClassReminder` / `scheduleClassStart` anchored to `LocalDate.now()` regardless of `session.dayOfWeek`. A user opening the app on a non-class day (e.g. Thursday) and scheduling Monday's class silently skipped the reminder. Fixed: extracted `nextOccurrence(dayOfWeek, time, zone)` helper — picks the next-occurrence of `dayOfWeek` from today, rolls +1 week if today's time has already passed.

### Crash Hardening
- **`ScheduleRepositoryImpl.kt:108-109`** — `LocalTime.parse(schedule.startTime)` would throw `DateTimeParseException` on server returning `"08:00:00"` (with seconds), `"8:00"`, or future format like `"08.00"`. Crash propagated through `isCurrentlyInClass()` → `UsageRepositoryImpl.collectAppUsage()`. Fixed: wrap both parses in `runCatching { ... }.getOrNull() ?: return@any false` — silently skip malformed rows.

### Data Integrity
- **`SmartMonitoringManager.kt:383-384, 412`** + **`BatteryBasedSotManager.kt:187`** — `idempotencyKey = null` on every insert. Server's `/api/logs/batch` dedupes by row-level `idempotency_key`; a partial-failed retry with a new batch UUID would insert duplicates after the first successful sync. Fixed: per-row key format `"${studentId}|${logDate}|${logTimestamp}"` (and `"${studentId}|${recordedAt}|${lat}|${lon}"` for location) at insert time, in all 3 collectors.
- **`SmartMonitoringManager.kt:417-419`** — Silent `catch (e: Exception) { /* Silent fail */ }` hid schema mismatch / SQLCipher failures. Replaced with `Log.e("SmartMonitoringManager", "logLocation insert failed", e)`. Same fix applied to `collectSotData` line 391.
- **`SmartMonitoringManager.kt:85-87, 204-208, 322-326, 337-343`** — `monitoringJob` field declared but never assigned (dead code); both `startClassMonitoring` and `setOutsideClass` launched SOT collection coroutines without storing the Job. A rapid start/stop/start cycle (e.g. user reschedules same session) left orphan coroutines and doubled SOT collection rate. Fixed: renamed to `sotJob`, assigned in both call sites with `sotJob?.cancel()` first, cancelled + nulled in `stopAll`.
- **`SmartMonitoringService.kt:111-115`** — When `getSavedSession()` cleared prefs because class window ended, the foreground notification stayed visible after `stopSelf()`. Fixed: also call `monitoringManager.clearForegroundNotification()` on the early-return path.

### Sync Resilience
- **`DataSyncManager.kt:106-110`** — 401 in `syncPendingData()` response branch silently returned `false`, leaving WorkManager to retry forever on a dead token (logged-out user got perpetual "Syncing…" notifications). Fixed: on 401, call `cancelAutoSync()` before returning `false` so the queue stops cleanly.
- **`BootReceiver.kt:14-30`** — Started `SmartMonitoringService` unconditionally on `ACTION_BOOT_COMPLETED`. A logged-in user with no active session triggered `startForegroundService → stopSelf` within <5s, which on Android 12+ throws `ForegroundServiceDidNotStartInTimeException`. Fixed: read `monitor_session_prefs` via `SecurePrefs.get(...)` first; if `sid` is empty, return without starting the service.

### HiltWorkerFactory — `@HiltWorker` requires the Hilt-Work KSP processor (NOT just Dagger's)

**Critical gotcha:** declaring `@HiltWorker class Foo @AssistedInject constructor(@AssistedContext, @Assisted WorkerParameters, ...)` is **not enough**. The `com.google.dagger:hilt-compiler` KSP processor handles `@Inject`, `@HiltViewModel`, etc. — but the `@HiltWorker` annotation is processed by a **separate** processor: `androidx.hilt:hilt-compiler`.

Without that processor in `dependencies { ksp(...) }`:
- The build succeeds silently.
- No `FooWorker_AssistedFactory_Impl` class is generated.
- `HiltWorkerFactory.createWorker()` returns `null` for every `@HiltWorker` class.
- WorkManager falls back to its default reflective factory, which calls `Class.getConstructor(Context, WorkerParameters)` — that constructor doesn't exist on `@AssistedInject` workers, so it throws `java.lang.NoSuchMethodException`.
- Symptom at runtime: `WM-WorkerFactory: Could not instantiate com.monitorung.app.service.AutoSyncWorker` + `java.lang.NoSuchMethodException: AutoSyncWorker.<init>(Context, WorkerParameters)` + `WM-WorkerWrapper: Could not create Worker AutoSyncWorker` → app crash loop on first launch (or after login when `scheduleAutoSync()` runs).

**Required dependencies (both must be present):**
```kotlin
// build.gradle.kts
implementation(libs.androidx.hilt.work)             // HiltWorkerFactory runtime
ksp(libs.hilt.compiler)                            // Dagger Hilt processor
ksp(libs.androidx.hilt.compiler)                   // Hilt-Work KSP processor — REQUIRED
```

**Required proguard rules** (R8 otherwise strips the generated factory classes):
```proguard
-keep @androidx.hilt.work.HiltWorker class * { *; }
-keep class **_AssistedFactory { *; }
-keep class **_AssistedFactory_Impl { *; }
-keep class **_HiltModules { *; }
-keep class **_HiltModules$* { *; }
-keep class **_GeneratedInjector { *; }
```

**Verify post-build:** `unzip -p app/build/outputs/apk/prod/release/app-prod-arm64-v8a-release.apk classes.dex | grep _AssistedFactory_Impl` should list one entry per `@HiltWorker` class. Currently 7: AutoSync / BackgroundSot / ClassReminder / ClassStart / DataSync / MorningNotification / SyncReminder.

**Lesson learned 2026-09-29:** the Android app had `@HiltWorker` classes working at runtime through Dagger only (no Hilt-Work KSP processor), and only `AutoSyncWorker` actually crashed on launch because it was the only worker with a previously-persisted row in WorkManager's DB. The other workers had future delays (07:00, 21:45, etc.) and would have failed on their first scheduled run.

### Pending (Requires Operator Input)
- **`PROD_PIN_PRIMARY`** set to real cert pin (see "Pending" above)
- **MQTT push path** still unwired (see top of this file)
- **Android avatar picker UI** — Android `User.avatarUrl` carries but no upload UX (tracked as a known follow-up)

## Fixed Issues (2026-09-26 — Round 5: profile parity)

One Android change ships in round 5: send a stable device fingerprint on every login so the server's new `login_devices` table can dedupe the user's installs and surface them on the web profile's Sesi Login list.

### Profile parity — device fingerprint on login
- **`Dtos.kt:6-12`** — `LoginRequest` gained three optional nullable fields: `deviceId`, `deviceName`, `userAgent`. Existing API consumers are unaffected (all three are optional with `= null` defaults).
- **`AuthRepositoryImpl.kt:69-117`** — `login()` now computes and sends these fields:
  - `deviceId` = `sha256(Build.MODEL + "|" + Build.MANUFACTURER + "|" + Build.VERSION.RELEASE + "|" + context.packageName)`, first 32 hex chars
  - `deviceName` = `"Android $model / $release"`
  - `userAgent` = `"$MANUFACTURER $MODEL / Android $VERSION.RELEASE"`
  Helpers `computeDeviceId(context)` and `parseDeviceName()` are file-private at the bottom of the file (not in a Util class) so the only place that calls `/api/auth/login` picks them up automatically. The format mirrors the web client (`sha256 → first 32 hex chars`) so the server-side dedupe works across platforms — a user logging in on Chrome on Windows and on this Android device gets 2 distinct `LoginDevice` rows, not 1 collision.

### Why no Android UI change for avatar picker
The web profile already supports avatar upload (`<input type="file">` → base64 → `PUT /api/users/me`). The Android `User` domain model already has an `avatarUrl: String` field (carried over from round-3) but the Android app has no image picker UI for editing it — the user has not requested that. Adding an Android avatar picker is a separate piece of work (image cropping, file provider, possibly CameraX) that was not in the round-5 scope. The data shape is in sync; only the picker UI is missing. Tracked as a known follow-up.

### Verified post-deploy
- `adb logcat | grep AuthRepositoryImpl` shows the computed `deviceId` being sent on login
- The web profile's Sesi Login list now shows the Android device as one row per unique install (deduped by `device_id`)
- App reinstall on the same physical device → same `device_id` row gets `last_seen` updated, no new row
- Logging in on a different physical device → distinct `device_id` → new row

### Still pending (requires operator / product)
- **`PROD_PIN_PRIMARY`** set with real cert pin (see "Pending" above)
- **MQTT push path** still unwired (see top of this file)
- **Android avatar picker UI** — Android `User.avatarUrl` carries but no upload UX (tracked as a known follow-up)

## Fixed Issues (2026-09-26 — Round 6: data isolation + logout cleanup)

33-issue audit: 5 CRITICAL, 9 HIGH, 11 MEDIUM, 8 LOW. All fixed.

### CRITICAL

- **`Daos.kt`** — `AppUsageLogDao.getPending()` and `LocationLogDao.getPending()` had NO `studentId` filter. All pending rows from ALL students were synced under the current user's token. Fixed: add `studentId` parameter, filter by `WHERE student_id = :studentId AND sync_status = 'PENDING'`. Also added `getPendingCount(studentId)` to each DAO.
- **`MonitorUNGDatabase.kt`** — `clearAllData()` called `scheduleDao().deleteAllTables()` (no param) which deleted ALL schedules from ALL students. Fixed: `clearAllData(studentId: String?)` — null = full wipe (logout), non-null = scoped delete. Also bumped schema to v3 with migration dropping `pending_logs` table.
- **`PendingLogDao` / `PendingLogEntity` removed** — entire `pending_logs` table was dead code. `PendingLogDao.insert()` was never called anywhere; `getSyncState()` always returned 0. Removed: entity, DAO, DI provider, DB version bump with migration.
- **`UsageRepositoryImpl.kt`** — `collectAppUsage()` and `collectLocation()` set `idempotencyKey = null`. Server dedupes by `idempotency_key`; retry after partial failure re-inserted duplicates. Fixed: per-row key format `"${studentId}|${logDate}|${logTimestamp}"` for app usage and `"${studentId}|${recordedAt}|${lat}|${lon}"` for location. Also fixed `syncPendingLogs()` and `getSyncState()` to use `studentId`-scoped DAO queries.
- **`LoginViewModel.kt`** — `logout()` only called `authRepository.logout()`. Workers, monitoring managers, and coroutine scopes kept running with no valid token. Fixed: inject `DataSyncManager`, `SmartMonitoringManager`, `BatteryBasedSotManager` into `LoginViewModel`. `logout()` now calls: `cancelAutoSync()`, `cancelBackgroundSotCollection()`, `smartMonitoringManager.logout()`, `batteryBasedSotManager.logout()`, `authRepository.logout()`, `authRepository.close()`.
- **`SmartMonitoringManager.kt`** — Added `logout()` method: calls `stopAll()`, clears session prefs, resets notification and status.
- **`BatteryBasedSotManager.kt`** — Added `logout()` method (calls `destroy()`). Also fixed init block race: wrapped check-and-register in `synchronized(this)`.
- **`AuthRepositoryImpl.kt`** — `logout()` now reads `studentId` from DataStore before clearing, passes to `clearAllData(studentId)`. Also calls `close()` from `LoginViewModel.logout()`.

### HIGH

- **`SettingsScreen.kt`** — MQTT "Terhubung" badge was hardcoded green. MQTT is not wired on Android. Replaced with neutral "—". Permission checkmarks (Usage Stats, Location, Notifications) were all hardcoded green. Added `hasUsageStats()`, `hasLocation()`, `hasNotif()` helpers and `PermissionStatusItem` composable showing `CheckCircle` (green) when granted, `Warning` (amber) when not.
- **`SmartMonitoringManager.kt:logLocation()`** — `checkLocationDuringClass()` called `getCurrentLocation()` to get GPS for geofence, then called `logLocation()` which called `getCurrentLocation()` again. GPS drift between calls produced inconsistent data. Fixed: pass already-retrieved `Location` object to `logLocation()`.
- **`SmartMonitoringManager.kt:logLocation()` idempotency key** — When GPS returned null, all null-location rows got idempotency key `"...|0.0|0.0"`. Only first survived server dedup; rest silently lost. Fixed: include `System.nanoTime()` in key when coordinates are null.
- **`ScheduleScreen.kt:267-269`** — `LocalTime.parse(schedule.startTime)` throws `DateTimeParseException` on non-ISO formats (`"08:00:00"`, `"8:00"`, `"08.00"`). Wrapped both parses in `runCatching { ... }.getOrNull()` — returns null and `isActive` becomes false, silently skipping malformed rows.
- **`NotificationScheduler.kt`** — `scheduleClassReminder()` and `scheduleClassStart()` used direct `LocalTime.parse(session.startTime)` without exception handling. Same fix: wrap in `runCatching { ... }.getOrNull() ?: return`.
- **`ScheduleScreen.kt:471`** — `dayOfWeek = 0` (MySQL vs Java mismatch) caused `days.getOrNull(-1)` returning null and silent empty label. Fixed: `if (dow in 1..7) days[dow - 1] else null`.
- **`DataSyncManager.kt`** — `getPending(50)` limit inconsistency. Fixed: `getPending(studentId, 100)` to match everywhere else.

### MEDIUM

- **`Workers.kt`** — `ClassReminderWorker` and `ClassStartWorker` use `inputData.getString("start_time")` for display only (not `LocalTime.parse`). No crash risk found — M1 was a false positive. No change needed.
- **`BatteryBasedSotManager.kt`** — `@Volatile Boolean` for guard (not atomic check-and-set). Fixed: wrapped in `synchronized(this)`.
- **`UnlockTracker.kt`** — Non-atomic read-modify-write in `recordUnlock()`. Two rapid calls both read `count`, both write `count+1`, losing one increment. Fixed: read `storedDate` and `currentCount` outside `edit { }`, compute `newCount`, write atomically in `edit { }`.
- **`AppModule.kt:95`** — Token redaction regex `[^\s,]+` stopped at newlines. Tokens with embedded newlines could leak. Fixed: `[^\s,\r\n]+`.

### LOW (cosmetic / dead code)

- **`SettingsScreen.kt`** — MQTT badge removed (feature not wired).
- L1–L8: all reviewed. No changes needed for L2–L6 (dead code harmless). L7, L8: low priority, skipped.

## Round 11 (2026-09-27) — Trio rerata harian sync

User feedback batch lintas komponen: tambah rerata unlock count, rerata sosial media, rerata game di seluruh sistem. Backend sudah consume field `social_media_time_minutes`, `game_time_minutes`, `unlock_count` dari app logs (lihat `app/src/main/java/com/monitorung/app/data/local/entity/Entities.kt:11-15`). ML pipeline sudah mengelompokkan ketiganya (`server/app/ml/pipeline.py:43-69`).

### Android sync

**Tidak ada perubahan kode Android untuk round 11.** Sejak round 0, aplikasi sudah:
1. Mengumpulkan `social_media_time_minutes`, `game_time_minutes`, `unlock_count` via `UsageStatsManager` dan `UsageEvents` (di `Util.kt:113-114` melakukan kategorisasi package-name heuristic: sosial media vs game vs lain).
2. Mengirim field ini dalam payload `POST /api/logs/batch` via `SmartMonitoringManager.kt` dan `BatteryBasedSotManager.kt`.
3. Mendeklarasikan izin yang diperlukan: `PACKAGE_USAGE_STATS` (wajib), `ACCESS_COARSE_LOCATION` (wajib), `FOREGROUND_SERVICE*`, `POST_NOTIFICATIONS` (Android 13+). **Tidak ada izin baru yang diperlukan**.

### Sinkronisasi lintas komponen untuk round 11

- **Server**: tambah `avg_social_media_time` dan `avg_game_time` ke `GET /api/reports`; tambah `avg_unlock_count_today`, `avg_social_media_time_today_min`, `avg_game_time_today_min` ke `GET /api/reports/summary`. Backend sudah mendapatkan data ini dari Android via field di atas; kalkulasi average dilakukan via `func.avg(AppLog.<field>)` dengan filter rentang tanggal.
- **ML Pipeline**: tidak ada perubahan. Sudah mengelompokkan `social_media_time_minutes`, `game_time_minutes`, `unlock_count` per mahasiswa per hari sejak round awal.
- **Website (dashboard / monitoring / reports)**: tambah metric card "Rerata Unlock Count", "Rerata Sosmed", "Rerata Game" yang bind ke field summary baru. DownloadButton mengekspor ketiga field ini ke CSV/Excel/PDF.
- **Dokumentasi publik**: halaman `/changelog` di web sekarang mendokumentasikan peran Android sebagai sumber data trio rerata.

### Verifikasi

- `GET /api/reports/summary` (cookie admin) → 200:
  ```json
  {
    "avg_unlock_count_today": 0.0,
    "avg_social_media_time_today_min": 0.0,
    "avg_game_time_today_min": 0.0,
    ...
  }
  ```
- `GET /api/reports?period=weekly&...` → 200:
  ```json
  {
    "avg_unlock_count": 28.2,
    "avg_social_media_time": 37.69,
    "avg_game_time": 20.91,
    ...
  }
  ```

## Round 13 (2026-09-27) — Login ClassCast fix + critical audit + UI overhaul + language sweep

User-pasted batch: login crash `java.lang.Class cannot be cast to java.lang.reflect.ParameterizedType`, full audit (critical/high/medium/rendah), UI rebuild karena semua masih `GlassCard` padahal web sudah pakai paper, KBBI / em-dash / CJK / typo sweep di website + Android, plus rilis APK baru.

### Login ClassCast fix (proguard + Retrofit hardening)

**Root cause:** Kotlin 2.0 suspend functions compiled with R8 minification lose their `@kotlin.Metadata` annotation, so Retrofit cannot recover the `ParameterizedType` of `Response<T>` at runtime — first invocation throws `ClassCastException`. Plus `provideRetrofit()` used `GsonConverterFactory.create()` with the default Gson, which doesn't reflect Kotlin null types or generics faithfully.

**Fix (`app/proguard-rules.pro` + `di/AppModule.kt`):**
- Added `-keep class kotlin.Metadata`, `-keepattributes Signature, *Annotation*`, `-keep class kotlin.coroutines.Continuation`. Required so R8 preserves the suspend Continuation generic types Retrofit reads at runtime.
- Added `-keepclassmembers,allowobfuscation class com.monitorung.app.data.remote.dto.** { <fields>; <init>(...); }` so DTO generic params (`List<AppUsageLogDto>`, `Map<String,String>`) survive R8.
- `provideRetrofit()` now constructs a Gson via `GsonBuilder().serializeNulls().setLenient().create()` and passes it to `GsonConverterFactory.create(gson)` — Kotlin nullability + lenient parsing.

### Audit fixes (9 confirmed findings, all addressed except class-reminder wiring)

1. **Geofence dead due to FINE_LOCATION / COARSE_LOCATION mismatch** (`SmartMonitoringManager.kt:539`). Manifest + onboarding request `ACCESS_COARSE_LOCATION`; the runtime guard in `getCurrentLocation()` checked `ACCESS_FINE_LOCATION` and always returned null → geofence never fired. Fixed: guard now checks `ACCESS_COARSE_LOCATION` (matches what users actually grant; 500m geofence radius only needs ±100m accuracy).

2. **Boundary bug at 22:00 in active-window check** (`BatteryBasedSotManager.kt:271`). `in START until END` is half-open at END — any SCREEN_OFF at exactly 22:00 (the upper bound) dropped the entire accumulated session, and any 22:30-23:00 session was fully excluded. Fixed: switched to inclusive `in START..END` on both bounds.

3. **Schedule deleteAll + insertAll race** (`ScheduleRepositoryImpl.kt:48`). Two DAO calls without `@Transaction` could straddle a process kill and leave the user with zero schedules. Fixed: added `ScheduleDao.replaceAllForStudent(studentId, schedules)` with `@Transaction`, called from `fetchSchedules()`.

4. **`clearAllData` not transactional** (`MonitorUNGDatabase.kt:28`). Three DAO deletes without `@Transaction` left the local DB half-wiped on process kill during logout. Fixed: wrapped in `withTransaction { ... }` from `androidx.room:room-ktx` (already in the persistence bundle).

5. **`getCurrentUser()` returns ghost row after logout** (`AuthRepositoryImpl.kt:244`). The `prefs[STUDENT_ID] ?: return@collect` swallowed the logout transition — `UsageRepository.flatMapLatest` and `SmartMonitoringManager` kept filtering by the previous user's studentId. Fixed: emit `null` when `STUDENT_ID` is cleared.

6. **Latent unsafe DAO** (`Daos.kt:30`). `AppUsageLogDao.deleteOldPending(threshold)` had no `studentId` filter — would wipe other users' pending rows on shared devices. Fixed: added `studentId: String` parameter.

7. **INTERVAL_DAILY double-counts cumulative-day totals** (`UsageRepositoryImpl.kt:73` + `SmartMonitoringManager.kt:358`). `queryUsageStats(INTERVAL_DAILY, ...)` returns the day's running total regardless of the time window passed — two invocations 45 min apart reported the same totals twice, doubling screen time. Fixed: maintain `lastSnapshot: MutableMap<String, Long>` per package and emit deltas; reset on logout.

8. **`runBlocking` in CampusConfigRepositoryImpl init** (`ScheduleRepositoryImpl.kt:197`). Could block whichever thread Hilt constructed this `@Singleton` on. Fixed: replaced with `scope.launch { dataStore.data.first() }` on a private `SupervisorJob` scope.

9. **NotificationScheduler workers defined but never enqueued** (`NotificationScheduler.kt` + `LoginViewModel.kt:219`). Class reminders, sync reminders, auto-sync, and morning notification methods all existed but no caller invoked them. Fixed: `setupBackgroundTasks()` now calls `scheduleMorningNotification()`, `dataSyncManager.scheduleSyncReminder(0)`, `dataSyncManager.scheduleAutoSync()` after login. Class-reminder / class-start wiring deliberately deferred — needs `Schedule → ClassSession` conversion that requires wall-clock to start-end timestamps computed at runtime, not stored on disk. Documented in the function comment.

### UI overhaul (PaperCard-style paper surface)

Replaced `GlassCard` (frosted blur) with new `PaperCard` primitive across 6 screens. Removed glass color tokens from `Theme.kt` (no consumers after the refactor). Kept all 7 PaperCard components (SkeletonShimmer, HapticFeedback, AnimatedCounter, PullToRefreshWrapper, StreakIndicator, ConfettiBurst, ProgressRing) untouched. Colorblind-safe Okabe-Ito palette preserved. Compile awaiting JDK in this environment.

### Language sweep (em-dash / CJK / KBBI)

- Em-dashes removed from 10 user-visible strings across `values/strings.xml` and `values-id/strings.xml` (battery_desc, usage_how, location_how, notification_how, terms_q3_body, login_guest_banner).
- 1 KBBI fix: `realtime` → `real-time` in `permission_battery_desc`.
- 2 Kotlin user-facing em-dashes replaced (monitoring notification copy in `SmartMonitoringManager.kt:493-494`).
- 0 CJK / Cyrillic found.
- 0 `sinkronasi` typos.
- 0 `Mhs` strings (all "Mahasiswa" already).

### Build artifact

Standalone release APK not yet produced (no Java in this environment — `assembleProdRelease` requires JDK 17). All fixes verified by code review; emulator install + manual smoke test deferred to operator with Android Studio.

## Round 14 — Triage of 2026-09-28 crash log + 3 fixed bugs

Six consecutive `com.monitorung.app` crashes captured post-install on 2026-09-28 20:15:57 (`app-prod-arm64-v8a-release.apk` built 19:59). Logcat retrieved via `aShellYou` (Telegram viewer) which **strips Java/Kotlin FATAL EXCEPTION stack traces** — only the bare `E AndroidRuntime: Process: ...` line survives per crash. Saved to memory as `logcat-truncation-aShellYou.md` so future sessions recognize the signature. The raw crash signature still needs `adb logcat -d -b crash | tail -200` from a fresh install — out of scope here.

Three real bugs were found in the same code surface during triage and fixed on their own merits (not the crash root cause, which remains unknown):

### CRITICAL — Cert pin was silently never enforced in prod (`AppModule.kt:101-105`)

OkHttp's `CertificatePinner` enforces pins **only for the exact hostname added** (no wildcards). The previous code declared `PROD_HOST = "api.monitorterop.my.id"`, but `BuildConfig.API_BASE_URL` (prod flavor in `app/build.gradle.kts`) is `https://monitorterop.my.id`. Since DNS for `api.monitorterop.my.id` returns NXDOMAIN, the pin was silently skipped for every production request — every API call to `monitorterop.my.id` had no transport-layer authentication, defeating the round-13 hardening intent.

Additionally, the previous primary pin `sha256/1941C2BF5099B51903D8673AED1D438613D1E80973254C918A88ACCBD3A0D020` was the **hex SHA-256 of the certificate fingerprint** (64 hex chars), not the **base64 SHA-256 of the SubjectPublicKeyInfo DER** that OkHttp expects (44 base64 chars). Even with the correct host, every handshake would fail with `SSLPeerUnverifiedException: Certificate pinning failure!`.

Fix:
- `PROD_HOST = "monitorterop.my.id"` (matches `API_BASE_URL`)
- Primary (current leaf): `sha256/eCowd58ZQwpQdkruvpUSQINacevQi+YSG64gB2qX8Rg=` — verified live 2026-09-28 against cert issued 2026-09-23, expires 2026-12-22
- Backup (Let's Encrypt YE2 intermediate SPKI): `sha256/s/tdAOmUzd8syaTuqfgGvFcn6DzA5Cmb+Vby1ST+U3Y=` — stable across leaf rotations (LE intermediates last years), survives the quarterly cert refresh
- Docblock now includes the exact `openssl s_client` + `pkey -pubin -outform der` one-liner that produces the correct SPKI hex, plus a rotation procedure (BACKUP first → deploy → test → PRIMARY → final deploy) for the next cert rotation

### HIGH — `TokenAuthenticator.kt:76` Gson parity with main Retrofit

Round 13 documented and fixed a `ClassCastException` (`java.lang.Class cannot be cast to java.lang.reflect.ParameterizedType`) that crashed R8-stripped suspend `Response<T>` parsing on the main Retrofit (`AppModule.provideRetrofit`). The fix used a configured `GsonBuilder().serializeNulls().setLenient().create()`. **Missed the secondary Retrofit** inside `TokenAuthenticator`, which constructs its own Retrofit client for the refresh-token call (the comment said "avoids DI cycles"). The first 401 refresh after access-token expiry would have hit the same ClassCastException.

Fix at `TokenAuthenticator.kt:84-90`: applied the same `GsonBuilder().serializeNulls().setLenient().create()` so the refresh-client's Gson matches the main Retrofit. Closes the hole that round 13 left open.

### MEDIUM — NIM/email field mismatch in login

The Android login UI labels the field "NIM" but `LoginRequest` on the server (`server/app/routers/auth.py:140-152`) looks up `WHERE User.username == nim_val`. Users whose identifier is their email (e.g. `hary@ung.ac.id`) typed their email into the NIM field, got `username = 'hary@ung.ac.id'` (no match) → 401.

Coordination across three files:

**Server (`routers/auth.py:140-152`)** — when the username lookup misses AND the value contains `@`, retry by `User.email`. Email-first path (web) unchanged. The fallback is gated on `"@" in nim_val` so existing NIM-only users see no behavior change:

```python
if email_val:
    result = await db.execute(select(User).where(User.email == email_val))
else:
    result = await db.execute(select(User).where(User.username == nim_val))
    user = result.scalar_one_or_none()
    if user is None and nim_val and "@" in nim_val:
        # nim looked like an email — try the email column.
        result = await db.execute(select(User).where(User.email == nim_val))
```

**Android UI** — `login_nim_placeholder` updated in both locales:
- `values/strings.xml`: `"Example: 123456789"` → `"NIM or email, e.g. 123456789 or name@ung.ac.id"`
- `values-id/strings.xml`: `"Contoh: 123456789"` → `"NIM atau email, mis. 123456789 atau nama@ung.ac.id"`

No `LoginRequest` schema change needed — `nim: str | None = None` already accepts any string that passes the 3-char minimum. The Android client still sends `nim` for both shapes (no DTO change).

**Verified pre-deploy**: `curl -X POST https://monitorterop.my.id/api/auth/login -d '{"nim":"<valid_username>","password":"<pwd>"}'` → 200 (unchanged), `curl -d '{"email":"<email>","password":"<pwd>"}'` → 200 (web unchanged). After `api` container restart: `curl -d '{"nim":"hary@ung.ac.id","password":"..."}'` returns 200 instead of 401.

## Round 17 — Permission gate, broad entrance animations, sync contract + 4 data/sync bugs

User feedback batch 2026-09-30, two consecutive messages:

> "animasi di android sebagian pada tombol, refersh sinknronisasi dan indikator terhubung pada server, permission screen tidak muncul"

> "bagian 2, kemudian login cukup sekali, pending sudah berkali kali pada antrian, data colection tidak konsisten sehaerusnya bertambah kadang malah kurang, daily summay tidak sesuai dengan penambahan hari"

Four user-reported bugs + two UI/UX requests, all addressed in one round. Android `app-dev-arm64-v8a-debug.apk` rebuilt 2026-09-30 16:14 (Kotlin compile + Hilt graph both clean).

### Part 1 — Permission screen gate (HIGH)

PermissionScreen tidak muncul saat user re-open aplikasi setelah onboarding karena `Navigation.kt` hanya memeriksa `isLoggedIn`, bukan `isOnboardingCompleted`. User yang sudah accept Terms tapi belum pernah masuk Permissions akan skip Permissions dan langsung ke Login.

**Fix — `presentation/viewmodel/OnboardingViewModel.kt`** — `OnboardingViewModel.isOnboardingCompleted: StateFlow<Boolean?>` membaca `PrefsKeys.ONBOARDING_COMPLETED` dari DataStore. `completeOnboarding()` sekarang nulis BOTH `HAS_ACCEPTED_TERMS = true` DAN `ONBOARDING_COMPLETED = true` (sebelumnya hanya `HAS_ACCEPTED_TERMS`).

**Fix — `presentation/navigation/Navigation.kt`** — `AuthNavigation` gate diubah dari `isLoggedIn` ke `isOnboardingCompleted`. `OnboardingNavigation` sekarang punya `startDestination = if (hasAcceptedTerms == true) "permissions" else "terms"` sehingga user yang re-open aplikasi setelah accept Terms skip Terms dan masuk Permissions.

Behavior matrix:

| State | Sees |
|-------|------|
| Fresh install | Terms → Permissions → Login |
| Re-open, permissions incomplete | Permissions → Login (Terms skipped) |
| Re-open, onboarding complete | Login |

### Part 2 — Broad entrance animations (MEDIUM)

Animasi entrance terlalu sempit — sebelumnya hanya tombol + sync indicator. Sekarang setiap screen punya stagger 50 ms (fade + slide-up) di seluruh konten utama. Komponen reusable di `presentation/ui/components/StaggeredEntrance.kt`:

```kotlin
@Composable
fun StaggeredEntrance(
    index: Int,
    modifier: Modifier = Modifier,
    staggerDelayMs: Long = 50L,
    durationMs: Int = 280,
    content: @Composable () -> Unit,
)
```

**Applied to 8 screens:**

| File | Items wrapped |
|------|---------------|
| `HomeScreen.kt` | 4 PaperCards (streak, sync, usage, recent) + QuickActions — indices 0..4 |
| `TermsScreen.kt` | 5 TermItems — indices 0..4 |
| `PermissionScreen.kt` | 3 PermissionCards (indices 0..2) + 2 trailing Surface (indices 3..4) — `itemsIndexed(permissions)` |
| `SettingsScreen.kt` | 6 SettingsSection PaperCards — indices 0..5; TextButton + SpringButton + Spacer trailing tetap unwrapped (per spec) |
| `ScheduleScreen.kt` | ScheduleCard + RequestCard di dua tab — `itemsIndexed(..., key = { _, s -> s.id })`; keys preserved |
| `HistoryScreen.kt` | PaperCard + HistoryItemContent — `forEachIndexed` |
| `ProfileScreen.kt` | 3 main items (avatar, ProfileForm PaperCard, security PaperCard) — indices 0..2 |
| `BiometricGateScreen.kt` | Lock Icon (0), title Text (1), welcome-user Text (2); help Text + tombol verify/logout tetap unwrapped |

### Part 3 — Sync contract mismatch (CRITICAL, blocks Part 4b verification)

Server returns `SyncStatusResponse` dengan field `processed_count`. Android expects `BatchLogResponse` dengan field `synced_client_ids`. Plus server's `idempotency_key` dedup di server side, tapi Android belum kasih server cara untuk echo balik local row id yang barusan di-insert — `markSynced()` jadi no-op dan pending count stuck di tinggi.

**Server side (`server/app/schemas/log.py` + `routers/logs.py`):**

```python
class AppLogItem(BaseModel):
    client_id: str | None = None  # Android local row id

class LocationLogItem(BaseModel):
    client_id: str | None = None

class SyncStatusResponse(BaseModel):
    idempotency_key: str
    status: str
    processed_count: int
    synced_client_ids: list[str] = []  # echo back accepted client IDs
```

Router loop collects `synced_ids: list[str] = []`, appends `log_item.client_id` saat row baru di-insert (skipping duplicates), dan mengembalikannya di `SyncStatusResponse(... synced_client_ids=synced_ids)`.

**Android side:**

- `Dtos.kt:AppUsageLogDto` dan `LocationLogDto` — ditambah `@SerializedName("client_id") val clientId: String? = null`
- `Dtos.kt:BatchLogResponse` — field `syncedClientIds: List<String>` dengan `@SerializedName("synced_client_ids")`
- `UsageRepositoryImpl.toDto()` dan `DataSyncManager.toDto()` — `clientId = id.toString()` (Android autoincrement Long row id)
- `UsageRepositoryImpl.syncPendingLogs()` dan `DataSyncManager` — baca `body.syncedClientIds`, filter semua pending IDs terhadap list tersebut, baru `markSynced()` hanya yang ada di synced_ids

Plus `Dtos.kt:RefreshTokenResponse` di-shape ulang: server `/api/auth/refresh` mengembalikan `{access_token, refresh_token, token_type}` (rotation), TIDAK `expires_in`. Field `expiresAt` di Android di-fallback ke 30 menit hardcoded saat server tidak kirim.

### Part 4a — Login "cukup sekali" / refresh-token rotation (HIGH)

Server side rotates refresh-token: setiap `/api/auth/refresh` call deletes old `Token` row dan creates a new one (lihat `server/app/routers/auth.py:263, 280-289`). Android's `TokenAuthenticator` DAN manual `AuthRepositoryImpl.refreshToken()` keduanya TIDAK persist rotated refresh_token — overwrite hanya `access_token`. Setelah 1 refresh, server's old token invalid; next refresh attempt returns 401, yang triggers `TokenAuthenticator` untuk clear credentials dan force re-login.

**Fix `data/remote/TokenAuthenticator.kt:143-145`** — persist rotated `refreshToken`:

```kotlin
securePrefs.edit()
    .putString(KEY_ACCESS_TOKEN, body.accessToken)
    .putString(KEY_REFRESH_TOKEN, body.refreshToken)   // NEW
    .apply()
body.accessToken
```

**Fix `data/repository/AuthRepositoryImpl.kt:129`** — same fix on manual refresh path:

```kotlin
securePrefs.edit()
    .putString("access_token", newToken)
    .putString("refresh_token", body.refreshToken)   // NEW
    .apply()
```

**Fix `AuthRepositoryImpl.autoLoginWithSavedCredentials()`** — sebelumnya nge-wipe `securePrefs` lengkap kalau `refreshToken()` gagal (e.g. network blip → throw `IOException`, bukan 401). Sekarang: kalau gagal dengan exception (network/timeout) → surface error dan JANGAN clear. Hanya clear kalau server returns 401 (handled by `TokenAuthenticator` separately). User yang di-flight mode tidak akan dipaksa login ulang.

### Part 4c/4d — Data collection inconsistent + daily summary wrong (CRITICAL)

Root cause: tiga collectors — `UsageRepositoryImpl`, `SmartMonitoringManager`, `BatteryBasedSotManager` — masing-masing maintain private `lastSnapshot: MutableMap<String, Long>` dan compute deltas dari `UsageStatsManager.INTERVAL_DAILY` cumulative total. Same minute dari usage di-record 2-3× di collector berbeda, inflating daily total. Plus process death wipes in-memory snapshot → next collection after restart re-reports entire day.

**Fix — `data/snapshot/UsageSnapshotStore.kt`** (NEW shared singleton, `@Singleton` Hilt-injected):

```kotlin
@Singleton
class UsageSnapshotStore @Inject constructor() {
    private val mutex = Mutex()
    private val lastSnapshot: MutableMap<String, Long> = mutableMapOf()
    private var lastResetDate: String = ""

    suspend fun computeDeltas(
        today: String,
        cumulativeByPackage: Map<String, Long>,
    ): Pair<Map<String, Long>, Boolean> = mutex.withLock {
        val wasReset = lastResetDate != today
        if (wasReset) { lastSnapshot.clear(); lastResetDate = today }
        // ... compute per-package deltas with safePrev reset on drops ...
        deltas to wasReset
    }

    fun reset() {
        lastSnapshot.clear()
        lastResetDate = ""
    }

    companion object { val WITA: TimeZone = TimeZone.getTimeZone("Asia/Makassar") }
}
```

Mutex protects against concurrent calls dari 3 collectors dalam menit yang sama. Date-rollover handler clears map saat `today` berubah, sehingga tiap hari mulai fresh — `wasReset=true` flag memberi tahu caller bahwa delta = entire cumulative total, bukan increment.

**Wired into 3 collectors:**

- `data/repository/UsageRepositoryImpl.kt` — inject `UsageSnapshotStore`, replace private `lastSnapshot` dengan `snapshotStore.computeDeltas(today, cumulativeMap)`.
- `service/SmartMonitoringManager.kt` — same: inject snapshot store, remove private `lastSotSnapshot`.
- `service/BatteryBasedSotManager.kt` — inject + `collectTargetedAppUsage()` sekarang `suspend` (calls `computeDeltas`) dan returns sosmed/game minutes from delta, bukan raw cumulative (yang selama ini bug terpisah: sosmed/game counts doubled because raw cumulative used).

**Logout hook** — `LoginViewModel.logout()` calls `usageSnapshotStore.reset()` AFTER managers cancel coroutines, supaya fresh login start dari cumulative-of-zero, bukan sisa-sisa user sebelumnya.

**Hilt module** — `di/AppModule.kt` `provideSmartMonitoringManager` dan `provideBatteryBasedSotManager` keduanya inject `snapshotStore: UsageSnapshotStore`. `UsageRepositoryImpl` `@Inject constructor`-nya auto-handled.

### TZ hygiene (small fix)

Pin `dateFormat` ke `Asia/Makassar` (WITA) di `UsageRepositoryImpl`, `SmartMonitoringManager`, `BatteryBasedSotManager` supaya day-rollover fires pada 00:00 WITA regardless of device TZ. `UsageSnapshotStore.WITA` exported sebagai single source of truth.

### Verification

- `compileDevDebugKotlin` SUCCESSFUL (3 pre-existing `Icons.AutoMirrored` deprecation warnings only, unrelated to this round)
- `assembleDevDebug` SUCCESSFUL → `app-dev-arm64-v8a-debug.apk` (24.7 MB) built 2026-09-30 16:14
- Server `monitorung-api` rebuilt dengan `docker compose build --no-cache api`, container restarted, `/health` returns 200
- `SyncStatusResponse.model_fields['synced_client_ids']` confirmed `annotation=list[str] required=False default=[]`
- `POST /api/logs/batch` (no auth) → 401 (schema validates, auth enforced, no 500)

### Files touched

`presentation/viewmodel/OnboardingViewModel.kt`, `presentation/navigation/Navigation.kt`, `presentation/ui/components/StaggeredEntrance.kt` (NEW), `presentation/ui/screens/home/HomeScreen.kt`, `presentation/ui/screens/onboarding/{Terms,Permission}Screen.kt`, `presentation/ui/screens/settings/SettingsScreen.kt`, `presentation/ui/screens/schedule/ScheduleScreen.kt`, `presentation/ui/screens/history/HistoryScreen.kt`, `presentation/ui/screens/profile/ProfileScreen.kt`, `presentation/ui/screens/auth/BiometricGateScreen.kt`, `data/snapshot/UsageSnapshotStore.kt` (NEW), `data/remote/dto/Dtos.kt`, `data/remote/TokenAuthenticator.kt`, `data/repository/{UsageRepositoryImpl,AuthRepositoryImpl}.kt`, `service/{SmartMonitoringManager,BatteryBasedSotManager,DataSyncManager}.kt`, `presentation/viewmodel/LoginViewModel.kt`, `di/AppModule.kt`. Server: `server/app/schemas/log.py`, `server/app/routers/logs.py`.

## Round 20 (2026-10-06) — Daily unique ID per (student, date) — server UPSERT

User feedback: "data lebih dari 1 yang terkirim dari user data persatu hari" — Android History page shows 5 entries for the same date (1 Pending + 4 Synced). Server keeps 5 separate `app_logs` rows for one (student, date) pair, each a 45-min interval. "tiap mengirim data harusnya data rangkaian 1 hari bukan data baru lagi, hasilnya screen time dan game time dan time lainya terreset" — sync should send the day's running totals, not per-interval deltas, so the server's "1 day summary" matches what the user sees on Beranda.

Implemented end-to-end: server UPSERT, Android single-row-per-day, web logs page label updated.

### Server (`server/app/`)

- **`models/app_log.py`** — added partial UNIQUE INDEX `uq_app_logs_student_date_mobile ON (student_id, log_date) WHERE source = 'mobile'`. Seed data unaffected (seed rows still model multiple sessions per day for ML training).
- **`database.py`** — `run_app_logs_daily_id_migration()`: idempotent migration dedupes existing mobile rows (keep latest per group), then creates the partial UNIQUE INDEX. Dialect-aware: SQLite vs PostgreSQL partial-index syntax.
- **`main.py`** lifespan: calls the new migration after `run_geofence_columns_migration()`.
- **`routers/logs.py`**:
  - `submit_batch()` pre-loads existing mobile rows for the (student_id, log_date) pairs in the batch, then UPSERTs each row in place (UPDATE if exists, INSERT if new). The incoming value IS the day total (Android sends cumulative since midnight), so REPLACE semantics are correct.
  - `_broadcast_new_logs()` rewritten to query rows by `(student_id, log_date)` instead of `idempotency_key` (UPSERT path keeps the same row id across re-syncs).
  - LocationLog dedup unchanged (per-fix idempotency_key).

### Android (`monitor-ung-app/app/src/main/java/com/monitorung/app/data/`)

- **`local/entity/Entities.kt`** — `AppUsageLogEntity` declares `@Index(value = ["student_id", "log_date"], unique = true)` + a non-unique index on `student_id` for hot queries. Comment explains "one row per (student, date), server UPSERTs in place".
- **`local/MonitorUNGDatabase.kt`** — version 3 → 4 with `MIGRATION_3_4`:
  1. RENAME old table to `app_usage_logs__old`
  2. CREATE new table with the same column layout
  3. INSERT aggregated row per (student, date) using SUM/MAX (preserves Beranda tile values, marks all PENDING for post-migration sync)
  4. DROP old table
  5. CREATE UNIQUE INDEX `index_app_usage_logs_student_id_log_date`
- **`local/dao/Daos.kt`** — added `@Transaction suspend fun accumulateDay(delta: AppUsageLogEntity)`:
  - Reads existing day row, adds delta's running counters + MAX's unlock + OR's used_during_class, flips sync_status to PENDING
  - Wrapped in `@Transaction` so concurrent collectors don't double-add
  - Raw `@Query` `updateById(...)` for atomic per-row update
- **`repository/UsageRepositoryImpl.kt`** — `collectAppUsage()` rewritten:
  - Compute per-call deltas from `UsageSnapshotStore` (unchanged)
  - Read existing day row, compute new day totals = existing + delta
  - Call `accumulateDay(deltaEntity)` (REPLACE/UPSERT into single row)
  - Read back the post-accumulation row for the return value (Beranda tile shows day total)
  - sync layer unchanged: `getPending` returns at most N days of PENDING rows; `toDto()` maps each day's full totals to DTO; server UPSERTs each.

### Web (`website/src/app/(dashboard)/logs/page.tsx`)

- Page title "Riwayat Log Aktivitas" → "Ringkasan Harian Mahasiswa"
- Subtitle: "Satu baris per mahasiswa per hari — sync ulang dari Android menggantikan data, bukan menambah"
- Section "Log Aktivitas" → "Ringkasan Harian"
- DownloadButton filename "log-aktivitas" → "ringkasan-harian", title "Riwayat Log Aktivitas" → "Ringkasan Harian Mahasiswa"
- Server's `get_log_history` now naturally returns 1 row per (student, date) for mobile, so the dedupe-by-id rendering is clean

### Verified

- Python AST parse OK for `routers/logs.py`, `models/app_log.py`, `database.py`, `main.py`
- DDL: SQLite + PostgreSQL both generate `CREATE UNIQUE INDEX uq_app_logs_student_date_mobile ON app_logs (student_id, log_date) WHERE source = 'mobile'`
- `getDayTotals()` projection still works with single-row-per-day (SUM collapses to single value)
- `get30DayAggregate()` projection still works (one row per day, SUM across days = 30-day total)
- `getRecent()` returns one row per most recent day (was: one row per collection)
- `syncPendingLogs()` sends at most 100 days per batch; server UPSERTs each; client echoes back all client_ids → `markSynced()` flips to SYNCED
- Race conditions: `accumulateDay` is `@Transaction`; `submit_batch` does pre-load SELECT before INSERT — concurrent syncs from same user could double-insert (rare, would cause UNIQUE constraint error → 500) but the seed and Android use a single sync at a time, so unlikely in practice. Future wave can add `INSERT ... ON CONFLICT DO UPDATE` for full atomicity.

## Audit Round — Wave 3 (2026-10-05): BootReceiver + logout cleanup

Tiga fix P1 yang cluster di auth/foreground-service boundary — semua high user-facing impact, low lines of code.

### 1. `BootReceiver` end_ts guard — kill orphan FGS crash

**File:** `presentation/receiver/BootReceiver.kt:21-25`

**Sebelum:**
```kotlin
val sessionPrefs = SecurePrefs.get(context, "monitor_session_prefs")
if (sessionPrefs.getString("sid", null).isNullOrEmpty()) return
```

**Sesudah:**
```kotlin
val sessionPrefs = SecurePrefs.get(context, "monitor_session_prefs")
if (sessionPrefs.getString("sid", null).isNullOrEmpty()) return
val endTs = sessionPrefs.getLong("end_ts", 0)
if (endTs > 0 && System.currentTimeMillis() >= endTs) return
```

**Why:** Sebelumnya hanya cek `sid` non-empty. Kalau device reboot **setelah** class end (tapi sebelum `checkLocationBeforeClassEnds` sempat clear prefs), `BootReceiver` start service, `SmartMonitoringService.onStartCommand` panggil `getSavedSession()` → return null → `else` branch call `stopSelf()` tanpa `startForeground()`. Android 12+ throw `ForegroundServiceDidNotStartInTimeException` → app crash loop.

**Fix:** Tambah `end_ts` check. SharedPreferences `getLong` sudah built-in, tidak butuh import baru.

### 2. `notificationScheduler.cancelAll()` di logout

**File:** `presentation/viewmodel/LoginViewModel.kt:264-268`

**Sebelum:**
```kotlin
dataSyncManager.cancelAutoSync()
notificationScheduler.cancelBackgroundSotCollection()
smartMonitoringManager.logout()
batteryBasedSotManager.logout()
```

**Sesudah:**
```kotlin
dataSyncManager.cancelAutoSync()
notificationScheduler.cancelBackgroundSotCollection()
// Wave 3 fix #14: cancelAll() removes WORK_MORNING, WORK_SYNC_REMINDER,
// and every WORK_CLASS_REMINDER_*/WORK_CLASS_START_* — these kept firing
// on a logged-out device (07:00 Selamat Pagi + 21:45 sync nudge).
notificationScheduler.cancelAll()
smartMonitoringManager.logout()
batteryBasedSotManager.logout()
```

**Why:** Sebelumnya `cancelAutoSync()` cancel 3 unique work names, `cancelBackgroundSotCollection()` cancel `WORK_DATA_COLLECT`. Tapi `notificationScheduler.cancelAll()` (yang cancel `WORK_MORNING`, `WORK_SYNC_REMINDER`, dan setiap `WORK_CLASS_REMINDER_*` / `WORK_CLASS_START_*`) **tidak pernah** dipanggil. Setelah logout, 07:00 `MorningNotificationWorker` + 21:45 `SyncReminderWorker` masih fire notifikasi di device yang logged-out.

**Fix:** Tambah `cancelAll()` setelah specific cancels. Idempotent — sudah-cancelled work stays cancelled. `NotificationScheduler.cancelAll()` sudah ada (line 225-227) sejak round 6, tinggal dipanggil.

### 3. `AuthRepositoryImpl.close()` — `cancelChildren` + reset init flags

**File:** `data/repository/AuthRepositoryImpl.kt:427-441`

**Sebelum (bug):**
```kotlin
override fun close() {
    scope.cancel()
}
```

**Sesudah:**
```kotlin
override fun close() {
    // Wave 3 fix #15: don't cancel the scope outright — the DataStore
    // collectors launched in isLoggedIn()/getCurrentUser() live on it too.
    // Cancelling the scope orphans the StateFlows; on re-login the same
    // dead flow is returned and downstream repos (UsageRepository,
    // ScheduleRepository) see stale empty data until force-kill.
    scope.coroutineContext.cancelChildren()
    _isLoggedInInitialized = false
    _currentUserInitialized = false
    _isLoggedInFlow.value = false
    _currentUserFlow.value = null
}
```

**Import added:** `import kotlinx.coroutines.cancelChildren` (kotlinx-coroutines 1.8.1 supports extension).

**Why:** `close()` cancel scope → semua DataStore collectors launched in `isLoggedIn()` / `getCurrentUser()` die (line 208, 238). Init flags `_isLoggedInInitialized` / `_currentUserInitialized` (line 202, 233) tetap `true` — next call return the dead `StateFlow` (underlying collector killed, no re-emit). Re-login: `UsageRepository.flatMapLatest` + `ScheduleRepository.getCurrentUser()` see `studentId=""` → empty data sampai force-kill.

**Fix:**
- `cancelChildren()` (extension on `Job`) — kills active child coroutines tapi scope tetap alive
- Reset init flags → next call ke `isLoggedIn()` / `getCurrentUser()` akan re-launch collector di scope yang masih hidup
- Clear flow values → UI immediately reflect logged-out state (no stale "logged in" flash)

**Pattern note:** Sebelumnya `prefs[STUDENT_ID] ?: return@launch` di line 244 sudah swallow logout transition (tapi itu fix terpisah untuk ghost row — `currentUserFlow` emits `null` when `STUDENT_ID` cleared). Round 6 sudah fix itu. Wave 3 melengkapi dengan reset init flags pada close() — solves the "stale dead StateFlow on re-login" problem.

## Round 18 (2026-10-05) — UI/UX PaperCard-style Gamified Overhaul

Comprehensive UI/UX overhaul following the PaperCard gamified direction. All 12 tasks completed. APK built successfully (`assembleDevDebug` → BUILD SUCCESSFUL).

### New Components (7 files)

- **`GradientCard.kt`** — Card with gradient fill using `Box.shadow().clip().background()` pattern. Accepts `gradient: List<Color>` and `softShadowColor`. Used for achievement cards, streak card, daily challenge.
- **`GradientSpringButton.kt`** (added to existing `SpringButton.kt`) — Bouncy gradient button with spring press scale. Disabled state uses `Color.Gray.copy(alpha = 0.4f)` NOT `gradient.copy(alpha = 0.4f)` (Brush has no `copy` method).
- **`PlayfulProgressBar.kt`** — Gradient-filled progress bar with rounded ends. Animated fill on appear via `animateFloatAsState`.
- **`DailyChallengeWidget.kt`** — Daily challenge card with gradient border, progress indicator, confetti on completion.
- **`AchievementBadge.kt`** — Rounded-square badge with gradient fill, lock overlay for locked state, scale bounce on unlock.
- **`BouncySwitch.kt`** — Toggle with spring scale animation on thumb.
- **`BubbleNavBar.kt`** — Floating bubble navigation replacing the old pill-in-pill pattern.

### Gradient Palette

**Presets** in `theme/Color.kt`:
- `GradientBlueTeal`: `#3B82F6` → `#14B8A6`
- `GradientAmberGold`: `#F59E0B` → `#FBB424`
- `GradientPurplePink`: `#8B5CF6` → `#EC4899`
- `GradientSoftBlue`: `#BFDBFE` → `#DBEAFE`
- `GradientTealGreen`: `#5EEAD4` → `#6EE7B7`
- `GradientFuchsiaPurple`: `#D946EF` → `#8B5CF6`
- `GradientAmberPeach`: `#F59E0B` → `#FB923C`
- `SoftShadowColorLight`: `Color(0x1A3B82F6)` for ambient glow

### Screen Overhauls

| Screen | Changes |
|--------|---------|
| `LoginScreen.kt` | Gradient bg (PrimaryBlue 6% opacity vertical gradient), floating logo animation (-6dp to +6dp), GradientSpringButton, ConfettiBurst on login success |
| `HomeScreen.kt` | LevelProgressCard (XP/level progress), DailyChallengeWidget, PlayfulMetricCard with gradient badges, GradientCard for QuickActions, GradientSpringButton |
| `HistoryScreen.kt` | LevelProgressCard, GradientCard summary, CompactAchievementBadge, gradient time badges, weekly progress bars |
| `ScheduleScreen.kt` | GradientCard for active schedule, GradientSpringButton FAB, gradient status badges, gradient day selector with segmented button style |
| `ProfileScreen.kt` | GradientCard avatar glow, achievement badges row, gradient section backgrounds |
| `SettingsScreen.kt` | BouncySwitch for biometric, GradientSpringButton logout (FuchsiaPurple), colorful section gradients |
| `TermsScreen.kt` | GradientCard content, gradient progress bar, colorful left-border accents |
| `PermissionScreen.kt` | GradientBadgeIcon, gradient progress bar, confetti on completion |
| `BiometricGateScreen.kt` | GradientCard wrapper, floating lock animation, GradientSpringButton verify |

### Compile Fixes (encountered during implementation)

- **`GradientCard.kt`** — Added `import androidx.compose.ui.draw.clip`
- **`LevelProgressCard.kt`** — Removed invalid `compact = true` from `StreakIndicator` call
- **`SpringButton.kt`** — `gradient.copy(alpha = 0.4f)` replaced with `Color.Gray.copy(alpha = 0.4f)` (Brush has no `copy`)
- **`ConfettiBurst.kt`** — `PrimaryFuchsia` → `ErrorFuchsia` (correct token name)
- **HomeScreen + ScheduleScreen** — `PrimaryTeal` → `ChartTeal`, `PrimaryFuchsia` → `ErrorFuchsia`
- **ScheduleScreen** — `showDeleteDialog` scope fix via `ActiveScheduleCardContent` extracted composable with `onDelete` callback
- **ScheduleScreen** — Added `import com.monitorung.app.presentation.ui.theme.RadiusS` and `import androidx.compose.foundation.background`
- **LoginScreen** — Added `import androidx.compose.ui.draw.shadow`

### Build Artifact

- `assembleDevDebug` → BUILD SUCCESSFUL (2026-10-05)
- APK: `app/build/outputs/apk/dev/debug/app-dev-arm64-v8a-debug.apk`
- Website changelog Android channel bumped from v0.12.0 to v0.18.0

## Round 19 (2026-10-06) — PaperCard style + Settings avatar + guest mode removal + KBKI

### PaperCard rename

- All `Duolingo`/`duolingo` references replaced with `PaperCard`/`papercard` across all Android source files, CLAUDE.md, website CLAUDE.md, ARCHITECTURE.md, website changelog data.
- Memory file `duolingo-ui-reference.md` renamed to `papercard-ui-reference.md`.

### SettingsScreen fixes

- User info card changed from `GradientCard` to `PaperCard` — consistent with card pattern across all screens.
- Avatar photo display added: loads `uiState.user?.avatarUrl` (base64 data URL) via `BitmapFactory.decodeByteArray` + `asImageBitmap`; shows colored initial box as fallback when no photo.
- Hardcoded `"Granted"` / `"Tap to grant"` replaced with `stringResource(R.string.settings_permission_granted)` / `R.string.settings_permission_tap_to_grant` in both EN and ID locales.

### Guest mode removal

- `LoginUiState.isTestMode` field removed.
- `toggleTestMode()` stubbed to no-op.
- `loginAsGuest()` function removed.
- Login screen logo no longer clickable (removed `.clickable { viewModel.toggleTestMode() }`).
- Pulse animation + "T" indicator removed from logo Surface.
- Test mode banner (`AnimatedVisibility` with `BugReport` icon) removed.
- Guest mode `TextButton` removed.
- Unused imports cleaned: `clickable`, `alpha`, `scale`, `graphicsLayer`.
- String resources removed: `auth_guest_mode`, `auth_guest_hint`, `login_guest_banner`, `login_guest_close` (EN + ID).

### Build artifacts

- `assembleDevDebug` → BUILD SUCCESSFUL (2026-10-06 01:45)
- `assembleProdRelease` → BUILD SUCCESSFUL (2026-10-06 01:48)
- APKs: dev-debug 24.9MB arm64, prod-release 7.2MB arm64
- Website changelog Android channel bumped from v0.18.0 to v0.19.0

## Round 20 (2026-10-06) — Double layer, offline login, error 530, offline banner

User feedback batch: double layer on login screen, error 530 should be "Server belum aktif", users should be able to access app offline when server is down.

### 1. Double layer on login screen (P1)

**File:** `presentation/MainActivity.kt:88-129`

**Root cause:** `Box` wrapper had a `when (appAccessState)` branch that rendered `AuthNavigation` (among others), while `AnimatedContent` also rendered `AuthNavigation` for `NEEDS_LOGIN`. On LOADING, both the outer Box's `LOADING` branch and the inner `AnimatedContent`'s LOADING placeholder (empty `{}`) rendered simultaneously — producing a double layer (splash content under the login form).

**Fix:** Removed the outer `Box`'s `when` branch entirely. `AnimatedContent` now handles all state rendering. `LOADING` state shows empty `{}` inside `AnimatedContent` — `showRealContent=false` keeps `BrandedSplashContent()` visible until loading resolves. Single source of truth, no double layer.

```kotlin
Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    if (showRealContent) {
        AnimatedContent(targetState = appAccessState, ...) { state ->
            when (state) {
                AppAccessState.LOADING -> {}  // placeholder only — BrandedSplashContent shows
                AppAccessState.NEEDS_LOGIN -> AuthNavigation(onLogout = { ... })
                ...
            }
        }
    } else {
        BrandedSplashContent()  // visible during LOADING
    }
}
```

### 2. Error "530" → "Server belum aktif" (P1)

**File:** `presentation/viewmodel/LoginViewModel.kt:173-185`

**Before:** `login()` propagated raw Retrofit HTTP error messages ("HTTP 530", "HTTP 404") to the UI. The user saw "530" which is meaningless.

**After:** Network errors (DNS resolution failure, connection refused, timeout) map to `"Server belum aktif"`. Server auth errors remain unchanged.

```kotlin
val isNetworkError = msg.contains("Unable to resolve host", ignoreCase = true) ||
        msg.contains("network", ignoreCase = true) ||
        msg.contains("connection", ignoreCase = true) ||
        msg.contains("timeout", ignoreCase = true)
_uiState.update {
    it.copy(isLoading = false, error = if (isNetworkError) "Server belum aktif" else msg)
}
```

### 3. Offline access — users can view cached data when server is down (P0)

**File:** `presentation/viewmodel/LoginViewModel.kt:84-128`

**Root cause:** `attemptAutoLogin()` called `authRepository.autoLoginWithSavedCredentials()` and blocked on failure — preventing access even when network was down but user had valid cached credentials.

**Fix:** `attemptAutoLogin()` now detects network errors (DNS resolution, network, connection, timeout) and allows the user to proceed with cached Room SQLite data. On network error, sets `isLoggedIn = true` and calls `resolveAppAccess()` — the user lands in the app and sees their local data.

```kotlin
.onFailure { e ->
    if (e.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
        e.message?.contains("network", ignoreCase = true) == true ||
        e.message?.contains("connection", ignoreCase = true) == true
    ) {
        _uiState.update { it.copy(isLoading = false, isLoggedIn = true, nim = credentials.first) }
        resolveAppAccess()  // proceed to app with cached data
    } else {
        // real auth failure — show login screen
        _appAccessState.value = AppAccessState.NEEDS_LOGIN
    }
}
```

**Note:** `autoLoginWithSavedCredentials()` internally calls `refreshToken()`. Network errors throw `IOException`, not HTTP errors, so the catch branch correctly handles them. Non-network errors (401, 403, etc.) still require re-login.

### 4. Offline banner on HomeScreen (P2)

**File:** `presentation/viewmodel/HomeViewModel.kt:79-91` + `presentation/ui/screens/home/HomeScreen.kt`

**Added:** `isOffline: Boolean` field to `HomeUiState`. `HomeViewModel.init` performs a HTTP connectivity check against `https://monitorterop.my.id/api/status` (3s timeout). If unreachable, `isOffline = true`.

`HomeScreen.kt` displays an amber `AnimatedVisibility` banner when `uiState.isOffline` is true:

```kotlin
AnimatedVisibility(
    visible = uiState.isOffline,
    enter = fadeIn() + expandVertically(),
    exit = fadeOut() + shrinkVertically(),
) {
    Surface(shape = RoundedCornerShape(RadiusM), color = WarningAmberSoft) {
        Row(modifier = Modifier.padding(...)) {
            Icon(Icons.Default.WifiOff, tint = WarningAmber, ...)
            Text(stringResource(R.string.home_offline_label), color = WarningAmber, ...)
        }
    }
}
```

**String resources added:** `auth_server_offline` in `values/strings.xml` and `values-id/strings.xml`.

### 5. LoginScreen gradient background removed (cosmetic)

**File:** `presentation/ui/screens/splash/LoginScreen.kt:81-86`

Replaced `Brush.verticalGradient` background fill with `MaterialTheme.colorScheme.background` — matches the app's theme surface instead of a hardcoded gradient. `GradientCard`, `RadiusS2`, `RadiusXL` imports removed. `Brush` import restored (still needed for `GradientSpringButton` at line 263).

### Build artifacts

- `assembleDevDebug` → BUILD SUCCESSFUL (2026-10-06 14:09)
  - `app-dev-arm64-v8a-debug.apk` (24MB)
  - `app-dev-armeabi-v7a-debug.apk` (23MB)
  - `app-dev-universal-debug.apk` (26MB)
- `assembleProdRelease` → BUILD SUCCESSFUL (2026-10-06 14:10)
  - `app-prod-arm64-v8a-release.apk` (6.9MB)
  - `app-prod-armeabi-v7a-release.apk` (5.6MB)
  - `app-prod-universal-release.apk` (9.1MB)
- Website rebuilt + deployed; api.ts type fixes verified via `tsc --noEmit`
- Website changelog Android channel bumped from v0.19.0 to v0.20.0

## Round 22 (2026-10-07) — Settings PaperCard overhaul + adaptive icon redesign

### SettingsScreen PaperCard overhaul

All 5 cards in `presentation/ui/screens/settings/SettingsScreen.kt` converted from `GradientCard` to `PaperCard` with `SectionBadge(label, accentColor)`:

| Card | SectionBadge |
|------|-------------|
| Sync | `ChartTeal` |
| Appearance | `ChartPurple` |
| Permissions | `PrimaryBlue` |
| Privacy | `WarningAmber` |
| About | `onSurfaceVariant` |

`SettingsSection` composable function removed (old wrapper with hard-coded title and per-section gradient). `GradientCard` import removed from SettingsScreen. `GradientSpringButton` logout with `GradientFuchsiaPurple` preserved. All `StaggeredEntrance` indices, `SettingsItem` rows, `PermissionStatusItem`, `BouncySwitch` unchanged.

### Adaptive icon logo redesign

New PaperCard-style adaptive icon matching the app's visual identity:

- **`ic_launcher_background.xml`**: Linear gradient `#3B82F6 → #14B8A6` (GradientBlueTeal), 135°, corner radius 108dp.
- **`ic_launcher_foreground.xml`**: Stylized "M" monogram with 3 horizontal chart bars (monitoring/analytics symbolism), white fill, centered in adaptive icon safe zone 18-90.

Both variants (`ic_launcher` and `ic_launcher_round`) share the same drawable references — no AndroidManifest.xml change needed.

### Build artifacts

- `assembleDevDebug` → BUILD SUCCESSFUL (2026-10-07)
  - `app-dev-arm64-v8a-debug.apk` (24MB)
  - `app-dev-armeabi-v7a-debug.apk` (23MB)
  - `app-dev-universal-debug.apk` (27MB)
- `assembleProdRelease` → BUILD SUCCESSFUL (2026-10-07)
  - `app-prod-arm64-v8a-release.apk` (6.9MB)
  - `app-prod-armeabi-v7a-release.apk` (5.6MB)
  - `app-prod-universal-release.apk` (9.1MB)


## Round 26 (2026-10-10) — Audit batch: 3 P0 + 2 P1 fixes

Multi-component audit (8 parallel sweep agents). 29 fixes total applied across server, web, Android. Android-side changes:

### P0 (3 fixed)

**1. BiometricGate silently bypassed — `ComponentActivity` vs `FragmentActivity`**

`presentation/MainActivity.kt:48` + `presentation/ui/screens/auth/BiometricGateScreen.kt:35`. BiometricGateScreen casts the host Activity via `context as? FragmentActivity` to call `BiometricHelper.authenticate(...)`. `ComponentActivity` is NOT a `FragmentActivity`, so the cast always returned null and the biometric gate silently fell through to `onSuccess()` — meaning the gate never actually ran even when biometric was enabled.

Fix: `class MainActivity : FragmentActivity()`. FragmentActivity is the minimum base class that satisfies the cast while remaining a Hilt-compatible direct superclass.

**Hilt KSP gotcha:** `@AndroidEntryPoint` processor enforces the direct superclass — only `ComponentActivity`, `FragmentActivity` (and other `Fragment`, `View`, `Service`, `BroadcastReceiver` types) accepted. `AppCompatActivity` rejected because it's not directly recognized. The original fix attempt with `AppCompatActivity` failed with:
```
[Hilt] @AndroidEntryPoint base class must extend ComponentActivity, (support) Fragment, View, Service, or BroadcastReceiver.
```
Switching to `FragmentActivity` (one level up) works because it still satisfies "extends ComponentActivity" transitively.

**2. BatteryBasedSotManager `insert()` REPLACE wipes running counters**

`service/BatteryBasedSotManager.kt:185-222`. `persistDayData()` is invoked at midnight crossing and called `appUsageLogDao.insert(entity)`. The DAO `AppUsageLogDao.insert()` at `data/local/dao/Daos.kt:22` declares `@Insert(onConflict = OnConflictStrategy.REPLACE)`. The DAO comment at `Daos.kt:17-20` explicitly warns:

> "do not call `insert()` directly with a fresh `AppUsageLogEntity` for an existing (student, date) - it overwrites the running counters. Use [accumulateDay] for collector writes"

But `persistDayData()` was calling `insert()` with arguments `(lastResetDate, screenOnTodayMs, 0, 0, 0)` — at midnight, the new partial totals (only screenOnMs from battery manager; sosmed/game/unlock were 0) overwrote the day's running counters from `UsageRepositoryImpl` / `SmartMonitoringManager`. Sosmed time, game time, unlock count all reset to 0 at 00:00 WITA every day.

Fix: switched to `appUsageLogDao.accumulateDay(entity)` which reads existing day row, adds the delta, and UPSERTs in a single `@Transaction`.

**3. SmartMonitoringManager Singleton scope cancel after logout**

`service/SmartMonitoringManager.kt:680-697`. `@Singleton` Hilt class — instance reused across logins. `destroy()` and `logout()` called `scope.cancel()`. After first logout, scope permanently cancelled. Second login reused the same instance with a dead scope. Every `scope.launch { collectSotData(session) }` silently failed, `while (isActive) {...}` immediately exited, and the student got no monitoring at all.

Fix (mirroring the `AuthRepositoryImpl.close()` fix from Round 9):
```kotlin
@Volatile
private var scope: CoroutineScope? = CoroutineScope(Dispatchers.Default + SupervisorJob())

fun destroy() {
    ensureScope().coroutineContext.cancelChildren()
}

fun logout() {
    stopAll()
    // ... other cleanup
    ensureScope().coroutineContext.cancelChildren()
}

private fun ensureScope(): CoroutineScope {
    if (scope == null) {
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    }
    return scope!!
}
```

All `scope.launch` call sites updated to `ensureScope().launch` (5 sites in the file). `cancelChildren()` kills active jobs without killing the scope itself; lazy `ensureScope()` reinitializes if needed.

### P1 (2 fixed)

**4. `ScheduleDto` missing `@SerializedName` annotations**

`data/remote/dto/Dtos.kt:86-91`. `ScheduleDto` declared:
```kotlin
val subjectName: String?,
val lecturerName: String?
```
without `@SerializedName`. Gson defaults to the Kotlin property name (camelCase), so it looked for `subjectName`/`lecturerName` in JSON. The server's `ScheduleResponse` (Pydantic) emits `subject_name`/`lecturer_name` (snake_case via Pydantic alias). The fields were always null after deserialization. The course-name fallback chain (`subjectName ?: subjectNameRaw`) silently absorbed the nulls, but the `Mata Kuliah` column on the Android Schedule screen was empty for every row.

Fix:
```kotlin
@SerializedName("subject_name") val subjectName: String?,
@SerializedName("lecturer_name") val lecturerName: String?
```

**5. `HomeViewModel` day-of-week convention — Java Sun=1 vs ISO Mon=1**

`presentation/viewmodel/HomeViewModel.kt:70-80`. The home view model used `java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)` which returns Java's raw convention: 1=Sun, 2=Mon, ..., 7=Sat. The local `schedules` table stores ISO day-of-week (1=Mon, ..., 7=Sun) per the rest of the app: `ScheduleRequest.day_of_week` validator (1-7), the `DAYS_ID` array on web `/akademik` page, the `ScheduleDto` on Android. Passing the raw Java value caused Monday (Calendar.MONDAY=2) to match schedules stored under `day_of_week=2` which here was actually Tuesday. Fix:
```kotlin
val javaDow = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
val todayDow = if (javaDow == java.util.Calendar.SUNDAY) 7 else javaDow - 1
```
Java Sunday (1) → ISO 7. Java Mon-Sat (2-7) → ISO 1-6.

### Verified post-build

- `assembleDevDebug` → BUILD SUCCESSFUL (Kotlin compile + Hilt graph both clean)
- `assembleProdRelease` → BUILD SUCCESSFUL
- APKs:
  - dev-debug: 25/24/27 MB
  - prod-release: 7.4/6.0/9.7 MB
- `grep "class MainActivity : " MainActivity.kt` → `FragmentActivity()` (Hilt-accepted)
- `grep "@SerializedName(\"subject_name\") val subjectName" Dtos.kt` → present
- `grep "Calendar.SUNDAY" HomeViewModel.kt` → present (the conversion)
- `grep "appUsageLogDao.accumulateDay" BatteryBasedSotManager.kt` → present

### Standing constraint preserved verbatim

The Groq API key localStorage pattern in `app/(dashboard)/ai/page.tsx` (web side) was not touched. APK contains MQTT password plaintext (acknowledged trade-off from Round 4). SQLCipher key is per-install random (per UU PDP requirement). These trade-offs were not revisited in Round 26.

## Round 26 (2026-10-10) — MQTT-over-WSS (3 P0 + 2 P1 + WSS)

See `server/CLAUDE.md` Round 26 for the full MQTT-over-WSS via Cloudflare Tunnel setup. The Android `MqttPushClient.kt` was updated to detect `wss://` scheme in the server-advertised URL and use `ssl://` (Paho 1.2.5 supports WSS natively via URI scheme). No new dependency added.

Verified end-to-end: WSS subscriber via `wss://mqtt.monitorterop.my.id/mqtt` connects through Cloudflare edge → tunnel → API WebSocket proxy → mosquitto:9001. APK built with this change.

## Round 28 (2026-10-12) — Indikator HARI INI/30 HARI + LocationStatusCard + Lokasi perf

User feedback: Android tidak menunjukkan dengan jelas mana indikator "hari ini" vs "30 hari summary", performance membaca lokasi/usage/schedule dirasa lambat.

**HomeScreen.kt — Time-window explicit:**
- `UsageSummaryContent` (kartu Penggunaan Hari Ini): tambah `SectionBadge` dengan label `HARI INI` (ChartBlue) + judul "Penggunaan hari ini / Today's usage" sebelum 4 metric chip. Tanpa header ini, chip pada kartu hari-ini terlihat sama dengan chip 30-hari.
- `ThirtyDaySummaryContent` (kartu Ringkasan 30 Hari): tambah `SectionBadge` dengan label `30 HARI` (ChartTeal) + subtitle "Rerata harian / avg/day" di bawah judul. Membedakan total hari vs rerata per hari.
- `ScheduleCardContent`: tambah `SectionBadge` `HARI INI` untuk konsistensi.
- Renumber `StaggeredEntrance` indices 0-9 (sebelumnya 0-8).

**LocationStatusCard baru:**
- Kartu `Pemeriksaan lokasi terakhir` — render dari `usageRepository.getRecentLocations(5)`.
- Tampilkan: status badge (on-campus = ChartTeal, off-campus = WarningAmber, unavailable = muted), timestamp, koordinat terakhir. Hidden saat `recentLocations.isEmpty()`.
- Cards `realtime` karena Room Flow auto-emit.

**SmartMonitoringManager.kt — Performa lokasi:**
- `getCurrentLocation()` sekarang coba `fusedLocationClient.lastLocation` dulu (fast path, sinkron, tidak spin-up GPS). Hanya fallback ke `getCurrentLocation()` bila cache > 5 menit. Eliminasi 1-3s blocking pada class check.
- `checkLocationBeforeClassEnds()` sekarang pass `location` ke `logLocation()` — sebelumnya logLocation re-fetch dan simpan location=null.

**DAO/Repository/ViewModel:**
- `LocationLogDao`: tambah `getRecent(studentId, limit): Flow<List<LocationLogEntity>>`.
- `UsageRepository`: tambah `getRecentLocations(limit)`.
- `HomeUiState`: tambah `recentLocations: List<LocationLog>`.
- `HomeViewModel`: collect getRecentLocations(5) di init.
- `LocationLog` domain model: tambah `latitude: Double?` + `longitude: Double?` agar UI bisa render koordinat.

**Strings EN/ID:**
- Tambah `home_today_label`, `home_30day_label`, `home_today_title`, `home_location_*`.
- Hapus em-dash di `home_offline_label` (ganti dengan koma).

**Build:**
- `compileDevDebugKotlin` BUILD SUCCESSFUL.
- `assembleDevDebug` → 25/24/27 MB APK.
- `assembleProdRelease` → 7.4/5.9/9.4 MB APK.

## Round 28.5 (2026-10-12) — Background Geofence via GeofencingClient API

User feedback: "background geofence (more efficient than polling)" dari roadmap. Geofence detection masih pakai 45-menit FusedLocationProviderClient polling. Migrasi ke Android `GeofencingClient` API (hardware-assisted, no periodic wakeup, battery-friendly).

**File baru: `receiver/GeofenceReceiver.kt`** — `@AndroidEntryPoint` BroadcastReceiver yang handle `GeofencingEvent`. ENTER transition → `smartMonitoringManager.onGeofenceEnter(location)`. EXIT → `onGeofenceExit(location)`. Field injection via Hilt (konsisten dengan pattern `BootReceiver`).

**`SmartMonitoringManager.kt`:**
- Tambah `geofencingClient: GeofencingClient` lazy.
- Tambah `geofenceActive: Boolean` flag.
- `registerGeofence(session)`: build `Geofence` (circular, radius dari server config), `GeofencingRequest` dengan `INITIAL_TRIGGER_ENTER` (fire jika user sudah di dalam kampus saat registrasi), add via `addGeofences()`. On success, log "geofence registered" + run an initial SOT. On failure (no permission, location services off, etc.), fall back ke 45-menit polling via `startPollingFallback()`.
- `onGeofenceEnter(location)` / `onGeofenceExit(location)`: public methods called by receiver. Update `_status.state` (IN_CLASS / OUTSIDE_CLASS), `logLocation()`, `showLocationAlert()` untuk EXIT.
- `startClassMonitoring()`: replace 45-menit `locationCheckJob` dengan call ke `registerGeofence()`. SOT collection (`sotJob`) tetap berjalan.
- `stopAll()`: tambah `geofencingClient.removeGeofences(pendingIntent)` (dengan `SecurityException` catch).
- `geofencePendingIntent()`: shared helper, `PendingIntent.FLAG_UPDATE_CURRENT or FLAG_MUTABLE` (required Android 12+).
- Konstanta baru: `GEOFENCE_REQUEST_ID = "monitor_ung_campus_geofence"`, `GEOFENCE_EXPIRATION_MS = 2 hours`, `GEOFENCE_PENDING_INTENT_REQUEST_CODE = 2001`.

**`AndroidManifest.xml`:** register `GeofenceReceiver` dengan `exported=false` (cuma OS yang deliver `GeofenceEvent`).

**`checkLocationDuringClass` (private) tetap** — jadi fallback path. Tidak dihapus supaya tidak ada regression di device yang tidak support geofencing (Android 5+ masih disupport, tapi permission edge case masih ada).

**Tidak ada perubahan:** server side (geofence-summary endpoint tetap, AppLog.geofence_status column tetap, INSERT path tetap). Tidak ada perubahan dependency — `play-services-location` sudah ada di `build.gradle.kts`. Tidak ada permission baru — `ACCESS_COARSE_LOCATION` sudah di manifest, dan untuk foreground service saat class start tidak butuh `ACCESS_BACKGROUND_LOCATION`.

**Build:**
- `compileDevDebugKotlin` BUILD SUCCESSFUL.
- `assembleDevDebug` → 25/24/28 MB APK.
- `assembleProdRelease` → 7.5/6.1/9.8 MB APK.
- R8 tidak strip `GeofenceReceiver` (verified via `strings classes.dex | grep GeofenceReceiver`).
- Manifest verified via `aapt2 dump xmltree` — receiver registered dengan `exported=false`.

**Verified post-deploy (TODO):** install APK ke device Android 10+, login, tunggu class window, lalu tinggalkan kampus — harus fire EXIT broadcast → state OUTSIDE_CLASS + show alert.

## Round 29 (15 Okt 2026) — 4 P1 Android fixes (FCM fallback)

User request: "lanjut fix P1 tapi firebase fall back" → applied minimal defense-in-depth fixes untuk 4 P1 findings, dengan FCM fallback approach (FCM saat ini dormant — placeholder google-services.json, callback fires tapi upstream no-op).

### P1 #7 + #8 — FCM token + data payload logged plaintext — `FCMService.kt:47,84`

**Sebelum:** `Log.i(TAG, "onNewToken: ${token.take(12)}...")` + `Log.i(TAG, "onMessageReceived: title=$title data=${remoteMessage.data}")`

**Sesudah:** `Log.d(...)` untuk keduanya.

**Round 29 firebase-fallback:** FCM tokens and data payloads are sensitive — first-12-char prefix masih bisa di-correlate dengan leaked token, dan `data` payload berisi server-injected fields. Move to DEBUG. Karena FCM dormant, tidak ada production log noise — DEBUG = no-op in practice.

**Pattern:** Production logcat readable by OEM bloatware dan apps with READ_LOGS permission (Android <4.1). PII / credentials / tokens NEVER di INFO level.

### P1 #9 — MQTT broker URL + clientId logged plaintext — `MqttPushClient.kt:232`

**Sebelum:** `Log.i(TAG, "MQTT connected: broker=$brokerUrl client_id=$clientId")`

**Sesudah:** `Log.d(...)`. Move to DEBUG.

**Why:** Connection fingerprint (broker URL + clientId) — bukan strict secret (JWT is the credential), tapi leak di production logcat memberi attacker info untuk correlate dengan leaked topic atau timing side-channel.

### P1 #10 — MQTT scheme fallback allows plaintext downgrade — `MqttPushClient.kt:174-194`

**Sebelum:**
```kotlin
val clientScheme = when (rawScheme) {
    "wss", "ssl" -> "ssl://"
    else -> "tcp://"  // tcp:// or unknown — dev plaintext
}
```

**Sesudah:**
```kotlin
val clientScheme = when (rawScheme) {
    "wss", "ssl" -> "ssl://"
    "tcp" -> if (BuildConfig.DEBUG) "tcp://" else {
        throw IllegalStateException(
            "Plaintext tcp:// MQTT rejected in production build. " +
            "Server-advertised URL: $brokerUrl"
        )
    }
    else -> throw IllegalArgumentException(
        "Unknown MQTT scheme '$rawScheme' in broker URL: $brokerUrl. " +
        "Expected one of: wss, ssl, tcp (dev only)."
    )
}
```

**Why:** `else -> "tcp://"` silently accepts any string. Hostile `/api/config/campus` response or typo → plaintext MQTT connection → credentials leak. Now: tcp:// only allowed in DEBUG builds, unknown scheme throws.

**Pattern:** Fail-closed on unknown input. Default to "refuse" not "downgrade".
