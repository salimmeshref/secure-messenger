package com.salimmeshref.securemessenger.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salimmeshref.securemessenger.domain.model.User
import com.salimmeshref.securemessenger.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val displayName: String = "",
    val isSignUpMode: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isAuthenticated: Boolean = false,
    val user: User? = null
)

sealed class AuthEvent {
    data class EmailChanged(val email: String) : AuthEvent()
    data class PasswordChanged(val password: String) : AuthEvent()
    data class ConfirmPasswordChanged(val confirmPassword: String) : AuthEvent()
    data class DisplayNameChanged(val displayName: String) : AuthEvent()
    data object ToggleAuthMode : AuthEvent()
    data object Submit : AuthEvent()
    data object ClearError : AuthEvent()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        observeCurrentUser()
    }

    private fun observeCurrentUser() {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                _uiState.update { it.copy(user = user, isAuthenticated = user != null) }
            }
        }
    }

    fun onEvent(event: AuthEvent) {
        when (event) {
            is AuthEvent.EmailChanged -> {
                _uiState.update { it.copy(email = event.email, errorMessage = null) }
            }
            is AuthEvent.PasswordChanged -> {
                _uiState.update { it.copy(password = event.password, errorMessage = null) }
            }
            is AuthEvent.ConfirmPasswordChanged -> {
                _uiState.update { it.copy(confirmPassword = event.confirmPassword, errorMessage = null) }
            }
            is AuthEvent.DisplayNameChanged -> {
                _uiState.update { it.copy(displayName = event.displayName, errorMessage = null) }
            }
            is AuthEvent.ToggleAuthMode -> {
                _uiState.update {
                    it.copy(
                        isSignUpMode = !it.isSignUpMode,
                        errorMessage = null,
                        confirmPassword = "",
                        displayName = ""
                    )
                }
            }
            is AuthEvent.Submit -> submit()
            is AuthEvent.ClearError -> {
                _uiState.update { it.copy(errorMessage = null) }
            }
        }
    }

    private fun submit() {
        val state = _uiState.value

        if (!validateInput(state)) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val result = if (state.isSignUpMode) {
                authRepository.signUp(
                    email = state.email.trim(),
                    password = state.password,
                    displayName = state.displayName.trim()
                )
            } else {
                authRepository.signIn(
                    email = state.email.trim(),
                    password = state.password
                )
            }

            result.fold(
                onSuccess = { user ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isAuthenticated = true,
                            user = user,
                            password = "",
                            confirmPassword = ""
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Authentication failed"
                        )
                    }
                }
            )
        }
    }

    private fun validateInput(state: AuthUiState): Boolean {
        if (state.email.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Email is required") }
            return false
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches()) {
            _uiState.update { it.copy(errorMessage = "Invalid email format") }
            return false
        }

        if (state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Password is required") }
            return false
        }

        if (state.password.length < 6) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 6 characters") }
            return false
        }

        if (state.isSignUpMode) {
            if (state.displayName.isBlank()) {
                _uiState.update { it.copy(errorMessage = "Display name is required") }
                return false
            }

            if (state.confirmPassword != state.password) {
                _uiState.update { it.copy(errorMessage = "Passwords do not match") }
                return false
            }
        }

        return true
    }

    fun signOut() {
        authRepository.signOut()
        _uiState.update {
            AuthUiState()
        }
    }
}
