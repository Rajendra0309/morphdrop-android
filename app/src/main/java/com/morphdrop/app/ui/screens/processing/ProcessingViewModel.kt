package com.morphdrop.app.ui.screens.processing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morphdrop.app.domain.model.ConversionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProcessingUiState(
    val conversionType: ConversionType? = null,
    val fileName: String = "Processing...",
    val progress: Float = 0f,
    val currentStage: String = "Initializing conversion...",
    val isCompleted: Boolean = false,
    val isCancelled: Boolean = false
)

@HiltViewModel
class ProcessingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val conversionTypeId: String? = savedStateHandle["conversionTypeId"]
    val workIdString: String? = savedStateHandle["workId"]
    private val _uiState = MutableStateFlow(ProcessingUiState())
    val uiState: StateFlow<ProcessingUiState> = _uiState.asStateFlow()

    init {
        val selectedType = ConversionType.defaultList.find { it.id == conversionTypeId }
        _uiState.update { it.copy(conversionType = selectedType) }
    }

    fun observeWork(context: android.content.Context) {
        val idStr = workIdString ?: return
        val workManager = androidx.work.WorkManager.getInstance(context)
        val workId = try {
            java.util.UUID.fromString(idStr)
        } catch (e: Exception) {
            return
        }
        
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(workId).collect { workInfo ->
                if (workInfo != null) {
                    val rawProgress = workInfo.progress.getInt("progress", 0)
                    val outputName = workInfo.progress.getString("output_name") ?: ""
                    val progress = (rawProgress / 100f).coerceIn(0f, 1f)
                    val stage = when {
                        progress < 0.2f -> "Initializing conversion..."
                        progress < 0.5f -> "Processing content..."
                        progress < 0.9f -> "Rendering output document..."
                        else -> "Finalizing..."
                    }
                    
                    _uiState.update {
                        it.copy(
                            progress = progress,
                            currentStage = stage,
                            fileName = outputName.ifBlank { it.conversionType?.name ?: it.fileName },
                            isCompleted = workInfo.state == androidx.work.WorkInfo.State.SUCCEEDED,
                            isCancelled = workInfo.state == androidx.work.WorkInfo.State.CANCELLED || workInfo.state == androidx.work.WorkInfo.State.FAILED
                        )
                    }
                }
            }
        }
    }

    fun cancelConversion(context: android.content.Context) {
        val idStr = workIdString ?: return
        val workManager = androidx.work.WorkManager.getInstance(context)
        val workId = try {
            java.util.UUID.fromString(idStr)
        } catch (e: Exception) {
            return
        }
        workManager.cancelWorkById(workId)
        _uiState.update { it.copy(isCancelled = true) }
    }
}
