package com.monitorung.app.presentation.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import com.monitorung.app.R
import com.monitorung.app.domain.model.AppUsageLog
import com.monitorung.app.domain.model.Schedule
import com.monitorung.app.presentation.ui.components.*
import com.monitorung.app.presentation.ui.theme.*
import com.monitorung.app.presentation.viewmodel.HomeViewModel
import com.monitorung.app.util.formatDuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.collectUsage() }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                viewModel.refreshServerStatus()
                delay(30_000)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header — title only (theme switcher moved to Settings)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Offline warning — shown when server is unreachable (user accessed via
        // cached credentials when server was down). User can still view local data.
        AnimatedVisibility(
            visible = uiState.isOffline,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(RadiusM),
                color = WarningAmberSoft,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.home_offline_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningAmber,
                    )
                }
            }
        }

        // Level progress card — derived from screen time milestones
        StaggeredEntrance(index = 0, modifier = Modifier.fillMaxWidth()) {
            val streak = computeStreak(uiState.recentActivity)
            val level = computeLevel(uiState.todayUsage?.totalScreenTimeMinutes ?: 0)
            val xp = computeXp(uiState.todayUsage?.totalScreenTimeMinutes ?: 0)
            val xpMax = xpPerLevel(level)
            LevelProgressCard(
                level = level,
                currentXp = xp,
                xpToNextLevel = xpMax,
                streakDays = streak,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Schedule card — today's active / upcoming class
        StaggeredEntrance(index = 1, modifier = Modifier.fillMaxWidth()) {
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
                ScheduleCardContent(schedules = uiState.todaySchedules)
            }
        }

        // Last location check — makes the geofence pipeline visible
        // (was previously hidden behind SettingsScreen). Users can now
        // see whether the device was inside the campus on the last probe.
        if (uiState.recentLocations.isNotEmpty() || uiState.isLoading) {
            StaggeredEntrance(index = 2, modifier = Modifier.fillMaxWidth()) {
                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
                    LocationStatusCard(
                        locations = uiState.recentLocations,
                    )
                }
            }
        }

        // Daily challenge widget
        StaggeredEntrance(index = 3, modifier = Modifier.fillMaxWidth()) {
            DailyChallengeWidget(
                challenge = DailyChallengeType.REDUCE_SCREEN_TIME,
                progress = computeChallengeProgress(uiState.todayUsage?.totalScreenTimeMinutes ?: 0),
                isCompleted = (uiState.todayUsage?.totalScreenTimeMinutes ?: 999) < 60,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Screen time card — today's 4 metrics in a dedicated card
        StaggeredEntrance(index = 4, modifier = Modifier.fillMaxWidth()) {
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
                when {
                    uiState.isLoading && uiState.todayUsage == null -> {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SkeletonCard(modifier = Modifier.fillMaxWidth(), baseColor = null)
                            SkeletonCard(modifier = Modifier.fillMaxWidth(0.7f), baseColor = null)
                        }
                    }
                    uiState.todayUsage != null -> {
                        UsageSummaryContent(usage = uiState.todayUsage!!)
                    }
                    else -> {
                        EmptyUsageContent()
                    }
                }
            }
        }

        // Class-time split: derived locally from usage events and geofence checks.
        StaggeredEntrance(index = 5, modifier = Modifier.fillMaxWidth()) {
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
                ClassUsageContent(uiState.dailyUsageBreakdown)
            }
        }

        // 30-day summary card — longer-term trends
        StaggeredEntrance(index = 6, modifier = Modifier.fillMaxWidth()) {
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
                ThirtyDaySummaryContent(aggregate = uiState.thirtyDayAggregate)
            }
        }

        // Pending entries card — prominent sync queue indicator
        StaggeredEntrance(index = 6, modifier = Modifier.fillMaxWidth()) {
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
                PendingEntriesContent(
                    pendingCount = uiState.syncState.pendingCount,
                    lastSync = uiState.syncState.lastSyncTimestamp,
                    onSyncClick = { viewModel.syncNow() },
                    isSyncing = uiState.isLoading,
                )
            }
        }

        // Streak indicator
        StaggeredEntrance(index = 7, modifier = Modifier.fillMaxWidth()) {
            StreakIndicator(streakDays = computeStreak(uiState.recentActivity))
        }

        // Aktivitas 3 hari terakhir
        if (uiState.recentActivity.isNotEmpty()) {
            StaggeredEntrance(index = 8, modifier = Modifier.fillMaxWidth()) {
                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
                    RecentActivityContent(recentActivity = uiState.recentActivity)
                }
            }
        }

        // Quick actions
        StaggeredEntrance(index = 9, modifier = Modifier.fillMaxWidth()) {
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
                QuickActionsContent(
                    onCollectClick = { viewModel.collectUsage() },
                    isCollecting = uiState.isLoading
                )
            }
        }
    }
}

