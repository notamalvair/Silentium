package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CryptoInspectorData
import com.example.data.Repository
import com.example.data.local.ChatEntity
import com.example.data.local.MessageEntity
import com.example.data.local.MyProfileEntity
import com.example.data.local.UserEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface Screen {
    object Auth : Screen
    object MainChats : Screen
    data class ChatRoom(val chatId: Int, val chatName: String, val type: String) : Screen
}

class MessengerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = Repository(application)

    // Reactive State Bindings
    val myProfile: StateFlow<MyProfileEntity?> = repository.myProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allChats: StateFlow<List<ChatEntity>> = repository.allChats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allContacts: StateFlow<List<UserEntity>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isOnlineMode: StateFlow<Boolean> = repository.isOnlineMode

    val inspectorData: StateFlow<CryptoInspectorData?> = repository.inspectorData

    // UI Navigation State
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Auth)
    val currentScreen: StateFlow<Screen> = _currentScreen

    // Message lists
    private val _activeChatId = MutableStateFlow<Int?>(null)
    val activeChatMessages: StateFlow<List<MessageEntity>> = _activeChatId
        .flatMapLatest { id ->
            if (id != null) repository.getMessagesForChatFlow(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Loading states
    val isAuthLoading = MutableStateFlow(false)
    val authError = MutableStateFlow<String?>(null)

    // Decryption cache to avoid repeating heavy math on every recomposition
    private val _decryptedMessagesCache = MutableStateFlow<Map<Int, String>>(emptyMap())
    val decryptedMessagesCache: StateFlow<Map<Int, String>> = _decryptedMessagesCache

    init {
        // Auto navigate to main chats if profile already exists
        viewModelScope.launch {
            myProfile.collect { profile ->
                if (profile != null) {
                    _currentScreen.value = Screen.MainChats
                } else {
                    _currentScreen.value = Screen.Auth
                }
            }
        }

        // Keep updating our decryption cache when active messages change
        viewModelScope.launch {
            activeChatMessages.collect { messages ->
                val currentCache = _decryptedMessagesCache.value.toMutableMap()
                var cacheUpdated = false
                for (msg in messages) {
                    if (!currentCache.containsKey(msg.id)) {
                        val decrypted = repository.decryptMessage(msg)
                        currentCache[msg.id] = decrypted
                        cacheUpdated = true
                    }
                }
                if (cacheUpdated) {
                    _decryptedMessagesCache.value = currentCache
                }
            }
        }
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
        if (screen is Screen.ChatRoom) {
            _activeChatId.value = screen.chatId
        } else {
            _activeChatId.value = null
        }
    }

    fun toggleOnlineMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.isOnlineMode.value = enabled
        }
    }

    fun handleRegister(username: String, pass: String, serverUrl: String) {
        viewModelScope.launch {
            isAuthLoading.value = true
            authError.value = null
            val success = repository.register(username, pass, serverUrl)
            isAuthLoading.value = false
            if (success) {
                _currentScreen.value = Screen.MainChats
            } else {
                authError.value = "Ошибка регистрации. Проверьте адрес сервера VPS или переключитесь на локальный режим."
            }
        }
    }

    fun handleLogin(username: String, pass: String, serverUrl: String) {
        viewModelScope.launch {
            isAuthLoading.value = true
            authError.value = null
            val success = repository.login(username, pass, serverUrl)
            isAuthLoading.value = false
            if (success) {
                _currentScreen.value = Screen.MainChats
            } else {
                authError.value = "Неверный логин, пароль или ошибка подключения."
            }
        }
    }

    fun handleLogout() {
        viewModelScope.launch {
            repository.logout()
            _decryptedMessagesCache.value = emptyMap()
            _currentScreen.value = Screen.Auth
        }
    }

    fun handleCreateDirectChat(peer: UserEntity) {
        viewModelScope.launch {
            val success = repository.createChat(
                type = "direct",
                name = null,
                members = listOf(peer)
            )
            if (success) {
                // Return to chats view, lists will update reactively
                _currentScreen.value = Screen.MainChats
            }
        }
    }

    fun handleCreateGroupChat(name: String, members: List<UserEntity>) {
        viewModelScope.launch {
            val success = repository.createChat(
                type = "group",
                name = name,
                members = members
            )
            if (success) {
                _currentScreen.value = Screen.MainChats
            }
        }
    }

    fun handleSendMessage(plaintext: String) {
        val chatId = _activeChatId.value ?: return
        if (plaintext.isBlank()) return

        viewModelScope.launch {
            repository.sendMessage(chatId, plaintext)
        }
    }

    fun handleSendPhotoSimulated() {
        val chatId = _activeChatId.value ?: return
        viewModelScope.launch {
            // Simulate sending an encrypted attachment blob
            repository.sendMessage(chatId, "📸 [Отправлено фото: secure_attachment_blob.jpg]", null)
        }
    }
}
