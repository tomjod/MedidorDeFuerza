package com.tomjod.medidorfuerza.ui.features.measurement

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomjod.medidorfuerza.data.ble.BleConnectionState
import com.tomjod.medidorfuerza.data.ble.BleRepository
import com.tomjod.medidorfuerza.data.ble.ForceReadings
import com.tomjod.medidorfuerza.data.db.AppDao
import com.tomjod.medidorfuerza.data.db.entities.Measurement
import com.tomjod.medidorfuerza.data.db.entities.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ForceMeterViewModel @Inject constructor(
    private val bleRepository: BleRepository,
    private val appDao: AppDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Obtenemos el ID del perfil desde el NavController.
    // checkNotNull asegura que la app falle rápido si el ID no se pasa,
    // lo cual es correcto, ya que esta pantalla NO PUEDE funcionar sin un ID.
    private val profileId: Long = checkNotNull(savedStateHandle["profileId"])

    // --- 1. EXPOSICIÓN DE ESTADOS ---
    // Exponemos cada pieza de estado como un StateFlow simple y separado.
    // La UI (Compose) se encargará de observarlos y combinarlos visualmente.

    /**
     * Expone el perfil del usuario activo, obtenido de la base de datos.
     */
    val profile: StateFlow<UserProfile?> = appDao.getProfileById(profileId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null // Comienza como nulo hasta que Room lo cargue
        )

    /**
     * Expone el promedio de fuerza para este usuario, calculado por Room.
     */
    val averageForce: StateFlow<Float?> = appDao.getAverageForceForProfile(profileId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0f // Comienza en 0.0
        )

    /**
     * Expone el estado de la conexión BLE (directamente desde el repositorio).
     */
    val connectionState: StateFlow<BleConnectionState> = bleRepository.connectionState

    /**
     * Expone la última lectura de fuerza (directamente desde el repositorio).
     */
    val latestForce: StateFlow<ForceReadings?> = bleRepository.forceData


    // --- 2. MANEJO DE EVENTOS ---

    /**
     * Punto de entrada único para todos los eventos de la UI.
     */
    fun onEvent(event: MeasurementEvent) {
        when (event) {
            MeasurementEvent.ConnectClicked -> bleRepository.startScan()
            MeasurementEvent.TareClicked -> bleRepository.sendTareCommand()
            MeasurementEvent.SaveClicked -> saveCurrentMeasurement()
            MeasurementEvent.DisconnectClicked -> bleRepository.disconnect()
            is MeasurementEvent.CalibrateIsquios -> bleRepository.calibrateIsquios(event.factor)
            is MeasurementEvent.CalibrateCuads -> bleRepository.calibrateCuads(event.factor)
        }
    }

    // --- 3. LÓGICA INTERNA ---

    /**
     * Guarda la lectura de fuerza actual en la base de datos.
     */
    private fun saveCurrentMeasurement() {
        // Obtenemos el valor actual del flow 'latestForce'
        val currentReadings = latestForce.value
        if (currentReadings != null && currentReadings.ratio > 0) {
            viewModelScope.launch {
                appDao.insertMeasurement(
                    Measurement(
                        profileId = profileId,
                        forceValue = currentReadings.ratio,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    /**
     * Limpia los recursos (GATT) cuando el ViewModel se destruye.
     */
    override fun onCleared() {
        super.onCleared()
        bleRepository.disconnect()
        bleRepository.release()
    }
}