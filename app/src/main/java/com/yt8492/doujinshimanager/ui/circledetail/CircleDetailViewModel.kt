package com.yt8492.doujinshimanager.ui.circledetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yt8492.doujinshimanager.shared.domain.model.Circle
import com.yt8492.doujinshimanager.shared.domain.model.CircleId
import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiSearchSpec
import com.yt8492.doujinshimanager.shared.domain.repository.CircleRepository
import com.yt8492.doujinshimanager.shared.domain.repository.DoujinshiRepository
import com.yt8492.doujinshimanager.ui.bindingmodel.DoujinshiBindingModel
import com.yt8492.doujinshimanager.ui.converter.BindingModelConverter
import com.yt8492.doujinshimanager.ui.detail.DetailDestination
import com.yt8492.doujinshimanager.ui.lib.Destination
import com.yt8492.doujinshimanager.ui.lib.PopBackDestination
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CircleDetailViewModel(
    private val id: CircleId,
    private val circleRepository: CircleRepository,
    private val doujinshiRepository: DoujinshiRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CircleDetailUiState.init(id))
    val uiState: StateFlow<CircleDetailUiState> = _uiState.asStateFlow()

    private val _destination = MutableStateFlow<Destination?>(null)
    val destination: StateFlow<Destination?> = _destination.asStateFlow()

    init {
        viewModelScope.launch {
            val circleDeferred = async {
                circleRepository.get(id)
            }
            val spec = DoujinshiSearchSpec(
                circle = id,
            )
            val result = doujinshiRepository.search(spec)
            val circle = circleDeferred.await()
            _uiState.update {
                it.copy(
                    bindingModel = it.bindingModel.copy(
                        circle = circle,
                        inputCircleName = circle.name,
                        list = result.list.map {
                            BindingModelConverter.convertToDoujinshiBindingModel(it)
                        },
                        page = 0,
                        hasNextPage = result.hasNextPage,
                    )
                )
            }
        }
    }

    fun onInputName(value: String) {
        _uiState.update {
            it.copy(
                bindingModel = it.bindingModel.copy(
                    inputCircleName = value,
                ),
            )
        }
    }

    fun onClickSave() {
        val current = _uiState.value.bindingModel
        if (current.circle.name == current.inputCircleName) {
            return
        }
        viewModelScope.launch {
            val circle = Circle(
                id = id,
                name = current.inputCircleName,
            )
            circleRepository.update(circle)
            _uiState.update {
                it.copy(
                    bindingModel = it.bindingModel.copy(
                        circle = circle,
                        isEditMode = false,
                        list = it.bindingModel.list.map { doujinshi ->
                            doujinshi.copy(
                                circleName = circle.name,
                            )
                        }
                    ),
                )
            }
        }
    }

    fun onClickEdit() {
        _uiState.update {
            it.copy(
                bindingModel = it.bindingModel.copy(
                    isEditMode = true,
                )
            )
        }
    }

    fun loadMore() {
        viewModelScope.launch {
            val page = _uiState.value.bindingModel.page
            _uiState.update {
                it.copy(
                    loadingMore = true,
                )
            }
            val spec = DoujinshiSearchSpec(
                circle = id,
            )
            val result = doujinshiRepository.search(
                searchSpec = spec,
                page = page + 1,
            )
            _uiState.update {
                it.copy(
                    bindingModel = it.bindingModel.copy(
                        list = it.bindingModel.list + result.list.map {
                            BindingModelConverter.convertToDoujinshiBindingModel(it)
                        },
                        page = it.bindingModel.page + 1,
                        hasNextPage = result.hasNextPage,
                    ),
                    loadingMore = false,
                )
            }
        }
    }

    fun onClickItem(item: DoujinshiBindingModel) {
        _destination.value = DetailDestination(item.id.value)
    }

    fun onClickBack() {
        _destination.value = PopBackDestination
    }

    fun completeNavigation() {
        _destination.value = null
    }
}
