package com.tomjod.medidorfuerza.ui.features.measurement

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomjod.medidorfuerza.data.ble.BleConnectionState
import com.tomjod.medidorfuerza.data.ble.BleRepository
import com.tomjod.medidorfuerza.data.ble.ForceReadings
import com.tomjod.medidorfuerza.data.db.AppDao
import com.tomjod.medidorfuerza.data.db.entities.UserProfile
import com.tomjod.medidorfuerza.data.repositories.MeasurementRepository
import com.tomjod.medidorfuerza.domain.MeasurementSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ForceMeterViewModel @Inject constructor(
    private val bleRepository: BleRepository,
    private val appDao: AppDao,
    private val measurementRepository: MeasurementRepository,
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
     * Expone el número total de mediciones para este perfil.
     */
    val measurementCount: StateFlow<Int> = measurementRepository.getMeasurementCount(profileId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    /**
     * Expone el estado de la conexión BLE (directamente desde el repositorio).
     */
    val connectionState: StateFlow<BleConnectionState> = bleRepository.connectionState

    /**
     * Expone la última lectura de fuerza (directamente desde el repositorio).
     */
    val latestForce: StateFlow<ForceReadings?> = bleRepository.forceData

    /**
     * Sesión de medición actual (null si no hay sesión activa).
     */
    private val _currentSession = MutableStateFlow<MeasurementSession?>(null)
    val currentSession: StateFlow<MeasurementSession?> = _currentSession.asStateFlow()

    /**
     * Indica si se guardó exitosamente una medición (para mostrar feedback).
     */
    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    // --- 2. OBSERVADOR DE LECTURAS ---
    // Cuando hay una sesión activa, acumulamos las lecturas automáticamente
    init {
        viewModelScope.launch {
            latestForce.collect { reading ->
                val session = _currentSession.value
                if (session != null && reading != null) {
                    session.addReading(reading)
                }
            }
        }
    }

    // --- 3. MANEJO DE EVENTOS ---

    /**
     * Punto de entrada único para todos los eventos de la UI.
     */
    fun onEvent(event: MeasurementEvent) {
        when (event) {
            MeasurementEvent.ConnectClicked -> bleRepository.startScan()
            MeasurementEvent.TareClicked -> bleRepository.sendTareCommand()
            @Suppress("DEPRECATION")
            MeasurementEvent.SaveClicked -> saveCurrentMeasurement() // Mantener compatibilidad
            MeasurementEvent.DisconnectClicked -> bleRepository.disconnect()
            is MeasurementEvent.CalibrateIsquios -> bleRepository.calibrateIsquios(event.factor)
            is MeasurementEvent.CalibrateCuads -> bleRepository.calibrateCuads(event.factor)
            MeasurementEvent.StartSession -> startSession()
            is MeasurementEvent.StopAndSaveSession -> stopAndSaveSession(event.notes)
        }
    }

    // --- 4. LÓGICA INTERNA ---

    /**
     * Inicia una nueva sesión de medición.
     */
    private fun startSession() {
        _currentSession.value = MeasurementSession(profileId = profileId)
        _saveSuccess.value = false // Reset del estado de guardado
    }

    /**
     * Detiene la sesión actual y guarda los datos en la base de datos.
     */
    private fun stopAndSaveSession(notes: String?) {
        val session = _currentSession.value
        if (session != null) {
            viewModelScope.launch {
                val measurement = session.toMeasurement(notes)
                measurementRepository.saveMeasurement(measurement)
                _currentSession.value = null // Limpiar sesión
                _saveSuccess.value = true // Indicar éxito
            }
        }
    }

    /**
     * Guarda la lectura de fuerza actual en la base de datos (método legacy).
     * @deprecated Use startSession() and stopAndSaveSession() instead
     */
    @Deprecated("Use session-based measurement tracking")
    private fun saveCurrentMeasurement() {
        // Obtenemos el valor actual del flow 'latestForce'
        val currentReadings = latestForce.value
        if (currentReadings != null && currentReadings.ratio > 0) {
            viewModelScope.launch {
                measurementRepository.saveMeasurement(
                    com.tomjod.medidorfuerza.data.db.entities.Measurement(
                        profileId = profileId,
                        isquiosAvg = currentReadings.isquios,
                        isquiosMax = currentReadings.isquios,
                        cuadsAvg = currentReadings.cuads,
                        cuadsMax = currentReadings.cuads,
                        ratio = currentReadings.ratio,
                        timestamp = System.currentTimeMillis(),
                        durationSeconds = 0
                    )
                )
                _saveSuccess.value = true
            }
        }
    }

    /**
     * Resetea el estado de guardado exitoso.
     */
    fun resetSaveSuccess() {
        _saveSuccess.value = false
    }

    /**
     * Limpia los recursos cuando el ViewModel se destruye.
     * 
     * NOTA: NO desconectamos automáticamente el Bluetooth aquí porque:
     * - El usuario podría navegar hacia atrás y volver sin querer reconectar
     * - La conexión BLE debe persistir mientras la app esté activa
     * - La desconexión debe ser manual (botón desconectar) o cuando la app se cierra
     */
    override fun onCleared() {
        super.onCleared()
        // bleRepository.disconnect() // Comentado: causa desconexión al navegar
        // bleRepository.release() // Comentado: se libera cuando la app se cierra
    }
}