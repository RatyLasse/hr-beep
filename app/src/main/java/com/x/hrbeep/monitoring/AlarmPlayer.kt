package com.x.hrbeep.monitoring

import android.media.AudioManager
import android.media.ToneGenerator

class AlarmPlayer {
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)

    @Synchronized
    fun beep() {
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 250)
    }

    @Synchronized
    fun release() {
        toneGenerator.release()
    }
}

