// Archivo principal: ViewModel de lista con búsqueda en tiempo real, prioridad y Undo real en Room
package com.example.android.architecture.blueprints.todoapp.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.android.architecture.blueprints.todoapp.ADD_EDIT_RESULT_OK
import com.example.android.architecture.blueprints.todoapp.DELETE_RESULT_OK
import com.example.android.architecture.blueprints.todoapp.EDIT_RESULT_OK
import com.example.android.architecture.blueprints.todoapp.R
import com.example.android.architecture.blueprints.todoapp.data.Task
import com.example.android.architecture.blueprints.todoapp.data.TaskRepository
import com.example.android.architecture.blueprints.todoapp.tasks.TasksFilterType.ACTIVE_TASKS
import com.example.android.architecture.blueprints.todoapp.tasks.TasksFilterType.ALL_TASKS
import com.example.android.architecture.blueprints.todoapp.tasks.TasksFilterType.COMPLETED_TASKS
import com.example.android.architecture.blueprints.todoapp.util.Async
import com.example.android.architecture.blueprints.todoapp.util.WhileUiSubscribed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TasksUiState(
    val items: List<Task> = emptyList(),
    val isLoading: Boolean = false,
    val filteringUiInfo: FilteringUiInfo = FilteringUiInfo(),
    val userMessage: Int? = null,
    val searchQuery: String = "",
    val canUndoDelete: Boolean = false
)

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _savedFilterType =
        savedStateHandle.getStateFlow(TASKS_FILTER_SAVED_STATE_KEY, ALL_TASKS)
    private val _searchQuery = MutableStateFlow("")
    private val _lastDeletedTasks = MutableStateFlow<List<Task>>(emptyList())

    private val _filterUiInfo = _savedFilterType.map { getFilterUiInfo(it) }.distinctUntilChanged()
    private val _userMessage: MutableStateFlow<Int?> = MutableStateFlow(null)
    private val _isLoading = MutableStateFlow(false)

    // Bloque clave: Combina flujo de Room, filtro de estado y búsqueda instantánea por título o descripción
    private val _filteredTasksAsync =
        combine(
            taskRepository.getTasksStream(),
            _savedFilterType,
            _searchQuery
        ) { tasks, type, query ->
            filterAndSearchTasks(tasks, type, query)
        }
            .map { Async.Success(it) }
            .catch<Async<List<Task>>> { emit(Async.Error(R.string.loading_tasks_error)) }

    val uiState: StateFlow<TasksUiState> = combine(
        _filterUiInfo,
        _isLoading,
        _userMessage,
        _filteredTasksAsync,
        _searchQuery,
        _lastDeletedTasks
    ) { values ->
        val filterUiInfo = values[0] as FilteringUiInfo
        val isLoading = values[1] as Boolean
        val userMessage = values[2] as Int?
        @Suppress("UNCHECKED_CAST")
        val tasksAsync = values[3] as Async<List<Task>>
        val query = values[4] as String
        @Suppress("UNCHECKED_CAST")
        val deletedBackup = values[5] as List<Task>

        when (tasksAsync) {
            Async.Loading -> TasksUiState(isLoading = true, searchQuery = query)
            is Async.Error -> TasksUiState(
                userMessage = tasksAsync.errorMessage,
                searchQuery = query,
                canUndoDelete = deletedBackup.isNotEmpty()
            )
            is Async.Success -> TasksUiState(
                items = tasksAsync.data,
                filteringUiInfo = filterUiInfo,
                isLoading = isLoading,
                userMessage = userMessage,
                searchQuery = query,
                canUndoDelete = deletedBackup.isNotEmpty()
            )
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = WhileUiSubscribed,
            initialValue = TasksUiState(isLoading = true)
        )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFiltering(requestType: TasksFilterType) {
        savedStateHandle[TASKS_FILTER_SAVED_STATE_KEY] = requestType
    }

    // Bloque clave: Respalda tareas completadas antes de borrarlas para permitir Deshacer real
    fun clearCompletedTasks() {
        viewModelScope.launch {
            val currentItems = (uiState.value.items).filter { it.isCompleted }
            if (currentItems.isNotEmpty()) {
                _lastDeletedTasks.value = currentItems
            }
            taskRepository.clearCompletedTasks()
            showSnackbarMessage(R.string.completed_tasks_cleared)
            refresh()
        }
    }

    // Bloque clave: Restaura en Room las últimas tareas eliminadas al pulsar Deshacer
    fun undoDelete() {
        val toRestore = _lastDeletedTasks.value
        if (toRestore.isEmpty()) return
        viewModelScope.launch {
            toRestore.forEach { task ->
                taskRepository.createTask(
                    title = task.title,
                    description = task.description
                )
            }
            _lastDeletedTasks.value = emptyList()
            refresh()
        }
    }

    fun completeTask(task: Task, completed: Boolean) = viewModelScope.launch {
        if (completed) {
            taskRepository.completeTask(task.id)
            showSnackbarMessage(R.string.task_marked_complete)
        } else {
            taskRepository.activateTask(task.id)
            showSnackbarMessage(R.string.task_marked_active)
        }
    }

    fun showEditResultMessage(result: Int) {
        when (result) {
            EDIT_RESULT_OK -> showSnackbarMessage(R.string.successfully_saved_task_message)
            ADD_EDIT_RESULT_OK -> showSnackbarMessage(R.string.successfully_added_task_message)
            DELETE_RESULT_OK -> showSnackbarMessage(R.string.successfully_deleted_task_message)
        }
    }

    fun snackbarMessageShown() {
        _userMessage.value = null
    }

    private fun showSnackbarMessage(message: Int) {
        _userMessage.value = message
    }

    fun refresh() {
        _isLoading.value = true
        viewModelScope.launch {
            taskRepository.refresh()
            _isLoading.value = false
        }
    }

    private fun filterAndSearchTasks(
        tasks: List<Task>,
        filteringType: TasksFilterType,
        query: String
    ): List<Task> {
        val normalizedQuery = query.trim().lowercase()
        return tasks.filter { task ->
            val matchesFilter = when (filteringType) {
                ALL_TASKS -> true
                ACTIVE_TASKS -> task.isActive
                COMPLETED_TASKS -> task.isCompleted
            }
            val matchesSearch = normalizedQuery.isEmpty() ||
                    task.title.lowercase().contains(normalizedQuery) ||
                    task.description.lowercase().contains(normalizedQuery)
            matchesFilter && matchesSearch
        }
    }

    private fun getFilterUiInfo(requestType: TasksFilterType): FilteringUiInfo =
        when (requestType) {
            ALL_TASKS -> FilteringUiInfo(
                R.string.label_all, R.string.no_tasks_all, R.drawable.logo_no_fill
            )
            ACTIVE_TASKS -> FilteringUiInfo(
                R.string.label_active, R.string.no_tasks_active, R.drawable.ic_check_circle_96dp
            )
            COMPLETED_TASKS -> FilteringUiInfo(
                R.string.label_completed, R.string.no_tasks_completed, R.drawable.ic_verified_user_96dp
            )
        }
}

const val TASKS_FILTER_SAVED_STATE_KEY = "TASKS_FILTER_SAVED_STATE_KEY"

data class FilteringUiInfo(
    val currentFilteringLabel: Int = R.string.label_all,
    val noTasksLabel: Int = R.string.no_tasks_all,
    val noTaskIconRes: Int = R.drawable.logo_no_fill,
)