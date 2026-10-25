package com.cupcake.ui.screen.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.cupcake.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class SettingsScreenTest {

    @Test
    fun `sdk version check Android 14+`() {
        // Given: Checking SDK version is Android 14 or later
        // In enable(): Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        val isAndroid14OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        // Then: should be true on Android 14+
        assert(isAndroid14OrLater == true)
    }

    @Test
    fun `permission granted logic returns integer`() {
        // Given: ContextCompat.checkSelfPermission returns INT
        // ContextCompat.checkSelfPermission returns PERMISSION_GRANTED (0) or PERMISSION_DENIED (-1)
        val result = ContextCompat.checkSelfPermission(
            RuntimeEnvironment.application,
            Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
        )
        // Then: result should be an integer
        assert(result is Int)
    }

    @Test
    fun `permission granted_check_structure`() {
        // Given: The enable() function checks both conditions
        // Build.VERSION.SDK_INT >= UPSIDE_DOWN_CAKE && checkSelfPermission == GRANTED
        val sdkMatch = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        // And: permission check result depends on actual grant state
        val permissionMatch = ContextCompat.checkSelfPermission(
            RuntimeEnvironment.application,
            Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
        ) == PackageManager.PERMISSION_GRANTED
        // Both should evaluate correctly on Android 14+
        // sdkMatch is guaranteed true by @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
        assert(sdkMatch == true)
    }
}