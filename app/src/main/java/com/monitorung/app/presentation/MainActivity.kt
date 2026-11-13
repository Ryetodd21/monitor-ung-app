package com.monitorung.app.presentation

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import com.monitorung.app.R
import com.monitorung.app.presentation.navigation.AppNavigation
import com.monitorung.app.presentation.navigation.AuthNavigation
import com.monitorung.app.presentation.ui.screens.auth.BiometricGateScreen
import com.monitorung.app.presentation.ui.screens.auth.BiometricSetupDialog
import com.monitorung.app.presentation.ui.theme.GradientBlueTeal
import com.monitorung.app.presentation.ui.theme.MonitorUNGTheme
import com.monitorung.app.presentation.viewmodel.AppAccessState
import com.monitorung.app.presentation.viewmodel.LoginViewModel
import com.monitorung.app.presentation.viewmodel.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
// Round 26 audit (P0 #2): Changed base class from ComponentActivity to
// FragmentActivity. BiometricGateScreen.kt:35 casts the host Activity via
// `context as? FragmentActivity` to call BiometricHelper.authenticate(...).
// ComponentActivity is NOT a FragmentActivity, so the cast always
// returned null and the biometric gate silently fell through to onSuccess()
// — meaning the gate never actually ran even when biometric was enabled.
// FragmentActivity is the minimum base class that satisfies the cast
// while still being a Hilt-compatible direct superclass (Hilt KSP
// @AndroidEntryPoint accepts ComponentActivity / FragmentActivity /
// Fragment / View / Service / BroadcastReceiver; AppCompatActivity is
// rejected because it adds an extra AppCompat layer that Hilt does not
// recognize in the direct-superclass check).
class MainActivity : FragmentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeVariant by themeViewModel.themeVariant.collectAsStateWithLifecycle()
            val windowSizeClass = calculateWindowSizeClass(this)

            val loginViewModel: LoginViewModel = hiltViewModel()
            DisposableEffect(loginViewModel) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) loginViewModel.onResume()
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }
            val uiState by loginViewModel.uiState.collectAsStateWithLifecycle()
            val appAccessState by loginViewModel.appAccessState.collectAsStateWithLifecycle()

            // Show biometric setup dialog after successful login
            if (uiState.showBiometricSetupDialog) {
                BiometricSetupDialog(
                    onAccept = { loginViewModel.onBiometricSetupAccept() },
                    onDismiss = { loginViewModel.onBiometricSetupDismiss() },
                )
            }
            MonitorUNGTheme(themeVariant = themeVariant) {
                // Gradient background for biometric gate, solid for others.
                val gateGradient = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                        MaterialTheme.colorScheme.surface,
                    )
                )
                val bgModifier = if (appAccessState == AppAccessState.LOCKED) {
                    Modifier.background(gateGradient)
                } else {
                    Modifier.background(MaterialTheme.colorScheme.background)
                }
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(bgModifier)
                ) {
                    // Show splash while loading; once resolved, animate in the real content.
                    // This ensures the branded splash stays visible for a meaningful moment
                    // before the login/gate/home screens slide in.
                    var showRealContent by remember { mutableStateOf(false) }
                    LaunchedEffect(appAccessState) {
                        if (appAccessState != AppAccessState.LOADING) {
                            kotlinx.coroutines.delay(300)
                            showRealContent = true
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        // Real content slides in from top after loading resolves.
                        // Only visible after showRealContent = true.
                        if (showRealContent) {
                            AnimatedContent(
                                targetState = appAccessState,
                                transitionSpec = {
                                    (slideInVertically(
                                        initialOffsetY = { -it / 4 },
                                        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
                                    ) + fadeIn(
                                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                                    )).togetherWith(fadeOut(animationSpec = tween(200)))
                                },
                                label = "app_content",
                            ) { state ->
                                when (state) {
                                    AppAccessState.LOADING -> {}
                                    AppAccessState.NEEDS_LOGIN -> {
                                        AuthNavigation(onLogout = { loginViewModel.logout() })
                                    }
                                    AppAccessState.LOCKED -> {
                                        if (!uiState.showBiometricSetupDialog) {
                                            BiometricGateScreen(
                                                userName = uiState.user?.name ?: "",
                                                onSuccess = { loginViewModel.onBiometricSuccess() },
                                                onLogout = { loginViewModel.dismissBiometricGate() },
                                            )
                                        }
                                    }
                                    AppAccessState.UNLOCKED -> {
                                        AppNavigation(
                                            windowSizeClass = windowSizeClass,
                                            onLogout = { loginViewModel.logout() }
                                        )
                                    }
                                }
                            }
                        } else {
                            BrandedSplashContent()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrandedSplashContent() {
    // Bouncy scale-in animation for the logo
    val infiniteTransition = rememberInfiniteTransition(label = "splash")
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "logo_scale",
    )
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 300),
        label = "logo_alpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size((80 * scale).dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(GradientBlueTeal)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size((48 * scale).dp),
                tint = Color.White,
            )
        }
    }
}
