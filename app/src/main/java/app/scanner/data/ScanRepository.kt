package app.scanner.data

import kotlinx.coroutines.flow.Flow

class ScanRepository(private val dao: ScanHistoryDao) {
    val allScans: Flow<List<ScanHistoryEntity>> = dao.observeAll()

    suspend fun saveScan(item: ScanHistoryEntity): Long = dao.insert(item)

    suspend fun toggleFavorite(id: Long, currentFavorite: Boolean) {
        dao.setFavorite(id, !currentFavorite)
    }

    suspend fun updateNote(id: Long, note: String) {
        dao.updateNote(id, note)
    }

    suspend fun delete(id: Long) {
        dao.deleteById(id)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }
}
