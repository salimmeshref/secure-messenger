package com.salimmeshref.securemessenger.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.salimmeshref.securemessenger.domain.repository.MessageRepository
import com.salimmeshref.securemessenger.domain.repository.UserRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MessagingService : FirebaseMessagingService() {

    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var messageRepository: MessageRepository
    @Inject lateinit var notificationManager: AppNotificationManager

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        // Update FCM token in Firestore
        CoroutineScope(Dispatchers.IO).launch {
            userRepository.updateFcmToken(token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        remoteMessage.data.let { data ->
            when (data["type"]) {
                "new_message" -> handleNewMessage(data)
                "typing" -> handleTypingIndicator(data)
            }
        }
    }

    private fun handleNewMessage(data: Map<String, String>) {
        val conversationId = data["conversationId"] ?: return
        val senderId = data["senderId"] ?: return
        val senderName = data["senderName"] ?: "Unknown"

        // Fetch and decrypt the message
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Trigger local sync
                messageRepository.syncConversation(conversationId)

                // Show notification
                notificationManager.showMessageNotification(
                    conversationId = conversationId,
                    senderName = senderName,
                    messagePreview = "New encrypted message" // Don't show content in notification
                )
            } catch (e: Exception) {
                Log.e("FCM", "Failed to process message: ${e.message}")
            }
        }
    }

    private fun handleTypingIndicator(data: Map<String, String>) {
        // Broadcast typing event to UI
        val conversationId = data["conversationId"] ?: return
        val userId = data["userId"] ?: return

        // Use LocalBroadcastManager or EventBus to notify UI
    }
}