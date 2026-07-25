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
    val fileName: String = "Document.pdf",
    val progress: Float = 0f,
    val currentStage: String = "Initializing conversion...",
    val isCompleted: Boolean = false,
    val isCancelled: Boolean = false
)

@HiltViewModel
class ProcessingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val conversionTypeId: String = checkNotNull(savedStateHandle["conversionTypeId"])
    val workIdString: String = checkNotNull(savedStateHandle["workId"])
    private val _uiState = MutableStateFlow(ProcessingUiState())
    val uiState: StateFlow<ProcessingUiState> = _uiState.asStateFlow()

    init {
        val selectedType = ConversionType.defaultList.find { it.id == conversionTypeId }
        _uiState.update { it.copy(conversionType = selectedType) }
    }

    fun observeWork(context: android.content.Context) {
        val workManager = androidx.work.WorkManager.getInstance(context)
        val workId = java.util.UUID.fromString(workIdString)
        
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(workId).collect { workInfo ->
                if (workInfo != null) {
                    val progress = workInfo.progress.getInt("progress", 0) / 100f
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
                            isCompleted = workInfo.state == androidx.work.WorkInfo.State.SUCCEEDED,
                            isCancelled = workInfo.state == androidx.work.WorkInfo.State.CANCELLED || workInfo.state == androidx.work.WorkInfo.State.FAILED
                        )
                    }
                }
            }
        }
    }

    fun cancelConversion(context: android.content.Context) {
        val workManager = androidx.work.WorkManager.getInstance(context)
        val workId = java.util.UUID.fromString(workIdString)
        workManager.cancelWorkById(workId)
        _uiState.update { it.copy(isCancelled = true) }
    }
}
