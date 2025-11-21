package com.tomjod.medidorfuerza.domain

import com.tomjod.medidorfuerza.data.ble.ForceReadings
import com.tomjod.medidorfuerza.data.db.entities.Measurement

/**
 * Clase helper para acumular lecturas de fuerza durante una sesión de medición.
 * 
 * Durante una sesión, se van agregando múltiples lecturas (ForceReadings) y al final
 * se calcula el promedio y máximo para crear una entidad Measurement persistible.
 */
data class MeasurementSession(
    val profileId: Long,
    val readings: MutableList<ForceReadings> = mutableListOf(),
    val startTime: Long = System.currentTimeMillis()
) {
    /**
     * Agrega una nueva lectura a la sesión.
     */
    fun addReading(reading: ForceReadings) {
        readings.add(reading)
    }

    /**
     * Calcula la duración de la sesión en segundos.
     */
    fun getDurationSeconds(): Int {
        return ((System.currentTimeMillis() - startTime) / 1000).toInt()
    }

    /**
     * Convierte la sesión acumulada en una entidad Measurement para persistir.
     * Calcula promedios y máximos de todas las lecturas.
     */
    fun toMeasurement(notes: String? = null): Measurement {
        if (readings.isEmpty()) {
            // Si no hay lecturas, retornar medición con valores en 0
            return Measurement(
                profileId = profileId,
                isquiosAvg = 0f,
                isquiosMax = 0f,
                cuadsAvg = 0f,
                cuadsMax = 0f,
                ratio = 0f,
                timestamp = startTime,
                durationSeconds = getDurationSeconds(),
                notes = notes
            )
        }

        val isquiosValues = readings.map { it.isquios }
        val cuadsValues = readings.map { it.cuads }
        
        val avgIsquios = isquiosValues.average().toFloat()
        val avgCuads = cuadsValues.average().toFloat()
        
        // Calcular el ratio basado en los promedios
        val calculatedRatio = if (avgCuads > 0f) avgIsquios / avgCuads else 0f

        return Measurement(
            profileId = profileId,
            isquiosAvg = avgIsquios,
            isquiosMax = isquiosValues.maxOrNull() ?: 0f,
            cuadsAvg = avgCuads,
            cuadsMax = cuadsValues.maxOrNull() ?: 0f,
            ratio = calculatedRatio,
            timestamp = startTime,
            durationSeconds = getDurationSeconds(),
            notes = notes
        )
    }

    /**
     * Retorna el número de lecturas acumuladas.
     */
    fun getReadingCount(): Int = readings.size
}
