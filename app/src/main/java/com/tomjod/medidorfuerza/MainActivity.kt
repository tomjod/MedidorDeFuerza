package com.tomjod.medidorfuerza

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter // Necesitarás esta librería para cargar fotos: implementation "io.coil-kt:coil-compose:2.5.0"

// Definimos los colores de la UI
private val AppBackgroundColor = Color(0xFF1C1C1E)
private val TextColorPrimary = Color.White
private val TextColorSecondary = Color(0xFFAEAEB2)
private val StatusColorConnected = Color(0xFF34C759) // Verde
private val StatusColorConnecting = Color(0xFFFF9500) // Naranjo
private val StatusColorDisconnected = Color(0xFFFF3B30) // Rojo
private val ButtonColor = Color(0xFF0A84FF) // Azul
private val ButtonColorSecondary = Color(0xFF303032) // Gris oscuro

// --- Modelo de Datos ---

data class UserProfile(
    val id: String,
    val nombre: String,
    val apellido: String,
    val edad: Int,
    val fotoUri: Uri? = null // Guardamos el URI de la foto
)

// Clase de estado para la UI
data class UiState(
    val connectionStatus: String = "Desconectado",
    val statusColor: Color = StatusColorDisconnected,
    val forceReading: String = "---",
    val buttonText: String = "Buscar y Conectar"
)

class MainActivity : ComponentActivity() {

    // ***
    // TODO: Aquí irían las variables de Bluetooth LE (BLE)
    // ***

    // El estado de la UI, manejado por Compose
    private val uiState = mutableStateOf(UiState())

    // --- Lógica de Perfil y Promedio (Simulada) ---
    // TODO: Esto se movería a un ViewModel con Room
    private val currentProfile = mutableStateOf<UserProfile?>(null)
    private val measurementHistory = mutableStateListOf<Float>()
    private val averageForce = derivedStateOf {
        if (measurementHistory.isEmpty()) 0.0f else measurementHistory.average().toFloat()
    }
    // --- Fin de la lógica simulada ---

    // Lanzador para los permisos de BLE y Almacenamiento
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                // Si se pidieron permisos de BLE, iniciar escaneo
                if (permissions.containsKey(Manifest.permission.BLUETOOTH_SCAN)) {
                    startBleScan()
                }
                // Si se pidieron permisos de galería, no hacer nada (se maneja en el click)
            } else {
                // Informar al usuario que los permisos son necesarios
                updateUiStatus("Permisos requeridos", StatusColorDisconnected)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: En una app real, aquí habría navegación.
        // Por ahora, cargamos un perfil falso para la demo.
        loadFakeProfile()

        setContent {
            // Pasamos el estado actual a nuestro Composable principal
            ForceMeterScreen(
                uiState = uiState.value,
                profile = currentProfile.value,
                averageForce = averageForce.value,
                onActionButtonClick = {
                    if (uiState.value.connectionStatus == "¡Conectado!") {
                        sendTareCommand() // Botón 1: Tarar
                    } else {
                        checkBlePermissions() // Botón 1: Conectar
                    }
                },
                onSaveMeasurementClick = {
                    // Botón 2: Guardar
                    saveCurrentMeasurement()
                }
            )
        }
    }

    private fun loadFakeProfile() {
        // TODO: Reemplazar con lógica de carga desde DB (Room)
        currentProfile.value = UserProfile(
            id = "1",
            nombre = "Andrea",
            apellido = "Zunino",
            edad = 23,
            fotoUri = null // Uri.parse("...")
        )
    }

    private fun checkBlePermissions() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            // Permisos para API < 31
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        // TODO: También deberíamos chequear CAMERA y READ_EXTERNAL_STORAGE aquí
        // al momento de querer cambiar la foto.
        // val storagePermissions = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA)
        // requestPermissionLauncher.launch(storagePermissions)

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            startBleScan()
        } else {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun startBleScan() {
        // ... (igual que antes)
        println("Iniciando escaneo BLE...")
        updateUiStatus("Buscando...", StatusColorConnecting)

        // Simulación para demo
        // onServicesDiscovered()
    }
    // ... (connectToDevice, onServicesDiscovered - sin cambios)
// ...
    private fun connectToDevice(device: Any /* BluetoothDevice */) {
// ... (igual que antes)
    }
    private fun onServicesDiscovered() {
// ... (igual que antes)
    }

    private fun onDataReceived(data: ByteArray?) {
        // TODO: Llamado cada vez que el ESP32 envía datos
        val simulatedData = (Math.random() * 50).toFloat()
        val forceString = String.format("%.1f", simulatedData)

        // Actualizamos el estado. Compose se encarga del resto.
        uiState.value = uiState.value.copy(forceReading = forceString)
    }


    private fun sendTareCommand() {
        println("Enviando comando 'Tarar' (Poner en Cero)...")
        uiState.value = uiState.value.copy(forceReading = "0.0") // Simulación de reseteo
    }

    private fun saveCurrentMeasurement() {
        val currentReading = uiState.value.forceReading.toFloatOrNull()
        if (currentReading != null && currentReading > 0) {
            // TODO: Guardar esto en la Base de Datos (Room) asociado al UserProfile
            measurementHistory.add(currentReading)
            println("Medición guardada: $currentReading. Nuevo promedio: ${averageForce.value}")
        }
    }

    // --- Funciones Helper de UI (ahora solo actualizan el estado) ---
// ... (updateUiOnConnect, updateUiOnDisconnect, updateUiStatus - sin cambios)
// ...
    private fun updateUiOnConnect() {
// ... (igual que antes)
    }

    private fun updateUiOnDisconnect() {
// ... (igual que antes)
    }

    private fun updateUiStatus(message: String, color: Color) {
// ... (igual que antes)
    }
}

