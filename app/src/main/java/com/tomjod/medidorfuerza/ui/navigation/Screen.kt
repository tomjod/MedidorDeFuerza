package com.tomjod.medidorfuerza.ui.navigation

/**
 * Define las rutas de navegación de forma tipada.
 */
sealed class Screen(val route: String) {
    object ProfileList : Screen("profile_list")
    object ProfileCreate : Screen("profile_create")
    object BluetoothConfig : Screen("bluetooth_config")
    object Measurement : Screen("measurement/{profileId}") {
        // Función helper para construir la ruta con un ID
        fun createRoute(profileId: Long) = "measurement/$profileId"
    }
    object MeasurementHistory : Screen("measurement_history/{profileId}") {
        // Función helper para construir la ruta con un ID
        fun createRoute(profileId: Long) = "measurement_history/$profileId"
    }
}