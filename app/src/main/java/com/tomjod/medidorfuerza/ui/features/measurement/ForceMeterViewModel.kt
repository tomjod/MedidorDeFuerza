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

    // --- 5. SEQUENTIAL MEASUREMENT WORKFLOW ---

    enum class MeasurementStep {
        IDLE,
        MEASURING_ISQUIOS,
        WAITING_FOR_CUADS,
        MEASURING_CUADS,
        FINISHED
    }

    private val _measurementStep = MutableStateFlow(MeasurementStep.IDLE)
    val measurementStep: StateFlow<MeasurementStep> = _measurementStep.asStateFlow()

    private val _capturedIsquios = MutableStateFlow(0f)
    val capturedIsquios: StateFlow<Float> = _capturedIsquios.asStateFlow()

    private val _capturedCuads = MutableStateFlow(0f)
    val capturedCuads: StateFlow<Float> = _capturedCuads.asStateFlow()

    // Temporary max trackers for the current phase
    private var currentMaxIsquios = 0f
    private var currentMaxCuads = 0f

    private val _selectedLeg = MutableStateFlow<String?>(null)
    val selectedLeg: StateFlow<String?> = _selectedLeg.asStateFlow()

    init {
        viewModelScope.launch {
            latestForce.collect { reading ->
                val session = _currentSession.value
                if (session != null && reading != null) {
                    session.addReading(reading)
                }

                // Logic for sequential measurement
                if (reading != null) {
                    when (_measurementStep.value) {
                        MeasurementStep.MEASURING_ISQUIOS -> {
                            if (reading.isquios > currentMaxIsquios) {
                                currentMaxIsquios = reading.isquios
                            }
                        }
                        MeasurementStep.MEASURING_CUADS -> {
                            if (reading.cuads > currentMaxCuads) {
                                currentMaxCuads = reading.cuads
                            }
                        }
                        else -> {}
                    }
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
            
            // New Workflow
            MeasurementEvent.StartIsquios -> startIsquios()
            MeasurementEvent.CaptureIsquios -> captureIsquios()
            MeasurementEvent.StartCuads -> startCuads()
            MeasurementEvent.CaptureCuads -> captureCuads()
            MeasurementEvent.CancelMeasurement -> cancelMeasurement()
            MeasurementEvent.ResetMeasurement -> resetMeasurement()
            is MeasurementEvent.SelectLeg -> _selectedLeg.value = event.leg
        }
    }

    private fun startIsquios() {
        currentMaxIsquios = 0f
        _measurementStep.value = MeasurementStep.MEASURING_ISQUIOS
    }

    private fun captureIsquios() {
        _capturedIsquios.value = currentMaxIsquios
        _measurementStep.value = MeasurementStep.WAITING_FOR_CUADS
    }

    private fun startCuads() {
        currentMaxCuads = 0f
        _measurementStep.value = MeasurementStep.MEASURING_CUADS
    }

    private fun captureCuads() {
        _capturedCuads.value = currentMaxCuads
        _measurementStep.value = MeasurementStep.FINISHED
    }

    private fun cancelMeasurement() {
        _measurementStep.value = MeasurementStep.IDLE
        _capturedIsquios.value = 0f
        _capturedCuads.value = 0f
        currentMaxIsquios = 0f
        currentMaxCuads = 0f
        _selectedLeg.value = null
    }

    private fun resetMeasurement() {
        cancelMeasurement()
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
        // If we are in the new workflow, we save the captured values
        if (_measurementStep.value == MeasurementStep.FINISHED) {
             viewModelScope.launch {
                 val isquios = _capturedIsquios.value
                 val cuads = _capturedCuads.value
                 val ratio = if (cuads > 0) isquios / cuads else 0f
                 
                 measurementRepository.saveMeasurement(
                    com.tomjod.medidorfuerza.data.db.entities.Measurement(
                        profileId = profileId,
                        isquiosAvg = isquios, // Using max as avg for now in this mode
                        isquiosMax = isquios,
                        cuadsAvg = cuads,
                        cuadsMax = cuads,
                        ratio = ratio,
                        timestamp = System.currentTimeMillis(),
                        durationSeconds = 0,
                        notes = notes,
                        leg = _selectedLeg.value ?: "Right"
                    )
                )
                _saveSuccess.value = true
                _measurementStep.value = MeasurementStep.IDLE // Reset after save
                _selectedLeg.value = null // Reset selected leg after saving
             }
        } else {
            // Legacy session saving
            val session = _currentSession.value
            if (session != null) {
                viewModelScope.launch {
                    val measurement = session.toMeasurement(notes)
                    // Legacy session doesn't support leg selection explicitly yet, defaulting to Right
                    measurementRepository.saveMeasurement(measurement.copy(leg = _selectedLeg.value ?: "Right"))
                    _currentSession.value = null // Limpiar sesión
                    _saveSuccess.value = true // Indicar éxito
                    _selectedLeg.value = null // Reset selected leg after saving
                }
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
        if (currentReadings != null) {
             // If we are in legacy mode, we might not have a ratio if the device doesn't send it.
             // But this method is deprecated anyway.
            viewModelScope.launch {
                measurementRepository.saveMeasurement(
                    com.tomjod.medidorfuerza.data.db.entities.Measurement(
                        profileId = profileId,
                        isquiosAvg = currentReadings.isquios,
                        isquiosMax = currentReadings.isquios,
                        cuadsAvg = currentReadings.cuads,
                        cuadsMax = currentReadings.cuads,
                        ratio = currentReadings.ratio, // Will be 0 from device now
                        timestamp = System.currentTimeMillis(),
                        durationSeconds = 0,
                        leg = _selectedLeg.value ?: "Right"
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