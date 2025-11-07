package com.tomjod.medidorfuerza.ui.features.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileCreateScreen(
    navController: NavController,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val creationState by viewModel.creationState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Manejo de éxito y errores
    LaunchedEffect(creationState.isSuccess) {
        if (creationState.isSuccess) {
            navController.popBackStack()
            viewModel.resetCreationState()
        }
    }

    LaunchedEffect(creationState.errorMessage) {
        creationState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crear Perfil") },
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                        viewModel.resetCreationState()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header con icono
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Nuevo Atleta",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Completa la información básica",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Formulario
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Campo Nombre
                    OutlinedTextField(
                        value = creationState.nombre,
                        onValueChange = viewModel::updateNombre,
                        label = { Text("Nombre") },
                        placeholder = { Text("Ej: Juan") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !creationState.isLoading,
                        singleLine = true
                    )

                    // Campo Apellido
                    OutlinedTextField(
                        value = creationState.apellido,
                        onValueChange = viewModel::updateApellido,
                        label = { Text("Apellido") },
                        placeholder = { Text("Ej: Pérez") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !creationState.isLoading,
                        singleLine = true
                    )

                    // Campo Edad
                    OutlinedTextField(
                        value = creationState.edad,
                        onValueChange = viewModel::updateEdad,
                        label = { Text("Edad") },
                        placeholder = { Text("Ej: 25") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !creationState.isLoading,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Botón de crear
                    Button(
                        onClick = viewModel::createProfile,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !creationState.isLoading
                    ) {
                        if (creationState.isLoading) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.width(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Creando...")
                            }
                        } else {
                            Text("Crear Perfil")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Información adicional
            Text(
                text = "💡 Podrás agregar una foto después de crear el perfil",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}