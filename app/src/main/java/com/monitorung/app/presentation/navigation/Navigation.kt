package com.monitorung.app.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.monitorung.app.presentation.ui.components.NavBarItem
import com.monitorung.app.presentation.ui.components.BubbleNavBar
import com.monitorung.app.presentation.ui.screens.history.HistoryScreen
import com.monitorung.app.presentation.ui.screens.home.HomeScreen
import com.monitorung.app.presentation.ui.screens.onboarding.PermissionScreen
import com.monitorung.app.presentation.ui.screens.onboarding.TermsScreen
import com.monitorung.app.presentation.ui.screens.profile.ProfileScreen
import com.monitorung.app.presentation.ui.screens.schedule.ScheduleScreen
import com.monitorung.app.presentation.ui.screens.settings.SettingsScreen
import com.monitorung.app.presentation.ui.screens.splash.LoginScreen
import com.monitorung.app.presentation.ui.util.DeviceFormFactor
import com.monitorung.app.presentation.ui.util.contentMaxWidthDp
import com.monitorung.app.presentation.ui.util.formFactor
import com.monitorung.app.presentation.viewmodel.OnboardingViewModel
import kotlinx.coroutines.launch

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : Screen("home", "Beranda", Icons.Filled.Home, Icons.Outlined.Home)
    data object Schedule : Screen("schedule", "Jadwal", Icons.Filled.CalendarToday, Icons.Outlined.CalendarToday)
    data object History : Screen("history", "Histori", Icons.Filled.History, Icons.Outlined.History)
    data object Settings : Screen("settings", "Profil", Icons.Filled.Person, Icons.Outlined.Person)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Schedule,
    Screen.History,
    Screen.Settings
)

// Animation specs — slideInHorizontally/slideOutHorizontally use Int
private val EaseOut = tween<IntOffset>(350, easing = EaseOutCubic)
private val EaseIn = tween<IntOffset>(350, easing = EaseInCubic)
private val FadeIn = fadeIn(animationSpec = tween(300))
private val FadeOut = fadeOut(animationSpec = tween(200))

