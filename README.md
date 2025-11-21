# ForceMetrics - Sistema de Medición de Fuerza Muscular

## 📋 Descripción del Proyecto

**ForceMetrics** es una aplicación Android profesional diseñada para la medición, registro y análisis de fuerza muscular en isquiotibiales y cuádriceps. El sistema está orientado a fisioterapeutas, entrenadores deportivos y profesionales de la salud que requieren un seguimiento preciso y sistemático de la fuerza muscular de sus pacientes/atletas.

### Contexto Académico

Este proyecto fue desarrollado como parte de una tesis de grado, con el objetivo de crear una herramienta tecnológica que facilite la evaluación objetiva de la fuerza muscular y el seguimiento del progreso de atletas en entrenamiento.

---

## 🎯 Objetivos del Proyecto

### Objetivo General

Desarrollar una aplicación móvil que permita la medición, almacenamiento y análisis de datos de fuerza muscular mediante un dispositivo de medición conectado por Bluetooth.

### Objetivos Específicos

1. **Medición en tiempo real** de fuerza muscular en isquiotibiales y cuádriceps
2. **Cálculo automático** del ratio H/Q (Hamstrings/Quadriceps)
3. **Almacenamiento persistente** de mediciones con historial completo
4. **Gestión de perfiles** de múltiples pacientes/atletas
5. **Exportación de datos** en formato CSV para análisis posterior
6. **Interfaz intuitiva** siguiendo principios de Material Design 3

---

## 🏗️ Arquitectura del Sistema

### Componentes Principales

```
┌─────────────────────────────────────────────────────────┐
│                   APLICACIÓN ANDROID                     │
│                     (ForceMetrics)                       │
├─────────────────────────────────────────────────────────┤
│  UI Layer (Jetpack Compose)                             │
│  ├── ProfileListScreen                                  │
│  ├── ProfileDetailScreen                                │
│  ├── ForceMeterScreen                                   │
│  ├── MeasurementHistoryScreen                           │
│  └── BluetoothConfigScreen                              │
├─────────────────────────────────────────────────────────┤
│  ViewModel Layer (MVVM)                                 │
│  ├── ProfileViewModel                                   │
│  ├── ForceMeterViewModel                                │
│  ├── MeasurementHistoryViewModel                        │
│  └── BluetoothConfigViewModel                           │
├─────────────────────────────────────────────────────────┤
│  Domain Layer                                            │
│  ├── MeasurementSession                                 │
│  └── CsvExporter                                        │
├─────────────────────────────────────────────────────────┤
│  Data Layer                                              │
│  ├── Repository (MeasurementRepository)                 │
│  ├── Database (Room)                                    │
│  └── Bluetooth (BluetoothClassicServiceManager)         │
└─────────────────────────────────────────────────────────┘
                          │
                          │ Bluetooth Classic (SPP)
                          ▼
┌─────────────────────────────────────────────────────────┐
│              DISPOSITIVO DE MEDICIÓN                     │
│                   (ESP32 + Sensores)                     │
└─────────────────────────────────────────────────────────┘
```

### Patrón de Arquitectura: MVVM (Model-View-ViewModel)

**Ventajas de MVVM:**

- Separación clara de responsabilidades
- Facilita el testing unitario
- Código más mantenible y escalable
- Ciclo de vida gestionado automáticamente

---

## 💾 Modelo de Datos

### Base de Datos (Room)

#### Entidad: UserProfile

```kotlin
@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nombre: String,
    val apellido: String,
    val edad: Int,
    val peso: Float,      // kg
    val altura: Float,    // cm
    val genero: Gender
)
```

#### Entidad: Measurement
```kotlin
@Entity(tableName = "measurements")
data class Measurement(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val profileId: Long,
    val isquiosAvg: Float,    // Promedio isquiotibiales (N)
    val isquiosMax: Float,    // Máximo isquiotibiales (N)
    val cuadsAvg: Float,      // Promedio cuádriceps (N)
    val cuadsMax: Float,      // Máximo cuádriceps (N)
    val ratio: Float,         // Ratio H/Q
    val timestamp: Long,      // Fecha/hora de medición
    val durationSeconds: Int, // Duración de la sesión
    val notes: String?        // Notas opcionales
)
```

