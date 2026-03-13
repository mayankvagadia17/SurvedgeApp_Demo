package com.nexova.survedge.data.db.converter

import androidx.room.TypeConverter
import java.util.Date

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    // Example: If needed to store list of doubles as string (though simple tables are better)
    // kept minimal for now as per schema
}
