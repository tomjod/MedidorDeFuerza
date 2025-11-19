package com.tomjod.medidorfuerza.ui.features.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.core.content.ContextCompat
import com.tomjod.medidorfuerza.data.ble.BleConnectionState

/**
 * Manager para manejar permisos y estado de Bluetooth
 */
class BluetoothPermissionManager(private val context: Context) {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    /**
     * Current active listener (kept to allow explicit notifications)
     */
    private var currentListener: BluetoothStateListener? = null

    /**
     * Verifica si Bluetooth está soportado en el dispositivo
     */
    fun isBluetoothSupported(): Boolean {
        return bluetoothAdapter != null &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    /**
     * Verifica si Bluetooth está habilitado
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * Obtiene los permisos necesarios según la versión de Android
     */
    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+)
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            // Android < 12
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    /**
     * Verifica si todos los permisos necesarios están concedidos
     */
    fun hasAllPermissions(): Boolean {
        return getRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Obtiene el estado actual de Bluetooth y permisos
     */
    fun getCurrentBluetoothState(): BleConnectionState {
        return when {
            !isBluetoothSupported() -> BleConnectionState.BluetoothNotSupported
            !hasAllPermissions() -> BleConnectionState.PermissionsRequired
            !isBluetoothEnabled() -> BleConnectionState.BluetoothDisabled
            else -> BleConnectionState.Disconnected
        }
    }

    /**
     * Crea el Intent para habilitar Bluetooth
     */
    fun createEnableBluetoothIntent(): Intent {
        return Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
    }

    /**
     * Obtiene los permisos faltantes
     */
    fun getMissingPermissions(): List<String> {
        return getRequiredPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(
                context,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Obtiene nombres amigables para los permisos
     */
    fun getPermissionDisplayName(permission: String): String {
        return when (permission) {
            Manifest.permission.BLUETOOTH_SCAN -> "Escanear dispositivos Bluetooth"
            Manifest.permission.BLUETOOTH_CONNECT -> "Conectar dispositivos Bluetooth"
            Manifest.permission.BLUETOOTH -> "Bluetooth"
            Manifest.permission.BLUETOOTH_ADMIN -> "Administrar Bluetooth"
            Manifest.permission.ACCESS_FINE_LOCATION -> "Ubicación precisa"
            else -> permission
        }
    }

    /**
     * BroadcastReceiver para escuchar cambios en el estado del adaptador Bluetooth
     */
    private var stateReceiver: BroadcastReceiver? = null
    private var isReceiverRegistered: Boolean = false

    /**
     * Listener para notificar cambios de estado de Bluetooth (incluye permisos)
     */
    interface BluetoothStateListener {
        fun onStateChanged(state: BleConnectionState)
    }

    /**
     * Lanza el Intent para pedir al usuario que active Bluetooth usando un launcher de ActivityResult.
     * Note: the Activity/Composable should forward the launcher's result into [handleEnableBluetoothResult].
     */
    fun requestEnableBluetooth(launcher: ManagedActivityResultLauncher<Intent, Boolean>) {
        launcher.launch(createEnableBluetoothIntent())
    }

    /**
     * Registra un listener que será notificado cuando cambie el estado del adaptador Bluetooth.
     * Llama inmediatamente con el estado actual.
     */
    fun registerStateListener(listener: BluetoothStateListener) {
        currentListener = listener

        // Notify immediately with the current state
        listener.onStateChanged(getCurrentBluetoothState())

        if (isReceiverRegistered) {
            return
        }

        // On Android 12+ the app needs BLUETOOTH_CONNECT to receive some Bluetooth broadcasts.
        val needsConnectPermissionOnS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED

        if (needsConnectPermissionOnS) {
            // Can't rely on ACTION_STATE_CHANGED broadcasts without BLUETOOTH_CONNECT;
            // the caller should request permissions and/or call handleEnableBluetoothResult after enabling.
            return
        }

        stateReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    currentListener?.onStateChanged(getCurrentBluetoothState())
                }
            }
        }

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(stateReceiver, filter)
        isReceiverRegistered = true
    }

    /**
     * Call this from the ActivityResult callback (the boolean result of the enable-Bluetooth request).
     * This ensures the UI updates immediately after the user enables Bluetooth.
     */
    fun handleEnableBluetoothResult(enabled: Boolean) {
        // Force re-evaluation and notify the current listener
        currentListener?.onStateChanged(getCurrentBluetoothState())
    }

    /**
     * Desregistra el listener para evitar fugas.
     */
    fun unregisterStateListener() {
        currentListener = null
        if (!isReceiverRegistered) return
        stateReceiver?.let { context.unregisterReceiver(it) }
        stateReceiver = null
        isReceiverRegistered = false
    }
}