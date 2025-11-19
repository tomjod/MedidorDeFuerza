package com.tomjod.medidorfuerza.data.ble

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Esta es una implementación FALSA de BleRepository usada para
 * pruebas de UI y desarrollo sin hardware real.
 */
@Singleton
class FakeBleRepository @Inject constructor() : BleRepository {

    // 1. Creamos los mismos Flows mutables que el repositorio real
    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _forceData = MutableStateFlow<ForceReadings?>(null)
    override val forceData: StateFlow<ForceReadings?> = _forceData.asStateFlow()

    private val simulationScope = CoroutineScope(Dispatchers.Default)
    private var isSimulating = false

    /**
     * Al llamar a startScan, simulamos una conexión exitosa.
     */
    override fun startScan() {
        if (isSimulating) return
        isSimulating = true

        simulationScope.launch {
            _connectionState.value = BleConnectionState.Scanning
            delay(1500) // Simula tiempo de escaneo
            _connectionState.value = BleConnectionState.Connecting
            delay(1000) // Simula tiempo de conexión
            _connectionState.value = BleConnectionState.Connected

            // Inicia el bucle de envío de datos falsos
            simulateForceData()
        }
    }

    private fun simulateForceData() {
        simulationScope.launch {
            while (isSimulating) {
                // Genera valores de fuerza aleatorios
                val isquios = Random.nextFloat() * 25.0f + 20.0f
                val cuads = Random.nextFloat() * 40.0f + 30.0f
                val ratio = if (cuads != 0f) isquios / cuads else 0f
                
                _forceData.value = ForceReadings(isquios, cuads, ratio)
                delay(500) // Envía un nuevo dato cada 500ms
            }
        }
    }

    /**
     * Al llamar a disconnect, detenemos la simulación.
     */
    override fun disconnect() {
        if (!isSimulating) return
        isSimulating = false
        simulationScope.launch {
            _connectionState.value = BleConnectionState.Disconnected
            _forceData.value = null
        }
    }

    /**
     * Simula el comando de "Tarar" poniendo la fuerza a 0.
     */
    override fun sendTareCommand() {
        if (isSimulating) {
            _forceData.value = ForceReadings(0f, 0f, 0f)
        }
    }

    /**
     * No hace nada en el simulador.
     */
    override fun release() {
        isSimulating = false
    }

    override fun calibrateIsquios(factor: Float) {
        // No-op for fake repository
    }

    override fun calibrateCuads(factor: Float) {
        // No-op for fake repository
    }
}
