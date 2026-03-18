package com.x.hrbeep.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BleHeartRateRepositoryBatteryTest {
    @Test
    fun `parses valid battery percentages`() {
        assertEquals(0, BleHeartRateRepository.parseBatteryLevelPercent(byteArrayOf(0x00)))
        assertEquals(87, BleHeartRateRepository.parseBatteryLevelPercent(byteArrayOf(0x57)))
        assertEquals(100, BleHeartRateRepository.parseBatteryLevelPercent(byteArrayOf(0x64)))
    }

    @Test
    fun `ignores invalid battery percentages`() {
        assertNull(BleHeartRateRepository.parseBatteryLevelPercent(null))
        assertNull(BleHeartRateRepository.parseBatteryLevelPercent(byteArrayOf()))
        assertNull(BleHeartRateRepository.parseBatteryLevelPercent(byteArrayOf(0x65)))
        assertNull(BleHeartRateRepository.parseBatteryLevelPercent(byteArrayOf(0xFF.toByte())))
    }
}
