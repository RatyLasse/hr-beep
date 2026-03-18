package com.x.hrbeep.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionHistoryRepositoryTest {
    private lateinit var db: SessionDatabase
    private lateinit var repository: SessionHistoryRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SessionDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = SessionHistoryRepository(db.sessionDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun savesSessionAndReadsItBack() = runBlocking {
        val session = SessionRecord(
            startTimeMs = 1_000_000L,
            durationSeconds = 120,
            averageHr = 145,
            distanceMeters = 1500.0,
        )
        repository.saveSession(session)

        val sessions = repository.sessions.first()
        assertEquals(1, sessions.size)
        assertEquals(120, sessions[0].durationSeconds)
        assertEquals(145, sessions[0].averageHr)
        assertEquals(1500.0, sessions[0].distanceMeters!!, 0.001)
    }

    @Test
    fun returnsNewestSessionFirst() = runBlocking {
        repository.saveSession(SessionRecord(startTimeMs = 1000L, durationSeconds = 60, averageHr = null, distanceMeters = null))
        repository.saveSession(SessionRecord(startTimeMs = 3000L, durationSeconds = 90, averageHr = null, distanceMeters = null))
        repository.saveSession(SessionRecord(startTimeMs = 2000L, durationSeconds = 30, averageHr = null, distanceMeters = null))

        val sessions = repository.sessions.first()
        assertEquals(3000L, sessions[0].startTimeMs)
        assertEquals(2000L, sessions[1].startTimeMs)
        assertEquals(1000L, sessions[2].startTimeMs)
    }

    @Test
    fun deletesSessionById() = runBlocking {
        repository.saveSession(SessionRecord(startTimeMs = 1000L, durationSeconds = 60, averageHr = null, distanceMeters = null))
        val id = repository.sessions.first()[0].id

        repository.deleteSession(id)

        assertTrue(repository.sessions.first().isEmpty())
    }
}
