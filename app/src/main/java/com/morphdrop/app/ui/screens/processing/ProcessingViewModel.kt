package com.morphdrop.app.ui.screens.processing

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.morphdrop.app.domain.model.ConversionType
import com.morphdrop.app.worker.ConversionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

data class ProcessingUiState(
    val conversionType: ConversionType? = null,
    val fileName: String = "Processing...",
    val progress: Float = 0f,
    val currentStage: String = "Initializing conversion...",
    val isCompleted: Boolean = false,
    val isCancelled: Boolean = false,
    /** Terminal failure: show real error UI, never auto-navigate. */
    val isFailed: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ProcessingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val conversionTypeId: String? = savedStateHandle["conversionTypeId"]
    val workIdString: String? = savedStateHandle["workId"]
    private val _uiState = MutableStateFlow(ProcessingUiState())
    val uiState: StateFlow<ProcessingUiState> = _uiState.asStateFlow()

    /** Single cancellable observation job — a second call replaces the first. */
    private var observeJob: Job? = null

    init {
        val selectedType = ConversionType.defaultList.find { it.id == conversionTypeId }
        _uiState.update { it.copy(conversionType = selectedType) }
    }

    fun observeWork(context: Context) {
        val idStr = workIdString
        if (idStr.isNullOrBlank()) {
            _uiState.update {
                it.copy(isFailed = true, errorMessage = "Missing conversion reference. Please try again.")
            }
            return
        }
        val workId = try {
            UUID.fromString(idStr)
        } catch (e: IllegalArgumentException) {
            _uiState.update {
                it.copy(isFailed = true, errorMessage = "Invalid conversion reference. Please try again.")
            }
            return
        }

        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            WorkManager.getInstance(context).getWorkInfoByIdFlow(workId).collect { workInfo ->
                if (workInfo == null) return@collect
                // Never update once we reached a terminal state.
                val current = _uiState.value
                if (current.isCompleted || current.isFailed || current.isCancelled) return@collect

                when (workInfo.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val progressName = workInfo.progress.getString("output_name")?.takeIf { it.isNotBlank() }
                        val outputUriStr = workInfo.outputData.getString(ConversionWorker.KEY_OUTPUT_URI)
                        val resolvedName = progressName ?: outputUriStr?.let { uriStr ->
                            withContext(Dispatchers.IO) {
                                runCatching {
                                    com.morphdrop.app.util.FileHelper.getFileName(context, Uri.parse(uriStr))
                                }.getOrNull()
                            }
                        }?.takeIf { it.isNotBlank() } ?: ""
                        _uiState.update {
                            it.copy(
                                progress = 100f,
                                currentStage = "Done",
                                fileName = resolvedName.ifBlank { it.fileName },
                                isCompleted = true
                            )
                        }
                    }
                    WorkInfo.State.FAILED -> {
                        val message = workInfo.outputData
                            .getString(ConversionWorker.KEY_ERROR)
                            ?.takeIf { it.isNotBlank() }
                            ?: "Conversion failed. Please try again."
                        _uiState.update {
                            it.copy(isFailed = true, errorMessage = message)
                        }
                    }
                    WorkInfo.State.CANCELLED -> {
                        _uiState.update { it.copy(isCancelled = true) }
                    }
                    else -> {
                        val rawProgress = workInfo.progress.getInt("progress", 0)
                        val outputName = workInfo.progress.getString("output_name") ?: ""
                        val currentProgress = (rawProgress / 100f).coerceIn(0f, 1f)

                        // Never let progress move backwards.
                        if (currentProgress * 100f < _uiState.value.progress) return@collect

                        val stage = when {
                            currentProgress < 0.1f -> "Initializing…"
                            currentProgress < 0.3f -> "Loading file…"
                            currentProgress < 0.7f -> "Converting…"
                            currentProgress < 0.95f -> "Finalizing…"
                            else -> "Almost there…"
                        }

                        _uiState.update {
                            it.copy(
                                progress = currentProgress * 100f,
                                currentStage = stage,
                                fileName = if (outputName.isNotBlank()) outputName else it.fileName
                            )
                        }
                    }
                }
            }
        }
    }

    fun cancelConversion(context: Context) {
        val idStr = workIdString ?: return
        val workId = try {
            UUID.fromString(idStr)
        } catch (e: IllegalArgumentException) {
            return
        }
        WorkManager.getInstance(context).cancelWorkById(workId)
        _uiState.update { it.copy(isCancelled = true) }
    }

    override fun onCleared() {
        observeJob?.cancel()
        super.onCleared()
    }
}
