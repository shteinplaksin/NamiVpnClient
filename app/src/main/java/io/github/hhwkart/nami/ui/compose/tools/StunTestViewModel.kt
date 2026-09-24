package io.github.hhwkart.nami.ui.compose.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import libcore.Libcore
import javax.inject.Inject

data class StunTestState(
    val server: String = "stun.voipgate.com:3478",
    val testing: Boolean = false,
    val result: String = "",
    val error: String? = null,
)

@HiltViewModel
class StunTestViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(StunTestState())
    val uiState: StateFlow<StunTestState> = _uiState.asStateFlow()

    fun setServer(server: String) = _uiState.update { it.copy(server = server, error = null) }

    fun test() {
        val server = _uiState.value.server.trim()
        if (server.isBlank()) {
            _uiState.update { it.copy(error = "STUN server is required") }
            return
        }
        _uiState.update { it.copy(testing = true, result = "", error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val result = Libcore.stunTest(server)
                if (result == null || !result.success) error(result?.text ?: "STUN test failed")
                result.text
            }.onSuccess { value ->
                _uiState.update { it.copy(testing = false, result = value) }
            }.onFailure { error ->
                _uiState.update { it.copy(testing = false, error = error.message ?: "STUN test failed") }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
