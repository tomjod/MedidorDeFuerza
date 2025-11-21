package com.tomjod.medidorfuerza.ui.features.measurement

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberAsyncImagePainter
import com.tomjod.medidorfuerza.data.ble.BleConnectionState
import com.tomjod.medidorfuerza.data.ble.ForceReadings
import com.tomjod.medidorfuerza.data.db.entities.Gender
import com.tomjod.medidorfuerza.data.db.entities.UserProfile

// --- Define tus colores en ui/theme/Color.kt ---
// --- Design System Colors ---
private val AppBackground = Color(0xFF121212)
private val SurfaceColor = Color(0xFF1E1E1E)
private val PrimaryAccent = Color(0xFFBB86FC) // Purple accent
private val SecondaryAccent = Color(0xFF03DAC6) // Teal accent
private val TextPrimary = Color(0xFFEEEEEE)
private val TextSecondary = Color(0xFFB0B0B0)
private val GoodRatioColor = Color(0xFF4CAF50)
private val WarningRatioColor = Color(0xFFFFC107)
private val BadRatioColor = Color(0xFFCF6679)

// --- Status Colors Mapped to Theme ---
private val StatusColorConnected = GoodRatioColor
private val StatusColorConnecting = WarningRatioColor
private val StatusColorDisconnected = BadRatioColor

// --- Custom Components ---

@Composable
fun MetricCard(
    title: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    iconVector: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun CircularRatioIndicator(
    ratio: Float,
    modifier: Modifier = Modifier
) {
    val color = when {
        ratio >= 0.6f -> GoodRatioColor
        ratio < 0.5f -> BadRatioColor
        else -> WarningRatioColor
    }
    
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        CircularProgressIndicator(
            progress = 1f,
            modifier = Modifier.fillMaxSize(),
            color = SurfaceColor,
            strokeWidth = 12.dp,
            trackColor = SurfaceColor,
        )
        CircularProgressIndicator(
            progress = ratio.coerceIn(0f, 1f),
            modifier = Modifier.fillMaxSize(),
            color = color,
            strokeWidth = 12.dp,
            trackColor = Color.Transparent,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = String.format("%.2f", ratio),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Ratio H/Q",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary
            )
        }
    }
}

// --- 1. CONTENEDOR STATEFUL (CON LÓGICA) ---

/**
 * Este es el composable "inteligente" o "contenedor".
 * Se encarga de:
 * 1. Inyectar el ViewModel.
 * 2. Colectar los estados (States).
 * 3. Manejar la lógica de permisos.
 * 4. Pasar los datos simples al composable "tonto" (Stateless).
 */
@Composable
fun ForceMeterRoute(
    navController: NavController,
    viewModel: ForceMeterViewModel = hiltViewModel()
) {
    // --- Colectamos los estados del ViewModel ---
    // Usamos collectAsStateWithLifecycle para seguridad en el ciclo de vida.
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val measurementCount by viewModel.measurementCount.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val latestForce by viewModel.latestForce.collectAsStateWithLifecycle()
    val currentSession by viewModel.currentSession.collectAsStateWithLifecycle()
    val saveSuccess by viewModel.saveSuccess.collectAsStateWithLifecycle()

    // --- Lógica de Permisos de Bluetooth ---
    val context = LocalContext.current
    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            // Si los permisos fueron concedidos AHORA, iniciamos el escaneo.
            viewModel.onEvent(MeasurementEvent.ConnectClicked)
        } else {
            // TODO: Mostrar un SnackBar o mensaje al usuario indicando que
            // los permisos son necesarios para conectar.
        }
    }

    ForceMeterScreen(
        profile = profile,
        measurementCount = measurementCount,
        connectionState = connectionState,
        latestForce = latestForce,
        currentSession = currentSession,
        saveSuccess = saveSuccess,
        onEvent = viewModel::onEvent, // Pasamos la función de eventos directamente
        onNavigateBack = {
            navController.navigateUp()
        },
        onConnectClick = {
            // Esta lambda especial maneja la lógica de permisos
            val allGranted = requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
            if (allGranted) {
                // Si ya tenemos permisos, escaneamos.
                viewModel.onEvent(MeasurementEvent.ConnectClicked)
            } else {
                // Si no, pedimos permisos.
                permissionLauncher.launch(requiredPermissions)
            }
        },
        onViewHistory = {
            profile?.let {
                navController.navigate(com.tomjod.medidorfuerza.ui.navigation.Screen.MeasurementHistory.createRoute(it.id))
            }
        },
        onResetSaveSuccess = viewModel::resetSaveSuccess
    )
}


