package com.monitorung.app.presentation.ui.screens.splash

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monitorung.app.R
import com.monitorung.app.presentation.ui.components.ConfettiBurst
import com.monitorung.app.presentation.ui.components.GradientSpringButton
import com.monitorung.app.presentation.ui.components.rememberHapticFeedback
import com.monitorung.app.util.AppConstants
import com.monitorung.app.presentation.ui.theme.Blue600
import com.monitorung.app.presentation.ui.theme.GradientBlueTeal
import com.monitorung.app.presentation.ui.theme.PrimaryBlue
import com.monitorung.app.presentation.ui.theme.RadiusM
import com.monitorung.app.presentation.ui.theme.RadiusPlayful
import com.monitorung.app.presentation.ui.theme.SoftShadowColorLight
import com.monitorung.app.presentation.viewmodel.LoginViewModel

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    var passwordVisible by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") } // local only — never persisted
    // Guest mode indicator + confetti trigger
    var showConfetti by remember { mutableStateOf(false) }
    val haptic = rememberHapticFeedback()

    // Seamless: navigate when logged in — and trigger confetti on the transition.
    // Delay before navigating so the user actually sees the confetti burst (durationMs=1400 → +200ms buffer).
    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) {
            haptic.success()
            showConfetti = true
            kotlinx.coroutines.delay(AppConstants.CONFETTI_ANIMATION_DURATION_MS)
            onLoginSuccess()
        }
    }

    // Buzz on login error so the failure is felt, not just seen.
    LaunchedEffect(uiState.error) {
        if (uiState.error != null && !uiState.isLoading) haptic.error()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Logo area — floating animation + clickable to toggle guest mode
            val infiniteTransition = rememberInfiniteTransition(label = "logo_float")
            val floatOffset by infiniteTransition.animateFloat(
                initialValue = -6f,
                targetValue = 6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "float_y",
            )
            Surface(
                modifier = Modifier
                    .size(80.dp)
                    .offset { androidx.compose.ui.unit.IntOffset(0, floatOffset.dp.roundToPx()) }
                    .shadow(8.dp, RoundedCornerShape(RadiusPlayful), ambientColor = SoftShadowColorLight, spotColor = SoftShadowColorLight)
                    .clip(RoundedCornerShape(RadiusPlayful)),
                shape = RoundedCornerShape(RadiusPlayful),
                color = PrimaryBlue,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "M",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // App name
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.login_app_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Auto-login indicator
            AnimatedVisibility(
                visible = uiState.hasAttemptedAutoLogin && uiState.isLoading,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(RadiusM),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.login_auto_login),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // NIM field
            OutlinedTextField(
                value = uiState.nim,
                onValueChange = viewModel::onNimChange,
                label = { Text(stringResource(R.string.login_nim_label)) },
                placeholder = { Text(stringResource(R.string.login_nim_placeholder)) },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                shape = RoundedCornerShape(RadiusM),
                enabled = !uiState.isLoading
            )

            // Password field
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.auth_password)) },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = stringResource(
                                if (passwordVisible) R.string.login_password_toggle_hide
                                else R.string.login_password_toggle_show
                            )
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (!uiState.isLoading) {
                            haptic.lightTap()
                            viewModel.login(password)
                        }
                    }
                ),
                shape = RoundedCornerShape(RadiusM),
                enabled = !uiState.isLoading
            )

            // Error message with animation
            AnimatedVisibility(
                visible = uiState.error != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Text(
                    text = uiState.error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Login button — gradient bouncy papercard-style CTA
            GradientSpringButton(
                text = if (uiState.isLoading) "" else stringResource(R.string.auth_login),
                onClick = {
                    haptic.lightTap()
                    viewModel.login(password)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !uiState.isLoading,
                gradient = Brush.horizontalGradient(GradientBlueTeal),
                contentColor = Color.White,
                cornerRadius = RadiusPlayful,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Register link — opens the website registration page in a browser.
            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, "https://monitorterop.my.id/register".toUri())
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.login_register),
                    style = MaterialTheme.typography.bodySmall,
                    color = Blue600,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Download app from Google Drive
            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, "https://drive.google.com/drive/folders/1k0x-LaoNRP89DF0cJW2Go-rZS3CzLc_w?usp=sharing".toUri())
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.login_download_app),
                    style = MaterialTheme.typography.bodySmall,
                    color = Blue600,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Privacy note
            Text(
                text = stringResource(R.string.login_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))
        }

        // Confetti overlay — fires once when login succeeds and clears itself after.
        ConfettiBurst(
            trigger = showConfetti,
            onFinished = { showConfetti = false },
        )
    }
}