### Diagrama de Relaciones

```
UserProfile (1) ──────< (N) Measurement
    │                        │
    │                        ├─ isquiosAvg
    │                        ├─ isquiosMax
    │                        ├─ cuadsAvg
    │                        ├─ cuadsMax
    │                        ├─ ratio
    │                        ├─ timestamp
    │                        └─ durationSeconds
    │
    ├─ nombre
    ├─ apellido
    ├─ edad
    ├─ peso
    ├─ altura
    └─ genero
```

---

## 🔌 Comunicación Bluetooth

### Protocolo de Comunicación

**Tipo:** Bluetooth Classic (SPP - Serial Port Profile)  
**UUID:** `00001101-0000-1000-8000-00805F9B34FB`

### Formato de Paquete

```
┌────┬────┬──────────────┬──────────┬────┐
│STX │LEN │   PAYLOAD    │ CHECKSUM │ETX │
├────┼────┼──────────────┼──────────┼────┤
│0x02│0x0C│  12 bytes    │  1 byte  │0x03│
└────┴────┴──────────────┴──────────┴────┘
```

**Payload (12 bytes):**

- Bytes 0-3: Fuerza Isquiotibiales (float, little-endian)
- Bytes 4-7: Fuerza Cuádriceps (float, little-endian)
- Bytes 8-11: Ratio H/Q (float, little-endian)

**Checksum:** XOR de todos los bytes del payload

### Comandos Soportados

| Comando | Descripción |
|---------|-------------|
| `t\n` | Tarar (poner en cero) |
| `i=X.XX\n` | Calibrar isquiotibiales (factor X.XX) |
| `q=X.XX\n` | Calibrar cuádriceps (factor X.XX) |

---

## 📱 Funcionalidades Principales

### 1. Gestión de Perfiles

**Características:**

- Crear perfiles de pacientes/atletas
- Almacenar datos antropométricos (edad, peso, altura, género)
- Visualizar lista de todos los perfiles
- Acceder a detalles y mediciones de cada perfil

**Pantallas:**

- `ProfileListScreen`: Lista de perfiles
- `ProfileCreateScreen`: Creación de nuevo perfil
- `ProfileDetailScreen`: Detalles del perfil

### 2. Medición de Fuerza

**Características:**

- Conexión automática con dispositivo ESP32
- Visualización en tiempo real de:
  - Fuerza isquiotibiales (N)
  - Fuerza cuádriceps (N)
  - Ratio H/Q
- Sistema de sesiones:
  - Iniciar sesión de medición
  - Acumular múltiples lecturas
  - Calcular promedios y máximos
  - Guardar sesión completa
- Función de tarado (poner en cero)
- Calibración de sensores

**Pantalla:**

- `ForceMeterScreen`: Medición en tiempo real

### 3. Historial de Mediciones

**Características:**

- Visualización cronológica de todas las mediciones
- Detalles de cada medición:
  - Fecha y hora
  - Valores promedio y máximo
  - Duración de la sesión
  - Notas adicionales
- Eliminación de mediciones (con confirmación)
- Exportación a CSV

**Pantalla:**

- `MeasurementHistoryScreen`: Historial completo

### 4. Exportación de Datos

**Características:**

- Generación de archivos CSV
- Formato estándar compatible con Excel/Sheets
- Compartir vía:
  - WhatsApp
  - Email
  - Google Drive
  - Cualquier app compatible

**Formato CSV:**

```csv
Fecha,Hora,Isquios Avg (N),Isquios Max (N),Cuads Avg (N),Cuads Max (N),Ratio H/Q,Duración (s),Notas
21/11/2024,18:30:45,45.20,52.10,68.50,75.30,0.66,15,"Primera medición"
```

### 5. Configuración Bluetooth

**Características:**

- Búsqueda automática de dispositivos
- Conexión/desconexión manual
- Estado de conexión en tiempo real
- Gestión de permisos automática
- Información técnica del dispositivo

