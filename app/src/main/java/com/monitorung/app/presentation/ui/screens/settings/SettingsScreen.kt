package com.monitorung.app.presentation.ui.screens.settings

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.monitorung.app.R
import com.monitorung.app.presentation.ui.components.BouncySwitch
import com.monitorung.app.presentation.ui.components.ConfettiBurst
import com.monitorung.app.presentation.ui.components.GradientSpringButton
import com.monitorung.app.presentation.ui.components.PaperCard
import com.monitorung.app.presentation.ui.components.SectionBadge
import com.monitorung.app.presentation.ui.components.StaggeredEntrance
import com.monitorung.app.util.AppConstants
import com.monitorung.app.presentation.ui.screens.auth.BiometricHelper
import com.monitorung.app.presentation.ui.theme.*
import com.monitorung.app.presentation.viewmodel.LoginViewModel
import com.monitorung.app.presentation.viewmodel.SettingsViewModel
import com.monitorung.app.presentation.viewmodel.ThemeViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    onProfileClick: () -> Unit,
    onHomeClick: () -> Unit,
    loginViewModel: LoginViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by loginViewModel.uiState.collectAsStateWithLifecycle()
    val themeVariant by themeViewModel.themeVariant.collectAsStateWithLifecycle()
    val dataDeleted by settingsViewModel.dataDeleted.collectAsStateWithLifecycle()
    val pendingCount by settingsViewModel.pendingCount.collectAsStateWithLifecycle()
    val syncState by settingsViewModel.syncState.collectAsStateWithLifecycle()
    val lastMqttEvent by settingsViewModel.lastMqttEvent.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteDataDialog by remember { mutableStateOf(false) }
    var showDeleteSuccess by remember { mutableStateOf(false) }
    // papercard-style celebration: confetti on delete success.
    var showConfetti by remember { mutableStateOf(false) }
    val deleteSuccessToast = stringResource(R.string.settings_delete_success_toast)
    val defaultUserName = stringResource(R.string.settings_default_user_name)

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // User info — clickable for profile edit
        StaggeredEntrance(index = 0, modifier = Modifier.fillMaxWidth()) {
        PaperCard(
            modifier = Modifier.fillMaxWidth(),
            padding = 0.dp,
            onClick = onProfileClick,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val userAvatar = uiState.user?.avatarUrl
                val avatarBitmap = remember(userAvatar) {
                    userAvatar?.let { url ->
                        if (url.startsWith("data:", ignoreCase = true)) {
                            val base64 = url.substringAfter("base64,").substringBefore(",")
                            try {
                                val bytes = Base64.decode(base64, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                            } catch (_: Exception) { null }
                        } else { null }
                    }
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(RadiusM)),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarBitmap != null) {
                        Image(
                            bitmap = avatarBitmap,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(RadiusM))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Blue600),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = uiState.user?.name?.firstOrNull()?.uppercase() ?: "M",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = uiState.user?.name ?: defaultUserName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(text = uiState.user?.email ?: uiState.user?.studentId ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.ChevronRight, stringResource(R.string.settings_edit_profile_cd), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        }

        StaggeredEntrance(index = 1, modifier = Modifier.fillMaxWidth()) {
        PaperCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
            Column {
                SectionBadge(
                    stringResource(R.string.settings_section_sync), ChartTeal,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                // Sync status row — shows connectivity to backend.
                // Pending: "X menunggu" (amber). Synced fresh: "Tersinkron" (green)
                // with last-sync time. Empty: "Belum ada data".
                val statusLabel: String
                val statusColor: Color
                when {
                    pendingCount > 0 -> {
                        statusLabel = pluralStringResource(R.plurals.settings_pending_count, pendingCount, pendingCount)
                        statusColor = Amber500
                    }
                    syncState.lastSyncTimestamp != null -> {
                        statusLabel = stringResource(R.string.home_last_sync, formatRelative(syncState.lastSyncTimestamp!!))
                        statusColor = Green500
                    }
                    else -> {
                        statusLabel = stringResource(R.string.settings_pending_logs)
                        statusColor = MaterialTheme.colorScheme.onSurfaceVariant
                    }
                }
                SettingsItem(
                    icon = if (pendingCount > 0) Icons.Default.CloudUpload else Icons.Default.CloudDone,
                    title = stringResource(R.string.settings_sync),
                    subtitle = statusLabel,
                    trailing = {
                        if (syncState.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = statusColor
                            )
                        } else {
                            Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor)
                        }
                    }
                )
                SettingsItem(
                    icon = Icons.Default.Sync,
                    title = stringResource(R.string.settings_sync_now),
                    subtitle = stringResource(R.string.settings_sync_now_subtitle),
                    onClick = { settingsViewModel.syncNow() },
                    trailing = { Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                )
                SettingsItem(
                    icon = Icons.Default.Wifi,
                    title = stringResource(R.string.settings_mqtt_connection),
                    subtitle = stringResource(R.string.settings_mqtt_subtitle),
                    trailing = {
                        // Reactive: the tile shows the last event name we received
                        // from `dashboard/broadcast`. The broker URL is fetched at
                        // login; the subscriber is wired in `SmartMonitoringService`
                        // and runs in the background. When the tile says "Connected"
                        // (green) the socket is up but no event has been published
                        // yet — that's the steady state until the user or the admin
                        // triggers a sync / retrain. Events flip it to teal so the
                        // user can see real-time activity without parsing text.
                        val label = when {
                            lastMqttEvent == "log_received" ->
                                stringResource(R.string.settings_mqtt_log_received)
                            lastMqttEvent == "training_completed" ->
                                stringResource(R.string.settings_mqtt_training_completed)
                            lastMqttEvent == null ->
                                stringResource(R.string.settings_mqtt_idle)
                            else -> lastMqttEvent ?: stringResource(R.string.settings_mqtt_idle)
                        }
                        val tint = if (lastMqttEvent != null) ChartTeal else Green500
                        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
                    }
                )
            }
        }
        }

        StaggeredEntrance(index = 2, modifier = Modifier.fillMaxWidth()) {
        PaperCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
            Column {
                SectionBadge(
                    stringResource(R.string.settings_section_appearance), ChartPurple,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemeOptionChip(stringResource(R.string.settings_theme_light), themeVariant == ThemeVariant.LIGHT, Modifier.weight(1f)) {
                        themeViewModel.setTheme(ThemeVariant.LIGHT)
                    }
                    ThemeOptionChip(stringResource(R.string.settings_theme_dark), themeVariant == ThemeVariant.DARK, Modifier.weight(1f)) {
                        themeViewModel.setTheme(ThemeVariant.DARK)
                    }
                    ThemeOptionChip(stringResource(R.string.settings_theme_amoled), themeVariant == ThemeVariant.AMOLED, Modifier.weight(1f)) {
                        themeViewModel.setTheme(ThemeVariant.AMOLED)
                    }
                }
            }
        }
        }

        StaggeredEntrance(index = 3, modifier = Modifier.fillMaxWidth()) {
        PaperCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
            Column {
                SectionBadge(
                    stringResource(R.string.settings_section_permissions), PrimaryBlue,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                PermissionStatusItem(
                    icon = Icons.Default.Apps,
                    title = stringResource(R.string.settings_usage_access),
                    subtitle = stringResource(R.string.settings_usage_access_subtitle),
                    granted = hasUsageStats(context),
                    onClick = {
                        context.startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                )
                PermissionStatusItem(
                    icon = Icons.Default.LocationOn,
                    title = stringResource(R.string.settings_location),
                    subtitle = stringResource(R.string.settings_location_subtitle),
                    granted = hasLocation(context),
                    onClick = {
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.fromParts("package", context.packageName, null)
                            }
                        } else {
                            Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        }
                        context.startActivity(intent)
                    }
                )
                PermissionStatusItem(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.settings_notification_status),
                    subtitle = stringResource(R.string.settings_notification_subtitle),
                    granted = hasNotif(context),
                    onClick = {
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                        } else {
                            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.fromParts("package", context.packageName, null)
                            }
                        }
                        context.startActivity(intent)
                    }
                )
            }
        }
        }

        StaggeredEntrance(index = 4, modifier = Modifier.fillMaxWidth()) {
        val biometricEnrollRequired = stringResource(R.string.settings_biometric_enroll_required)
        val biometricVerifyFailed = stringResource(R.string.settings_biometric_verify_failed)
        val settings_biometric = stringResource(R.string.settings_biometric)
        val biometric_verify_title = stringResource(R.string.biometric_verify_title)
        val biometric_verify_settings_subtitle = stringResource(R.string.biometric_verify_settings_subtitle)
        val common_skip = stringResource(R.string.common_skip)

        // Local state: temporarily holds the pending switch value while biometric
        // verification runs. Null = no pending verification in progress.
        var pendingBiometricToggle by remember { mutableStateOf<Boolean?>(null) }
        val activity = androidx.activity.compose.LocalActivity.current as? androidx.fragment.app.FragmentActivity
        PaperCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
            Column {
                SectionBadge(
                    stringResource(R.string.settings_section_privacy), WarningAmber,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                SettingsItem(
                    icon = Icons.Default.Fingerprint,
                    title = stringResource(R.string.settings_biometric),
                    subtitle = stringResource(R.string.settings_biometric_subtitle),
                    trailing = {
                        BouncySwitch(
                            modifier = Modifier.semantics {
                                contentDescription = settings_biometric
                            },
                            checked = pendingBiometricToggle ?: uiState.biometricEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    // Bug 2 fix: before enabling fingerprint, require a successful
                                    // biometric auth to prove the enrolled fingerprint actually works.
                                    // canAuthenticate() checks device capability but not enrollment.
                                    if (!BiometricHelper.canAuthenticate(context)) {
                                        Toast.makeText(
                                            context,
                                            biometricEnrollRequired,
                                            Toast.LENGTH_LONG
                                        ).show()
                                        return@BouncySwitch
                                    }
                                    if (activity != null) {
                                        pendingBiometricToggle = true
                                        BiometricHelper.authenticate(
                                            activity = activity,
                                            title = biometric_verify_title,
                                            subtitle = biometric_verify_settings_subtitle,
                                            negativeButtonText = common_skip,
                                            onResult = { result ->
                                                when (result) {
                                                    is com.monitorung.app.presentation.ui.screens.auth.BiometricResult.Success -> {
                                                        loginViewModel.setBiometricEnabled(true)
                                                        pendingBiometricToggle = null
                                                    }
                                                    is com.monitorung.app.presentation.ui.screens.auth.BiometricResult.Cancelled,
                                                    is com.monitorung.app.presentation.ui.screens.auth.BiometricResult.Error -> {
                                                        pendingBiometricToggle = null
                                                        if (result is com.monitorung.app.presentation.ui.screens.auth.BiometricResult.Error) {
                                                            Toast.makeText(
                                                                context,
                                                                biometricVerifyFailed,
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                        }
                                                    }
                                                }
                                            },
                                        )
                                    } else {
                                        loginViewModel.setBiometricEnabled(true)
                                    }
                                } else {
                                    loginViewModel.setBiometricEnabled(false)
                                }
                            },
                        )
                    }
                )
                SettingsItem(
                    icon = Icons.Default.Security,
                    title = stringResource(R.string.settings_privacy_policy),
                    subtitle = stringResource(R.string.settings_privacy_policy_subtitle),
                    onClick = { settingsViewModel.openPrivacyPolicy() }
                )
                SettingsItem(
                    icon = Icons.Default.DeleteForever,
                    title = stringResource(R.string.settings_delete_all),
                    subtitle = stringResource(R.string.settings_delete_all_subtitle),
                    onClick = { showDeleteDataDialog = true },
                    trailing = { Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                )
            }
        }
        }

        StaggeredEntrance(index = 5, modifier = Modifier.fillMaxWidth()) {
        PaperCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
            Column {
                SectionBadge(
                    stringResource(R.string.settings_section_about), MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.settings_version),
                    subtitle = stringResource(R.string.settings_version_value),
                    trailing = { Text(stringResource(R.string.settings_version_value), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                )
            }
        }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Kembali ke Beranda — navigates to the home screen.
        TextButton(
            onClick = onHomeClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.settings_home),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        GradientSpringButton(
            text = stringResource(R.string.settings_logout_button),
            onClick = { showLogoutDialog = true },
            modifier = Modifier.fillMaxWidth(),
            gradient = Brush.horizontalGradient(GradientFuchsiaPurple),
            contentColor = Color.White,
            cornerRadius = RadiusM,
        )

        Spacer(modifier = Modifier.height(24.dp))
    }

        // papercard-style celebration overlay — fires when delete data succeeds.
        ConfettiBurst(
            trigger = showConfetti,
            onFinished = { showConfetti = false },
        )
    }

    LaunchedEffect(dataDeleted) {
        if (dataDeleted) {
            showDeleteDataDialog = false
            showDeleteSuccess = true
            showConfetti = true
            settingsViewModel.resetDataDeletedFlag()
        }
    }

    if (showDeleteDataDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDataDialog = false },
            title = { Text(stringResource(R.string.settings_delete_confirm_title)) },
            text = { Text(stringResource(R.string.settings_delete_confirm_body) + "\n\n" + stringResource(R.string.settings_server_data_note)) },
            confirmButton = {
                TextButton(
                    onClick = { settingsViewModel.deleteAllData() },
                    colors = ButtonDefaults.textButtonColors(contentColor = Red500)
                ) {
                    Text(stringResource(R.string.settings_delete_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDataDialog = false }) {
                    Text(stringResource(R.string.settings_delete_confirm_no))
                }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.settings_logout_confirm_title)) },
            text = { Text(stringResource(R.string.settings_logout_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        loginViewModel.logout()
                        onLogout()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Red500)
                ) {
                    Text(stringResource(R.string.settings_logout_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.settings_logout_confirm_no))
                }
            }
        )
    }

    if (showDeleteSuccess) {
        LaunchedEffect(Unit) {
            Toast.makeText(context, deleteSuccessToast, Toast.LENGTH_LONG).show()
            delay(AppConstants.DELETE_SUCCESS_TOAST_DURATION_MS)
            showDeleteSuccess = false
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
private fun PermissionStatusItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = stringResource(if (granted) R.string.settings_permission_granted else R.string.settings_permission_tap_to_grant),
            tint = if (granted) Green500 else Amber500,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ThemeOptionChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val color = if (selected) Blue600 else MaterialTheme.colorScheme.onSurfaceVariant
    val bg = if (selected) Blue50 else Color.Transparent
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(RadiusS2))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
    }
}

// ─── Permission check helpers (mirrors PermissionScreen.kt) ───────────────────
private fun hasUsageStats(ctx: Context): Boolean {
    val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager ?: return false
    val cal = java.util.Calendar.getInstance()
    cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
    return usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, System.currentTimeMillis()).isNotEmpty()
}

private fun hasLocation(ctx: Context): Boolean =
    ctx.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun hasNotif(ctx: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true

/** Format a timestamp as a short Indonesian relative-time string. */
private fun formatRelative(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    val minutes = diff / 60_000
    return when {
        minutes < 1 -> "baru saja"
        minutes < 60 -> "$minutes menit lalu"
        minutes < 1440 -> "${minutes / 60} jam lalu"
        else -> "${minutes / 1440} hari lalu"
    }
}
