// SettingsViewModel.kt
package at.roboalex2.rdc.view_model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import at.roboalex2.rdc.model.NumberItem
import at.roboalex2.rdc.model.SettingsState
import at.roboalex2.rdc.persistence.AppDatabase
import at.roboalex2.rdc.persistence.entity.NumberItemEntity
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.getDatabase(app).numberDao()

    private val _uiState = MutableStateFlow(SettingsState())
    val uiState: StateFlow<SettingsState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            dao.getAllNumbers()
                .map { entities -> entities.map { it.toModel() } }
                .collect { modelList ->
                    _uiState.value = _uiState.value.copy(numbers = modelList)
                }
        }
    }

    fun setTabIndex(index: Int) {
        _uiState.update { it.copy(tabIndex = index) }
    }

    fun addNumber(number: String) = viewModelScope.launch {
        val exists = dao.getAllNumbers()
            .first()
            .any { it.number == number }
        if (!exists) {
            dao.upsertNumber(NumberItemEntity(number, permissions = emptyList()))
        }
    }

    fun deleteNumber(number: String) = viewModelScope.launch {
        dao.deleteNumber(number)
    }

    fun addPermission(number: String, permission: String) = viewModelScope.launch {
        addNumber(number) // Ensure the number exists
        val current = dao.getAllNumbers()
            .first()
            .find { it.number == number }
            ?.permissions
            ?: emptyList()

        // Only add if it isn’t already in the list
        if (permission !in current) {
            dao.upsertNumber(
                NumberItemEntity(
                    number = number,
                    permissions = (current + permission).distinct()
                )
            )
        }
    }

    fun removePermission(number: String, permission: String) = viewModelScope.launch {
        val current = dao.getAllNumbers().first()
            .find { it.number == number }
            ?.permissions
            ?: emptyList()

        dao.upsertNumber(NumberItemEntity(number, current - permission))
    }
}
