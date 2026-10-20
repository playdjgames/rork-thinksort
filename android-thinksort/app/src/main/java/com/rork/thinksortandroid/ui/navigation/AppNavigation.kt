package com.rork.thinksortandroid.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rork.thinksortandroid.data.AccessState
import com.rork.thinksortandroid.data.nowMillis
import com.rork.thinksortandroid.services.CalendarService
import com.rork.thinksortandroid.services.PurchaseService
import com.rork.thinksortandroid.ui.BrainViewModel
import com.rork.thinksortandroid.ui.FocusViewModel
import com.rork.thinksortandroid.ui.components.ToastHost
import com.rork.thinksortandroid.ui.screens.BeforeYouGoSheet
import com.rork.thinksortandroid.ui.screens.BrainDumpScreen
import com.rork.thinksortandroid.ui.screens.EveningResetScreen
import com.rork.thinksortandroid.ui.screens.FocusScreen
import com.rork.thinksortandroid.ui.screens.ForgettingSheet
import com.rork.thinksortandroid.ui.screens.HistoryScreen
import com.rork.thinksortandroid.ui.screens.LeftOffScreen
import com.rork.thinksortandroid.ui.screens.MorningBriefingScreen
import com.rork.thinksortandroid.ui.screens.NotesScreen
import com.rork.thinksortandroid.ui.screens.OnboardingScreen
import com.rork.thinksortandroid.ui.screens.OneThingScreen
import com.rork.thinksortandroid.ui.screens.PaywallGateScreen
import com.rork.thinksortandroid.ui.screens.PaywallSheet
import com.rork.thinksortandroid.ui.screens.PrivacyScreen
import com.rork.thinksortandroid.ui.screens.QuickAddSheet
import com.rork.thinksortandroid.ui.screens.RunningLateSheet
import com.rork.thinksortandroid.ui.screens.SearchScreen
import com.rork.thinksortandroid.ui.screens.SettingsScreen
import com.rork.thinksortandroid.ui.screens.ShoppingScreen
import com.rork.thinksortandroid.ui.screens.StuckSheet
import com.rork.thinksortandroid.ui.screens.TaskDetailScreen
import com.rork.thinksortandroid.ui.screens.TimeAvailableSheet
import com.rork.thinksortandroid.ui.screens.TodayScreen
import com.rork.thinksortandroid.ui.screens.TrialStartScreen
import com.rork.thinksortandroid.ui.screens.UpcomingScreen
import com.rork.thinksortandroid.ui.theme.AppTheme
import com.rork.thinksortandroid.ui.theme.Think

private enum class Gate { ONBOARDING, TRIAL, EXPIRED, APP }

/** Decides between onboarding, the trial gate, the hard paywall and the main tab shell. */
@Composable
fun AppNavigation() {
    val vm: BrainViewModel = viewModel()
    val router: RouterViewModel = viewModel()
    val focus: FocusViewModel = viewModel()
    val context = LocalContext.current
    val data by vm.data.collectAsStateWithLifecycle()
    val unlocked by PurchaseService.isUnlocked.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(nowMillis()) }
    var pendingOneThing by remember { mutableStateOf<String?>(null) }
    var showTrialPaywall by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        PurchaseService.attach(cachedUnlock = data.settings.lifetimeUnlocked == true)
        vm.refreshNotifications()
        vm.scheduleTrialReminders()
        if (data.settings.calendarEnabled) CalendarService.reload(context)
    }
    LaunchedEffect(unlocked) { if (unlocked) vm.setLifetimeUnlocked(true) }
    LifecycleResumeEffect(Unit) {
        now = nowMillis()
        PurchaseService.refresh()
        onPauseOrDispose { vm.saveNow() }
    }

    AppTheme(appearance = data.settings.appearance) {
        val access = PurchaseService.accessState(data.settings.trialStartedAt, unlocked || data.settings.lifetimeUnlocked == true, now)
        val gate = when {
            !data.settings.hasCompletedOnboarding -> Gate.ONBOARDING
            access == AccessState.TrialNotStarted -> Gate.TRIAL
            access == AccessState.Expired -> Gate.EXPIRED
            else -> Gate.APP
        }
        Box(Modifier.fillMaxSize().background(Think.colors.canvas)) {
            Crossfade(targetState = gate, animationSpec = tween(300), label = "gate") { current ->
                when (current) {
                    Gate.ONBOARDING -> OnboardingScreen(vm, onStartTask = { pendingOneThing = it })
                    Gate.TRIAL -> {
                        TrialStartScreen(vm, onUnlockNow = { showTrialPaywall = true })
                        if (showTrialPaywall) PaywallSheet(onDismiss = { showTrialPaywall = false })
                    }
                    Gate.EXPIRED -> PaywallGateScreen()
                    Gate.APP -> MainShell(vm, router, focus, pendingOneThing) { pendingOneThing = null }
                }
            }
        }
    }
}

