// Archivo principal: Modelo de tarea con prioridad/amenaza y persistencia en Room
package com.example.android.architecture.blueprints.todoapp.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class TaskPriority(val label: String) {
    NORMAL("Normal"),
    HIGH("Alta"),
    CRITICAL("Crítica")
}

// Bloque clave: Entidad de Room con columna priority por defecto para mantener compatibilidad
@Entity(tableName = "tasks")
data class Task(
    @ColumnInfo(name = "title") val title: String = "",
    @ColumnInfo(name = "description") val description: String = "",
    @ColumnInfo(name = "isCompleted") val isCompleted: Boolean = false,
    @ColumnInfo(name = "priority", defaultValue = "NORMAL") val priority: String = TaskPriority.NORMAL.name,
    @PrimaryKey @ColumnInfo(name = "entryid") val id: String = UUID.randomUUID().toString()
) {
    val titleForList: String
        get() = title.ifEmpty { description }

    val isActive: Boolean
        get() = !isCompleted

    val isEmpty: Boolean
        get() = title.isEmpty() && description.isEmpty()

    val priorityEnum: TaskPriority
        get() = runCatching { TaskPriority.valueOf(priority) }.getOrDefault(TaskPriority.NORMAL)
}