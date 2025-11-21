package com.tomjod.medidorfuerza.data.repositories

import com.tomjod.medidorfuerza.data.db.AppDao
import com.tomjod.medidorfuerza.data.db.entities.Measurement
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interfaz que define las operaciones disponibles para mediciones.
 */
interface MeasurementRepository {
    suspend fun saveMeasurement(measurement: Measurement): Long
    fun getMeasurementsForProfile(profileId: Long): Flow<List<Measurement>>
    fun getRecentMeasurements(profileId: Long, limit: Int = 10): Flow<List<Measurement>>
    fun getMeasurementById(id: Long): Flow<Measurement?>
    suspend fun deleteMeasurement(id: Long)
    fun getMeasurementCount(profileId: Long): Flow<Int>
}

/**
 * Implementación del repositorio de mediciones.
 * Delega todas las operaciones al DAO de la base de datos.
 */
@Singleton
class MeasurementRepositoryImpl @Inject constructor(
    private val dao: AppDao
) : MeasurementRepository {

    override suspend fun saveMeasurement(measurement: Measurement): Long {
        return dao.insertMeasurement(measurement)
    }

    override fun getMeasurementsForProfile(profileId: Long): Flow<List<Measurement>> {
        return dao.getMeasurementsForProfile(profileId)
    }

    override fun getRecentMeasurements(profileId: Long, limit: Int): Flow<List<Measurement>> {
        return dao.getRecentMeasurements(profileId, limit)
    }

    override fun getMeasurementById(id: Long): Flow<Measurement?> {
        return dao.getMeasurementById(id)
    }

    override suspend fun deleteMeasurement(id: Long) {
        dao.deleteMeasurement(id)
    }

    override fun getMeasurementCount(profileId: Long): Flow<Int> {
        return dao.getMeasurementCount(profileId)
    }
}
