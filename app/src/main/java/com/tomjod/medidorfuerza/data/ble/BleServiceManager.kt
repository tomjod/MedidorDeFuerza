package com.tomjod.medidorfuerza.data.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@SuppressLint("MissingPermission")
@Singleton
class BleServiceManager @Inject constructor(
    @ApplicationContext private val context: Context
) : BleRepository { // <-- Implementa la interfaz

    // --- UUIDs (Definiciones) ---
    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        val FORCE_CHARACTERISTIC_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        val TARE_CHARACTERISTIC_UUID: UUID = UUID.fromString("c2a7921a-4c28-4b4e-9b0a-3d23b2c6c4e0")
        val CCCD_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    // --- Flujos de Estado (Implementando la interfaz) ---
    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _forceData = MutableStateFlow<ForceReadings?>(null)
    override val forceData: StateFlow<ForceReadings?> = _forceData.asStateFlow()

    // --- Componentes BLE (Privados) ---
    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner = bluetoothAdapter?.bluetoothLeScanner
    private var bluetoothGatt: BluetoothGatt? = null

    // --- Callbacks (Privados) ---

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            if (_connectionState.value == BleConnectionState.Scanning) {
                bleScanner?.stopScan(this)
                connectToDevice(result.device)
            }
        }
        override fun onScanFailed(errorCode: Int) {
            _connectionState.value = BleConnectionState.Error("Fallo en escaneo: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceName = gatt.device.name ?: gatt.device.address
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    _connectionState.value = BleConnectionState.Connecting
                    bluetoothGatt = gatt
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    _connectionState.value = BleConnectionState.Disconnected
                    gatt.close()
                }
            } else {
                _connectionState.value = BleConnectionState.Error("Error de conexión: $status")
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                val characteristic = service?.getCharacteristic(FORCE_CHARACTERISTIC_UUID)
                if (characteristic != null) {
                    subscribeToCharacteristic(gatt, characteristic)
                } else {
                    _connectionState.value = BleConnectionState.Error("Característica no encontrada")
                }
            } else {
                _connectionState.value = BleConnectionState.Error("Fallo al descubrir servicios: $status")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == FORCE_CHARACTERISTIC_UUID) {
                parseForceData(value)
            }
        }

        // Esta es la callback "legacy" (antigua).
        // API < 33.
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            // Verificamos que estemos en una API < 33 para evitar doble procesamiento
            // si el OS (por algún motivo) llamara a ambas.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                if (characteristic.uuid == FORCE_CHARACTERISTIC_UUID) {
                    // En la API antigua, leemos el valor desde 'characteristic.value'
                    @Suppress("DEPRECATION")
                    parseForceData(characteristic.value)
                }
            }
        }
    }

    // --- Acciones Públicas (Implementando la interfaz) ---

    override fun startScan() {
        if (bleScanner == null || bluetoothAdapter?.isEnabled == false) {
            _connectionState.value = BleConnectionState.Error("Bluetooth no disponible")
            return
        }
        _connectionState.value = BleConnectionState.Scanning

        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bleScanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
    }

    override fun sendTareCommand() {
        val service = bluetoothGatt?.getService(SERVICE_UUID)
        val tareCharacteristic = service?.getCharacteristic(TARE_CHARACTERISTIC_UUID)
        if (tareCharacteristic != null) {
            val data = byteArrayOf(0x01) // Comando "Tarar"
            // --- CORRECCIÓN API 33 (writeCharacteristic) ---
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // API 33+ (Tiramisu): usa la nueva firma con 3 argumentos
                bluetoothGatt?.writeCharacteristic(
                    tareCharacteristic,
                    data,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
            } else {
                // API < 33 (Legacy): usa la firma antigua
                // 1. Setea el valor en la característica
                @Suppress("DEPRECATION")
                tareCharacteristic.value = data
                // 2. Setea el tipo de escritura en la característica
                @Suppress("DEPRECATION")
                tareCharacteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                // 3. Llama al método antiguo
                @Suppress("DEPRECATION")
                bluetoothGatt?.writeCharacteristic(tareCharacteristic)
            }
        }
    }

    override fun disconnect() {
        bluetoothGatt?.disconnect()
    }

    override fun release() {
        bluetoothGatt?.close()
        bluetoothGatt = null
        println("Recursos de BleServiceManager liberados.")
    }

    override fun calibrateIsquios(factor: Float) {
        // Not implemented for BLE legacy
    }

    override fun calibrateCuads(factor: Float) {
        // Not implemented for BLE legacy
    }

    // --- Lógica Interna (Privada) ---

    private fun connectToDevice(device: BluetoothDevice) {
        _connectionState.value = BleConnectionState.Connecting
        device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private fun subscribeToCharacteristic(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val cccd = characteristic.getDescriptor(CCCD_DESCRIPTOR_UUID)

        val value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            @Suppress("DEPRECATION")
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // API 33+ (Tiramisu): usa la nueva firma con 2 argumentos
            gatt.writeDescriptor(cccd, value)
        } else {
            // API < 33 (Legacy): usa la firma antigua
            // 1. Setea el valor en el descriptor
            @Suppress("DEPRECATION")
            cccd.value = value
            // 2. Llama al método antiguo
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(cccd)
        }

        // Asumimos que la conexión es exitosa después de pedir la suscripción
        _connectionState.value = BleConnectionState.Connected
    }

    private fun parseForceData(data: ByteArray) {
        if (data.size == 4) { // Esperamos un float (4 bytes)
            val force = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).float
            // Asumimos que el valor es el ratio por ahora, o solo fuerza
            _forceData.value = ForceReadings(0f, 0f, force)
        }
    }
}