// Web-like horizontal slide: enter from right, exit to left
private fun slideInRight() = slideInHorizontally(animationSpec = EaseOut) { it }
private fun slideOutLeft() = slideOutHorizontally(animationSpec = EaseIn) { -it }
private fun slideInLeft() = slideInHorizontally(animationSpec = EaseOut) { -it }
private fun slideOutRight() = slideOutHorizontally(animationSpec = EaseIn) { it }

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AppNavigation(
    windowSizeClass: WindowSizeClass,
    onLogout: () -> Unit,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val formFactor = windowSizeClass.formFactor()
    val isTablet = formFactor != DeviceFormFactor.PHONE
    val maxContentWidth = formFactor.contentMaxWidthDp()
    val scope = rememberCoroutineScope()

    // Phone-branch state: pager (untuk 4 tab utama) + profile overlay.
    // Diangkat ke AppNavigation agar bottom bar dan konten body saling berbagi state.
    val pagerState = rememberPagerState(pageCount = { bottomNavItems.size })
    var showProfile by remember { mutableStateOf(false) }

    // Sistem back menutup Profile dulu sebelum keluar activity.
    BackHandler(enabled = showProfile) {
        showProfile = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Bottom bar hanya di phone, dan disembunyikan saat Profile overlay tampil.
            if (!isTablet && !showProfile) {
                BubbleNavBar(
                    items = bottomNavItems.map { screen ->
                        NavBarItem(
                            route = screen.route,
                            label = screen.title,
                            selectedIcon = screen.selectedIcon,
                            unselectedIcon = screen.unselectedIcon,
                        )
                    },
                    currentRoute = bottomNavItems.getOrNull(pagerState.currentPage)?.route
                        ?: Screen.Home.route,
                    onItemSelected = { route ->
                        val targetIdx = bottomNavItems.indexOfFirst { it.route == route }
                        if (targetIdx >= 0 && targetIdx != pagerState.currentPage) {
                            scope.launch { pagerState.animateScrollToPage(targetIdx) }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        if (isTablet) {
            val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route
            Row(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                if (currentRoute != "profile") {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.background,
                    ) {
                        bottomNavItems.forEach { screen ->
                            val selected = currentRoute == screen.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        if (selected) screen.selectedIcon else screen.unselectedIcon,
                                        contentDescription = screen.title
                                    )
                                },
                                label = { Text(screen.title, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(modifier = Modifier.widthIn(max = maxContentWidth.dp)) {
                        MainNavHostContent(navController, onLogout) {
                            SettingsScreen(
                                onLogout = onLogout,
                                onProfileClick = { navController.navigate("profile") },
                                onHomeClick = { navController.navigate(Screen.Home.route) { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } }
                            )
                        }
                    }
                }
            }
        } else {
            // Phone — swipe horizontal antara 4 tab menggunakan HorizontalPager.
            // Profile adalah overlay di atas pager (bukan NavHost terpisah).
            SwipeableBottomNav(
                pagerState = pagerState,
                showProfile = showProfile,
                onShowProfile = { showProfile = true },
                onDismissProfile = { showProfile = false },
                onLogout = onLogout,
                paddingValues = paddingValues,
                maxContentWidth = maxContentWidth,
            )
        }
    }
}

/**
 * Phone-only layout: 4 tab utama (Home/Schedule/History/Settings) di-render
 * dalam HorizontalPager sehingga user bisa swipe berpindah tab. Profile adalah
 * overlay modal di atas pager — bukan destination di NavGraph.
 *
 * Sebelumnya (commit yang crash) ada NavHost terpisah dengan startDestination="home"
 * tapi hanya composable("profile") yang didefinisikan. Saat setGraph dipanggil,
 * NavController navigate ke start destination "home" → findDestination("home")
 * returns null → IllegalArgumentException: navigation destination home is not a
 * direct child of this NavGraph. Setiap buka app crash di View.draw pass.
 *
 * Fix: hapus NavHost, pakai boolean state untuk showProfile, dan pagerState diangkat
 * ke AppNavigation sehingga bottom bar & pager berbagi sumber kebenaran yang sama.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SwipeableBottomNav(
    pagerState: PagerState,
    showProfile: Boolean,
    onShowProfile: () -> Unit,
    onDismissProfile: () -> Unit,
    onLogout: () -> Unit,
    paddingValues: PaddingValues,
    maxContentWidth: Int,
) {
    val scope = rememberCoroutineScope()

    // Pager merender 4 halaman tab utama; Profile adalah overlay modal di atasnya.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .widthIn(max = maxContentWidth.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // BeyondBoundsPageCount default 0; tetap biarkan default agar
            // halaman tetangga tidak di-precompose untuk menghemat memory.
            key = { page -> bottomNavItems[page].route },
        ) { page ->
            val screen = bottomNavItems[page]
            when (screen) {
                Screen.Home -> HomeScreen()
                Screen.Schedule -> ScheduleScreen()
                Screen.History -> HistoryScreen()
                Screen.Settings -> SettingsScreen(
                    onLogout = onLogout,
                    onProfileClick = onShowProfile,
                    onHomeClick = {
                        // Tutup Profile (kalau terbuka) lalu scroll pager ke tab Home.
                        onDismissProfile()
                        scope.launch { pagerState.animateScrollToPage(0) }
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = showProfile,
            enter = slideInRight() + FadeIn,
            exit = slideOutRight() + FadeOut,
        ) {
            ProfileScreen(onBack = onDismissProfile)
        }
    }
}

@Composable
private fun MainNavHostContent(
    navController: NavHostController,
    onLogout: () -> Unit,
    onSettingsReady: @Composable () -> Unit,
) {
    // Tablet / fallback branch: NavHost murni (tanpa swipe pager). Profile
    // juga dirender di sini agar jalur navigasinya konsisten.
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        enterTransition = { FadeIn },
        exitTransition = { FadeOut },
        popEnterTransition = { FadeIn },
        popExitTransition = { FadeOut },
    ) {
        composable(
            route = Screen.Home.route,
            enterTransition = { FadeIn },
            exitTransition = { FadeOut },
        ) { HomeScreen() }
        composable(
            route = Screen.Schedule.route,
            enterTransition = { FadeIn },
            exitTransition = { FadeOut },
        ) { ScheduleScreen() }
        composable(
            route = Screen.History.route,
            enterTransition = { FadeIn },
            exitTransition = { FadeOut },
        ) { HistoryScreen() }
        composable(
            route = Screen.Settings.route,
            enterTransition = { FadeIn },
            exitTransition = { FadeOut },
        ) {
            onSettingsReady()
        }
        composable(
            route = "profile",
            enterTransition = { slideInRight() + FadeIn },
            exitTransition = { slideOutRight() + FadeOut },
            popEnterTransition = { slideInLeft() + FadeIn },
            popExitTransition = { slideOutLeft() + FadeOut },
        ) {
            ProfileScreen(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
fun OnboardingNavigation(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit,
) {
    val navController = rememberNavController()
    val hasAcceptedTerms by viewModel.hasAcceptedTerms.collectAsStateWithLifecycle(initialValue = false)
    // Re-entering users who already clicked Setuju skip Terms and land on
    // Permissions (where they left off). Fresh installs see Terms first.
    val startDestination = if (hasAcceptedTerms == true) "permissions" else "terms"

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { slideInRight() + FadeIn },
        exitTransition = { slideOutLeft() + FadeOut },
        popEnterTransition = { slideInLeft() + FadeIn },
        popExitTransition = { slideOutRight() + FadeOut },
    ) {
        composable("terms") {
            TermsScreen(
                onAccept = {
                    navController.navigate("permissions") {
                        popUpTo("terms") { inclusive = true }
                    }
                },
                onDecline = { },
                viewModel = viewModel
            )
        }
        composable("permissions") {
            PermissionScreen(
                onComplete = onComplete,
                viewModel = viewModel
            )
        }
    }
}

@Composable
fun AuthNavigation(
    onLogout: () -> Unit,
) {
    val navController = rememberNavController()
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    // Gate on full completion, not just term acceptance — otherwise a fresh
    // user who clicked Setuju would skip PermissionScreen entirely. The
    // onboarding sub-flow itself decides which screen to start on (Terms vs
    // Permissions) via `hasAcceptedTerms`.
    val isOnboardingCompleted by onboardingViewModel.isOnboardingCompleted
        .collectAsStateWithLifecycle(initialValue = false)

    // Seamlessly transition between onboarding and login using AnimatedContent
    AnimatedContent(
        targetState = isOnboardingCompleted == true,
        transitionSpec = {
            val slideIn = slideInHorizontally(EaseOut) { it }
            val slideOut = slideOutHorizontally(EaseIn) { -it }
            val slideInBack = slideInHorizontally(EaseOut) { -it }
            val slideOutBack = slideOutHorizontally(EaseIn) { it }
            if (targetState) {
                (slideIn + FadeIn) togetherWith (slideOut + FadeOut)
            } else {
                (slideInBack + FadeIn) togetherWith (slideOutBack + FadeOut)
            }
        },
        label = "auth_transition",
        contentKey = { it },
    ) { showLogin ->
        if (showLogin) {
            LoginScreen(
                onLoginSuccess = { /* handled by MainActivity via isLoggedIn state */ }
            )
        } else {
            OnboardingNavigation(
                viewModel = onboardingViewModel,
                onComplete = { /* state change triggers transition */ }
            )
        }
    }
}
