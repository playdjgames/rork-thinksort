package com.rork.thinksortandroid.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class FocusUiState(
    val selectedTaskID: String? = null,
    val minutes: Int = 25,
    val remaining: Int = 25 * 60,
    val isRunning: Boolean = false,
    val startedAt: Long? = null,
    val showKeepGoing: Boolean = false,
) {
    val progress: Float
        get() {
            val total = minutes * 60f
            return if (total <= 0f) 0f else (1f - remaining / total).coerceIn(0f, 1f)
        }
    val hasProgress: Boolean get() = isRunning || remaining < minutes * 60
}

/** Focus timer state. Lives above the tab so the timer keeps running while you look elsewhere. */
class FocusViewModel : ViewModel() {
    private val _state = MutableStateFlow(FocusUiState())
    val state: StateFlow<FocusUiState> = _state.asStateFlow()
    private var ticker: Job? = null
    private var initializedDefault = false

    fun applyDefaultMinutes(minutes: Int) {
        if (initializedDefault) return
        initializedDefault = true
        setMinutes(minutes)
    }

    fun select(taskID: String) = _state.update { it.copy(selectedTaskID = taskID) }

    fun setMinutes(minutes: Int) = _state.update {
        if (it.isRunning) it else it.copy(minutes = minutes, remaining = minutes * 60)
    }

    fun start() {
        _state.update {
            it.copy(
                isRunning = true,
                remaining = if (it.remaining <= 0) it.minutes * 60 else it.remaining,
                startedAt = it.startedAt ?: System.currentTimeMillis(),
            )
        }
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val current = _state.value
                if (!current.isRunning) break
                val next = (current.remaining - 1).coerceAtLeast(0)
                _state.value = if (next == 0) current.copy(remaining = 0, isRunning = false, showKeepGoing = true) else current.copy(remaining = next)
                if (next == 0) break
            }
        }
    }

    fun pause() {
        ticker?.cancel()
        _state.update { it.copy(isRunning = false) }
    }

    fun extend(by: Int) {
        _state.update { it.copy(remaining = by * 60, minutes = it.minutes + by, showKeepGoing = false) }
        start()
    }

    fun dismissKeepGoing() = _state.update { it.copy(showKeepGoing = false) }

    /** Ends the session and returns (elapsed minutes, startedAt) for recording. */
    fun finish(): Pair<Int, Long?> {
        ticker?.cancel()
        val current = _state.value
        val elapsed = (current.minutes * 60 - current.remaining).coerceAtLeast(0) / 60
        _state.update { it.copy(isRunning = false, startedAt = null, remaining = it.minutes * 60, showKeepGoing = false) }
        return elapsed to current.startedAt
    }
}
