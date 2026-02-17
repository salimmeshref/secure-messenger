package com.salimmeshref.securemessenger.ui.chat

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salimmeshref.securemessenger.domain.model.Message
import com.salimmeshref.securemessenger.domain.repository.AuthRepository
import com.salimmeshref.securemessenger.domain.repository.ConversationRepository
import com.salimmeshref.securemessenger.domain.repository.MessageRepository
import com.salimmeshref.securemessenger.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
class ChatViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val authRepository: AuthRepository,
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val conversationId: String = savedStateHandle.get<String>("conversationId") ?: ""

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var typingJob: Job? = null

    init {
        val currentUserId = authRepository.getCurrentUserId()
        Log.d("ChatViewModel", "init: conversationId=$conversationId, currentUserId=$currentUserId")
        _uiState.update { it.copy(currentUserId = currentUserId ?: "") }
        viewModelScope.launch {
            loadConversationTitle()
        }
        loadConversation()
    }

    private suspend fun loadConversationTitle() {
        val currentUserId = _uiState.value.currentUserId
        val conversation = conversationRepository.getConversationById(conversationId) ?: return
        val participantIds = conversation.getParticipantIdsList()
        val otherUserId = participantIds.firstOrNull { it != currentUserId } ?: participantIds.firstOrNull()
        val displayName = otherUserId?.let { userRepository.getUserById(it)?.displayName } ?: "Chat"
        _uiState.update { it.copy(conversationTitle = displayName) }
    }

    private fun loadConversation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            Log.d("ChatViewModel", "loadConversation: subscribing to messages for $conversationId")

            // Observe local database for UI updates
            messageRepository.getMessages(conversationId)
                .onEach { messages ->
                    Log.d("ChatViewModel", "loadConversation: received ${messages.size} messages")
                    _uiState.update { state ->
                        state.copy(messages = messages, isLoading = false)
                    }
                }
                .launchIn(viewModelScope)

            // Observe incoming messages from Firebase (syncs to local DB automatically)
            messageRepository.observeIncomingMessages(conversationId)
                .onEach { message ->
                    Log.d("ChatViewModel", "loadConversation: incoming message from ${message.senderId}")
                }
                .catch { e ->
                    Log.e("ChatViewModel", "Error observing incoming messages: ${e.message}", e)
                }
                .launchIn(viewModelScope)
        }
    }

    fun updateMessageInput(input: String) {
        _uiState.update { it.copy(messageInput = input) }
//        updateTypingStatus(input.isNotEmpty())
    }

//    private fun updateTypingStatus(isTyping: Boolean) {
//        typingJob?.cancel()
//        typingJob = viewModelScope.launch {
//            typingIndicatorManager.setTyping(conversationId, isTyping)
//
//            if (isTyping) {
//                delay(3000)
//                typingIndicatorManager.setTyping(conversationId, false)
//            }
//        }
//    }

    fun sendMessage() {
        val content = _uiState.value.messageInput.trim()
        if (content.isEmpty()) return

        val senderId = _uiState.value.currentUserId
        Log.d("ChatViewModel", "sendMessage: content=$content, senderId=$senderId, conversationId=$conversationId")

        if (senderId.isEmpty()) {
            Log.e("ChatViewModel", "sendMessage failed: currentUserId is empty")
            _uiState.update { it.copy(error = "User not authenticated") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(messageInput = "", isSending = true) }

            val result = messageRepository.sendMessage(
                conversationId = conversationId,
                content = content,
                type = "TEXT",
                senderId = senderId
            )

            result.onSuccess {
                Log.d("ChatViewModel", "sendMessage succeeded")
            }.onFailure { error ->
                Log.e("ChatViewModel", "sendMessage failed: ${error.message}", error)

                // Check if this is a key-related error
                val errorMessage = error.message ?: ""
                val isKeyError = errorMessage.contains("session key") ||
                        errorMessage.contains("IllegalBlockSizeException") ||
                        errorMessage.contains("decryption") ||
                        errorMessage.contains("private key")

                if (isKeyError) {
                    _uiState.update {
                        it.copy(
                            error = "Encryption keys mismatch. Try refreshing keys.",
                            showRefreshKeysOption = true
                        )
                    }
                } else {
                    _uiState.update { it.copy(error = error.message) }
                }
            }

            _uiState.update { it.copy(isSending = false) }
        }
    }

    fun refreshConversationKeys() {
        val currentUserId = _uiState.value.currentUserId
        if (currentUserId.isEmpty()) {
            _uiState.update { it.copy(error = "User not authenticated") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingKeys = true, error = null) }
            Log.d("ChatViewModel", "Refreshing conversation keys for $conversationId")

            val result = conversationRepository.refreshConversationKeys(conversationId, currentUserId)

            result.onSuccess {
                Log.d("ChatViewModel", "Successfully refreshed conversation keys")
                _uiState.update {
                    it.copy(
                        isRefreshingKeys = false,
                        showRefreshKeysOption = false,
                        error = null
                    )
                }
                // Reload messages after refreshing keys
                loadConversation()
            }.onFailure { error ->
                Log.e("ChatViewModel", "Failed to refresh conversation keys: ${error.message}")
                _uiState.update {
                    it.copy(
                        isRefreshingKeys = false,
                        error = "Failed to refresh keys: ${error.message}"
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, showRefreshKeysOption = false) }
    }
}

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val messageInput: String = "",
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val isRefreshingKeys: Boolean = false,
    val showRefreshKeysOption: Boolean = false,
    val typingUsers: List<String> = emptyList(),
    val currentUserId: String = "",
    val conversationTitle: String = "",
    val error: String? = null
)
