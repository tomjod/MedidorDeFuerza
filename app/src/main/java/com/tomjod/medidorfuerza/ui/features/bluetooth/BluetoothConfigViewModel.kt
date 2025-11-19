package com.tomjod.medidorfuerza.ui.features.bluetooth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomjod.medidorfuerza.data.ble.BleConnectionState
import com.tomjod.medidorfuerza.data.ble.BleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BluetoothConfigViewModel @Inject constructor(
    private val bleRepository: BleRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val permissionManager = BluetoothPermissionManager(context)

    // Estado combinado que incluye permisos y conexión BLE
    private val _bluetoothState =
        MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val bluetoothState: StateFlow<BleConnectionState> = _bluetoothState.asStateFlow()

    /**
     * Expone los datos de fuerza del repositorio BLE
     */
    val forceData = bleRepository.forceData

    init {
        // Register listener so changes to the adapter state are propagated automatically
        permissionManager.registerStateListener(object : BluetoothPermissionManager.BluetoothStateListener {
            override fun onStateChanged(state: BleConnectionState) {
                _bluetoothState.value = state
            }
        })

        // Verificar estado inicial
        checkBluetoothStatus()

        // Observar cambios en el estado de conexión BLE
        viewModelScope.launch {
            bleRepository.connectionState.collect { bleState ->
                // Solo actualizar si ya tenemos permisos y Bluetooth habilitado
                if (hasRequiredPermissionsAndBluetooth()) {
                    _bluetoothState.value = bleState
                }
            }
        }
    }

    /**
     * Verifica el estado actual de Bluetooth y permisos
     */
    fun checkBluetoothStatus() {
        _bluetoothState.value = permissionManager.getCurrentBluetoothState()
    }

    /**
     * Verifica si tenemos todos los requisitos para usar BLE
     */
    private fun hasRequiredPermissionsAndBluetooth(): Boolean {
        return permissionManager.hasAllPermissions() &&
                permissionManager.isBluetoothEnabled() &&
                permissionManager.isBluetoothSupported()
    }

    /**
     * Inicia el escaneo de dispositivos BLE (solo si tenemos permisos)
     */
    fun startScan() {
        checkBluetoothStatus()

        when (_bluetoothState.value) {
            is BleConnectionState.Disconnected -> {
                bleRepository.startScan()
            }

            is BleConnectionState.BluetoothNotSupported -> {
                _bluetoothState.value =
                    BleConnectionState.Error("Este dispositivo no soporta Bluetooth LE")
            }

            is BleConnectionState.PermissionsRequired -> {
                // El estado ya está establecido, la UI manejará la solicitud de permisos
            }

            is BleConnectionState.BluetoothDisabled -> {
                // El estado ya está establecido, la UI manejará la habilitación de Bluetooth
            }

            else -> {
                // Ya está escaneando o conectado
            }
        }
    }

    /**
     * Envía el comando de tarar al dispositivo
     */
    fun sendTareCommand() {
        if (hasRequiredPermissionsAndBluetooth()) {
            bleRepository.sendTareCommand()
        }
    }

    /**
     * Desconecta del dispositivo actual
     */
    fun disconnect() {
        if (hasRequiredPermissionsAndBluetooth()) {
            bleRepository.disconnect()
        }
    }

    /**
     * Obtiene los permisos necesarios
     */
    fun getRequiredPermissions(): Array<String> {
        return permissionManager.getRequiredPermissions()
    }

    /**
     * Obtiene los permisos faltantes
     */
    fun getMissingPermissions(): List<String> {
        return permissionManager.getMissingPermissions()
    }

    /**
     * Obtiene el nombre amigable de un permiso
     */
    fun getPermissionDisplayName(permission: String): String {
        return permissionManager.getPermissionDisplayName(permission)
    }

    /**
     * Callback para cuando se conceden permisos
     */
    fun onPermissionsResult(grantedPermissions: Map<String, Boolean>) {
        val allGranted = grantedPermissions.values.all { it }
        if (allGranted) {
            checkBluetoothStatus()
        } else {
            _bluetoothState.value = BleConnectionState.PermissionsRequired
        }
    }

    /**
     * Callback para cuando se habilita/deshabilita Bluetooth
     */
    fun onBluetoothStateChanged() {
        checkBluetoothStatus()
    }

    // Propiedad de compatibilidad para la UI existente
    val connectionState: StateFlow<BleConnectionState> = bluetoothState

    override fun onCleared() {
        // Unregister the permission manager listener to avoid leaks
        permissionManager.unregisterStateListener()
        super.onCleared()
        bleRepository.release()
    }
}