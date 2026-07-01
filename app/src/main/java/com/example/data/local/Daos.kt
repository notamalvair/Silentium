package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MyProfileDao {
    @Query("SELECT * FROM my_profile WHERE id = 1 LIMIT 1")
    fun getProfileFlow(): Flow<MyProfileEntity?>

    @Query("SELECT * FROM my_profile WHERE id = 1 LIMIT 1")
    suspend fun getProfile(): MyProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: MyProfileEntity)

    @Query("DELETE FROM my_profile")
    suspend fun clearProfile()

    @Query("UPDATE my_profile SET isVerified = :verified WHERE id = 1")
    suspend fun updateVerificationStatus(verified: Boolean)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY createdAt ASC")
    fun getAllMessagesFlow(): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("DELETE FROM messages")
    suspend fun clearMessages()

    @Query("DELETE FROM messages WHERE eventId = :eventId")
    suspend fun deleteMessageById(eventId: String)
}
