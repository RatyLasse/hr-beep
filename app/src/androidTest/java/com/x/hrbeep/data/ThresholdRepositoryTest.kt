package com.x.hrbeep.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThresholdRepositoryTest {
    @Test
    fun savesAndReadsThreshold() = runBlocking {
        val repository = ThresholdRepository(ApplicationProvider.getApplicationContext())
        repository.saveThreshold(137)

        assertEquals(137, repository.thresholdFlow.first())
    }
}
