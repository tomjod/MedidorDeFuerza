package com.tomjod.medidorfuerza.ui.features.bluetooth

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.tomjod.medidorfuerza.data.ble.BleConnectionState
import com.tomjod.medidorfuerza.data.ble.ForceReadings
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothConfigScreen(
    navController: NavController,
    viewModel: BluetoothConfigViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val forceData by viewModel.forceData.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Mostrar mensajes de error como Snackbar
    LaunchedEffect(connectionState) {
        val currentState = connectionState
        if (currentState is BleConnectionState.Error) {
            snackbarHostState.showSnackbar(currentState.message)
        }
    }

    val enableBt = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // Notify ViewModel to re-evaluate Bluetooth/permission state after the user responds
        viewModel.onBluetoothStateChanged()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración Bluetooth") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // Estado de conexión
                ConnectionStatusCard(
                    connectionState = connectionState,
                    onScanClick = viewModel::startScan,
                    onDisconnectClick = viewModel::disconnect
                )
            }

            item {
                // Controles del dispositivo ESP32
                if (connectionState is BleConnectionState.Connected) {
                    DeviceControlCard(
                        forceData = forceData,
                        onTareClick = viewModel::sendTareCommand
                    )
                }
            }

            item {
                // Información técnica
                TechnicalInfoCard()
            }

            // Show enable-Bluetooth button reactively when ViewModel reports BluetoothDisabled
            item {
                if (connectionState is BleConnectionState.BluetoothDisabled) {
                    Button(
                        onClick = {
                            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            enableBt.launch(enableBtIntent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Bluetooth, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Activar Bluetooth")
                    }
                }
            }
        }
    }
}

/*
// Función comentada - ya no se usa porque duplicaba información
@Composable
private fun DeviceInfoCard(
    connectionState: BleConnectionState,
    forceData: ForceReadings?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (connectionState) {
                is BleConnectionState.Connected -> MaterialTheme.colorScheme.surface
                is BleConnectionState.Connecting -> MaterialTheme.colorScheme.surface
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icono de estado
            val (icon, iconColor, statusText) = when (connectionState) {
                is BleConnectionState.Connected -> Triple(
                    Icons.Filled.BluetoothConnected,
                    MaterialTheme.colorScheme.primary,
                    "Dispositivo ESP32 Conectado"
                )

                is BleConnectionState.Connecting -> Triple(
                    Icons.AutoMirrored.Filled.BluetoothSearching,
                    MaterialTheme.colorScheme.secondary,
                    "Conectando..."
                )

                is BleConnectionState.Scanning -> Triple(
                    Icons.AutoMirrored.Filled.BluetoothSearching,
                    MaterialTheme.colorScheme.secondary,
                    "Buscando dispositivos..."
                )

                is BleConnectionState.Error -> Triple(
                    Icons.Filled.BluetoothDisabled,
                    MaterialTheme.colorScheme.error,
                    "Error de conexión"
                )

                else -> Triple(
                    Icons.Filled.Bluetooth,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    "Desconectado"
                )
            }

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            if (forceData != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Ratio: ${String.format(Locale.getDefault(), "%.2f", forceData.ratio)}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Isquios: ${String.format(Locale.getDefault(), "%.1f", forceData.isquios)} / Cuads: ${String.format(Locale.getDefault(), "%.1f", forceData.cuads)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
*/

@Composable
private fun ConnectionStatusCard(
    connectionState: BleConnectionState,
    onScanClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Estado de Conexión",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            when (connectionState) {
                is BleConnectionState.Disconnected -> {
                    Text(
                        text = "El medidor de fuerza no está conectado. Presiona 'Buscar Dispositivo' para conectarte al ESP32.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(
                        onClick = onScanClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Buscar Dispositivo ESP32")
                    }
                }

                is BleConnectionState.Scanning -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Buscando dispositivos ESP32 cercanos...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                is BleConnectionState.Connecting -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Estableciendo conexión con el dispositivo...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                is BleConnectionState.Connected -> {
                    Text(
                        text = "✅ Dispositivo ESP32 conectado correctamente. El medidor está listo para usar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    OutlinedButton(
                        onClick = onDisconnectClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Desconectar")
                    }
                }

                is BleConnectionState.Error -> {
                    Text(
                        text = "❌ ${connectionState.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(
                        onClick = onScanClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reintentar")
                    }
                }

                is BleConnectionState.BluetoothDisabled -> {
                    Text(
                        text = "🔵 Bluetooth está deshabilitado. Actívalo para continuar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                is BleConnectionState.PermissionsRequired -> {
                    Text(
                        text = "🔒 Se requieren permisos de Bluetooth. Concédelos para continuar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                is BleConnectionState.BluetoothNotSupported -> {
                    Text(
                        text = "❌ Este dispositivo no soporta Bluetooth LE.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceControlCard(
    forceData: ForceReadings?,
    onTareClick: () -> Unit
) {
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
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "⚙️ Controles del Dispositivo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp),
                textAlign = TextAlign.Center
            )

            if (forceData != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Lectura Actual",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Ratio: ${String.format(Locale.getDefault(), "%.2f", forceData.ratio)}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "I: ${String.format(Locale.getDefault(), "%.1f", forceData.isquios)} / C: ${String.format(Locale.getDefault(), "%.1f", forceData.cuads)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Button(
                onClick = onTareClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🔄 Tarar (Poner en Cero)")
            }

            Text(
                text = "💡 La función 'Tarar' establece la lectura actual como punto cero para mediciones relativas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TechnicalInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🔧 Información Técnica",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp),
                textAlign = TextAlign.Center
            )

            val technicalSpecs = listOf(
                "Dispositivo" to "ESP32 con sensor de fuerza",
                "Protocolo" to "Bluetooth Classic (SPP)",
                "Rango" to "0 - 1000 N (configurable)",
                "Precisión" to "±0.1 N",
                "Frecuencia" to "10 Hz de muestreo"
            )

            technicalSpecs.forEach { (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}