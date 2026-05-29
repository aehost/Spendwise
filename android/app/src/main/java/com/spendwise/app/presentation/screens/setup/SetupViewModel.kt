package com.spendwise.app.presentation.screens.setup

import androidx.lifecycle.ViewModel
import com.spendwise.app.data.local.preferences.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(todayString())
    val selectedDate: StateFlow<String> = _selectedDate

    fun setDate(d: String) { _selectedDate.value = d }

    fun complete() {
        val ms = parseToLocalMidnightMs(_selectedDate.value)
        tokenManager.smsScanFromMs = ms
        tokenManager.setupDone = true
    }

    fun completeWithToday() {
        tokenManager.smsScanFromMs = todayMidnightMs()
        tokenManager.setupDone = true
    }

    private fun todayString(): String = LocalDate.now().toString()

    private fun todayMidnightMs(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun parseToLocalMidnightMs(dateStr: String): Long {
        return try {
            val parsed = LocalDate.parse(dateStr)
            // BUG FIX: a future date would set smsScanFromMs ahead of "now",
            // silently blocking ALL SMS import until that date arrives. Clamp any
            // future selection back to today.
            val safeDate = if (parsed.isAfter(LocalDate.now())) LocalDate.now() else parsed
            safeDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            todayMidnightMs()
        }
    }
}
