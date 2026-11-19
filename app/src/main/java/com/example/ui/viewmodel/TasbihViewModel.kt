package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NoorAlMushafApp
import com.example.data.local.TasbihItemEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TasbihViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NoorAlMushafApp
    private val tasbihRepo = app.tasbihRepository
    private val prefsRepo = app.preferencesRepository

    val items: StateFlow<List<TasbihItemEntity>> = tasbihRepo.allTasbihItems.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val totalLifetimeCount: StateFlow<Long> = tasbihRepo.totalLifetimeTasbih.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0L
    )

    private val _roundCompletedEvents = MutableSharedFlow<String>()
    val roundCompletedEvents = _roundCompletedEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            tasbihRepo.ensureSeeded()
        }
    }

    fun selectTasbih(id: Int) {
        viewModelScope.launch {
            prefsRepo.updateTasbihPreferences(selectedId = id)
        }
    }

    fun increment(item: TasbihItemEntity) {
        viewModelScope.launch {
            val completed = tasbihRepo.increment(item)
            if (completed) {
                _roundCompletedEvents.emit(item.phrase)
            }
        }
    }

    fun resetCurrent(item: TasbihItemEntity) {
        viewModelScope.launch {
            tasbihRepo.resetCurrent(item)
        }
    }

    fun updateTarget(item: TasbihItemEntity, target: Int) {
        viewModelScope.launch {
            tasbihRepo.updateTarget(item, target)
        }
    }

    fun addCustomTasbih(phrase: String, target: Int) {
        if (phrase.isBlank()) return
        viewModelScope.launch {
            val newId = tasbihRepo.addCustom(phrase, target).toInt()
            prefsRepo.updateTasbihPreferences(selectedId = newId)
        }
    }

    fun deleteCustom(id: Int) {
        viewModelScope.launch {
            tasbihRepo.deleteCustom(id)
        }
    }

    fun toggleSound(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.updateTasbihPreferences(sound = enabled)
        }
    }

    fun toggleVibration(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.updateTasbihPreferences(vibration = enabled)
        }
    }
}
