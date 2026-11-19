package app.scanner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val rawText: String,
    val formatName: String,
    val contentKind: String,
    val displayTitle: String,
    val displaySubtitle: String,
    val safetyLevel: String,
    val source: String,
    val isFavorite: Boolean = false,
    val isCreated: Boolean = false,
    val note: String = "",
    val timestampMs: Long = System.currentTimeMillis()
)
