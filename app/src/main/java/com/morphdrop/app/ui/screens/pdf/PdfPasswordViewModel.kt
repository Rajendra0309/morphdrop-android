package com.morphdrop.app.ui.screens.pdf

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.morphdrop.app.util.FileHelper
import com.morphdrop.app.worker.ConversionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject

data class PdfPasswordState(
    val selectedFile: Uri? = null,
    val fileName: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val outputFileName: String = "",
    val action: String = "ADD_PASSWORD" // "ADD_PASSWORD" or "REMOVE_PASSWORD"
)

@HiltViewModel
class PdfPasswordViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow(PdfPasswordState())
    val state: StateFlow<PdfPasswordState> = _state.asStateFlow()

    fun onFileSelected(uri: Uri?) {
        val name = uri?.let { FileHelper.getFileName(context, it) } ?: ""
        _state.update { it.copy(
            selectedFile = uri,
            fileName = name,
            outputFileName = if (name.isNotBlank()) name.substringBeforeLast(".") + "_protected.pdf" else ""
        ) }
    }

    fun onPasswordChanged(password: String) {
        _state.update { it.copy(password = password) }
    }

    fun onConfirmPasswordChanged(password: String) {
        _state.update { it.copy(confirmPassword = password) }
    }

    fun onOutputFileNameChanged(name: String) {
        _state.update { it.copy(outputFileName = name) }
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
                ConversionWorker.KEY_PASSWORD to currentState.password,
                ConversionWorker.KEY_ACTION to currentState.action,
                ConversionWorker.KEY_OUTPUT_FILE_NAME to currentState.outputFileName
            ))
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        return workRequest.id
    }
}
