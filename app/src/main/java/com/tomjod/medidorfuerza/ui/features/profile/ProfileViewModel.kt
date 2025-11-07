package com.tomjod.medidorfuerza.ui.features.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomjod.medidorfuerza.data.db.AppDao
import com.tomjod.medidorfuerza.data.db.entities.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileCreationState(
    val nombre: String = "",
    val apellido: String = "",
    val edad: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val appDao: AppDao
) : ViewModel() {

    /**
     * Expone la lista de todos los perfiles de la base de datos.
     */
    val profiles: StateFlow<List<UserProfile>> = appDao.getAllProfiles()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList() // Comienza con una lista vacía
        )

    // Estado para la creación de perfiles
    private val _creationState = MutableStateFlow(ProfileCreationState())
    val creationState: StateFlow<ProfileCreationState> = _creationState.asStateFlow()

    /**
     * Actualiza el nombre en el estado de creación
     */
    fun updateNombre(nombre: String) {
        _creationState.value = _creationState.value.copy(
            nombre = nombre,
            errorMessage = null
        )
    }

    /**
     * Actualiza el apellido en el estado de creación
     */
    fun updateApellido(apellido: String) {
        _creationState.value = _creationState.value.copy(
            apellido = apellido,
            errorMessage = null
        )
    }

    /**
     * Actualiza la edad en el estado de creación
     */
    fun updateEdad(edad: String) {
        // Solo permitir números
        if (edad.isEmpty() || edad.all { it.isDigit() }) {
            _creationState.value = _creationState.value.copy(
                edad = edad,
                errorMessage = null
            )
        }
    }

    /**
     * Valida y crea un nuevo perfil
     */
    fun createProfile() {
        val currentState = _creationState.value

        // Validación
        if (currentState.nombre.isBlank()) {
            _creationState.value = currentState.copy(errorMessage = "El nombre es obligatorio")
            return
        }

        if (currentState.apellido.isBlank()) {
            _creationState.value = currentState.copy(errorMessage = "El apellido es obligatorio")
            return
        }

        val edad = currentState.edad.toIntOrNull()
        if (edad == null || edad <= 0 || edad > 120) {
            _creationState.value =
                currentState.copy(errorMessage = "Ingresa una edad válida (1-120)")
            return
        }

        _creationState.value = currentState.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val newProfile = UserProfile(
                    nombre = currentState.nombre.trim(),
                    apellido = currentState.apellido.trim(),
                    edad = edad,
                    fotoUri = null
                )
                appDao.insertProfile(newProfile)

                _creationState.value = currentState.copy(
                    isLoading = false,
                    isSuccess = true
                )
            } catch (e: Exception) {
                _creationState.value = currentState.copy(
                    isLoading = false,
                    errorMessage = "Error al crear el perfil: ${e.message}"
                )
            }
        }
    }

    /**
     * Resetea el estado de creación
     */
    fun resetCreationState() {
        _creationState.value = ProfileCreationState()
    }

}
