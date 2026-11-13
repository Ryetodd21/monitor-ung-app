package com.monitorung.app.presentation.ui.screens.history

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monitorung.app.R
import com.monitorung.app.domain.model.AppUsageLog
import com.monitorung.app.domain.model.SyncStatus
import com.monitorung.app.presentation.ui.components.CompactAchievementBadge
import com.monitorung.app.presentation.ui.components.ConfettiBurst
import com.monitorung.app.presentation.ui.components.GradientCard
import com.monitorung.app.presentation.ui.components.LevelProgressCard
import com.monitorung.app.presentation.ui.components.PaperCard
import com.monitorung.app.presentation.ui.components.PlayfulProgressBar
import com.monitorung.app.presentation.ui.components.PullToRefreshWrapper
import com.monitorung.app.presentation.ui.components.SkeletonCard
import com.monitorung.app.presentation.ui.components.StaggeredEntrance
import com.monitorung.app.presentation.ui.theme.*
import com.monitorung.app.presentation.viewmodel.HistoryViewModel
import com.monitorung.app.util.formatDuration
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val totalScreenTime = uiState.logs.sumOf { it.totalScreenTimeMinutes }
    val totalSocial = uiState.logs.sumOf { it.socialMediaTimeMinutes }
    val totalGame = uiState.logs.sumOf { it.gameTimeMinutes }
    val avgDaily = if (uiState.logs.isNotEmpty()) totalScreenTime / uiState.logs.size else 0
    val usedInClass = uiState.logs.count { it.usedDuringClass }
    val pendingLogs = uiState.logs.count { it.syncStatus == SyncStatus.PENDING }

    // Gamified XP calculation: streak_days * 10 + min(avg_screen_time, 1000)
    val streakDays = uiState.logs
        .sortedByDescending { it.logDate }
        .takeWhile { it.syncStatus == SyncStatus.SYNCED }
        .count()
    val currentXp = minOf(streakDays * 10 + avgDaily, 5000)
    val currentLevel = 1 + (currentXp / 500)
    val xpToNextLevel = maxOf(0, (currentLevel * 500) - currentXp)

    // papercard-style celebration: confetti when everything is synced.
    var showConfetti by remember { mutableStateOf(false) }
    val allSyncedTrigger = pendingLogs == 0 && uiState.logs.isNotEmpty()
    LaunchedEffect(allSyncedTrigger) {
        if (allSyncedTrigger) showConfetti = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
    PullToRefreshWrapper(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { viewModel.onRefresh() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.history_30_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.history_30_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Data lifecycle info card
        DataLifecycleCard()

        // Level progress card (papercard gamified)
        LevelProgressCard(
            level = currentLevel,
            currentXp = currentXp,
            xpToNextLevel = xpToNextLevel,
            streakDays = streakDays,
            gradient = GradientBlueTeal,
        )

        // Summary card — 30-day totals
        GradientCard(
            modifier = Modifier.fillMaxWidth(),
            padding = 16.dp,
            gradient = Brush.linearGradient(GradientSoftBlue),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.history_summary_30),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (uiState.logs.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.history_30_day_total),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(RadiusS),
                        color = ChartBlue.copy(alpha = 0.12f),
                    ) {
                        Text(
                            text = pluralStringResource(R.plurals.history_days_suffix, uiState.logs.size, uiState.logs.size),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = ChartBlue,
                        )
                    }
                }

                // Streak achievement badges row
                if (uiState.logs.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactAchievementBadge(
                            icon = Icons.Filled.LocalFireDepartment,
                            label = pluralStringResource(R.plurals.streak_days, streakDays, streakDays),
                            isUnlocked = streakDays >= 1,
                            gradient = listOf(WarningAmber, ChartAmber),
                            tint = WarningAmber,
                        )
                        CompactAchievementBadge(
                            icon = Icons.Filled.EmojiEvents,
                            label = stringResource(R.string.history_achievement_3day),
                            isUnlocked = streakDays >= 3,
                            gradient = GradientBlueTeal,
                            tint = PrimaryBlue,
                        )
                        CompactAchievementBadge(
                            icon = Icons.Filled.Star,
                            label = stringResource(R.string.history_achievement_7day),
                            isUnlocked = streakDays >= 7,
                            gradient = GradientPurplePink,
                            tint = ChartPurple,
                        )
                        CompactAchievementBadge(
                            icon = Icons.Filled.MilitaryTech,
                            label = stringResource(R.string.history_achievement_14day),
                            isUnlocked = streakDays >= 14,
                            gradient = listOf(Color(0xFF0EA5E9), Color(0xFF06B6D4)),
                            tint = Color(0xFF0EA5E9),
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SummaryChip(
                        label = stringResource(R.string.history_total_screen),
                        value = totalScreenTime.formatDuration(),
                        color = PrimaryBlue,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryChip(
                        label = stringResource(R.string.history_avg_day),
                        value = avgDaily.formatDuration(),
                        color = ChartTeal,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SummaryChip(
                        label = stringResource(R.string.home_metric_social),
                        value = totalSocial.formatDuration(),
                        color = ChartPurple,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryChip(
                        label = stringResource(R.string.home_metric_game),
                        value = totalGame.formatDuration(),
                        color = ChartAmber,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (usedInClass > 0) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(RadiusS2),
                        color = WarningAmber.copy(alpha = 0.1f),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Warning,
                                contentDescription = null,
                                tint = WarningAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = pluralStringResource(R.plurals.history_used_during_class, usedInClass, usedInClass),
                                style = MaterialTheme.typography.bodyMedium,
                                color = WarningAmber,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Sync status summary
        SyncStatusBanner(
            totalLogs = uiState.logs.size,
            syncedLogs = uiState.logs.count { it.syncStatus == SyncStatus.SYNCED },
            pendingLogs = pendingLogs
        )

        // Day-by-day items
        if (uiState.logs.isEmpty() && uiState.isLoading) {
            // Skeleton shimmer while DB flow hasn't emitted yet
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonCard(modifier = Modifier.fillMaxWidth())
                SkeletonCard(modifier = Modifier.fillMaxWidth())
                SkeletonCard(modifier = Modifier.fillMaxWidth())
            }
        } else if (uiState.logs.isEmpty() && !uiState.isLoading) {
            EmptyHistoryContent()
        } else {
            Text(
                text = stringResource(R.string.history_daily_detail),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 4.dp)
            )
            uiState.logs.forEachIndexed { index, log ->
                StaggeredEntrance(index = index, modifier = Modifier.fillMaxWidth()) {
                    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
                        HistoryItemContent(log)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        }
    }

        // papercard-style celebration overlay — fires once when all data is synced.
        ConfettiBurst(
            trigger = showConfetti,
            onFinished = { showConfetti = false },
        )
    }
}

@Composable
private fun DataLifecycleCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RadiusM2),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = stringResource(R.string.history_about_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.history_about_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

@Composable
private fun SyncStatusBanner(
    totalLogs: Int,
    syncedLogs: Int,
    pendingLogs: Int
) {
    val allSynced = pendingLogs == 0 && totalLogs > 0
    val syncedGradient = listOf(ChartTeal, ChartTeal)
    val pendingGradient = listOf(ChartAmber, WarningAmber)
    val neutralGradient = listOf(
        MaterialTheme.colorScheme.outlineVariant,
        MaterialTheme.colorScheme.outlineVariant,
    )
    val gradient = if (allSynced) syncedGradient else if (pendingLogs > 0) pendingGradient else neutralGradient

    GradientCard(
        modifier = Modifier.fillMaxWidth(),
        padding = 0.dp,
        gradient = Brush.horizontalGradient(
            gradient.map { it.copy(alpha = 0.12f) }
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (allSynced) Icons.Default.CloudDone else Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = if (allSynced) ChartTeal else if (pendingLogs > 0) ChartAmber else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (allSynced) {
                        stringResource(R.string.history_all_uploaded)
                    } else if (pendingLogs > 0) {
                        pluralStringResource(R.plurals.history_pending_count, pendingLogs, pendingLogs)
                    } else {
                        stringResource(R.string.history_empty_to_upload)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (allSynced) ChartTeal else if (pendingLogs > 0) ChartAmber else MaterialTheme.colorScheme.outline
                )
            }
            if (totalLogs > 0) {
                Text(
                    text = "$syncedLogs/$totalLogs",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (allSynced) ChartTeal else if (pendingLogs > 0) ChartAmber else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun SummaryChip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(RadiusS2))
            .background(
                Brush.horizontalGradient(
                    listOf(color.copy(alpha = 0.12f), color.copy(alpha = 0.08f))
                )
            )
            .padding(10.dp)
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun EmptyHistoryContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Outlined.History,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.history_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.history_empty_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
private fun HistoryItemContent(log: AppUsageLog) {
    val dateFormat = SimpleDateFormat("EEEE, dd MMM yyyy", androidx.compose.ui.platform.LocalConfiguration.current.locales[0])
    val date = try {
        val inFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        dateFormat.format(inFormat.parse(log.logDate)!!)
    } catch (e: Exception) {
        log.logDate
    }

    val isSynced = log.syncStatus == SyncStatus.SYNCED

    // Gradient for the total time badge based on usage intensity
    val totalMinutes = log.totalScreenTimeMinutes
    val badgeGradient = when {
        totalMinutes < 60 -> listOf(PrimaryBlueLight, PrimaryBlueLight)
        totalMinutes < 180 -> listOf(PrimaryBlueLight, PrimaryBlueLight.copy(alpha = 0.6f))
        else -> listOf(ErrorFuchsiaSoft, ErrorFuchsiaSoft.copy(alpha = 0.7f))
    }
    val badgeTextColor = when {
        totalMinutes < 60 -> ChartBlue
        totalMinutes < 180 -> ChartTeal
        else -> ErrorFuchsia
    }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Date row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = date,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = log.logDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Total time badge with gradient background
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(RadiusS))
                    .background(Brush.horizontalGradient(badgeGradient))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = log.totalScreenTimeMinutes.formatDuration(),
                    style = MaterialTheme.typography.labelMedium,
                    color = badgeTextColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Metrics row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HistoryMetric(
                icon = Icons.Outlined.Share,
                label = stringResource(R.string.home_metric_social),
                value = log.socialMediaTimeMinutes.formatDuration(),
                color = ChartPurple
            )
            HistoryMetric(
                icon = Icons.Outlined.SportsEsports,
                label = stringResource(R.string.home_metric_game),
                value = log.gameTimeMinutes.formatDuration(),
                color = ChartAmber
            )
            HistoryMetric(
                icon = Icons.Outlined.LockOpen,
                label = stringResource(R.string.home_metric_unlock),
                value = "${log.unlockCount}x",
                color = PrimaryBlue
            )
            if (log.usedDuringClass) {
                HistoryMetric(
                    icon = Icons.Outlined.School,
                    label = stringResource(R.string.history_metric_label_class),
                    value = stringResource(R.string.history_metric_active),
                    color = WarningAmber
                )
            }
            Spacer(modifier = Modifier.weight(1f))

            // Sync status indicator
            SyncStatusBadge(isSynced = isSynced)
        }
    }
}

@Composable
private fun HistoryMetric(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SyncStatusBadge(isSynced: Boolean) {
    val badgeGradient = if (isSynced) {
        listOf(SuccessTealSoft, SuccessTealSoft.copy(alpha = 0.7f))
    } else {
        listOf(WarningAmberSoft, WarningAmberSoft.copy(alpha = 0.7f))
    }
    val badgeColor = if (isSynced) ChartTeal else ChartAmber

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(RadiusXS))
            .background(Brush.horizontalGradient(badgeGradient))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isSynced) Icons.Default.CloudDone else Icons.Default.CloudUpload,
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = stringResource(if (isSynced) R.string.history_synced else R.string.history_pending),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = badgeColor
            )
        }
    }
}
