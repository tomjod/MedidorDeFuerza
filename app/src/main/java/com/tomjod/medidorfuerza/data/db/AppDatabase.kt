package com.tomjod.medidorfuerza.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tomjod.medidorfuerza.data.db.entities.Measurement
import com.tomjod.medidorfuerza.data.db.entities.UserProfile

/**
 * Clase principal de la base de datos Room.
 * Lista todas las entidades (tablas) que contendrá la base de datos.
 */
@Database(
    entities = [UserProfile::class, Measurement::class],
    version = 3, // Incrementado de 2 a 3 para el nuevo esquema de Measurement
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    // Room implementará esta función abstracta por nosotros.
    abstract fun appDao(): AppDao
}
