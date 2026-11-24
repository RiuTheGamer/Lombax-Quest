// Archivo principal: ViewModel de creación y edición conectado a la prioridad de la misión en Room
package com.example.android.architecture.blueprints.todoapp.addedittask

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.android.architecture.blueprints.todoapp.R
import com.example.android.architecture.blueprints.todoapp.TodoDestinationsArgs
import com.example.android.architecture.blueprints.todoapp.data.TaskPriority
import com.example.android.architecture.blueprints.todoapp.data.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Bloque clave: Estado de UI con nivel de amenaza/prioridad incluido
data class AddEditTaskUiState(
    val title: String = "",
    val description: String = "",
    val priority: TaskPriority = TaskPriority.NORMAL,
    val isTaskCompleted: Boolean = false,
    val isLoading: Boolean = false,
    val userMessage: Int? = null,
    val isTaskSaved: Boolean = false
)

@HiltViewModel
class AddEditTaskViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val taskId: String? = savedStateHandle[TodoDestinationsArgs.TASK_ID_ARG]

    private val _uiState = MutableStateFlow(AddEditTaskUiState())
    val uiState: StateFlow<AddEditTaskUiState> = _uiState.asStateFlow()

    init {
        if (taskId != null) {
            loadTask(taskId)
        }
    }

    fun updateTitle(newTitle: String) {
        _uiState.update { it.copy(title = newTitle) }
    }

    fun updateDescription(newDescription: String) {
        _uiState.update { it.copy(description = newDescription) }
    }

    // Bloque clave: Actualiza la prioridad seleccionada en el formulario
    fun updatePriority(newPriority: TaskPriority) {
        _uiState.update { it.copy(priority = newPriority) }
    }

    fun snackbarMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    fun saveTask() {
        val currentState = _uiState.value
        if (currentState.title.isEmpty() || currentState.description.isEmpty()) {
            _uiState.update { it.copy(userMessage = R.string.empty_task_message) }
            return
        }
        if (taskId == null) {
            createTask(currentState)
        } else {
            updateTask(currentState)
        }
    }

    private fun createTask(state: AddEditTaskUiState) = viewModelScope.launch {
        taskRepository.createTask(
            title = state.title,
            description = state.description,
            priority = state.priority.name
        )
        _uiState.update { it.copy(isTaskSaved = true) }
    }

    private fun updateTask(state: AddEditTaskUiState) = viewModelScope.launch {
        val id = taskId ?: return@launch
        taskRepository.updateTask(
            taskId = id,
            title = state.title,
            description = state.description,
            priority = state.priority.name
        )
        _uiState.update { it.copy(isTaskSaved = true) }
    }

    private fun loadTask(id: String) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            taskRepository.getTask(id)?.let { task ->
                _uiState.update {
                    it.copy(
                        title = task.title,
                        description = task.description,
                        priority = task.priorityEnum,
                        isTaskCompleted = task.isCompleted,
                        isLoading = false
                    )
                }
            } ?: _uiState.update {
                it.copy(isLoading = false, userMessage = R.string.no_data)
            }
        }
    }
}