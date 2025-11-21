package com.tomjod.medidorfuerza.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tomjod.medidorfuerza.data.db.entities.Measurement
import com.tomjod.medidorfuerza.data.db.entities.UserProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // --- Consultas de Perfil ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfile): Long

    @Query("SELECT * FROM user_profiles WHERE id = :id")
    fun getProfileById(id: Long): Flow<UserProfile?> // Flow para observar cambios

    @Query("SELECT * FROM user_profiles ORDER BY nombre ASC")
    fun getAllProfiles(): Flow<List<UserProfile>>

    // --- Consultas de Medición ---
    @Insert
    suspend fun insertMeasurement(measurement: Measurement): Long

    @Query("SELECT * FROM measurements WHERE profileId = :profileId ORDER BY timestamp DESC")
    fun getMeasurementsForProfile(profileId: Long): Flow<List<Measurement>>

    @Query("SELECT * FROM measurements WHERE profileId = :profileId ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentMeasurements(profileId: Long, limit: Int = 10): Flow<List<Measurement>>

    @Query("SELECT * FROM measurements WHERE id = :id")
    fun getMeasurementById(id: Long): Flow<Measurement?>

    @Query("DELETE FROM measurements WHERE id = :id")
    suspend fun deleteMeasurement(id: Long)

    @Query("SELECT COUNT(*) FROM measurements WHERE profileId = :profileId")
    fun getMeasurementCount(profileId: Long): Flow<Int>
}