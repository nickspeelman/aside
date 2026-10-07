package com.nickspeelman.localjournal.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mood_entries")
data class MoodEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val rating: Int?,
    val note: String,
    val hashtags: String, // Comma separated
    val timestamp: Long = System.currentTimeMillis()
)
