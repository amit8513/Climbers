package com.example.climb.data

import kotlinx.coroutines.flow.Flow
import java.io.File

class ClimbRepository(private val dao: ClimbDao) {
    fun observeAll(): Flow<List<ClimbEntity>> = dao.observeAll()

    fun observeById(id: Long): Flow<ClimbEntity?> = dao.observeById(id)

    suspend fun save(climb: ClimbEntity): Long = dao.insert(climb)

    suspend fun update(climb: ClimbEntity) = dao.update(climb)

    suspend fun delete(climb: ClimbEntity) {
        dao.delete(climb)
        File(climb.videoPath).delete()
    }
}
