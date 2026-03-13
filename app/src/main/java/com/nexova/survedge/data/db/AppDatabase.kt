package com.nexova.survedge.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.nexova.survedge.data.db.converter.Converters
import com.nexova.survedge.data.db.dao.LineDao
import com.nexova.survedge.data.db.dao.PointDao
import com.nexova.survedge.data.db.dao.ProjectDao
import com.nexova.survedge.data.db.entity.LineEntity
import com.nexova.survedge.data.db.entity.LinePointCrossRef
import com.nexova.survedge.data.db.entity.PointEntity
import com.nexova.survedge.data.db.entity.ProjectEntity

@Database(
    entities = [
        ProjectEntity::class,
        PointEntity::class,
        LineEntity::class,
        LinePointCrossRef::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun pointDao(): PointDao
    abstract fun lineDao(): LineDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "survedge_database"
                )
                .fallbackToDestructiveMigration() // For development; remove in production
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