**Pantalla:**

- `BluetoothConfigScreen`: Configuración BT

---

## 🛠️ Tecnologías Utilizadas

### Framework y Lenguaje

- **Kotlin** 2.2.21
- **Android SDK** (minSdk: 26, targetSdk: 34)
- **Jetpack Compose** (UI moderna y declarativa)

### Arquitectura y Patrones

- **MVVM** (Model-View-ViewModel)
- **Clean Architecture** (separación en capas)
- **Repository Pattern** (abstracción de datos)
- **Dependency Injection** (Hilt/Dagger)

### Librerías Principales

| Librería | Versión | Propósito |
|----------|---------|-----------|
| Jetpack Compose | 1.5.4 | UI declarativa |
| Room | 2.6.0 | Base de datos local |
| Hilt | 2.48 | Inyección de dependencias |
| Navigation Compose | 2.7.5 | Navegación entre pantallas |
| Coroutines | 1.7.3 | Programación asíncrona |
| Material 3 | 1.1.2 | Diseño Material Design |
| Coil | 2.5.0 | Carga de imágenes |

### Componentes Android

- **Room Database**: Persistencia de datos
- **Bluetooth Classic**: Comunicación con hardware
- **FileProvider**: Compartir archivos de forma segura
- **StateFlow**: Gestión de estado reactivo
- **ViewModel**: Gestión de ciclo de vida

---

## 📊 Flujo de Trabajo

### Flujo Completo de Medición

```
1. CREAR PERFIL
   ↓
2. CONECTAR DISPOSITIVO BLUETOOTH
   ↓
3. INICIAR SESIÓN DE MEDICIÓN
   ↓
4. REALIZAR MEDICIONES
   │ (múltiples lecturas acumuladas)
   ↓
5. DETENER Y GUARDAR SESIÓN
   │ (cálculo de promedios/máximos)
   ↓
6. VISUALIZAR EN HISTORIAL
   ↓
7. EXPORTAR DATOS (opcional)
   ↓
8. ANÁLISIS EXTERNO
   (Excel, SPSS, etc.)
```

### Diagrama de Secuencia: Medición

```
Usuario          App          ViewModel       Repository      Database      Bluetooth
  │               │               │               │               │             │
  │─ Iniciar ────>│               │               │               │             │
  │   Sesión      │─ StartSession>│               │               │             │
  │               │               │─ Create ─────>│               │             │
  │               │               │  Session      │               │             │
  │               │               │               │               │             │
  │               │<─ Readings ───────────────────────────────────│             │
  │               │  (automático) │               │               │             │
  │               │               │─ Add Reading >│               │             │
  │               │               │               │               │             │
  │─ Detener ────>│               │               │               │             │
  │   y Guardar   │─ StopAndSave─>│               │               │             │
  │               │               │─ toMeasurement│               │             │
  │               │               │─ Save ───────>│               │             │
  │               │               │               │─ Insert ─────>│             │
  │               │               │               │<─ Success ────│             │
  │<─ Confirmación│<─ Success ────│               │               │             │
```

---

## 🎨 Diseño de Interfaz

### Principios de Diseño

1. **Material Design 3**: Diseño moderno y consistente
2. **Accesibilidad**: Textos legibles, contrastes adecuados
3. **Feedback Visual**: Estados claros (loading, success, error)
4. **Navegación Intuitiva**: Flujo lógico entre pantallas
5. **Responsive**: Adaptable a diferentes tamaños de pantalla

## 📈 Métricas y Cálculos

### Ratio H/Q (Hamstrings/Quadriceps)

**Fórmula:**
```
Ratio H/Q = Fuerza Isquiotibiales / Fuerza Cuádriceps
```

**Interpretación:**

- **Ratio > 0.60**: Óptimo (bajo riesgo de lesión)
- **Ratio 0.50-0.60**: Aceptable (monitorear)
- **Ratio < 0.50**: Riesgo elevado (requiere atención)

