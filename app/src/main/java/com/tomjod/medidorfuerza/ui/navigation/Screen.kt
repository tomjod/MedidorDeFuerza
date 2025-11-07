package com.tomjod.medidorfuerza.ui.navigation

/**
 * Define las rutas de navegación de forma tipada.
 */
sealed class Screen(val route: String) {
    object ProfileList : Screen("profile_list")
    object ProfileCreate : Screen("profile_create")
    object Measurement : Screen("measurement/{profileId}") {
        // Función helper para construir la ruta con un ID
        fun createRoute(profileId: Long) = "measurement/$profileId"
    }
    // TODO: Añadir ruta para crear/editar perfil
}