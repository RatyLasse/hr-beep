package com.x.hrbeep

import java.util.Locale

fun formatKilometers(distanceMeters: Double): String =
    String.format(Locale.US, "%.2f", distanceMeters / 1_000.0)
