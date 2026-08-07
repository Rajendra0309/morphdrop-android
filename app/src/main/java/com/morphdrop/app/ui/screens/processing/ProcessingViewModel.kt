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
                    // Critical Fix: Do not update state if we are already in a terminal success state
                    if (_uiState.value.isCompleted) return@collect

                    val rawProgress = workInfo.progress.getInt("progress", 0)
                    val outputName = workInfo.progress.getString("output_name") ?: ""
                    
                    val isSucceeded = workInfo.state == androidx.work.WorkInfo.State.SUCCEEDED
                    
                    val currentProgress = if (isSucceeded) 1.0f else (rawProgress / 100f).coerceIn(0f, 1f)
                    
                    // Logic to prevent progress from decreasing
                    val previousProgress = _uiState.value.progress / 100f
                    if (currentProgress < previousProgress && !isSucceeded) {
                         return@collect
                    }
                    
                    val stage = when {
                        isSucceeded -> "Success!"
                        currentProgress < 0.1f -> "Initializing..."
                        currentProgress < 0.3f -> "Loading file..."
                        currentProgress < 0.7f -> "Converting..."
                        currentProgress < 0.95f -> "Finalizing..."
                        else -> "Processing..."
                    }
                    
                    _uiState.update {
                        it.copy(
                            progress = currentProgress * 100f,
                            currentStage = stage,
                            fileName = if (outputName.isNotBlank()) outputName else it.fileName,
                            isCompleted = isSucceeded,
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
