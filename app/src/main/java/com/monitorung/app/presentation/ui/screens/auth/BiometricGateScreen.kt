package com.monitorung.app.presentation.ui.screens.auth

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.monitorung.app.R
import com.monitorung.app.presentation.ui.components.PaperCard

@Composable
fun BiometricGateScreen(
    userName: String,
    onSuccess: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val activity = androidx.activity.compose.LocalActivity.current as? FragmentActivity
    val currentOnSuccess by rememberUpdatedState(onSuccess)
    var biometricError by remember { mutableStateOf<String?>(null) }
    var authenticating by remember { mutableStateOf(false) }
    var prompt by remember { mutableStateOf<BiometricPrompt?>(null) }
    var active by remember { mutableStateOf(true) }

    val biometric_unavailable = stringResource(R.string.biometric_unavailable)
    val biometric_title = stringResource(R.string.biometric_title)
    val biometric_subtitle = stringResource(R.string.biometric_subtitle)
    val biometric_cancelled = stringResource(R.string.biometric_cancelled)

    fun triggerBiometricAuth() {
        if (authenticating) return
        biometricError = null
        if (activity == null || !BiometricHelper.canAuthenticate(context)) {
            biometricError = biometric_unavailable
            return
        }
        authenticating = true
        try {
            prompt = BiometricHelper.authenticate(
                activity = activity,
                title = biometric_title,
                subtitle = biometric_subtitle,
                onResult = { result ->
                    if (active) {
                        authenticating = false
                        when (result) {
                            is BiometricResult.Success -> currentOnSuccess()
                            is BiometricResult.Cancelled -> {
                                biometricError = biometric_cancelled
                            }
                            is BiometricResult.Error -> biometricError = result.message
                        }
                    }
                },
            )
        } catch (_: IllegalArgumentException) {
            authenticating = false
            biometricError = biometric_unavailable
        }
    }

    LaunchedEffect(Unit) { triggerBiometricAuth() }
    DisposableEffect(Unit) {
        onDispose {
            active = false
            prompt?.cancelAuthentication()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PaperCard(modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Filled.Lock, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(24.dp).size(40.dp))
                }
                Text(
                    text = stringResource(R.string.biometric_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                if (userName.isNotBlank()) {
                    Text(stringResource(R.string.biometric_welcome_user, userName),
                        style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                }
                Text(stringResource(R.string.biometric_help),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center)
                biometricError?.let { message ->
                    Surface(color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                        Text(message, color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Button(onClick = { triggerBiometricAuth() }, enabled = !authenticating,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(stringResource(if (authenticating) R.string.biometric_waiting
                        else R.string.biometric_verify_now), textAlign = TextAlign.Center)
                }
                TextButton(onClick = onLogout, enabled = !authenticating,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.biometric_logout), textAlign = TextAlign.Center)
                }
                Text(stringResource(R.string.biometric_local_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun BiometricSetupDialog(
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(stringResource(R.string.biometric_enable_title))
        },
        text = {
            Text(stringResource(R.string.biometric_enable_body))
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(stringResource(R.string.biometric_enable_yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_skip))
            }
        },
    )
}
