package com.datausage.monitor.ui.screen.importexport

import android.app.Application
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datausage.monitor.data.repository.ImportExportRepository
import com.datausage.monitor.data.repository.ImportMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImportExportState(
    val message: String = "",
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val showImportModeDialog: Boolean = false,
    val pendingImportUri: Uri? = null,
    val pendingImportFormat: String = "json"
)

@HiltViewModel
class ImportExportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val application: Application,
    private val importExportRepository: ImportExportRepository
) : ViewModel() {

    val profileId: Long = savedStateHandle.get<Long>("profileId") ?: -1L

    private val _state = MutableStateFlow(ImportExportState())
    val state: StateFlow<ImportExportState> = _state

    fun exportJson(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isExporting = true, message = "")
            try {
                val outputStream = application.contentResolver.openOutputStream(uri)
                if (outputStream != null) {
                    importExportRepository.exportToJson(outputStream, listOf(profileId))
                    outputStream.close()
                    _state.value = _state.value.copy(
                        isExporting = false, message = "Exported JSON successfully"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isExporting = false, message = "Export failed: ${e.message}"
                )
            }
        }
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isExporting = true, message = "")
            try {
                val outputStream = application.contentResolver.openOutputStream(uri)
                if (outputStream != null) {
                    importExportRepository.exportToCsv(outputStream, profileId)
                    outputStream.close()
                    _state.value = _state.value.copy(
                        isExporting = false, message = "Exported CSV successfully"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isExporting = false, message = "Export failed: ${e.message}"
                )
            }
        }
    }

    fun onImportFileSelected(uri: Uri, format: String) {
        _state.value = _state.value.copy(
            showImportModeDialog = true,
            pendingImportUri = uri,
            pendingImportFormat = format
        )
    }

    fun dismissImportModeDialog() {
        _state.value = _state.value.copy(
            showImportModeDialog = false,
            pendingImportUri = null
        )
    }

    fun executeImport(mode: ImportMode) {
        val uri = _state.value.pendingImportUri ?: return
        val format = _state.value.pendingImportFormat

        viewModelScope.launch {
            _state.value = _state.value.copy(
                isImporting = true, message = "", showImportModeDialog = false
            )
            try {
                val inputStream = application.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val count = if (format == "json") {
                        importExportRepository.importFromJson(inputStream, mode)
                    } else {
                        importExportRepository.importFromCsv(inputStream, mode, profileId)
                    }
                    inputStream.close()
                    _state.value = _state.value.copy(
                        isImporting = false,
                        message = "Imported $count ${if (format == "json") "profile(s)" else "row(s)"} (${mode.name})"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isImporting = false, message = "Import failed: ${e.message}"
                )
            }
        }
    }
}
