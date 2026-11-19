package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.data.catalog.AdhkarCatalog
import com.example.domain.model.AdhkarCategory
import com.example.domain.model.DhikrItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AdhkarUiState(
    val selectedCategory: AdhkarCategory = AdhkarCategory.MORNING,
    val remainingCounts: Map<String, Int> = emptyMap()
) {
    val categoryItems: List<DhikrItem>
        get() = AdhkarCatalog.allDhikr.filter { it.category == selectedCategory }

    fun remainingFor(item: DhikrItem): Int = remainingCounts[item.id] ?: item.targetCount
}

class AdhkarViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AdhkarUiState())
    val uiState: StateFlow<AdhkarUiState> = _uiState.asStateFlow()

    fun selectCategory(category: AdhkarCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun decrementDhikr(item: DhikrItem) {
        _uiState.update { current ->
            val rem = current.remainingFor(item)
            if (rem <= 0) return@update current
            current.copy(remainingCounts = current.remainingCounts + (item.id to (rem - 1)))
        }
    }

    fun resetCategory() {
        _uiState.update { current ->
            val idsInCategory = current.categoryItems.map { it.id }.toSet()
            current.copy(remainingCounts = current.remainingCounts - idsInCategory)
        }
    }
}
