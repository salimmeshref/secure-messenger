package com.salimmeshref.securemessenger.ui.conversations

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salimmeshref.securemessenger.data.local.db.dao.ConversationDao
import com.salimmeshref.securemessenger.data.local.prefrences.SecurePreferencesManager
import com.salimmeshref.securemessenger.domain.model.Conversation
import com.salimmeshref.securemessenger.domain.repository.AuthRepository
import com.salimmeshref.securemessenger.domain.repository.ConversationRepository
import com.salimmeshref.securemessenger.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val conversationDao: ConversationDao,
    private val conversationRepository: ConversationRepository,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val securePreferencesManager: SecurePreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationsUiState())
    val uiState: StateFlow<ConversationsUiState> = _uiState.asStateFlow()

    private val currentUserId = securePreferencesManager.getCurrentUserId()?:""

    init {
        Log.d("ConversationsVM", "init: currentUserId=$currentUserId")
        loadConversations()
        observeRemoteConversations()
    }

    private fun loadConversations() {
        viewModelScope.launch {
            conversationDao.getAllConversations()
                .collect { conversations ->
                    Log.d("ConversationsVM", "Loaded ${conversations.size} conversations from local DB")
                    val conversationItems = conversations.map { entity ->
                        val conversation = entity.toDomain()
                        val displayName = getOtherParticipantName(conversation)
                        ConversationItem(conversation, displayName)
                    }
                    _uiState.update {
                        it.copy(
                            conversations = conversationItems,
                            isLoading = false
                        )
                    }
                }
        }
    }

    private fun observeRemoteConversations() {
        if (currentUserId.isEmpty()) {
            Log.w("ConversationsVM", "Cannot observe remote conversations: currentUserId is empty")
            return
        }

        viewModelScope.launch {
            Log.d("ConversationsVM", "Starting to observe remote conversations for user: $currentUserId")
            conversationRepository.observeRemoteConversations(currentUserId)
                .onEach { conversation ->
                    Log.d("ConversationsVM", "Received remote conversation: ${conversation.id}")
                    Log.d("ConversationsVM", "Remote conversation encryptedSessionKeys: ${conversation.encryptedSessionKeys}")
                }
                .catch { e ->
                    Log.e("ConversationsVM", "Error observing remote conversations: ${e.message}")
                }
                .launchIn(viewModelScope)
        }
    }

    private suspend fun getOtherParticipantName(conversation: Conversation): String {
        val participantIds = conversation.getParticipantIdsList()
        val otherUserId = participantIds.firstOrNull { it != currentUserId } ?: participantIds.firstOrNull()
        return otherUserId?.let { userId ->
            userRepository.getUserById(userId)?.displayName
        } ?: "Unknown"
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            Log.d("ConversationsVM", "Deleting conversation: $conversationId")
            _uiState.update { it.copy(isDeleting = true, error = null) }

            val result = conversationRepository.deleteConversation(conversationId)

            result.fold(
                onSuccess = {
                    Log.d("ConversationsVM", "Successfully deleted conversation: $conversationId")
                    _uiState.update { it.copy(isDeleting = false) }
                },
                onFailure = { error ->
                    Log.e("ConversationsVM", "Failed to delete conversation: ${error.message}")
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            error = error.message ?: "Failed to delete conversation"
                        )
                    }
                }
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class ConversationItem(
    val conversation: Conversation,
    val displayName: String
)

data class ConversationsUiState(
    val conversations: List<ConversationItem> = emptyList(),
    val isLoading: Boolean = true,
    val isDeleting: Boolean = false,
    val error: String? = null
)