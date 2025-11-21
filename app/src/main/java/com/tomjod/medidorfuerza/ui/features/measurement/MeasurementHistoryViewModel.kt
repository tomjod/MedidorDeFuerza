package com.tomjod.medidorfuerza.ui.features.measurement

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomjod.medidorfuerza.data.db.entities.Measurement
import com.tomjod.medidorfuerza.data.repositories.MeasurementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel para la pantalla de historial de mediciones.
 * Muestra todas las mediciones guardadas para un perfil específico.
 */
@HiltViewModel
class MeasurementHistoryViewModel @Inject constructor(
    private val measurementRepository: MeasurementRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val profileId: Long = checkNotNull(savedStateHandle["profileId"])

    /**
     * Lista de todas las mediciones para este perfil, ordenadas por fecha (más reciente primero).
     */
    val measurements: StateFlow<List<Measurement>> =
        measurementRepository.getMeasurementsForProfile(profileId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    /**
     * Elimina una medición de la base de datos.
     */
    fun deleteMeasurement(id: Long) {
        viewModelScope.launch {
            measurementRepository.deleteMeasurement(id)
        }
    }
}
