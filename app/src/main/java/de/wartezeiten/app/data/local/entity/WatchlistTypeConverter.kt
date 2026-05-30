package de.wartezeiten.app.data.local.entity

import androidx.room.TypeConverter

class WatchlistTypeConverter {
    @TypeConverter
    fun fromType(type: WatchlistType): String = type.name

    @TypeConverter
    fun toType(value: String): WatchlistType = WatchlistType.valueOf(value)
}
