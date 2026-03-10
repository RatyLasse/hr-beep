package com.x.hrbeep.data

data class BleDeviceCandidate(
    val address: String,
    val name: String,
    val rssi: Int,
)