**Importancia Clínica:**
El ratio H/Q es un indicador clave del balance muscular. Un ratio bajo indica debilidad relativa de isquiotibiales, lo cual aumenta el riesgo de lesiones de ligamento cruzado anterior (LCA) y otras lesiones de rodilla.

### Estadísticas de Sesión

Para cada sesión de medición, se calculan:

1. **Promedio**: Media aritmética de todas las lecturas
2. **Máximo**: Valor máximo registrado
3. **Duración**: Tiempo total de la sesión en segundos
4. **Número de lecturas**: Cantidad de mediciones acumuladas

---

## 🔒 Seguridad y Privacidad

### Almacenamiento de Datos

- **Local**: Todos los datos se almacenan localmente en el dispositivo
- **Encriptación**: Base de datos Room con encriptación SQLCipher (opcional)
- **Sin conexión a internet**: No se envían datos a servidores externos

### Permisos Requeridos

```xml
<!-- Bluetooth -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- Almacenamiento (solo para Android < 13) -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
```

### Gestión de Permisos

La aplicación solicita permisos en tiempo de ejecución siguiendo las mejores prácticas de Android:

- Explicación clara del propósito
- Solicitud en el momento necesario
- Manejo de denegación de permisos

---

## 🧪 Testing y Validación

### Pruebas Realizadas

1. **Pruebas Unitarias**
   - ViewModels
   - Repositorios
   - Lógica de negocio (cálculos)

2. **Pruebas de Integración**
   - Base de datos (Room)
   - Comunicación Bluetooth
   - Exportación de datos

3. **Pruebas de UI**
   - Navegación entre pantallas
   - Validación de formularios
   - Estados de carga y error

4. **Pruebas de Usuario**
   - Usabilidad
   - Flujo completo de trabajo
   - Casos de uso reales

### Casos de Prueba Principales

| Caso | Descripción | Resultado Esperado |
|------|-------------|-------------------|
| CP-01 | Crear perfil con datos válidos | Perfil guardado correctamente |
| CP-02 | Conectar dispositivo Bluetooth | Conexión establecida |
| CP-03 | Realizar medición completa | Datos guardados en historial |
| CP-04 | Exportar mediciones a CSV | Archivo CSV generado |
| CP-05 | Eliminar medición | Medición eliminada con confirmación |

---

## 📚 Instalación y Configuración

### Requisitos del Sistema

**Dispositivo Android:**

- Android 8.0 (API 26) o superior
- Bluetooth habilitado
- 50 MB de espacio libre

**Dispositivo de Medición:**

- ESP32 con firmware compatible
- Sensores de fuerza calibrados
- Bluetooth Classic habilitado

### Pasos de Instalación

1. **Clonar el repositorio**

   ```bash
   git clone https://github.com/usuario/forcemetrics.git
   cd forcemetrics
   ```

2. **Abrir en Android Studio**
   - Android Studio Hedgehog (2023.1.1) o superior
   - Gradle 8.0+
   - Kotlin plugin actualizado

3. **Sincronizar dependencias**

   ```bash
   ./gradlew build
   ```

4. **Conectar dispositivo Android**
   - Habilitar modo desarrollador
   - Habilitar depuración USB

5. **Ejecutar aplicación**

   ```bash
   ./gradlew installDebug
   ```

## 📖 Guía de Uso

### 1. Primer Uso

1. Abrir la aplicación
2. Crear un perfil de paciente/atleta
3. Configurar conexión Bluetooth
4. Conectar al dispositivo ESP32

### 2. Realizar una Medición

1. Seleccionar perfil
2. Ir a "Medición"
3. Verificar conexión Bluetooth
4. Tarar el dispositivo (poner en cero)
5. Iniciar sesión de medición
6. Realizar las mediciones necesarias
7. Detener y guardar sesión

### 3. Consultar Historial

1. Desde el perfil, seleccionar "Ver historial"
2. Revisar mediciones anteriores
3. Exportar datos si es necesario

### 4. Exportar Datos

1. En pantalla de historial, tocar ícono de compartir
2. Seleccionar aplicación (WhatsApp, Email, etc.)
3. Enviar o guardar archivo CSV