// ─── Level / XP helpers ──────────────────────────────────────────────────────

/** XP earned today from screen time. 1 XP per minute, max 120 XP/day. */
private fun computeXp(screenMinutes: Int): Int = minOf(screenMinutes, 120)

/** XP needed to fill one level. */
private fun xpPerLevel(level: Int): Int = 100 + (level - 1) * 20

/** Derive level from cumulative XP today. Each level needs more XP. */
private fun computeLevel(screenMinutes: Int): Int {
    val xp = computeXp(screenMinutes)
    var lvl = 1
    var total = xpPerLevel(1)
    while (xp >= total) {
        lvl++
        total += xpPerLevel(lvl)
    }
    return lvl
}

/** Mock challenge progress: <60 min = 100%, <90 = 50%, else 0%. */
private fun computeChallengeProgress(screenMinutes: Int): Float {
    return when {
        screenMinutes < 30 -> 1f
        screenMinutes < 60 -> 0.75f
        screenMinutes < 90 -> 0.5f
        screenMinutes < 120 -> 0.25f
        else -> 0f
    }
}

// ─── Content composables ──────────────────────────────────────────────────────

@Composable
private fun StatusCardContent(
    pendingCount: Int,
    lastSync: Long?,
    onSyncClick: () -> Unit,
    isSyncing: Boolean
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.home_sync_status),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Box(contentAlignment = Alignment.Center) {
                // papercard-style progress ring — only shows when actively syncing
                if (isSyncing) {
                    ProgressRing(
                        progress = 0.65f,
                        size = 40.dp,
                        strokeWidth = 3.dp,
                        color = ChartBlue,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(enabled = !isSyncing, onClick = onSyncClick),
                    contentAlignment = Alignment.Center,
                ) {
                    FilledTonalButton(
                        onClick = onSyncClick,
                        enabled = !isSyncing,
                        shape = RoundedCornerShape(RadiusS2),
                        modifier = Modifier.size(32.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        PapercardRefreshIcon(
                            spinning = isSyncing,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatusChip(
                icon = Icons.Default.CloudQueue,
                label = stringResource(R.string.home_chip_pending),
                color = if (pendingCount > 0) WarningAmber else ChartTeal,
                modifier = Modifier.weight(1f),
            ) {
                AnimatedCounter(
                    targetValue = pendingCount,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (pendingCount > 0) WarningAmber else ChartTeal,
                    ),
                )
            }
            StatusChip(
                icon = Icons.Default.Wifi,
                label = stringResource(R.string.home_mqtt_label),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                valueSlot = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Animated pulse dot when MQTT status is active
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(WarningAmber.copy(alpha = 0.8f))
                        )
                        Text(
                            text = stringResource(R.string.home_chip_unavailable),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }

        if (lastSync != null) {
            Text(
                text = stringResource(R.string.home_last_sync, SimpleDateFormat("dd MMM, HH:mm", androidx.compose.ui.platform.LocalConfiguration.current.locales[0]).format(Date(lastSync))),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusChip(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    value: String = "",
    valueSlot: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(RadiusS2))
            .background(color.copy(alpha = 0.1f))
            .padding(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Column {
                if (valueSlot != null) {
                    valueSlot()
                } else {
                    Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
                }
                Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Count consecutive days (from today backward) where a usage log exists. */
private fun computeStreak(recent: List<AppUsageLog>): Int {
    if (recent.isEmpty()) return 0
    val dates = recent.map { it.logDate }.toSortedSet().toList()
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    val cal = java.util.Calendar.getInstance()
    var streak = 0
    while (true) {
        val key = sdf.format(cal.time)
        if (dates.contains(key)) { streak++; cal.add(java.util.Calendar.DAY_OF_YEAR, -1) }
        else break
    }
    return streak
}

@Composable
private fun UsageSummaryContent(usage: com.monitorung.app.domain.model.TodayAggregate) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // Header — explicit "TODAY" label. The data below is the
        // day's running totals (not 7-day or 30-day averages). Without this
        // label the metric cards look identical to the 30-day summary chips.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.home_today_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Surface(
                shape = RoundedCornerShape(RadiusXS),
                color = ChartBlue.copy(alpha = 0.12f),
            ) {
                Text(
                    text = stringResource(R.string.home_today_label),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ChartBlue,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlayfulMetricCard(
                label = stringResource(R.string.home_metric_screen_time),
                value = usage.totalScreenTimeMinutes.formatDuration(),
                icon = Icons.Default.Timer,
                gradient = GradientBlueTeal,
                tint = PrimaryBlue,
                modifier = Modifier.weight(1f)
            )
            PlayfulMetricCard(
                label = stringResource(R.string.home_metric_unlock),
                value = "${usage.unlockCount}x",
                icon = Icons.Default.LockOpen,
                gradient = GradientPurplePink,
                tint = ChartPurple,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlayfulMetricCard(
                label = stringResource(R.string.home_metric_social),
                value = usage.socialMediaTimeMinutes.formatDuration(),
                icon = Icons.Default.Share,
                gradient = GradientAmberGold,
                tint = WarningAmber,
                modifier = Modifier.weight(1f)
            )
            PlayfulMetricCard(
                label = stringResource(R.string.home_metric_game),
                value = usage.gameTimeMinutes.formatDuration(),
                icon = Icons.Default.SportsEsports,
                gradient = GradientAmberPeach,
                tint = ChartAmber,
                modifier = Modifier.weight(1f)
            )
        }

        if (usage.usedDuringClass) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(RadiusS))
                    .background(WarningAmber.copy(alpha = 0.1f))
                    .padding(10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(14.dp))
                    Text(stringResource(R.string.home_used_during_class), style = MaterialTheme.typography.bodySmall, color = WarningAmber)
                }
            }
        }
    }
}

@Composable
private fun ClassUsageContent(breakdown: com.monitorung.app.domain.model.DailyUsageBreakdown) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.home_class_usage_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayfulMetricCard(
                label = stringResource(R.string.home_class_usage_scheduled),
                value = breakdown.scheduledMinutes.formatDuration(),
                icon = Icons.Default.School,
                gradient = GradientBlueTeal,
                tint = ChartBlue,
                modifier = Modifier.weight(1f),
            )
            PlayfulMetricCard(
                label = stringResource(R.string.home_class_usage_outside),
                value = breakdown.outsideScheduleMinutes.coerceAtLeast(0).formatDuration(),
                icon = Icons.Default.Home,
                gradient = GradientSoftBlue,
                tint = ChartTeal,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = pluralStringResource(R.plurals.home_class_usage_classes, breakdown.scheduledClassCount, breakdown.scheduledClassCount, breakdown.attendedClassCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (breakdown.absentClassCount > 0) {
            Text(
                text = stringResource(R.string.home_class_usage_absent) + ": ${breakdown.absentClassCount}",
                style = MaterialTheme.typography.bodySmall,
                color = WarningAmber,
            )
        }
    }
}

/** Playful pastel metric card with gradient badge. */
@Composable
private fun PlayfulMetricCard(
    label: String,
    value: String,
    icon: ImageVector,
    gradient: List<Color>,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(RadiusPlayful))
            .background(Brush.linearGradient(gradient.map { it.copy(alpha = 0.1f) }))
            .padding(12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Gradient icon badge
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(RadiusSoft))
                        .background(Brush.linearGradient(gradient)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
                Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tint)
        }
    }
}

@Composable
private fun RecentActivityContent(recentActivity: List<AppUsageLog>) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.home_recent_activity),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        recentActivity.take(3).forEach { log ->
            CompactActivityRow(log)
        }
    }
}

