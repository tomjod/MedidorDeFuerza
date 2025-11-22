package com.tomjod.medidorfuerza.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tomjod.medidorfuerza.data.db.entities.UserProfile

/**
 * Define la tabla 'measurements'
 * Usa una ForeignKey para relacionar cada medición con un perfil de usuario.
 * 
 * Almacena datos completos de una sesión de medición:
 * - Valores promedio y máximo para isquiotibiales y cuádriceps
 * - Ratio calculado (isquios/cuads)
 * - Metadata de la sesión (timestamp, duración, notas)
 */
@Entity(
    tableName = "measurements",
    foreignKeys = [
        ForeignKey(
            entity = UserProfile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE // Si se borra un perfil, se borran sus mediciones
        )
    ],
    indices = [Index(value = ["profileId"])]
)
data class Measurement(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val profileId: Long, // La llave foránea
    
    // Mediciones de isquiotibiales (hamstrings)
    val isquiosAvg: Float,
    val isquiosMax: Float,
    
    // Mediciones de cuádriceps
    val cuadsAvg: Float,
    val cuadsMax: Float,
    
    // Ratio calculado (isquios/cuads)
    val ratio: Float,
    
    // Metadata de la sesión
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Int = 0, // Duración de la sesión de medición
    
    // Notas opcionales
    val notes: String? = null,

    // Pierna medida (Left / Right)
    val leg: String = "Right"
)