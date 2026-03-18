package com.x.hrbeep.data

import kotlinx.coroutines.flow.Flow

class SessionHistoryRepository(private val dao: SessionDao) {
    val sessions: Flow<List<SessionRecord>> = dao.getAllSessions()

    suspend fun saveSession(session: SessionRecord) {
        dao.insert(session)
    }

    suspend fun deleteSession(id: Long) {
        dao.deleteById(id)
    }
}
