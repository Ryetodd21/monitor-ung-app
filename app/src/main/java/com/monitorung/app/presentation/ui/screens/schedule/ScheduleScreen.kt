package com.monitorung.app.presentation.ui.screens.schedule

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.monitorung.app.R
import com.monitorung.app.domain.model.Schedule
import com.monitorung.app.domain.repository.ScheduleRequest
import com.monitorung.app.presentation.ui.components.ConfettiBurst
import com.monitorung.app.presentation.ui.components.EmptyStateBubble
import com.monitorung.app.presentation.ui.components.GradientCard
import com.monitorung.app.presentation.ui.components.GradientSpringButton
import com.monitorung.app.presentation.ui.components.PaperCard
import com.monitorung.app.presentation.ui.components.PullToRefreshWrapper
import com.monitorung.app.presentation.ui.components.SkeletonCard
import com.monitorung.app.presentation.ui.components.SpringButton
import com.monitorung.app.presentation.ui.components.StaggeredEntrance
import com.monitorung.app.presentation.ui.components.rememberHapticFeedback
import com.monitorung.app.presentation.ui.theme.Blue600
import com.monitorung.app.presentation.ui.theme.ChartBlue
import com.monitorung.app.presentation.ui.theme.ChartPurple
import com.monitorung.app.presentation.ui.theme.ChartTeal
import com.monitorung.app.presentation.ui.theme.ErrorFuchsia
import com.monitorung.app.presentation.ui.theme.GradientAmberGold
import com.monitorung.app.presentation.ui.theme.GradientBlueTeal
import com.monitorung.app.presentation.ui.theme.GradientPurplePink
import com.monitorung.app.presentation.ui.theme.GradientTealGreen
import com.monitorung.app.presentation.ui.theme.PrimaryBlue
import com.monitorung.app.presentation.ui.theme.PrimaryBlueDark
import com.monitorung.app.presentation.ui.theme.PrimaryBlueLight
import com.monitorung.app.presentation.ui.theme.RadiusM
import com.monitorung.app.presentation.ui.theme.RadiusPlayful
import com.monitorung.app.presentation.ui.theme.RadiusS
import com.monitorung.app.presentation.ui.theme.RadiusSoft
import com.monitorung.app.presentation.ui.theme.RadiusXS
import com.monitorung.app.presentation.ui.theme.RadiusXXS
import com.monitorung.app.presentation.ui.theme.TextOnPrimary
import com.monitorung.app.presentation.ui.theme.WarningAmber
import com.monitorung.app.presentation.viewmodel.ScheduleViewModel
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var requestSheetTrigger by remember { mutableIntStateOf(0) }
    var showRequestSheet by remember { mutableStateOf(false) }
    // Wait for loadDirectory() to finish before showing the bottom sheet,
    // so the subject/lecturer dropdowns are already populated.
    LaunchedEffect(requestSheetTrigger) {
        if (requestSheetTrigger > 0) {
            // loadDirectory() is already running (kicked off in onRequestClick).
            // Wait for it to finish by polling isLoadingDirectory.
            while (uiState.isLoadingDirectory) {
                delay(50)
            }
            showRequestSheet = true
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var requestRefreshing by remember { mutableStateOf(false) }
    // papercard-style celebration: confetti on request approval.
    var showConfetti by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(pageCount = { 2 })
    val haptics = rememberHapticFeedback()

    LaunchedEffect(uiState.actionSuccess) {
        uiState.actionSuccess?.let {
            snackbarHostState.showSnackbar(it)
            showConfetti = true
            viewModel.clearActionSuccess()
        }
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Pull-to-refresh on the requests tab flips local state, then reloads.
    val onRequestsRefresh: () -> Unit = {
        requestRefreshing = true
        viewModel.loadMyRequests()
    }
    LaunchedEffect(uiState.isLoadingRequests) {
        if (!uiState.isLoadingRequests) requestRefreshing = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // FAB removed: the "+" pill was a floating overlay that users
        // consistently described as "a white circle floating over the schedule"
        // — it obscured class rows and added nothing for day-to-day use. The
        // schedule-request flow is still reachable via a tab-bar action below
        // (see `ScheduleRequestTab` header button) and via the dedicated
        // `/permintaan` page when we wire it up later.
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Text(
                text = stringResource(R.string.schedule_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
            )

            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Blue600
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { /* handled by pager */ },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.schedule_tab_schedule))
                            if (uiState.schedules.isNotEmpty()) {
                                Badge(containerColor = Blue600) { Text("${uiState.schedules.size}") }
                            }
                        }
                    }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { /* handled by pager */ },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.schedule_tab_requests))
                            val pending = uiState.scheduleRequests.count { it.status == "pending" }
                            if (pending > 0) {
                                Badge(containerColor = WarningAmber) { Text("$pending") }
                            }
                        }
                    }
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> ScheduleListTab(
                        schedules = uiState.schedules,
                        selectedDay = uiState.selectedDay,
                        isLoading = uiState.isLoading,
                        onDaySelected = { viewModel.selectDay(it) },
                        onRefresh = { viewModel.loadSchedules() },
                        onRequestClick = {
                            haptics.lightTap()
                            viewModel.loadDirectory()
                            requestSheetTrigger++
                        },
                        onDelete = {
                            haptics.mediumTick()
                            viewModel.deleteSchedule(it)
                        },
                    )
                    1 -> RequestListTab(
                        requests = uiState.scheduleRequests,
                        isLoading = uiState.isLoadingRequests,
                        isRefreshing = requestRefreshing,
                        onRefresh = onRequestsRefresh,
                    )
                }
            }
        }
    }

    if (requestSheetTrigger > 0) {
        ScheduleRequestBottomSheet(
            subjects = uiState.subjects,
            lecturers = uiState.lecturers,
            isLoadingDirectory = uiState.isLoadingDirectory,
            isSubmitting = uiState.isLoading,
            initialDay = uiState.selectedDay,
            onDismiss = { requestSheetTrigger = 0 },
            onSubmit = { day, start, end, room, subjectId, lecturerId, subjectName, lecturerName, desc ->
                haptics.lightTap()
                viewModel.submitScheduleRequest(
                    dayOfWeek = day,
                    startTime = start,
                    endTime = end,
                    room = room,
                    subjectId = subjectId,
                    lecturerId = lecturerId,
                    subjectNameRaw = subjectName,
                    lecturerNameRaw = lecturerName,
                    description = desc
                )
                requestSheetTrigger = 0
            }
        )
    }

        // papercard-style celebration overlay — fires when schedule request succeeds.
        ConfettiBurst(
            trigger = showConfetti,
            onFinished = { showConfetti = false },
        )
    }
}

