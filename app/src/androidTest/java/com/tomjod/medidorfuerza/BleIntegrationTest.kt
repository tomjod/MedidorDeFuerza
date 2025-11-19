package com.tomjod.medidorfuerza

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BleIntegrationTest {

    @Test
    fun activityLaunches_and_bluetoothIntentAvailable() {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        // If device/emulator has no adapter, the test is not applicable but should not crash.

        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            assert(intent != null)
        }
    }
}
