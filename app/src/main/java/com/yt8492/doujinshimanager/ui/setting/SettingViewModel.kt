package com.yt8492.doujinshimanager.ui.setting

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yt8492.doujinshimanager.ui.lib.Destination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingViewModel(
    private val exportService: ExportService,
    private val importService: ImportService
) : ViewModel() {
    private val _destination = MutableStateFlow<Destination?>(null)
    val destination: StateFlow<Destination?> = _destination.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportResult = MutableStateFlow<Result<Unit>?>(null)
    val exportResult: StateFlow<Result<Unit>?> = _exportResult.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importResult = MutableStateFlow<Result<Unit>?>(null)
    val importResult: StateFlow<Result<Unit>?> = _importResult.asStateFlow()

    fun onCompleteNavigation() {
        _destination.value = null
    }

    fun onClickExportData() {
        // ActivityResultContractsを使用するため、UIから呼び出される
    }

    fun exportData(uri: Uri) {
        viewModelScope.launch {
            _isExporting.value = true
            _exportResult.value = null
            
            val result = exportService.exportData(uri)
            _exportResult.value = result
            _isExporting.value = false
        }
    }

    fun clearExportResult() {
        _exportResult.value = null
    }

    fun onClickImportData() {
        // ActivityResultContractsを使用するため、UIから呼び出される
    }

    fun importData(uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            _importResult.value = null
            
            val result = importService.importData(uri)
            _importResult.value = result
            _isImporting.value = false
        }
    }

    fun clearImportResult() {
        _importResult.value = null
    }
}
