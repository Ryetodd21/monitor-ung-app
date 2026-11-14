package com.monitorung.app.presentation.ui.screens.onboarding

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.monitorung.app.R
import com.monitorung.app.presentation.ui.components.ConfettiBurst
import com.monitorung.app.presentation.ui.components.GradientCard
import com.monitorung.app.presentation.ui.components.GradientSpringButton
import com.monitorung.app.presentation.ui.components.SpringButton
import com.monitorung.app.presentation.ui.components.StaggeredEntrance
import com.monitorung.app.presentation.ui.theme.*
import com.monitorung.app.presentation.viewmodel.OnboardingViewModel

data class Permission(
    val id: String,
    val icon: ImageVector,
    val color: Color,
    val title: String,
    val desc: String,
    val how: String,
    val required: Boolean,
    val isGranted: Boolean,
    val onRequest: () -> Unit
)

@Composable
fun PermissionScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Strings captured once so the (non-composable) refresh path can build the list.
    val usageTitle = stringResource(R.string.permission_usage_title)
    val usageDesc = stringResource(R.string.permission_usage_desc)
    val usageHow = stringResource(R.string.permission_usage_how)
    val locationTitle = stringResource(R.string.permission_location_title)
    val locationDesc = stringResource(R.string.permission_location_desc_human)
    val locationHow = stringResource(R.string.permission_location_how)
    val notificationTitle = stringResource(R.string.permission_notification_title)
    val notificationDesc = stringResource(R.string.permission_notification_desc_human)
    val notificationHow = stringResource(R.string.permission_notification_how)

    var permissions by remember {
        mutableStateOf(
            buildPermissionsWithActions(
                context = context,
                usageTitle = usageTitle,
                usageDesc = usageDesc,
                usageHow = usageHow,
                locationTitle = locationTitle,
                locationDesc = locationDesc,
                locationHow = locationHow,
                notificationTitle = notificationTitle,
                notificationDesc = notificationDesc,
                notificationHow = notificationHow,
            ) {}
        )
    }

    fun refresh() {
        permissions = buildPermissionsWithActions(
            context = context,
            usageTitle = usageTitle,
            usageDesc = usageDesc,
            usageHow = usageHow,
            locationTitle = locationTitle,
            locationDesc = locationDesc,
            locationHow = locationHow,
            notificationTitle = notificationTitle,
            notificationDesc = notificationDesc,
            notificationHow = notificationHow,
        ) { refresh() }
    }

    LaunchedEffect(Unit) {
        permissions = buildPermissionsWithActions(
            context = context,
            usageTitle = usageTitle,
            usageDesc = usageDesc,
            usageHow = usageHow,
            locationTitle = locationTitle,
            locationDesc = locationDesc,
            locationHow = locationHow,
            notificationTitle = notificationTitle,
            notificationDesc = notificationDesc,
            notificationHow = notificationHow,
        ) { refresh() }
    }

    // Refresh permissions whenever the screen resumes (e.g. after returning from Settings).
    // This fixes the "skips to login" bug where permission state wasn't updated after
    // the user went to system settings.
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val locLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { refresh() }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { refresh() }

    val requiredList = permissions.filter { it.required }
    val grantedCount = requiredList.count { it.isGranted }
    val totalRequired = requiredList.size
    val allDone = requiredList.all { it.isGranted }
    val progress = if (totalRequired > 0) grantedCount.toFloat() / totalRequired else 1f

    // papercard-style celebration: confetti when all permissions granted.
    var showConfetti by remember { mutableStateOf(false) }
    LaunchedEffect(allDone) {
        if (allDone) showConfetti = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                            MaterialTheme.colorScheme.surface,
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            StepDots(
                currentStep = 2,
                totalSteps = 2,
                labels = listOf(
                    stringResource(R.string.permission_step_terms),
                    stringResource(R.string.permission_step_permissions),
                ),
                modifier = Modifier.padding(top = 20.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(RadiusM),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = stringResource(R.string.permission_header_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.permission_header_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Progress card — playful gradient header bar + gradient progress fill
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(RadiusL),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Gradient header accent bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(RadiusMicro))
                            .background(
                                brush = Brush.horizontalGradient(GradientBlueTeal)
                            )
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (allDone) stringResource(R.string.permission_all_done) else stringResource(R.string.permission_pending),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = pluralStringResource(R.plurals.permission_progress, totalRequired, grantedCount, totalRequired),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Gradient badge icon when all done, circular progress otherwise
                        if (allDone) {
                            GradientBadgeIcon()
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    CircularProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.size(36.dp),
                                        strokeWidth = 3.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Gradient progress bar fill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(RadiusTiny))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .background(
                                    brush = if (allDone) {
                                        Brush.horizontalGradient(GradientTealGreen)
                                    } else {
                                        Brush.horizontalGradient(GradientBlueTeal)
                                    }
                                )
                        )
                    }

                    if (!allDone) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.permission_tap_each),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(permissions) { index, item ->
                    StaggeredEntrance(index = index, modifier = Modifier.fillMaxWidth()) {
                        PermissionCard(
                            item = item,
                            onAction = { id ->
                                when (id) {
                                    "usage" -> context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                    "location" -> locLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                    "notification" -> {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

                // Battery optimization card — set to Optimized for realtime updates
                item {
                    StaggeredEntrance(index = 3, modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(4.dp))

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(RadiusM2))
                                .clickable {
                                    try {
                                        context.startActivity(
                                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        )
                                    } catch (e: Exception) {
                                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                    }
                                },
                            shape = RoundedCornerShape(RadiusM2),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.BatteryChargingFull,
                                    contentDescription = null,
                                    tint = ChartGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.permission_battery_title),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.permission_battery_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 17.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.permission_battery_tap),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // Safety note
                item {
                    StaggeredEntrance(index = 4, modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(4.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(RadiusM2),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Security,
                                    contentDescription = null,
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.permission_safety_title),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.permission_safety_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 17.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // GradientSpringButton — papercard gamified CTA.
            GradientSpringButton(
                text = stringResource(if (allDone) R.string.permission_button_done else R.string.permission_button_continue),
                onClick = {
                    viewModel.completeOnboarding()
                    onComplete()
                },
                gradient = if (allDone) {
                    Brush.horizontalGradient(GradientTealGreen)
                } else {
                    Brush.horizontalGradient(GradientBlueTeal)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                cornerRadius = 14.dp,
                enabled = allDone,
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (!allDone) {
                Text(
                    text = stringResource(R.string.permission_skip_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // papercard-style celebration overlay — fires when all permissions granted.
        ConfettiBurst(
            trigger = showConfetti,
            onFinished = { showConfetti = false },
        )
    }
}

@Composable
private fun GradientBadgeIcon() {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(brush = Brush.linearGradient(GradientTealGreen)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun PermissionCard(
    item: Permission,
    onAction: (String) -> Unit
) {
    // Subtle gradient background tint based on item color
    val gradientBg = if (item.isGranted) {
        Brush.linearGradient(listOf(SuccessGreen.copy(alpha = 0.05f), SuccessGreen.copy(alpha = 0.02f)))
    } else {
        Brush.linearGradient(listOf(item.color.copy(alpha = 0.04f), item.color.copy(alpha = 0.01f)))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusL))
            .clickable(enabled = !item.isGranted) { onAction(item.id) },
        shape = RoundedCornerShape(RadiusL),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = when {
                item.isGranted -> SuccessGreen.copy(alpha = 0.3f)
                item.required -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            }
        )
    ) {
        Box(modifier = Modifier.background(gradientBg)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(RadiusM),
                    color = if (item.isGranted) {
                        SuccessGreen.copy(alpha = 0.12f)
                    } else {
                        item.color.copy(alpha = 0.12f)
                    },
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        if (item.isGranted) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(28.dp)
                            )
                        } else {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = item.color,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (item.required) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(RadiusXS),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = stringResource(R.string.permission_required_badge),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = item.desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = item.how,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Surface(
                    shape = CircleShape,
                    color = if (item.isGranted) {
                        SuccessGreen.copy(alpha = 0.15f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        if (item.isGranted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildPermissionsWithActions(
    context: Context,
    usageTitle: String,
    usageDesc: String,
    usageHow: String,
    locationTitle: String,
    locationDesc: String,
    locationHow: String,
    notificationTitle: String,
    notificationDesc: String,
    notificationHow: String,
    onRefresh: () -> Unit
): List<Permission> = listOf(
    Permission(
        id = "usage",
        icon = Icons.Outlined.Timer,
        color = ChartIndigo,
        title = usageTitle,
        desc = usageDesc,
        how = usageHow,
        required = true,
        isGranted = hasUsageStats(context),
        onRequest = {}
    ),
    Permission(
        id = "location",
        icon = Icons.Outlined.LocationOn,
        color = PrimaryBlue,
        title = locationTitle,
        desc = locationDesc,
        how = locationHow,
        required = true,
        isGranted = hasLocation(context),
        onRequest = {}
    ),
    Permission(
        id = "notification",
        icon = Icons.Outlined.Notifications,
        color = ChartAmber,
        title = notificationTitle,
        desc = notificationDesc,
        how = notificationHow,
        required = true,
        isGranted = hasNotif(context),
        onRequest = {}
    ),
)

@Suppress("DEPRECATION")
private fun hasUsageStats(ctx: Context): Boolean {
    val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        android.os.Process.myUid(),
        ctx.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun hasLocation(ctx: Context): Boolean {
    return ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
}

private fun hasNotif(ctx: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    } else true
}
