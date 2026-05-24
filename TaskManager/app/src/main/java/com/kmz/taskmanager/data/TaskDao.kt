package com.kmz.taskmanager.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY isDone ASC, priority DESC, dueDate ASC")
    fun getAllTasks(): Flow<List<Task>>

    @Query(
            "SELECT * FROM tasks WHERE folderId = :folderId ORDER BY isDone ASC, priority DESC, dueDate ASC"
    )
    fun getTasksByFolder(folderId: Long): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun getTaskById(id: Long): Task?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTask(task: Task): Long

    @Update suspend fun updateTask(task: Task)

    @Delete suspend fun deleteTask(task: Task)

    @Query("SELECT * FROM folders") fun getAllFolders(): Flow<List<Folder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertFolder(folder: Folder): Long

    @Update suspend fun updateFolder(folder: Folder)
    @Delete suspend fun deleteFolder(folder: Folder)
    @Query("SELECT * FROM tasks") suspend fun getAllTasksSync(): List<Task>

    @Query("SELECT * FROM folders") suspend fun getAllFoldersSync(): List<Folder>

    @Query("DELETE FROM tasks") suspend fun nukeTasks()

    @Query("DELETE FROM folders") suspend fun nukeFolders()
}
