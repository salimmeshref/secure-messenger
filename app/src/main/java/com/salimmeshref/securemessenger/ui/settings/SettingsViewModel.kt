package com.salimmeshref.securemessenger.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salimmeshref.securemessenger.data.local.datastore.ThemeMode
import com.salimmeshref.securemessenger.data.local.datastore.UserPreferencesDataStore
import com.salimmeshref.securemessenger.data.local.prefrences.SecurePreferencesManager
import com.salimmeshref.securemessenger.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val authRepository: AuthRepository,
    private val securePreferencesManager: SecurePreferencesManager
) : ViewModel() {

    private val _deleteState = MutableStateFlow(DeleteAccountState())

    val uiState: StateFlow<SettingsUiState> = combine(
        userPreferencesDataStore.userPreferences,
        _deleteState
    ) { prefs, deleteState ->
        SettingsUiState(
            themeMode = prefs.themeMode,
            notificationsEnabled = prefs.notificationsEnabled,
            messagePreviewEnabled = prefs.messagePreviewEnabled,
            biometricEnabled = prefs.biometricEnabled,
            isDeleting = deleteState.isDeleting,
            deleteError = deleteState.error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            userPreferencesDataStore.updateThemeMode(mode)
        }
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesDataStore.updateNotificationsEnabled(enabled)
        }
    }

    fun updateMessagePreviewEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesDataStore.updateMessagePreviewEnabled(enabled)
        }
    }

    fun updateBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesDataStore.updateBiometricEnabled(enabled)
        }
    }

    fun signOut() {
        authRepository.signOut()
        securePreferencesManager.clearAll()
    }

    fun deleteAccount(onSuccess: () -> Unit) {
        viewModelScope.launch {
            Log.d("SettingsVM", "Starting account deletion")
            _deleteState.update { it.copy(isDeleting = true, error = null) }

            val result = authRepository.deleteAccount()

            result.fold(
                onSuccess = {
                    Log.d("SettingsVM", "Account deleted successfully")
                    _deleteState.update { it.copy(isDeleting = false) }
                    onSuccess()
                },
                onFailure = { error ->
                    Log.e("SettingsVM", "Failed to delete account: ${error.message}")
                    _deleteState.update {
                        it.copy(
                            isDeleting = false,
                            error = error.message ?: "Failed to delete account"
                        )
                    }
                }
            )
        }
    }

    fun clearDeleteError() {
        _deleteState.update { it.copy(error = null) }
    }
}

private data class DeleteAccountState(
    val isDeleting: Boolean = false,
    val error: String? = null
)

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val notificationsEnabled: Boolean = true,
    val messagePreviewEnabled: Boolean = true,
    val biometricEnabled: Boolean = false,
    val isDeleting: Boolean = false,
    val deleteError: String? = null
)