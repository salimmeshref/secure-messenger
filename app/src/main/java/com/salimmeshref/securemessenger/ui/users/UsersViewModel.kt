package com.salimmeshref.securemessenger.ui.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salimmeshref.securemessenger.domain.model.User
import com.salimmeshref.securemessenger.domain.repository.AuthRepository
import com.salimmeshref.securemessenger.domain.repository.ConversationRepository
import com.salimmeshref.securemessenger.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UsersViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UsersUiState())
    val uiState: StateFlow<UsersUiState> = _uiState.asStateFlow()

    fun searchUsers(query: String) {
        _uiState.update { it.copy(searchQuery = query) }

        if (query.isBlank()) {
            _uiState.update { it.copy(users = emptyList(), isLoading = false) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Get current user ID to exclude from search results (prevent self-conversations)
                val currentUserId = authRepository.getCurrentUserId()
                val users = userRepository.searchUsers(query, excludeUserId = currentUserId)
                _uiState.update { it.copy(users = users, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to search users"
                    )
                }
            }
        }
    }

    fun startConversation(otherUserId: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingConversation = true, error = null) }
            try {
                val currentUserId = authRepository.getCurrentUserId()
                    ?: throw Exception("Not authenticated")

                val result = conversationRepository.getOrCreateConversation(
                    currentUserId = currentUserId,
                    otherUserId = otherUserId
                )

                _uiState.update { it.copy(isCreatingConversation = false) }

                result.fold(
                    onSuccess = { conversation ->
                        onResult(Result.success(conversation.id))
                    },
                    onFailure = { error ->
                        _uiState.update { it.copy(error = error.message) }
                        onResult(Result.failure(error))
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCreatingConversation = false,
                        error = e.message ?: "Failed to start conversation"
                    )
                }
                onResult(Result.failure(e))
            }
        }
    }
}

data class UsersUiState(
    val users: List<User> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isCreatingConversation: Boolean = false,
    val error: String? = null
)
