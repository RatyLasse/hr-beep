package com.x.hrbeep.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTimeMs: Long,
    val durationSeconds: Int,
    val averageHr: Int?,
    val distanceMeters: Double?,
    val paceSecondsPerKm: Int? = null,
    val hrHistory: String? = null,
    val upperBound: Int? = null,
    val lowerBound: Int? = null,
) {
    fun hrHistoryList(): List<Int> =
        hrHistory?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
}
