package com.tomjod.medidorfuerza.ui.features.measurement

/**
 * Define todas las acciones que el usuario puede realizar
 * en la pantalla de medición.
 */
sealed interface MeasurementEvent {
    /** El usuario ha pulsado el botón de conectar/escanear. */
    object ConnectClicked : MeasurementEvent

    /** El usuario ha pulsado el botón de "Tarar" (Poner en Cero). */
    object TareClicked : MeasurementEvent

    /** El usuario ha pulsado el botón de "Guardar" la medición actual. */
    @Deprecated("Use StartSession and StopAndSaveSession instead")
    object SaveClicked : MeasurementEvent

    /** El usuario ha pulsado el botón de "Desconectar". */
    object DisconnectClicked : MeasurementEvent

    /** El usuario quiere calibrar los isquios con un factor. */
    data class CalibrateIsquios(val factor: Float) : MeasurementEvent

    /** El usuario quiere calibrar los cuádriceps con un factor. */
    data class CalibrateCuads(val factor: Float) : MeasurementEvent

    /** El usuario inicia una sesión de medición. */
    object StartSession : MeasurementEvent

    /** El usuario detiene y guarda la sesión de medición actual. */
    data class StopAndSaveSession(val notes: String? = null) : MeasurementEvent

    // New Workflow Events
    object StartIsquios : MeasurementEvent
    object CaptureIsquios : MeasurementEvent
    object StartCuads : MeasurementEvent
    object CaptureCuads : MeasurementEvent
    object CancelMeasurement : MeasurementEvent
    object ResetMeasurement : MeasurementEvent
    
    /** El usuario selecciona la pierna a medir (Left/Right). */
    data class SelectLeg(val leg: String) : MeasurementEvent
}