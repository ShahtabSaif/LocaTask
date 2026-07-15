package com.example.data

import kotlinx.coroutines.flow.Flow

class LocaTaskRepository(private val dao: LocaTaskDao) {
    val allTasksFlow: Flow<List<LocaTask>> = dao.getAllTasksFlow()

    suspend fun getActiveTasks(): List<LocaTask> = dao.getActiveTasks()

    suspend fun getTaskById(id: Int): LocaTask? = dao.getTaskById(id)

    suspend fun insertTask(task: LocaTask): Long = dao.insertTask(task)

    suspend fun updateTask(task: LocaTask) = dao.updateTask(task)

    suspend fun deleteTaskById(id: Int) = dao.deleteTaskById(id)

    suspend fun updateTriggeredStatus(id: Int, triggered: Boolean) = dao.updateTriggeredStatus(id, triggered)

    suspend fun updateActiveStatus(id: Int, active: Boolean) = dao.updateActiveStatus(id, active)
}