// --- Composable que define la UI ---

@Composable
fun ForceMeterScreen(
    uiState: UiState,
    profile: UserProfile?,
    averageForce: Float,
    onActionButtonClick: () -> Unit,
    onSaveMeasurementClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AppBackgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // 1. Cabecera de Perfil
            if (profile != null) {
                ProfileHeader(profile = profile, modifier = Modifier.padding(top = 8.dp))
            }

            // 2. Indicador de Estado de Conexión
            Text(
                text = uiState.connectionStatus,
                color = uiState.statusColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            )

            // 3. Bloque de Lectura de Fuerza (Centrado)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f) // Ocupa el espacio central
            ) {
                Text(
                    text = uiState.forceReading,
                    color = TextColorPrimary,
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "KG",
                    color = TextColorSecondary,
                    fontSize = 34.sp,
                    textAlign = TextAlign.Center
                )

                // 4. Muestra de Promedio
                Text(
                    text = "Promedio: ${String.format("%.1f", averageForce)} KG",
                    color = TextColorSecondary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }

            // 5. Botones de Acción
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Botón Principal (Conectar / Tarar)
                Button(
                    onClick = onActionButtonClick,
                    colors = ButtonDefaults.buttonColors(containerColor = ButtonColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                ) {
                    Text(
                        text = uiState.buttonText,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Botón Secundario (Guardar Medición)
                Button(
                    onClick = onSaveMeasurementClick,
                    colors = ButtonDefaults.buttonColors(containerColor = ButtonColorSecondary),
                    enabled = uiState.connectionStatus == "¡Conectado!", // Solo se puede guardar si está conectado
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                ) {
                    Text(
                        text = "Guardar Medición",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// --- Componente de Cabecera de Perfil ---

@Composable
fun ProfileHeader(profile: UserProfile, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Foto de Perfil
        // TODO: Implementar lógica para elegir foto (onClick)
        Image(
            painter = if (profile.fotoUri != null) {
                rememberAsyncImagePainter(model = profile.fotoUri)
            } else {
                rememberVectorPainter(image = Icons.Default.Person)
            },
            contentDescription = "Foto de perfil",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(Color.Gray)
                .border(2.dp, Color.White, CircleShape)
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Info de Perfil
        Column {
            Text(
                text = "${profile.nombre} ${profile.apellido}",
                color = TextColorPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${profile.edad} años",
                color = TextColorSecondary,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // TODO: Añadir un botón/icono para "Editar Perfil" o "Cambiar Perfil"
        // Icon(Icons.Default.Edit, ...)
    }
}


// --- Previews ---

@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
fun DefaultPreview() {
    ForceMeterScreen(
        uiState = UiState(),
        profile = UserProfile("1", "Andrea", "Zunino", 23, null),
        averageForce = 0.0f,
        onActionButtonClick = {},
        onSaveMeasurementClick = {}
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
fun ConnectedPreview() {
    ForceMeterScreen(
        uiState = UiState(
            connectionStatus = "¡Conectado!",
            statusColor = StatusColorConnected,
            forceReading = "25.7",
            buttonText = "Poner en Cero (Tarar)"
        ),
        profile = UserProfile("1", "Andrea", "Zunino", 23, null),
        averageForce = 30.5f,
        onActionButtonClick = {},
        onSaveMeasurementClick = {}
    )
}

