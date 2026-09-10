package com.example.kanjipractice.data.db

import androidx.room.TypeConverter
import com.example.kanjipractice.domain.model.CardState
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Room stores [LocalDateTime] as epoch milliseconds.
 *
 * UTC is used on purpose: the stored value is a point in time used for ordering
 * and for computing elapsed days, so it must not shift when the device's time
 * zone changes.
 */
class Converters {

    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): Long? =
        value?.toInstant(ZoneOffset.UTC)?.toEpochMilli()

    @TypeConverter
    fun toLocalDateTime(value: Long?): LocalDateTime? =
        value?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC) }

    @TypeConverter
    fun fromCardState(state: CardState): Int = state.value

    @TypeConverter
    fun toCardState(value: Int): CardState = CardState.fromValue(value)
}
