package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity storing logged-in Matrix account session and the configured friend's user ID.
 * Only one row in this table (id = 1).
 */
@Entity(tableName = "my_profile")
data class MyProfileEntity(
    @PrimaryKey val id: Int = 1,
    val username: String,
    val userId: String,
    val accessToken: String,
    val deviceId: String,
    val homeserverUrl: String,
    val friendUserId: String,
    val isVerified: Boolean = false
)

/**
 * Entity representing cached 1:1 Matrix messages for fast offline launch.
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val eventId: String, // Matrix unique event ID
    val sender: String,             // Matrix user ID of sender
    val body: String,               // Plaintext body
    val msgType: String,            // "m.text" or "m.image"
    val fileUrl: String?,           // Local or remote file URL/MXC URL
    val createdAt: Long,            // Epoch millisecond timestamp
    val isEncrypted: Boolean,       // Whether the event came via Megolm/E2EE
    val isSentByMe: Boolean         // Helper flag for message bubble alignment
)
