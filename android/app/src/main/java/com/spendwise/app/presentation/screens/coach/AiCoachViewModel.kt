package com.spendwise.app.presentation.screens.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.remote.api.AiCoachApi
import com.spendwise.app.data.remote.dto.AiCoachMessage
import com.spendwise.app.data.remote.dto.AiCoachRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiCoachState(
    val messages: List<ChatMessage> = emptyList(),
    val isTyping: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AiCoachViewModel @Inject constructor(
    private val api: AiCoachApi
) : ViewModel() {
    private val _state = MutableStateFlow(AiCoachState())
    val state: StateFlow<AiCoachState> = _state

    fun sendMessage(userText: String) {
        val updated = _state.value.messages + ChatMessage("user", userText)
        _state.value = _state.value.copy(messages = updated, isTyping = true, error = null)

        viewModelScope.launch {
            try {
                val history = updated.dropLast(1).map { AiCoachMessage(it.role, it.content) }
                val resp = api.chat(AiCoachRequest(message = userText, history = history))
                if (resp.isSuccessful && resp.body()?.success == true) {
                    val reply = resp.body()?.data?.reply ?: "I couldn't generate a response."
                    _state.value = _state.value.copy(
                        messages = updated + ChatMessage("assistant", reply),
                        isTyping = false
                    )
                } else {
                    _state.value = _state.value.copy(
                        isTyping = false,
                        error = resp.body()?.error ?: "Failed to get response. Is ANTHROPIC_API_KEY set?"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isTyping = false,
                    error = "Network error: ${e.message}"
                )
            }
        }
    }
}