// --- 2. COMPOSABLE STATELESS (SIN LÓGICA, SOLO UI) ---

/**
 * Este es el composable "tonto" o "de presentación".
 * - No sabe nada sobre el ViewModel.
 * - Recibe todos los datos como parámetros.
 * - Devuelve todos los eventos a través de lambdas (onEvent, onNavigateBack, etc.).
 * - Es 100% previsualizable en el editor de Compose.
 */
@Composable
fun ForceMeterScreen(
    profile: UserProfile?,
    measurementCount: Int,
    connectionState: BleConnectionState,
    latestForce: ForceReadings?,
    currentSession: com.tomjod.medidorfuerza.domain.MeasurementSession?,
    saveSuccess: Boolean,
    onEvent: (MeasurementEvent) -> Unit,
    onNavigateBack: () -> Unit,
    onConnectClick: () -> Unit,
    onViewHistory: () -> Unit,
    onResetSaveSuccess: () -> Unit
) {
    // --- Lógica de UI (derivación de estado) ---
    // Derivamos el texto y color del estado de conexión.
    val (statusText, statusColor) = when (connectionState) {
        is BleConnectionState.Connected -> "¡Conectado!" to StatusColorConnected
        is BleConnectionState.Connecting -> "Conectando..." to StatusColorConnecting
        is BleConnectionState.Disconnected -> "Desconectado" to StatusColorDisconnected
        is BleConnectionState.Scanning -> "Buscando..." to StatusColorConnecting
        is BleConnectionState.Error -> connectionState.message to StatusColorDisconnected
        is BleConnectionState.BluetoothDisabled -> "Bluetooth Deshabilitado" to StatusColorDisconnected
        is BleConnectionState.BluetoothNotSupported -> "Bluetooth LE No Soportado" to StatusColorDisconnected
        is BleConnectionState.PermissionsRequired -> "Permisos Requeridos" to StatusColorDisconnected
    }

    val isConnected = connectionState is BleConnectionState.Connected
    var showCalibrationDialog by remember { mutableStateOf(false) }

    if (showCalibrationDialog) {
        CalibrationDialog(
            onDismiss = { showCalibrationDialog = false },
            onCalibrateIsquios = { factor ->
                onEvent(MeasurementEvent.CalibrateIsquios(factor))
                showCalibrationDialog = false
            },
            onCalibrateCuads = { factor ->
                onEvent(MeasurementEvent.CalibrateCuads(factor))
                showCalibrationDialog = false
            }
        )
    }

    // --- UI ---
    Scaffold(
        containerColor = AppBackground,
        topBar = {
            ProfileHeader(
                profile = profile,
                onBackClick = onNavigateBack,
                onSettingsClick = { showCalibrationDialog = true },
                showSettings = isConnected
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Status Indicator
            Surface(
                color = statusColor.copy(alpha = 0.1f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                Text(
                    text = statusText,
                    color = statusColor,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Main Ratio Indicator
            CircularRatioIndicator(
                ratio = latestForce?.ratio ?: 0f,
                modifier = Modifier.size(220.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 3. Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MetricCard(
                    title = "Isquios",
                    value = String.format("%.1f", latestForce?.isquios ?: 0f),
                    unit = "Newtons",
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Cuádriceps",
                    value = String.format("%.1f", latestForce?.cuads ?: 0f),
                    unit = "Newtons",
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Measurement count and history link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$measurementCount mediciones guardadas",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
                if (measurementCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onViewHistory) {
                        Text("Ver historial", color = PrimaryAccent)
                    }
                }
            }
            
            // Session info (if active)
            currentSession?.let { session ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = PrimaryAccent.copy(alpha = 0.1f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "📊 Medición en curso: ${session.getReadingCount()} lecturas • ${session.getDurationSeconds()}s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PrimaryAccent,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Save success feedback
            if (saveSuccess) {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(2000)
                    onResetSaveSuccess()
                }
                Surface(
                    color = GoodRatioColor.copy(alpha = 0.1f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "✓ Medición guardada exitosamente",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GoodRatioColor,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }

            // 4. Action Buttons
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                if (isConnected) {
                    Button(
                        onClick = { onEvent(MeasurementEvent.TareClicked) },
                        colors = ButtonDefaults.buttonColors(containerColor = SecondaryAccent),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("TARAR (CERO)", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    
                    // Session controls
                    if (currentSession == null) {
                        Button(
                            onClick = { onEvent(MeasurementEvent.StartSession) },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Text("INICIAR MEDICIÓN", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { onEvent(MeasurementEvent.StopAndSaveSession()) },
                            colors = ButtonDefaults.buttonColors(containerColor = GoodRatioColor),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Text("GUARDAR MEDICIÓN", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Button(
                        onClick = onConnectClick,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("CONECTAR DISPOSITIVO", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Componente de UI separado para la cabecera.
 */
@Composable
private fun ProfileHeader(
    profile: UserProfile?,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    showSettings: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Botón de "Atrás"
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .size(40.dp)
                .background(SurfaceColor, CircleShape)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Volver",
                tint = TextPrimary
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Foto de Perfil
        Image(
            painter = if (profile?.fotoUri != null) {
                rememberAsyncImagePainter(model = Uri.parse(profile.fotoUri))
            } else {
                rememberVectorPainter(image = Icons.Default.Person)
            },
            contentDescription = "Foto de perfil",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Gray)
                .border(1.dp, TextSecondary, CircleShape)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Info de Perfil
        if (profile != null) {
            Column {
                Text(
                    text = "${profile.nombre} ${profile.apellido}",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${profile.edad} años",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            // Muestra un placeholder mientras carga el perfil
            Text(text = "Cargando...", color = TextSecondary)
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        if (showSettings) {
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(40.dp)
                    .background(SurfaceColor, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Calibrar",
                    tint = TextPrimary
                )
            }
        }
    }
}

@Composable
fun CalibrationDialog(
    onDismiss: () -> Unit,
    onCalibrateIsquios: (Float) -> Unit,
    onCalibrateCuads: (Float) -> Unit
) {
    var isquiosFactor by remember { mutableStateOf("") }
    var cuadsFactor by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calibración de Sensores") },
        text = {
            Column {
                Text("Introduce el factor de calibración para cada sensor:", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = isquiosFactor,
                    onValueChange = { isquiosFactor = it },
                    label = { Text("Factor Isquios") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                Button(
                    onClick = {
                        isquiosFactor.toFloatOrNull()?.let { onCalibrateIsquios(it) }
                    },
                    enabled = isquiosFactor.toFloatOrNull() != null,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Calibrar Isquios")
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = cuadsFactor,
                    onValueChange = { cuadsFactor = it },
                    label = { Text("Factor Cuádriceps") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                Button(
                    onClick = {
                        cuadsFactor.toFloatOrNull()?.let { onCalibrateCuads(it) }
                    },
                    enabled = cuadsFactor.toFloatOrNull() != null,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Calibrar Cuádriceps")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

// --- 3. PREVIEW ---

@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
fun ForceMeterScreenPreview_Connected() {
    ForceMeterScreen(
        profile = UserProfile(id = 1, nombre = "Andrea", apellido = "Zunino", edad = 23, fotoUri = null, sexo = Gender.FEMENINO),
        measurementCount = 5,
        connectionState = BleConnectionState.Connected,
        latestForce = ForceReadings(20f, 30f, 0.66f),
        currentSession = null,
        saveSuccess = false,
        onEvent = {},
        onNavigateBack = {},
        onConnectClick = {},
        onViewHistory = {},
        onResetSaveSuccess = {}
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
fun ForceMeterScreenPreview_Disconnected() {
    ForceMeterScreen(
        profile = UserProfile(id = 1, nombre = "Andrea", apellido = "Zunino", edad = 23, fotoUri = null, sexo = Gender.FEMENINO),
        measurementCount = 0,
        connectionState = BleConnectionState.Disconnected,
        latestForce = null,
        currentSession = null,
        saveSuccess = false,
        onEvent = {},
        onNavigateBack = {},
        onConnectClick = {},
        onViewHistory = {},
        onResetSaveSuccess = {}
    )
}