@Composable
private fun CompactActivityRow(log: AppUsageLog) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val dateFormat = SimpleDateFormat("EEE, dd MMM", locale)
    val inFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val date = try {
        dateFormat.format(inFormat.parse(log.logDate)!!)
    } catch (e: Exception) {
        log.logDate
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(date, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Icon(Icons.Default.Share, null, Modifier.size(14.dp), tint = WarningAmber)
                    Text(log.socialMediaTimeMinutes.formatDuration(), style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Icon(Icons.Default.SportsEsports, null, Modifier.size(14.dp), tint = ChartAmber)
                    Text(log.gameTimeMinutes.formatDuration(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(RadiusSoft))
                .background(Brush.horizontalGradient(GradientBlueTeal.map { it.copy(alpha = 0.15f) }))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                log.totalScreenTimeMinutes.formatDuration(),
                style = MaterialTheme.typography.labelMedium,
                color = PrimaryBlue,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun EmptyUsageContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }
        Text(stringResource(R.string.home_no_data), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.home_tap_to_sync), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QuickActionsContent(
    onCollectClick: () -> Unit,
    isCollecting: Boolean
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.home_quick_action_card_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        GradientSpringButton(
            text = stringResource(if (isCollecting) R.string.home_collecting else R.string.home_collect_data),
            onClick = onCollectClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isCollecting,
        )
    }
}

// ─── Schedule Card ────────────────────────────────────────────────────────────

@Composable
private fun ScheduleCardContent(schedules: List<com.monitorung.app.domain.model.Schedule>) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionBadge(
            label = stringResource(R.string.home_today_label),
            accentColor = ChartBlue,
        )
        Text(
            text = stringResource(R.string.home_schedule_card_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (schedules.isEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.EventBusy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.home_schedule_no_class),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // Show up to 3 schedule rows
            schedules.take(3).forEach { schedule ->
                ScheduleRow(schedule)
            }
        }
    }
}

@Composable
private fun ScheduleRow(schedule: com.monitorung.app.domain.model.Schedule) {
    val isActive = isScheduleActive(schedule)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusS))
            .background(
                if (isActive) ChartTeal.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (isActive) ChartTeal else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = schedule.courseName.ifEmpty { schedule.subjectNameRaw ?: "—" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (!schedule.room.isNullOrEmpty()) {
                Text(
                    text = stringResource(R.string.home_schedule_room, schedule.room),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = "${schedule.startTime.take(5)}–${schedule.endTime.take(5)}",
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) ChartTeal else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** Check if a schedule is currently active based on current time. */
private fun isScheduleActive(schedule: com.monitorung.app.domain.model.Schedule): Boolean {
    return try {
        val cal = java.util.Calendar.getInstance()
        val nowMinutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val startParts = schedule.startTime.split(":")
        val endParts = schedule.endTime.split(":")
        if (startParts.size < 2 || endParts.size < 2) return false
        val startMins = startParts[0].toInt() * 60 + startParts[1].toInt()
        val endMins = endParts[0].toInt() * 60 + endParts[1].toInt()
        nowMinutes in startMins..endMins
    } catch (_: Exception) {
        false
    }
}

// ─── Location Status Card ────────────────────────────────────────────────────

@Composable
private fun LocationStatusCard(
    locations: List<com.monitorung.app.domain.model.LocationLog>,
) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val timeFormat = SimpleDateFormat("HH:mm, dd MMM", locale)
    val last = locations.firstOrNull()
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Same HARI INI badge as the usage cards — geofence checks belong
        // to the same "today" data window.
        SectionBadge(
            label = stringResource(R.string.home_today_label),
            accentColor = ChartTeal,
        )
        Text(
            text = stringResource(R.string.home_location_card_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        if (last == null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.LocationOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.home_location_never),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        val (statusLabel, statusColor, statusIcon) = when {
            last.latitude == null -> Triple(
                stringResource(R.string.home_location_unknown),
                MaterialTheme.colorScheme.onSurfaceVariant,
                Icons.Default.LocationOff,
            )
            last.isOnCampus -> Triple(
                stringResource(R.string.home_location_on_campus),
                ChartTeal,
                Icons.Default.LocationOn,
            )
            else -> Triple(
                stringResource(R.string.home_location_off_campus),
                WarningAmber,
                Icons.Default.LocationOff,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(statusColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor,
                )
                Text(
                    text = timeFormat.format(Date(last.logTimestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (last.latitude != null) {
                Text(
                    text = String.format(
                        locale,
                        "%.4f, %.4f",
                        last.latitude,
                        last.longitude ?: 0.0,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

// ─── 30-Day Summary Card ──────────────────────────────────────────────────────

@Composable
private fun ThirtyDaySummaryContent(aggregate: com.monitorung.app.domain.model.ThirtyDayAggregate) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // Header — explicit "30 DAYS" badge + title. The chips below are
        // per-day averages (not the day's running totals shown above).
        // Without this header users thought the numbers were screen time
        // for today, not the 30-day mean.
        SectionBadge(
            label = stringResource(R.string.home_30day_label),
            accentColor = ChartTeal,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_30day_summary_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.home_30day_avg),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (aggregate.activeDays > 0) {
                Text(
                    text = pluralStringResource(R.plurals.home_30day_active_days, aggregate.activeDays, aggregate.activeDays),
                    style = MaterialTheme.typography.labelSmall,
                    color = ChartTeal,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (aggregate.activeDays == 0) {
            Text(
                text = stringResource(R.string.home_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SummaryMetricChip(
                    label = stringResource(R.string.home_metric_screen_time),
                    value = aggregate.avgScreenMinutes.formatDuration(),
                    icon = Icons.Default.Timer,
                    color = PrimaryBlue,
                    modifier = Modifier.weight(1f),
                )
                SummaryMetricChip(
                    label = stringResource(R.string.home_metric_unlock),
                    value = "${aggregate.maxUnlockCount}x",
                    icon = Icons.Default.LockOpen,
                    color = ChartPurple,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SummaryMetricChip(
                    label = stringResource(R.string.home_metric_social),
                    value = aggregate.avgSocialMinutes.formatDuration(),
                    icon = Icons.Default.Share,
                    color = WarningAmber,
                    modifier = Modifier.weight(1f),
                )
                SummaryMetricChip(
                    label = stringResource(R.string.home_metric_game),
                    value = aggregate.avgGameMinutes.formatDuration(),
                    icon = Icons.Default.SportsEsports,
                    color = ChartAmber,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SummaryMetricChip(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(RadiusS2))
            .background(color.copy(alpha = 0.08f))
            .padding(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Column {
                Text(text = value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── Pending Entries Card ──────────────────────────────────────────────────────

@Composable
private fun PendingEntriesContent(
    pendingCount: Int,
    lastSync: Long?,
    onSyncClick: () -> Unit,
    isSyncing: Boolean,
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_pending_entries_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = if (pendingCount > 0) WarningAmber else ChartTeal,
                    )
                }
                FilledTonalButton(
                    onClick = onSyncClick,
                    enabled = !isSyncing && pendingCount > 0,
                    shape = RoundedCornerShape(RadiusS2),
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Icon(
                        Icons.Default.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (pendingCount > 0) "$pendingCount" else stringResource(R.string.home_sync_now),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (pendingCount > 0) Icons.Default.CloudUpload else Icons.Default.CloudDone,
                contentDescription = null,
                tint = if (pendingCount > 0) WarningAmber else ChartTeal,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = if (pendingCount > 0) {
                    pluralStringResource(R.plurals.settings_pending_count, pendingCount, pendingCount)
                } else {
                    stringResource(R.string.history_all_uploaded)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (pendingCount > 0) WarningAmber else ChartTeal,
            )
        }

        if (lastSync != null) {
            Text(
                text = stringResource(R.string.home_last_sync, SimpleDateFormat("dd MMM, HH:mm", androidx.compose.ui.platform.LocalConfiguration.current.locales[0]).format(Date(lastSync))),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