// ─── Tab 1: Approved Schedules ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleListTab(
    schedules: List<Schedule>,
    selectedDay: Int,
    isLoading: Boolean,
    onDaySelected: (Int) -> Unit,
    onRefresh: () -> Unit,
    onRequestClick: () -> Unit,
    onDelete: (String) -> Unit,
) {
    val refreshingState = remember { mutableStateOf(false) }
    val onPullRefresh: () -> Unit = {
        refreshingState.value = true
        onRefresh()
    }
    LaunchedEffect(isLoading) {
        if (!isLoading) refreshingState.value = false
    }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DaySelector(
            selectedDay = selectedDay,
            onDaySelected = onDaySelected
        )

        // FAB was removed in round X because users said it was a "white circle floating
        // over the schedule" — but the empty state hint "Press + to request a new class"
        // left no actual button behind. Restore it as a text-led pill above the day list
        // so empty-state users have a clear path forward.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GradientSpringButton(
                text = stringResource(R.string.schedule_request_fab),
                onClick = onRequestClick,
                modifier = Modifier
                    .height(52.dp)
                    .fillMaxWidth(),
                enabled = true,
                gradient = if (schedules.isEmpty()) Brush.horizontalGradient(GradientBlueTeal) else Brush.horizontalGradient(listOf(Color.Gray, Color.Gray)),
                contentColor = if (schedules.isEmpty()) Color.White else MaterialTheme.colorScheme.onSurface,
                cornerRadius = RadiusPlayful,
            )
        }

        if (isLoading && !refreshingState.value) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonCard(modifier = Modifier.fillMaxWidth())
                SkeletonCard(modifier = Modifier.fillMaxWidth())
            }
        } else {
            // Wrap list + empty state in PullToRefreshWrapper so empty state can still pull-to-refresh.
            // The empty state uses a vertically scrollable Column so the gesture is delivered.
            PullToRefreshWrapper(
                isRefreshing = refreshingState.value,
                onRefresh = onPullRefresh,
            ) {
                if (schedules.isEmpty()) {
                    EmptyStateBubble(
                        icon = Icons.Default.CalendarToday,
                        title = stringResource(R.string.schedule_no_classes),
                        hint = stringResource(R.string.schedule_no_requests_hint),
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(schedules, key = { _, s -> s.id }) { index, schedule ->
                            StaggeredEntrance(index = index, modifier = Modifier.fillMaxWidth()) {
                                ApprovedScheduleCard(schedule = schedule, onDelete = onDelete)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApprovedScheduleCard(schedule: Schedule, onDelete: (String) -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Hapus Jadwal") },
            text = { Text("Yakin ingin menghapus jadwal ini?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete(schedule.id)
                }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    val now = LocalTime.now()
    val start = runCatching { LocalTime.parse(schedule.startTime) }.getOrNull()
    val end = runCatching { LocalTime.parse(schedule.endTime) }.getOrNull()
    val isActive = start != null && end != null && now.isAfter(start) && now.isBefore(end)

    if (isActive) {
        GradientCard(
            modifier = Modifier.fillMaxWidth(),
            gradient = Brush.linearGradient(GradientBlueTeal),
            padding = 0.dp,
        ) {
            ActiveScheduleCardContent(schedule = schedule, isActive = true, onDelete = { onDelete(schedule.id) })
        }
    } else {
        PaperCard(
            modifier = Modifier.fillMaxWidth(),
            padding = 0.dp,
        ) {
            ActiveScheduleCardContent(schedule = schedule, isActive = false, onDelete = { showDeleteDialog = true })
        }
    }
}

@Composable
private fun ActiveScheduleCardContent(schedule: Schedule, isActive: Boolean, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = schedule.startTime,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = schedule.endTime,
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isActive) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(shape = RoundedCornerShape(RadiusXXS), color = Color.White.copy(alpha = 0.25f)) {
                    Text(
                        text = stringResource(R.string.schedule_active_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }

        VerticalDivider(modifier = Modifier.height(40.dp), color = if (isActive) Color.White.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant)

        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(
                text = schedule.courseName.ifEmpty { "Jadwal" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface
            )
            if (!schedule.room.isNullOrEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Room, null, Modifier.size(12.dp), tint = if (isActive) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(schedule.room, style = MaterialTheme.typography.bodySmall, color = if (isActive) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (schedule.lecturerDisplay.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Person, null, Modifier.size(12.dp), tint = if (isActive) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(schedule.lecturerDisplay, style = MaterialTheme.typography.bodySmall, color = if (isActive) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Hapus jadwal",
                modifier = Modifier.size(18.dp),
                tint = if (isActive) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.error,
            )
        }
    }
}

// ─── Tab 2: User Requests ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequestListTab(
    requests: List<ScheduleRequest>,
    isLoading: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.schedule_my_requests),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            // Refresh handled by PullToRefreshWrapper — no separate icon button.
        }

        if (isLoading && !isRefreshing) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonCard(modifier = Modifier.fillMaxWidth())
                SkeletonCard(modifier = Modifier.fillMaxWidth())
            }
        } else {
            // Wrap list + empty state in PullToRefreshWrapper so empty state can still pull-to-refresh.
            PullToRefreshWrapper(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
            ) {
                if (requests.isEmpty()) {
                    EmptyStateBubble(
                        icon = Icons.AutoMirrored.Filled.Send,
                        title = stringResource(R.string.schedule_no_requests),
                        hint = stringResource(R.string.schedule_no_requests_hint),
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(requests, key = { _, r -> r.id }) { index, request ->
                            StaggeredEntrance(index = index, modifier = Modifier.fillMaxWidth()) {
                                RequestCard(request = request)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestCard(request: ScheduleRequest) {
    val statusColor = when (request.status) {
        "pending" -> WarningAmber
        "approved" -> ChartTeal
        "rejected" -> ErrorFuchsia
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusIcon = when (request.status) {
        "pending" -> Icons.Default.Schedule
        "approved" -> Icons.Default.CheckCircle
        "rejected" -> Icons.Default.Cancel
        else -> Icons.Default.Info
    }
    val statusLabel = when (request.status) {
        "pending" -> stringResource(R.string.schedule_status_pending)
        "approved" -> stringResource(R.string.schedule_status_approved)
        "rejected" -> stringResource(R.string.schedule_status_rejected)
        else -> request.status
    }

    PaperCard(
        modifier = Modifier.fillMaxWidth(),
        padding = 0.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = request.courseName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                // Playful gradient status badge
                val (badgeGradient, badgeColor) = when (request.status) {
                    "pending" -> Brush.horizontalGradient(GradientAmberGold) to WarningAmber
                    "approved" -> Brush.horizontalGradient(GradientTealGreen) to ChartTeal
                    "rejected" -> Brush.horizontalGradient(listOf(ErrorFuchsia.copy(alpha = 0.6f), ChartPurple)) to ErrorFuchsia
                    else -> Brush.horizontalGradient(listOf(Color.Gray, Color.Gray)) to Color.Gray
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(RadiusSoft))
                        .background(badgeGradient)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(statusIcon, null, Modifier.size(14.dp), tint = Color.White)
                        Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Medium)
                    }
                }
            }

            if (request.startTime != null && request.endTime != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccessTime, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${request.startTime} - ${request.endTime}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val days = listOf(
                        stringResource(R.string.schedule_days_mon),
                        stringResource(R.string.schedule_days_tue),
                        stringResource(R.string.schedule_days_wed),
                        stringResource(R.string.schedule_days_thu),
                        stringResource(R.string.schedule_days_fri),
                        stringResource(R.string.schedule_days_sat),
                        stringResource(R.string.schedule_days_sun),
                    )
                    val dayLabel = request.dayOfWeek?.let { dow -> if (dow in 1..7) days[dow - 1] else null } ?: ""
                    Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(dayLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!request.room.isNullOrEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Room, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(request.room, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!request.adminNotes.isNullOrEmpty()) {
                Surface(shape = RoundedCornerShape(RadiusXS), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.Info, null, Modifier.size(14.dp), tint = statusColor)
                        Text(request.adminNotes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ─── Day Selector ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaySelector(
    selectedDay: Int,
    onDaySelected: (Int) -> Unit
) {
    // Day labels follow the user's system locale — pulled from string resources so
    // Monday→Senin, Tuesday→Selasa, etc., automatically when the device is in Indonesian.
    val days = listOf(
        1 to stringResource(R.string.schedule_days_mon),
        2 to stringResource(R.string.schedule_days_tue),
        3 to stringResource(R.string.schedule_days_wed),
        4 to stringResource(R.string.schedule_days_thu),
        5 to stringResource(R.string.schedule_days_fri),
        6 to stringResource(R.string.schedule_days_sat),
        7 to stringResource(R.string.schedule_days_sun),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        days.forEachIndexed { index, (day, label) ->
            val isSelected = selectedDay == day
            SegmentedButton(
                selected = isSelected,
                onClick = { onDaySelected(day) },
                shape = RoundedCornerShape(RadiusSoft),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary,
                    activeContentColor = Color.White,
                ),
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ─── Request Form Bottom Sheet ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleRequestBottomSheet(
    subjects: List<com.monitorung.app.domain.repository.SubjectItem>,
    lecturers: List<com.monitorung.app.domain.repository.LecturerItem>,
    isLoadingDirectory: Boolean,
    isSubmitting: Boolean,
    initialDay: Int,
    onDismiss: () -> Unit,
    onSubmit: (Int, String, String, String?, String?, String?, String?, String?, String) -> Unit
) {
    // Captured once in composition; falls back to localized default.
    val defaultDescription = stringResource(R.string.schedule_default_description)
    var selectedDay by remember { mutableIntStateOf(initialDay) }
    val now = LocalTime.now()
    val defaultStart = remember {
        // Round up to the next 30-minute mark so the default start is always in the future.
        val minute = ((now.minute / 30) + 1) * 30
        val rounded = if (minute >= 60) now.plusHours(1).withMinute(0) else now.withMinute(minute)
        rounded.withSecond(0).withNano(0)
    }
    val defaultEnd = remember { defaultStart.plusHours(2).withSecond(0).withNano(0) }
    var startTime by remember { mutableStateOf(defaultStart.toString()) }
    var endTime by remember { mutableStateOf(defaultEnd.toString()) }
    var room by remember { mutableStateOf("") }
    var selectedSubjectId by remember { mutableStateOf("") }
    var selectedLecturerId by remember { mutableStateOf("") }
    var subjectNameRaw by remember { mutableStateOf("") }
    var lecturerNameRaw by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var subjectDropdownExpanded by remember { mutableStateOf(false) }
    var lecturerDropdownExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.schedule_request_sheet_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            // Info banner
            Surface(
                shape = RoundedCornerShape(RadiusS),
                color = PrimaryBlueLight
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, null, Modifier.size(16.dp), tint = ChartBlue)
                    Text(
                        stringResource(R.string.schedule_request_info),
                        style = MaterialTheme.typography.bodySmall,
                        color = PrimaryBlueDark
                    )
                }
            }

            // Day selector
            Text(stringResource(R.string.schedule_field_day), style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val days = listOf(
                    1 to stringResource(R.string.schedule_days_mon),
                    2 to stringResource(R.string.schedule_days_tue),
                    3 to stringResource(R.string.schedule_days_wed),
                    4 to stringResource(R.string.schedule_days_thu),
                    5 to stringResource(R.string.schedule_days_fri),
                    6 to stringResource(R.string.schedule_days_sat),
                    7 to stringResource(R.string.schedule_days_sun),
                )
                days.forEachIndexed { index, (day, label) ->
                    SegmentedButton(
                        selected = selectedDay == day,
                        onClick = { selectedDay = day },
                        // Match the editorial paper style — see DaySelector comment.
                        shape = RoundedCornerShape(0.dp),
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Time pickers
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = startTime,
                    onValueChange = {},
                    label = { Text(stringResource(R.string.schedule_field_start_time)) },
                    readOnly = true,
                    modifier = Modifier.weight(1f),
                    trailingIcon = { IconButton(onClick = { showStartTimePicker = true }) { Icon(Icons.Default.AccessTime, null) } },
                    shape = RoundedCornerShape(RadiusM)
                )
                OutlinedTextField(
                    value = endTime,
                    onValueChange = {},
                    label = { Text(stringResource(R.string.schedule_field_end_time)) },
                    readOnly = true,
                    modifier = Modifier.weight(1f),
                    trailingIcon = { IconButton(onClick = { showEndTimePicker = true }) { Icon(Icons.Default.AccessTime, null) } },
                    shape = RoundedCornerShape(RadiusM)
                )
            }

            // Subject dropdown
            if (isLoadingDirectory) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.schedule_loading_directory), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ExposedDropdownMenuBox(
                expanded = subjectDropdownExpanded,
                onExpandedChange = { subjectDropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = subjects.find { it.id == selectedSubjectId }?.name ?: subjectNameRaw,
                    onValueChange = { selectedSubjectId = ""; subjectNameRaw = it },
                    label = { Text(stringResource(R.string.schedule_field_subject)) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subjectDropdownExpanded) },
                    shape = RoundedCornerShape(RadiusM)
                )
                ExposedDropdownMenu(
                    expanded = subjectDropdownExpanded,
                    onDismissRequest = { subjectDropdownExpanded = false }
                ) {
                    subjects.forEach { subject ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = "${subject.name}${subject.code?.let { " ($it)" } ?: ""}",
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    // Tampilkan info lokasi kampus agar mahasiswa
                                    // tahu di mana mata kuliah ini seharusnya
                                    // berlangsung. Subject yang di-bridge dari
                                    // mata_kuliah selalu punya campus_id;
                                    // legacy Subject rows akan null.
                                    val campusLabel = subject.campusName
                                        ?: stringResource(R.string.schedule_field_campus_unspecified)
                                    Text(
                                        text = campusLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            onClick = {
                                selectedSubjectId = subject.id; subjectNameRaw = subject.name; subjectDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Room
            OutlinedTextField(
                value = room,
                onValueChange = { room = it },
                label = { Text(stringResource(R.string.schedule_field_room_optional)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(RadiusM),
                singleLine = true
            )

            // Lecturer dropdown
            ExposedDropdownMenuBox(
                expanded = lecturerDropdownExpanded,
                onExpandedChange = { lecturerDropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = lecturers.find { it.id == selectedLecturerId }?.name ?: lecturerNameRaw,
                    onValueChange = { selectedLecturerId = ""; lecturerNameRaw = it },
                    label = { Text(stringResource(R.string.schedule_field_lecturer_optional)) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = lecturerDropdownExpanded) },
                    shape = RoundedCornerShape(RadiusM)
                )
                ExposedDropdownMenu(
                    expanded = lecturerDropdownExpanded,
                    onDismissRequest = { lecturerDropdownExpanded = false }
                ) {
                    lecturers.forEach { lecturer ->
                        DropdownMenuItem(
                            text = { Text(lecturer.name) },
                            onClick = { selectedLecturerId = lecturer.id; lecturerNameRaw = lecturer.name; lecturerDropdownExpanded = false }
                        )
                    }
                }
            }

            // Submit button — bouncy papercard press via SpringButton.
            SpringButton(
                onClick = {
                    onSubmit(
                        selectedDay, startTime, endTime,
                        room.ifEmpty { null },
                        selectedSubjectId.ifEmpty { null },
                        selectedLecturerId.ifEmpty { null },
                        subjectNameRaw.ifEmpty { null },
                        lecturerNameRaw.ifEmpty { null },
                        description.ifEmpty { defaultDescription }
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !isSubmitting && (selectedSubjectId.isNotEmpty() || subjectNameRaw.isNotEmpty()),
                cornerRadius = 12.dp,
                containerColor = Blue600,
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = TextOnPrimary, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.schedule_submit), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    if (showStartTimePicker) {
        TimePickerDialog(
            initialTime = startTime,
            onDismiss = { showStartTimePicker = false },
            onConfirm = { startTime = it; showStartTimePicker = false }
        )
    }
    if (showEndTimePicker) {
        TimePickerDialog(
            initialTime = endTime,
            onDismiss = { showEndTimePicker = false },
            onConfirm = { endTime = it; showEndTimePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialTime: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val parts = initialTime.split(":")
    val timePickerState = rememberTimePickerState(
        initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 8,
        initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val h = timePickerState.hour.toString().padStart(2, '0')
                val m = timePickerState.minute.toString().padStart(2, '0')
                onConfirm("$h:$m")
            }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
        text = { TimePicker(state = timePickerState) }
    )
}
