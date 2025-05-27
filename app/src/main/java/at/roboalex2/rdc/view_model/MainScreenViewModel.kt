package at.roboalex2.rdc.view_model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import at.roboalex2.rdc.model.Command
import at.roboalex2.rdc.model.CommandState
import at.roboalex2.rdc.persistence.AppDatabase
import at.roboalex2.rdc.persistence.entity.CommandEntity
import kotlinx.coroutines.launch

class MainScreenViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.getDatabase(app).commandDao()
    private val _uiState = MutableStateFlow(CommandState())

    init {
        viewModelScope.launch {
            dao.getAllCommands().collect { entities ->
                // map to your model and push into state
                val models = entities.map { it.toModel() }
                _uiState.value = CommandState(commands = models)
            }
        }
    }

    fun logCommand(cmd: Command) = viewModelScope.launch {
        dao.insertCommand(
            CommandEntity(
                dateTime = cmd.dateTime,
                issuer   = cmd.issuer,
                type     = cmd.type
            )
        )
    }

    val uiState: StateFlow<CommandState> = _uiState.asStateFlow()
}