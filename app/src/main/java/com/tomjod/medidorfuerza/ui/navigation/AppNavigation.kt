package com.tomjod.medidorfuerza.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tomjod.medidorfuerza.ui.features.measurement.ForceMeterRoute
import com.tomjod.medidorfuerza.ui.features.profile.ProfileCreateScreen
import com.tomjod.medidorfuerza.ui.features.profile.ProfileListScreen

/**
 * Composable principal que aloja el NavHost y define el gráfico de navegación.
 */

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.ProfileList.route
    ) {

        // --- Pantalla 1: Lista de Perfiles ---
        composable(route = Screen.ProfileList.route) {
            ProfileListScreen(
                navController = navController
            )
        }

        // --- Pantalla 2: Crear Perfil ---
        composable(route = Screen.ProfileCreate.route) {
            ProfileCreateScreen(
                navController = navController
            )
        }

        // --- Pantalla 3: Medición ---
        composable(
            route = Screen.Measurement.route,
            arguments = listOf(
                navArgument("profileId") {
                    type = NavType.LongType
                    // No pongas un defaultValue, es mejor que falle
                    // si no se pasa el ID.
                }
            )
        ) {
            // ¡Llama a la RUTA, no a la PANTALLA!
            // ForceMeterRoute se encargará de crear el ViewModel
            // y pasar el profileId.
            ForceMeterRoute(
                navController = navController
            )
        }
    }
}