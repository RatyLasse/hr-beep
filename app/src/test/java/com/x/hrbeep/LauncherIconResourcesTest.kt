package com.x.hrbeep

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherIconResourcesTest {

    @Test
    fun launcherForegroundIsScaledAndShiftedIntoAdaptiveIconSafeZone() {
        val xml = loadForegroundVector()

        assertTrue(xml.contains("<group"))
        assertTrue(xml.contains("android:scaleX=\"0.66\""))
        assertTrue(xml.contains("android:scaleY=\"0.66\""))
        assertTrue(xml.contains("android:translateX=\"4.20\""))
        assertTrue(xml.contains("android:translateY=\"4.40\""))
    }

    private fun loadForegroundVector(): String {
        val candidates = listOf(
            File("src/main/res/drawable/ic_launcher_foreground.xml"),
            File("app/src/main/res/drawable/ic_launcher_foreground.xml"),
        )

        val resourceFile = candidates.firstOrNull(File::exists)
            ?: error("Could not locate ic_launcher_foreground.xml from ${System.getProperty("user.dir")}")

        return resourceFile.readText()
    }
}
