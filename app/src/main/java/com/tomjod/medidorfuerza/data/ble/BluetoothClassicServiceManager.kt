package com.tomjod.medidorfuerza.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@SuppressLint("MissingPermission")
@Singleton
class BluetoothClassicServiceManager @Inject constructor(
    @ApplicationContext private val context: Context
) : BleRepository {

    companion object {
        // Standard Serial Port Profile (SPP) UUID
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        
        const val STX = 0x02.toByte()
        const val ETX = 0x03.toByte()
        const val EXPECTED_LEN = 0x0C.toByte() // 12 bytes
        const val ACK = 0x06.toByte()
        
        const val DEVICE_NAME = "ESP32_Fuerza_HQ"
    }

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _forceData = MutableStateFlow<ForceReadings?>(null)
    override val forceData: StateFlow<ForceReadings?> = _forceData.asStateFlow()

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null
    
    private val scope = CoroutineScope(Dispatchers.IO)

    // BroadcastReceiver for discovery
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                // For now, we just log or could auto-connect if we had a target name.
                // But the current UI expects a list or auto-connect logic.
                // The current BleServiceManager connects to the first device found in scan?
                // Let's check BleServiceManager. It connects to the first device found!
                // "connectToDevice(result.device)" inside onScanResult.
                
                device?.let {
                    // Check if it's the ESP32 (optional: filter by name if known, e.g. "ESP32")
                    // For now, we connect to the first device found to mimic previous behavior,
                    // or we could check for a specific name if the user provided one.
                    // Given the user didn't specify a name, we might want to be careful.
                    // But to match previous behavior:
                    if (it.name == DEVICE_NAME && _connectionState.value == BleConnectionState.Scanning) {
                        bluetoothAdapter?.cancelDiscovery()
                        connect(it)
                    }
                }
            }
        }
    }

    override fun startScan() {
        if (bluetoothAdapter?.isEnabled == false) {
            _connectionState.value = BleConnectionState.BluetoothDisabled
            return
        }

        _connectionState.value = BleConnectionState.Scanning
        
        // Register receiver
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        context.registerReceiver(receiver, filter)
        
        bluetoothAdapter?.startDiscovery()
    }

    private fun connect(device: BluetoothDevice) {
        // First, disconnect any existing connection
        disconnect()
        
        _connectionState.value = BleConnectionState.Connecting
        
        // Cancel discovery because it's heavy
        bluetoothAdapter?.cancelDiscovery()
        
        try {
            context.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered
        }

        connectThread = ConnectThread(device)
        connectThread?.start()
    }

    override fun sendTareCommand() {
        // Send 't' for Tare
        connectedThread?.write("t\n".toByteArray())
    }

    override fun calibrateIsquios(factor: Float) {
        connectedThread?.write("i=$factor\n".toByteArray())
    }

    override fun calibrateCuads(factor: Float) {
        connectedThread?.write("q=$factor\n".toByteArray())
    }

    override fun disconnect() {
        // Cancel discovery if running
        bluetoothAdapter?.cancelDiscovery()
        
        // Cancel and wait for threads to finish
        connectedThread?.cancel()
        connectThread?.cancel()
        
        // Wait a bit for threads to finish cleanup
        try {
            connectedThread?.join(500) // Wait max 500ms for thread to finish
            connectThread?.join(500)
        } catch (e: InterruptedException) {
            // Ignore
        }
        
        // Clear references
        connectedThread = null
        connectThread = null
        
        // Update state
        _connectionState.value = BleConnectionState.Disconnected
    }


    override fun release() {
        disconnect()
        try {
            context.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            // Ignore
        }
    }

    private inner class ConnectThread(private val device: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createRfcommSocketToServiceRecord(SPP_UUID)
        }

        override fun run() {
            bluetoothAdapter?.cancelDiscovery()

            try {
                mmSocket?.connect()
            } catch (e: IOException) {
                _connectionState.value = BleConnectionState.Error("Connection failed: ${e.message}")
                try {
                    mmSocket?.close()
                } catch (e2: IOException) {
                    // Ignore
                }
                return
            }

            mmSocket?.let { socket ->
                connectedThread = ConnectedThread(socket)
                connectedThread?.start()
                _connectionState.value = BleConnectionState.Connected
            }
        }

        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                // Ignore
            }
        }
    }

    private inner class ConnectedThread(private val socket: BluetoothSocket) : Thread() {
        private val mmInStream: InputStream = socket.inputStream
        private val mmOutStream = socket.outputStream
        private val buffer = ByteArray(1024)

        override fun run() {
            var numBytes: Int
            
            // State machine for parsing
            // STX (1) + LEN (1) + Payload (12) + Checksum (1) + ETX (1) = 16 bytes total
            val packetSize = 16
            val tempBuffer = ByteArray(packetSize)
            var bufferIndex = 0
            var state = 0 // 0: Waiting for STX, 1: Waiting for rest

            while (true) {
                try {
                    val byte = mmInStream.read()
                    if (byte == -1) break
                    
                    when (state) {
                        0 -> { // Waiting for STX or ACK
                            if (byte.toByte() == STX) {
                                tempBuffer[0] = STX
                                bufferIndex = 1
                                state = 1
                            } else if (byte.toByte() == ACK) {
                                // Handle ACK (e.g. Tare successful)
                                // For now we can just log it or maybe emit a one-shot event if we had that infrastructure
                                println("ACK received")
                            }
                        }
                        1 -> { // Reading rest
                            tempBuffer[bufferIndex] = byte.toByte()
                            bufferIndex++
                            
                            if (bufferIndex == 2) {
                                // Check LEN
                                if (tempBuffer[1] != EXPECTED_LEN) {
                                    // Invalid length, reset
                                    state = 0
                                    bufferIndex = 0
                                }
                            } else if (bufferIndex == packetSize) {
                                // Packet complete, verify ETX and Checksum
                                if (tempBuffer[15] == ETX) {
                                    processPacket(tempBuffer)
                                }
                                state = 0
                                bufferIndex = 0
                            }
                        }
                    }
                } catch (e: IOException) {
                    _connectionState.value = BleConnectionState.Disconnected
                    break
                }
            }
        }

        private fun processPacket(packet: ByteArray) {
            // Packet: STX(0) LEN(1) Payload(2..13) Checksum(14) ETX(15)
            val payload = packet.copyOfRange(2, 14)
            val receivedChecksum = packet[14]
            
            // Calculate checksum: XOR of payload
            var calculatedChecksum: Byte = 0
            for (b in payload) {
                calculatedChecksum = (calculatedChecksum.toInt() xor b.toInt()).toByte()
            }

            if (calculatedChecksum == receivedChecksum) {
                val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
                val isquios = buffer.float
                val cuads = buffer.float
                val ratio = buffer.float
                
                _forceData.value = ForceReadings(isquios, cuads, ratio)
            }
        }

        fun write(bytes: ByteArray) {
            try {
                mmOutStream.write(bytes)
            } catch (e: IOException) {
                // Error writing
            }
        }

        fun cancel() {
            try {
                socket.close()
            } catch (e: IOException) {
                // Ignore
            }
        }
    }
}
