package com.yt8492.doujinshimanager.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yt8492.doujinshimanager.ui.lib.Destination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingViewModel : ViewModel() {
    private val _destination = MutableStateFlow<Destination?>(null)
    val destination: StateFlow<Destination?> = _destination.asStateFlow()

    fun onCompleteNavigation() {
        _destination.value = null
    }

    fun onClickExportData() {
        // TODO: データエクスポート処理を実装
    }

    fun onClickImportData() {
        // TODO: データインポート処理を実装
    }
}
