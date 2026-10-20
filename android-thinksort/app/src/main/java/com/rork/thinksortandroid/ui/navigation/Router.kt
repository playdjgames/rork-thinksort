package com.rork.thinksortandroid.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppTab(val route: String, val title: String, val icon: ImageVector) {
    TODAY("today", "Today", Icons.Rounded.Home),
    UPCOMING("upcoming", "Upcoming", Icons.Rounded.CalendarMonth),
    BRAIN_DUMP("dump", "Brain Dump", Icons.Rounded.Mic),
    FOCUS("focus", "Focus", Icons.Rounded.TrackChanges),
    SHOPPING("shopping", "Shopping", Icons.Rounded.ShoppingCart),
}

object Routes {
    const val TASK = "task/{id}"
    const val ONE_THING = "oneThing/{id}"
    const val NOTES = "notes"
    const val SEARCH = "search"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"
    const val LEFT_OFF = "leftOff"
    const val MORNING = "morning"
    const val EVENING = "evening"
}

/** Bottom sheets presented over the current screen. */
sealed interface Sheet {
    data class Stuck(val taskID: String) : Sheet
    data object TimeAvailable : Sheet
    data object RunningLate : Sheet
    data object Forgetting : Sheet
    data object BeforeYouGo : Sheet
    data object QuickAdd : Sheet
    data object Paywall : Sheet
}

/** Owns presented sheets and the transient confirmation toast. */
class RouterViewModel : ViewModel() {
    private val _sheet = MutableStateFlow<Sheet?>(null)
    val sheet: StateFlow<Sheet?> = _sheet.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private var toastJob: Job? = null

    fun show(sheet: Sheet) {
        _sheet.value = sheet
    }

    fun dismissSheet() {
        _sheet.value = null
    }

    fun flash(message: String) {
        _toast.value = message
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(3000)
            if (_toast.value == message) _toast.value = null
        }
    }
}

/** Single entry point for in-app navigation so every screen drives the same destinations. */
class Nav(private val controller: NavHostController, private val router: RouterViewModel) {
    fun selectTab(tab: AppTab) {
        controller.navigate(tab.route) {
            popUpTo(controller.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openTask(id: String) = controller.navigate("task/$id")
    fun oneThing(id: String) = controller.navigate("oneThing/$id")
    fun push(route: String) = controller.navigate(route) { launchSingleTop = true }
    fun back() {
        controller.popBackStack()
    }

    fun show(sheet: Sheet) = router.show(sheet)
    fun dismissSheet() = router.dismissSheet()
    fun flash(message: String) = router.flash(message)
}

val LocalNav = staticCompositionLocalOf<Nav> { error("Nav not provided") }
