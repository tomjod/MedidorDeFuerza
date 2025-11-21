package com.tomjod.medidorfuerza.ui.features.measurement

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.tomjod.medidorfuerza.data.db.entities.Measurement
import java.text.SimpleDateFormat
import java.util.*

/**
 * Pantalla que muestra el historial de mediciones para un perfil.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementHistoryScreen(
    navController: NavController,
    viewModel: MeasurementHistoryViewModel = hiltViewModel()
) {
    val measurements by viewModel.measurements.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial de Mediciones") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        if (measurements.isEmpty()) {
            // Estado vacío
            EmptyHistoryState(modifier = Modifier.padding(padding))
        } else {
            // Lista de mediciones
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(measurements, key = { it.id }) { measurement ->
                    MeasurementCard(
                        measurement = measurement,
                        onDelete = { viewModel.deleteMeasurement(it.id) }
                    )
                }
            }
        }
    }
}

/**
 * Estado vacío cuando no hay mediciones.
 */
@Composable
fun EmptyHistoryState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "📊",
            style = MaterialTheme.typography.displayLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No hay mediciones guardadas",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Realiza una medición y guárdala para ver el historial aquí",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Tarjeta que muestra una medición individual.
 */
@Composable
fun MeasurementCard(
    measurement: Measurement,
    onDelete: (Measurement) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Encabezado con fecha y botón de eliminar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = formatDate(measurement.timestamp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatTime(measurement.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Datos de la medición
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MeasurementDataColumn(
                    label = "Isquios",
                    avgValue = measurement.isquiosAvg,
                    maxValue = measurement.isquiosMax,
                    modifier = Modifier.weight(1f)
                )
                MeasurementDataColumn(
                    label = "Cuádriceps",
                    avgValue = measurement.cuadsAvg,
                    maxValue = measurement.cuadsMax,
                    modifier = Modifier.weight(1f)
                )
                MeasurementDataColumn(
                    label = "Ratio",
                    value = measurement.ratio,
                    showMax = false,
                    modifier = Modifier.weight(1f)
                )
            }

            // Duración de la sesión
            if (measurement.durationSeconds > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Duración: ${formatDuration(measurement.durationSeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            // Notas (si existen)
            measurement.notes?.let { notes ->
                if (notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Notas: $notes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Diálogo de confirmación para eliminar
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar medición") },
            text = { Text("¿Estás seguro de que quieres eliminar esta medición? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(measurement)
                        showDeleteDialog = false
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

/**
 * Columna que muestra datos de medición (promedio y máximo).
 */
@Composable
fun MeasurementDataColumn(
    label: String,
    avgValue: Float? = null,
    maxValue: Float? = null,
    value: Float? = null,
    showMax: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(4.dp))
        
        if (value != null) {
            // Mostrar un solo valor (para ratio)
            Text(
                text = String.format("%.2f", value),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        } else {
            // Mostrar promedio y máximo
            Text(
                text = String.format("%.1f N", avgValue ?: 0f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (showMax) {
                Text(
                    text = "Max: ${String.format("%.1f N", maxValue ?: 0f)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

// --- Funciones de Utilidad ---

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return if (minutes > 0) {
        "${minutes}m ${remainingSeconds}s"
    } else {
        "${seconds}s"
    }
}
