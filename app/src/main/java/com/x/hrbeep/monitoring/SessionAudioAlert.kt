package com.x.hrbeep.monitoring

import android.content.Context
import com.x.hrbeep.R

sealed interface SessionAudioAlert {
    fun spokenText(context: Context): String

    data object SensorConnected : SessionAudioAlert {
        override fun spokenText(context: Context): String =
            context.getString(R.string.audio_alert_sensor_connected)
    }

    data object SensorDisconnected : SessionAudioAlert {
        override fun spokenText(context: Context): String =
            context.getString(R.string.audio_alert_sensor_disconnected)
    }

    data class DistanceMarker(
        val kilometers: Int,
    ) : SessionAudioAlert {
        override fun spokenText(context: Context): String =
            context.resources.getQuantityString(
                R.plurals.audio_alert_distance_marker_kilometers,
                kilometers,
                kilometers,
            )
    }
}
