package com.x.hrbeep.data

import android.os.SystemClock

object HeartRateParser {
    fun parse(payload: ByteArray, timestampElapsedMs: Long = SystemClock.elapsedRealtime()): HeartRateSample {
        require(payload.isNotEmpty()) { "Heart-rate payload cannot be empty." }

        val flags = payload[0].toInt() and 0xFF
        val isUint16 = flags and 0x01 != 0
        val hasContactSupport = flags and 0x02 != 0
        val contactDetected = flags and 0x04 != 0
        val hasRrIntervals = flags and 0x10 != 0

        var cursor = 1
        val bpm = if (isUint16) {
            val value = payload.readUInt16(cursor)
            cursor += 2
            value
        } else {
            val value = payload[cursor].toInt() and 0xFF
            cursor += 1
            value
        }

        val rrIntervals = buildList {
            if (hasRrIntervals) {
                while (cursor + 1 < payload.size) {
                    val rrRaw = payload.readUInt16(cursor)
                    add(rrRaw / 1024f * 1000f)
                    cursor += 2
                }
            }
        }

        return HeartRateSample(
            bpm = bpm,
            rrIntervalsMs = rrIntervals,
            contactDetected = if (hasContactSupport) contactDetected else null,
            receivedAtElapsedMs = timestampElapsedMs,
        )
    }

    private fun ByteArray.readUInt16(offset: Int): Int {
        val low = this[offset].toInt() and 0xFF
        val high = this[offset + 1].toInt() and 0xFF
        return low or (high shl 8)
    }
}