@Composable
private fun MainShell(
    vm: BrainViewModel,
    router: RouterViewModel,
    focus: FocusViewModel,
    pendingOneThing: String?,
    onPendingHandled: () -> Unit,
) {
    val navController = rememberNavController()
    val nav = remember(navController) { Nav(navController, router) }
    val sheet by router.sheet.collectAsStateWithLifecycle()
    val toast by router.toast.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val tabRoutes = AppTab.entries.map { it.route }
    val showBar = route == null || route in tabRoutes

    LaunchedEffect(pendingOneThing) {
        pendingOneThing?.let {
            nav.oneThing(it)
            onPendingHandled()
        }
    }

    CompositionLocalProvider(LocalNav provides nav) {
        Box(Modifier.fillMaxSize().background(Think.colors.canvas)) {
            Scaffold(
                containerColor = Think.colors.canvas,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = { if (showBar) ThinkTabBar(route, nav) },
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = AppTab.TODAY.route,
                    modifier = Modifier.padding(padding),
                    enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 10 } },
                    exitTransition = { fadeOut(tween(160)) },
                    popEnterTransition = { fadeIn(tween(220)) },
                    popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { it / 10 } },
                ) {
                    composable(AppTab.TODAY.route) { TodayScreen(vm) }
                    composable(AppTab.UPCOMING.route) { UpcomingScreen(vm) }
                    composable(AppTab.BRAIN_DUMP.route) { BrainDumpScreen(vm) }
                    composable(AppTab.FOCUS.route) { FocusScreen(vm, focus) }
                    composable(AppTab.SHOPPING.route) { ShoppingScreen(vm) }
                    composable(Routes.TASK, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                        Detail { TaskDetailScreen(vm, entry.arguments?.getString("id").orEmpty()) }
                    }
                    composable(Routes.ONE_THING, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                        Detail { OneThingScreen(vm, entry.arguments?.getString("id").orEmpty()) }
                    }
                    composable(Routes.LEFT_OFF) { Detail { LeftOffScreen(vm) } }
                    composable(Routes.NOTES) { Detail { NotesScreen(vm) } }
                    composable(Routes.SEARCH) { Detail { SearchScreen(vm) } }
                    composable(Routes.HISTORY) { Detail { HistoryScreen(vm) } }
                    composable(Routes.SETTINGS) { Detail { SettingsScreen(vm) } }
                    composable(Routes.PRIVACY) { Detail { PrivacyScreen() } }
                    composable(Routes.MORNING) { Detail { MorningBriefingScreen(vm) } }
                    composable(Routes.EVENING) { Detail { EveningResetScreen(vm) } }
                }
            }
            ToastHost(toast)
        }

        when (val current = sheet) {
            is Sheet.Stuck -> StuckSheet(vm, current.taskID)
            Sheet.TimeAvailable -> TimeAvailableSheet(vm)
            Sheet.RunningLate -> RunningLateSheet(vm)
            Sheet.Forgetting -> ForgettingSheet(vm)
            Sheet.BeforeYouGo -> BeforeYouGoSheet(vm)
            Sheet.QuickAdd -> QuickAddSheet(vm)
            Sheet.Paywall -> PaywallSheet(onDismiss = nav::dismissSheet)
            null -> Unit
        }
    }
}

@Composable
private fun Detail(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Think.colors.canvas).navigationBarsPadding()) { content() }
}

@Composable
private fun ThinkTabBar(route: String?, nav: Nav) {
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Think.colors.hairline))
        NavigationBar(containerColor = Think.colors.surface, tonalElevation = 0.dp) {
            AppTab.entries.forEach { tab ->
                val selected = route == tab.route || (route == null && tab == AppTab.TODAY)
                NavigationBarItem(
                    selected = selected,
                    onClick = { if (!selected) nav.selectTab(tab) },
                    icon = { Icon(tab.icon, contentDescription = null) },
                    label = { Text(tab.title, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Think.colors.accent,
                        selectedTextColor = Think.colors.accent,
                        indicatorColor = Think.colors.accentSoft,
                        unselectedIconColor = Think.colors.inkTertiary,
                        unselectedTextColor = Think.colors.inkTertiary,
                    ),
                )
            }
        }
    }
}
