// SettingsViewModel.kt
package at.roboalex2.rdc.view_model

import android.app.Application
import android.telephony.PhoneNumberUtils
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import at.roboalex2.rdc.model.SettingsState
import at.roboalex2.rdc.persistence.AppDatabase
import at.roboalex2.rdc.persistence.entity.NumberItemEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
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
            .any { it.number == PhoneNumberUtils.normalizeNumber(number) }
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
