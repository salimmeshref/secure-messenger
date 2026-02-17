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

    companion object {
        private const val TAG = "MessagingService"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        // Update FCM token in Firestore
        CoroutineScope(Dispatchers.IO).launch {
            userRepository.updateFcmToken(token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM message received from: ${remoteMessage.from}")

        // Handle notification payload (e.g., from Firebase Console)
        remoteMessage.notification?.let { notification ->
            Log.d(TAG, "Notification payload: title=${notification.title}, body=${notification.body}")
            CoroutineScope(Dispatchers.IO).launch {
                notificationManager.showMessageNotification(
                    conversationId = remoteMessage.data["conversationId"] ?: "unknown",
                    senderName = notification.title ?: "New Message",
                    messagePreview = notification.body ?: "You have a new message"
                )
            }
        }

        // Handle data payload (from app backend/Cloud Functions)
        remoteMessage.data.let { data ->
            if (data.isNotEmpty()) {
                Log.d(TAG, "Data payload: $data")
                when (data["type"]) {
                    "new_message" -> handleNewMessage(data)
                    "typing" -> handleTypingIndicator(data)
                }
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
                Log.e(TAG, "Failed to process message: ${e.message}")
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