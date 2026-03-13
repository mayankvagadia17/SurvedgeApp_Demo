package com.nexova.survedge.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.nexova.survedge.data.db.entity.LineEntity
import com.nexova.survedge.data.db.entity.LinePointCrossRef
import com.nexova.survedge.data.db.entity.LineWithPoints
import kotlinx.coroutines.flow.Flow

@Dao
interface LineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLine(line: LineEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinePointCrossRef(crossRef: LinePointCrossRef)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinePointCrossRefs(crossRefs: List<LinePointCrossRef>)

    @Query("SELECT * FROM lines WHERE projectId = :projectId AND (id = :code OR code = :code) LIMIT 1")
    suspend fun getLineByCode(projectId: Long, code: String): LineEntity?

    @Query("DELETE FROM line_points WHERE linePk = :linePk")
    suspend fun clearLinePoints(linePk: Long)

    @Transaction // Run in transaction to update whole line structure safely
    suspend fun updateLineWithPoints(line: LineEntity, crossRefs: List<LinePointCrossRef>) {
        val lineId = insertLine(line) // Returns PK
        clearLinePoints(lineId)
        insertLinePointCrossRefs(crossRefs)
    }

    @Query("SELECT * FROM line_points WHERE linePk = :linePk ORDER BY orderIndex ASC")
    suspend fun getLinePointCrossRefsByLinePk(linePk: Long): List<LinePointCrossRef>

    @Query("SELECT * FROM lines WHERE projectId = :projectId")
    fun getLinesByProject(projectId: Long): Flow<List<LineEntity>>

    @Transaction
    @Query("SELECT * FROM lines WHERE projectId = :projectId")
    fun getLinesWithPoints(projectId: Long): Flow<List<LineWithPoints>>
    
    @Delete
    suspend fun deleteLine(line: LineEntity)
}
