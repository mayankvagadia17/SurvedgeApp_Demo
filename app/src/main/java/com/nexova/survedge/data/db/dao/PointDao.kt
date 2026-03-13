package com.nexova.survedge.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nexova.survedge.data.db.entity.PointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PointDao {
    @Query("SELECT * FROM points WHERE projectId = :projectId ORDER BY ts ASC")
    fun getPointsByProject(projectId: Long): Flow<List<PointEntity>>

    @Query("SELECT * FROM points WHERE id = :pointId")
    suspend fun getPointById(pointId: String): PointEntity?

    @Query("SELECT * FROM points WHERE pk = :pk")
    suspend fun getPointByPk(pk: Long): PointEntity?

    @Query("SELECT * FROM points WHERE pk IN (:pks)")
    suspend fun getPointsByPks(pks: List<Long>): List<PointEntity>

    @Query("SELECT * FROM points WHERE projectId = :projectId AND id = :id LIMIT 1")
    suspend fun getPointByCode(projectId: Long, id: String): PointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: PointEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<PointEntity>)

    @Update
    suspend fun updatePoint(point: PointEntity)

    @Delete
    suspend fun deletePoint(point: PointEntity)
    
    @Query("DELETE FROM points WHERE projectId = :projectId")
    suspend fun deletePointsByProject(projectId: Long)
}
