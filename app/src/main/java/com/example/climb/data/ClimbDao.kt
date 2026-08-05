package com.example.climb.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ClimbDao {
    @Insert
    suspend fun insert(climb: ClimbEntity): Long

    @Update
    suspend fun update(climb: ClimbEntity)

    @Delete
    suspend fun delete(climb: ClimbEntity)

    @Query("SELECT * FROM climbs WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeAll(userId: String): Flow<List<ClimbEntity>>

    @Query("SELECT * FROM climbs WHERE id = :id AND userId = :userId")
    fun observeById(id: Long, userId: String): Flow<ClimbEntity?>
}
