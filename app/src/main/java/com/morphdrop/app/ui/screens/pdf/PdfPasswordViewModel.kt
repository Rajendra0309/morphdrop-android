package com.morphdrop.app.ui.screens.pdf

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.morphdrop.app.worker.ConversionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject

data class PdfPasswordState(
    val selectedFile: Uri? = null,
    val password: String = "",
    val confirmPassword: String = "",
    val action: String = "add" // "add" or "remove"
)

@HiltViewModel
class PdfPasswordViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(PdfPasswordState())
    val state: StateFlow<PdfPasswordState> = _state.asStateFlow()

    fun onFileSelected(uri: Uri?) {
        _state.update { it.copy(selectedFile = uri) }
    }

    fun onPasswordChanged(password: String) {
        _state.update { it.copy(password = password) }
    }

    fun onConfirmPasswordChanged(password: String) {
        _state.update { it.copy(confirmPassword = password) }
    }

    fun onActionChanged(action: String) {
        _state.update { it.copy(action = action) }
    }

    fun startProtect(context: Context): UUID? {
        val currentState = _state.value
        val uri = currentState.selectedFile ?: return null

        val workRequest = OneTimeWorkRequestBuilder<ConversionWorker>()
            .setInputData(workDataOf(
                ConversionWorker.KEY_CONVERSION_TYPE to "protect_pdf",
                ConversionWorker.KEY_INPUT_URI to uri.toString(),
                "password" to currentState.password,
                "action" to currentState.action
            ))
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        return workRequest.id
    }
}
