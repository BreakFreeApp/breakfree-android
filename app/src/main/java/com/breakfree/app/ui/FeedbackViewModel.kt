package com.breakfree.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.breakfree.app.core.FeedbackManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File

data class FeedbackUiState(
    val feedbackText: String = "",
    val imageFile: File? = null,
    val isSending: Boolean = false,
    val isSuccess: Boolean = false
)

class FeedbackViewModel(app: Application) : AndroidViewModel(app) {
    
    private val _uiState = MutableStateFlow(FeedbackUiState())
    val uiState: StateFlow<FeedbackUiState> = _uiState

    private val _events = MutableSharedFlow<FeedbackEvent>()
    val events = _events.asSharedFlow()

    sealed class FeedbackEvent {
        object Success : FeedbackEvent()
        data class Error(val message: String) : FeedbackEvent()
    }

    fun onTextChange(text: String) {
        _uiState.value = _uiState.value.copy(feedbackText = text)
    }

    fun onImageSelected(file: File?) {
        _uiState.value = _uiState.value.copy(imageFile = file)
    }

    fun removeImage() {
        _uiState.value = _uiState.value.copy(imageFile = null)
    }

    fun submit() {
        val state = _uiState.value
        if (state.feedbackText.isBlank()) return

        viewModelScope.launch {
            _uiState.value = state.copy(isSending = true)
            try {
                FeedbackManager.submitFeedback(
                    getApplication(),
                    state.feedbackText,
                    state.imageFile
                )
                _events.emit(FeedbackEvent.Success)
            } catch (e: Exception) {
                _events.emit(FeedbackEvent.Error(e.message ?: "Failed to send feedback"))
            } finally {
                _uiState.value = _uiState.value.copy(isSending = false)
            }
        }
    }
}
