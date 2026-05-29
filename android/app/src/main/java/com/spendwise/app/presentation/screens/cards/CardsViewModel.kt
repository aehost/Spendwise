package com.spendwise.app.presentation.screens.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.remote.api.UserApi
import com.spendwise.app.data.remote.dto.CreateCreditCardRequest
import com.spendwise.app.data.remote.dto.CreditCardDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CardsState(val cards: List<CreditCardDto> = emptyList(), val isLoading: Boolean = true, val showDialog: Boolean = false)

@HiltViewModel
class CardsViewModel @Inject constructor(private val api: UserApi) : ViewModel() {
    private val _state = MutableStateFlow(CardsState())
    val state: StateFlow<CardsState> = _state

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val r = api.getCreditCards()
                if (r.isSuccessful) _state.value = _state.value.copy(isLoading = false, cards = r.body()?.data ?: emptyList())
                else _state.value = _state.value.copy(isLoading = false)
            } catch (_: Exception) { _state.value = _state.value.copy(isLoading = false) }
        }
    }

    fun showAddDialog() { _state.value = _state.value.copy(showDialog = true) }
    fun hideDialog()    { _state.value = _state.value.copy(showDialog = false) }

    fun addCard(name: String, limit: Double, dueDay: Int, lastFour: String? = null) {
        viewModelScope.launch {
            try {
                api.createCreditCard(CreateCreditCardRequest(
                    name      = name,
                    creditLimit = limit,
                    dueDay    = dueDay,
                    // BUG FIX: require EXACTLY 4 digits. Previously "12" was accepted
                    // and stored as a 2-char "last four", breaking card matching.
                    lastFour  = lastFour
                        ?.filter { it.isDigit() }
                        ?.takeLast(4)
                        ?.takeIf { it.length == 4 }
                ))
                load()
            } catch (e: Exception) {
                android.util.Log.w("CardsViewModel", "addCard failed: ${e.message}")
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    /** Called by SmsSyncWorker-triggered refresh — just reloads the cards list. */
    fun refresh() { load() }

    fun delete(id: String) {
        viewModelScope.launch {
            try { api.deleteCreditCard(id); load() }
            catch (e: Exception) {
                android.util.Log.w("CardsViewModel", "delete failed: ${e.message}")
            }
        }
    }
}
