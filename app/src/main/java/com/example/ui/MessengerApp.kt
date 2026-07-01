package com.example.ui

import com.example.ui.theme.*
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.crypto.Curve25519
import com.example.data.CryptoInspectorData
import com.example.data.local.ChatEntity
import com.example.data.local.MessageEntity
import com.example.data.local.UserEntity
import com.example.data.local.MyProfileEntity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessengerApp(viewModel: MessengerViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val isOnlineMode by viewModel.isOnlineMode.collectAsStateWithLifecycle()
    val myProfile by viewModel.myProfile.collectAsStateWithLifecycle()
    val inspectorData by viewModel.inspectorData.collectAsStateWithLifecycle()

    var showCryptoSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (currentScreen is Screen.ChatRoom) {
                // Inline floating debug banner inside Chat Rooms
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SlateSurface)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "E2E Secure",
                                tint = CryptoGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Шифрование: X25519 + AES-256-GCM",
                                color = AccentText,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Button(
                            onClick = { showCryptoSheet = true },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .testTag("btn_inspect_crypto")
                        ) {
                            Text("Инспектор 🔐", fontSize = 11.sp, color = SoftWhite)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                },
                label = "ScreenTransition"
            ) { screen ->
                when (screen) {
                    is Screen.Auth -> AuthScreen(viewModel)
                    is Screen.MainChats -> MainChatsScreen(viewModel)
                    is Screen.ChatRoom -> ChatRoomScreen(
                        viewModel = viewModel,
                        chatId = screen.chatId,
                        chatName = screen.chatName,
                        chatType = screen.type
                    )
                }
            }
        }
    }

    // Modal Cryptographic Telemetry Bottom Sheet
    if (showCryptoSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCryptoSheet = false },
            containerColor = SpaceBg,
            contentColor = SoftWhite
        ) {
            CryptoInspectorSheetContent(
                inspectorData = inspectorData,
                myProfile = myProfile
            )
        }
    }
}

// ====================================================================
// 1. Auth Screen: Register/Login with VPS and Offline Toggle
// ====================================================================
@Composable
fun AuthScreen(viewModel: MessengerViewModel) {
    val isOnlineMode by viewModel.isOnlineMode.collectAsStateWithLifecycle()
    val isAuthLoading by viewModel.isAuthLoading.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()

    var isRegisterMode by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("http://localhost:3000") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpaceBg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Identity Header
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(ElectricCyan, shape = RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "App Logo",
                tint = ElectricTeal,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "SILENTIUM",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = SoftWhite,
                letterSpacing = 4.sp
            )
        )
        Text(
            text = "Автономный приватный E2E мессенджер",
            color = AccentText,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Connection Mode Selector Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateSurface),
            border = BorderStroke(1.dp, SlateBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isOnlineMode) "Сетевой режим (VPS)" else "Автономная песочница",
                        fontWeight = FontWeight.Bold,
                        color = if (isOnlineMode) ElectricCyan else CryptoGreen,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (isOnlineMode) "Подключение к вашему серверу Node.js" else "Локальная симуляция криптосистемы",
                        color = AccentText,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = isOnlineMode,
                    onCheckedChange = { viewModel.toggleOnlineMode(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SpaceBg,
                        checkedTrackColor = ElectricCyan,
                        uncheckedThumbColor = AccentText,
                        uncheckedTrackColor = SlateBorder
                    ),
                    modifier = Modifier.testTag("switch_online_mode")
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Text Fields
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Имя пользователя (Логин)") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = SoftWhite,
                unfocusedTextColor = SoftWhite,
                focusedBorderColor = ElectricCyan,
                unfocusedBorderColor = SlateBorder,
                focusedLabelColor = ElectricCyan,
                unfocusedLabelColor = AccentText
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_username")
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                IconButton(
                    onClick = { passwordVisible = !passwordVisible },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(imageVector = image, contentDescription = "Показать пароль", tint = AccentText)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = SoftWhite,
                unfocusedTextColor = SoftWhite,
                focusedBorderColor = ElectricCyan,
                unfocusedBorderColor = SlateBorder,
                focusedLabelColor = ElectricCyan,
                unfocusedLabelColor = AccentText
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_password")
        )

        if (isOnlineMode) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Адрес бэкенда VPS (HTTP URL)") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = SoftWhite,
                    unfocusedTextColor = SoftWhite,
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = SlateBorder,
                    focusedLabelColor = ElectricCyan,
                    unfocusedLabelColor = AccentText
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_server_url")
            )
        }

        if (authError != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = authError!!,
                color = ErrorRed,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Button
        Button(
            onClick = {
                if (isRegisterMode) {
                    viewModel.handleRegister(username, password, serverUrl)
                } else {
                    viewModel.handleLogin(username, password, serverUrl)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("btn_auth_action"),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isOnlineMode) ElectricCyan else CryptoGreen,
                contentColor = if (isOnlineMode) ElectricTeal else SpaceBg
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isAuthLoading) {
                CircularProgressIndicator(color = if (isOnlineMode) ElectricTeal else SpaceBg, modifier = Modifier.size(24.dp))
            } else {
                Text(
                    text = if (isRegisterMode) "Создать аккаунт (Сгенерировать ключи)" else "Войти в мессенджер",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = { isRegisterMode = !isRegisterMode },
            modifier = Modifier.minimumInteractiveComponentSize()
        ) {
            Text(
                text = if (isRegisterMode) "Уже зарегистрированы? Войти" else "Новый пользователь? Зарегистрироваться",
                color = ElectricCyan,
                fontSize = 13.sp
            )
        }
    }
}

// ====================================================================
// 2. Main Screen: Chats, Contacts, Profile Tabs
// ====================================================================
@Composable
fun MainChatsScreen(viewModel: MessengerViewModel) {
    val chats by viewModel.allChats.collectAsStateWithLifecycle()
    val contacts by viewModel.allContacts.collectAsStateWithLifecycle()
    val myProfile by viewModel.myProfile.collectAsStateWithLifecycle()
    val isOnlineMode by viewModel.isOnlineMode.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreateChatDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpaceBg)
    ) {
        // App header with active profile
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SlateSurface)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(ElectricCyan, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = myProfile?.username?.take(2)?.uppercase() ?: "??",
                            color = ElectricTeal,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = myProfile?.username ?: "Загрузка...",
                            fontWeight = FontWeight.Bold,
                            color = SoftWhite,
                            fontSize = 16.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        if (isOnlineMode) ElectricCyan else CryptoGreen,
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isOnlineMode) "Сетевой (VPS)" else "Локальная песочница",
                                color = AccentText,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { showCreateChatDialog = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("btn_create_chat_dialog")
                ) {
                    Icon(imageVector = Icons.Default.AddComment, contentDescription = "Новый чат", tint = ElectricCyan)
                }
            }
        }

        // Tab Row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = SlateSurface,
            contentColor = ElectricCyan
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Чаты", color = if (selectedTab == 0) ElectricCyan else AccentText) },
                icon = { Icon(Icons.Default.Chat, contentDescription = "Chats", tint = if (selectedTab == 0) ElectricCyan else AccentText) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Контакты", color = if (selectedTab == 1) ElectricCyan else AccentText) },
                icon = { Icon(Icons.Default.People, contentDescription = "Contacts", tint = if (selectedTab == 1) ElectricCyan else AccentText) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Профиль", color = if (selectedTab == 2) ElectricCyan else AccentText) },
                icon = { Icon(Icons.Default.Settings, contentDescription = "Profile", tint = if (selectedTab == 2) ElectricCyan else AccentText) }
            )
        }

        // Tab Content
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> ChatsTab(chats = chats, viewModel = viewModel)
                1 -> ContactsTab(contacts = contacts, viewModel = viewModel)
                2 -> ProfileTab(myProfile = myProfile, viewModel = viewModel)
            }
        }
    }

    // New Chat / Group Dialog
    if (showCreateChatDialog) {
        CreateChatDialog(
            contacts = contacts,
            onDismiss = { showCreateChatDialog = false },
            onCreateDirect = { peer ->
                viewModel.handleCreateDirectChat(peer)
                showCreateChatDialog = false
            },
            onCreateGroup = { name, selectedPeers ->
                viewModel.handleCreateGroupChat(name, selectedPeers)
                showCreateChatDialog = false
            }
        )
    }
}

@Composable
fun ChatsTab(chats: List<ChatEntity>, viewModel: MessengerViewModel) {
    if (chats.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.ChatBubbleOutline,
                contentDescription = "Нет чатов",
                modifier = Modifier.size(64.dp),
                tint = SlateBorder
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Нет активных диалогов", color = AccentText, fontSize = 15.sp)
            Text(
                "Нажмите иконку '+' сверху для создания",
                color = AccentText.copy(alpha = 0.7f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(chats) { chat ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.navigateTo(Screen.ChatRoom(chat.id, chat.name ?: "Приватный чат", chat.type)) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (chat.type == "group") ElectricTeal else SpaceBg,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(1.dp, SlateBorder, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (chat.type == "group") Icons.Default.Group else Icons.Default.Person,
                            contentDescription = "Chat Icon",
                            tint = if (chat.type == "group") ElectricCyan else AccentText
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = chat.name ?: "Приватный чат (1:1)",
                                fontWeight = FontWeight.Bold,
                                color = SoftWhite,
                                fontSize = 15.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.EnhancedEncryption,
                                    contentDescription = "Encrypted",
                                    tint = CryptoGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("E2E", color = CryptoGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                        Text(
                            text = if (chat.type == "group") "Шифрование групповым ключом" else "Прямое шифрование X25519",
                            color = AccentText,
                            fontSize = 12.sp
                        )
                    }
                }
                HorizontalDivider(color = SlateBorder)
            }
        }
    }
}

@Composable
fun ContactsTab(contacts: List<UserEntity>, viewModel: MessengerViewModel) {
    var searchQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
            },
            placeholder = { Text("Поиск контактов на сервере...", color = AccentText) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = AccentText) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = SoftWhite,
                unfocusedTextColor = SoftWhite,
                focusedBorderColor = ElectricCyan,
                unfocusedBorderColor = SlateBorder,
                focusedLabelColor = ElectricCyan,
                unfocusedLabelColor = AccentText
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )

        val filteredContacts = contacts.filter { it.username.contains(searchQuery, ignoreCase = true) }

        if (filteredContacts.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Пользователи не найдены", color = AccentText)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filteredContacts) { user ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.handleCreateDirectChat(user) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(SlateSurface, shape = RoundedCornerShape(10.dp))
                                .border(1.dp, SlateBorder, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(user.username.take(1).uppercase(), color = ElectricCyan, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(user.username, fontWeight = FontWeight.Bold, color = SoftWhite)
                            Text(
                                text = "X25519 Pub: ${user.publicKeyHex.take(16)}...",
                                fontSize = 11.sp,
                                color = AccentText,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    HorizontalDivider(color = SlateBorder)
                }
            }
        }
    }
}

@Composable
fun ProfileTab(myProfile: MyProfileEntity?, viewModel: MessengerViewModel) {
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .background(SpaceBg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(ElectricCyan, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                myProfile?.username?.take(2)?.uppercase() ?: "??",
                color = ElectricTeal,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(myProfile?.username ?: "User", style = MaterialTheme.typography.headlineSmall, color = SoftWhite)
        Text(
            text = if (viewModel.isOnlineMode.value) "Связь через: ${myProfile?.serverUrl}" else "Встроенный крипто-симулятор",
            color = AccentText,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Key Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateSurface),
            border = BorderStroke(1.dp, SlateBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Ваш E2E-ключ (X25519 Public Key)", fontWeight = FontWeight.Bold, color = ElectricCyan, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = myProfile?.publicKeyHex ?: "N/A",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = CryptoGreen,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { clipboardManager.setText(AnnotatedString(myProfile?.publicKeyHex ?: "")) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = AccentText, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { viewModel.handleLogout() },
            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_logout")
        ) {
            Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Выйти из аккаунта", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

// ====================================================================
// 3. Chat Room Screen: Messaging and Statuses
// ====================================================================
@Composable
fun ChatRoomScreen(
    viewModel: MessengerViewModel,
    chatId: Int,
    chatName: String,
    chatType: String
) {
    val messages by viewModel.activeChatMessages.collectAsStateWithLifecycle()
    val decCache by viewModel.decryptedMessagesCache.collectAsStateWithLifecycle()

    var textInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpaceBg)
    ) {
        // App bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SlateSurface)
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.navigateTo(Screen.MainChats) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("btn_back_to_chats")
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SoftWhite)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (chatType == "group") ElectricTeal else SlateSurface,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .border(1.dp, SlateBorder, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (chatType == "group") Icons.Default.Group else Icons.Default.Person,
                        contentDescription = "Icon",
                        tint = if (chatType == "group") ElectricCyan else AccentText,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = chatName, fontWeight = FontWeight.Bold, color = SoftWhite, fontSize = 15.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Encrypted",
                            tint = CryptoGreen,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (chatType == "group") "Зашифрованная группа E2E" else "E2E Канал защищен",
                            color = CryptoGreen,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Messages area
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
        ) {
            items(messages) { message ->
                val myProfile = viewModel.myProfile.value
                val isMe = message.senderId == myProfile?.id

                val decText = decCache[message.id] ?: "🔒 Расшифровка..."

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                ) {
                    // Chat Bubble container
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isMe) BubbleMe else BubblePeer,
                                shape = RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isMe) 16.dp else 2.dp,
                                    bottomEnd = if (isMe) 2.dp else 16.dp
                                )
                            )
                            .border(
                                width = 1.dp,
                                color = if (isMe) SlateBorder.copy(alpha = 0.5f) else SlateBorder,
                                shape = RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isMe) 16.dp else 2.dp,
                                    bottomEnd = if (isMe) 2.dp else 16.dp
                                )
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .widthIn(max = 280.dp)
                    ) {
                        Column {
                            if (!isMe && chatType == "group") {
                                Text(
                                    text = message.senderUsername,
                                    color = ElectricCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                            Text(
                                text = decText,
                                color = SoftWhite,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.align(Alignment.End),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.createdAt)),
                                    fontSize = 9.sp,
                                    color = AccentText
                                )
                                if (isMe) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = if (message.status == "read") Icons.Default.DoneAll else Icons.Default.Done,
                                        contentDescription = "Status",
                                        tint = if (message.status == "read") ElectricCyan else AccentText,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Input Row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SlateSurface)
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.handleSendPhotoSimulated() },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("btn_attach_media")
                ) {
                    Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = "Attach photo", tint = ElectricCyan)
                }

                Spacer(modifier = Modifier.width(4.dp))

                TextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Зашифрованное сообщение...", color = AccentText, fontSize = 14.sp) },
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = SoftWhite,
                        unfocusedTextColor = SoftWhite,
                        focusedContainerColor = SpaceBg,
                        unfocusedContainerColor = SpaceBg,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_message")
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            viewModel.handleSendMessage(textInput)
                            textInput = ""
                        }
                    },
                    modifier = Modifier
                        .background(ElectricCyan, shape = CircleShape)
                        .size(44.dp)
                        .testTag("btn_send")
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = ElectricTeal)
                }
            }
        }
    }
}

// ====================================================================
// 4. Crypto Telemetry Dashboard / Bottom Sheet Content
// ====================================================================
@Composable
fun CryptoInspectorSheetContent(
    inspectorData: CryptoInspectorData?,
    myProfile: MyProfileEntity?
) {
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SpaceBg)
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Крипто-Телеметрия 🔐",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan
                )
            )
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Shield",
                tint = CryptoGreen
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Анализ сквозного шифрования (E2E) текущей сессии",
            color = AccentText,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TelemetryItem(
                    title = "Ваш приватный ключ (X25519 Private Key - Clamp)",
                    value = myProfile?.privateKeyHex ?: "N/A",
                    clipboard = clipboardManager,
                    color = ErrorRed
                )
            }
            item {
                TelemetryItem(
                    title = "Ваш публичный ключ (X25519 Public Key)",
                    value = myProfile?.publicKeyHex ?: "N/A",
                    clipboard = clipboardManager,
                    color = ElectricCyan
                )
            }

            if (inspectorData != null) {
                if (!inspectorData.isGroup) {
                    item {
                        TelemetryItem(
                            title = "Публичный ключ собеседника (X25519 Peer)",
                            value = inspectorData.peerPublicHex,
                            clipboard = clipboardManager,
                            color = ElectricCyan
                        )
                    }
                    item {
                        TelemetryItem(
                            title = "Согласованный общий секрет DH (Diffie-Hellman)",
                            value = inspectorData.dhSecretHex,
                            clipboard = clipboardManager,
                            color = CryptoGreen.copy(alpha = 0.8f)
                        )
                    }
                } else {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SlateSurface),
                            border = BorderStroke(1.dp, SlateBorder)
                        ) {
                            Text(
                                text = "👥 Групповой режим: используется симметричный сессионный ключ группы, разданный участникам индивидуально через E2E DH-шифрование.",
                                modifier = Modifier.padding(12.dp),
                                color = ElectricCyan,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                item {
                    TelemetryItem(
                        title = "Производный ключ шифрования AES-256 (derived via HKDF)",
                        value = inspectorData.derivedSymmetricKeyHex,
                        clipboard = clipboardManager,
                        color = CryptoGreen
                    )
                }

                item {
                    TelemetryItem(
                        title = "Последний шифртекст (AES-256-GCM Ciphertext)",
                        value = inspectorData.rawCiphertextHex,
                        clipboard = clipboardManager,
                        color = CryptoGreen
                    )
                }

                item {
                    TelemetryItem(
                        title = "Криптографический вектор инициализации (12-byte GCM Nonce)",
                        value = inspectorData.nonceHex,
                        clipboard = clipboardManager,
                        color = ElectricCyan
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SlateSurface),
                        border = BorderStroke(1.dp, SlateBorder)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Расшифрованный текст на клиенте:", fontWeight = FontWeight.Bold, color = SoftWhite, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = inspectorData.decryptedPlaintext,
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 14.sp,
                                color = SoftWhite
                            )
                        }
                    }
                }
            } else {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Отправьте или получите сообщение для сбора данных шифрования",
                            color = AccentText,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun TelemetryItem(
    title: String,
    value: String,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    color: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = AccentText)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SlateSurface, shape = RoundedCornerShape(8.dp))
                .border(1.dp, SlateBorder, shape = RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = color,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = { clipboard.setText(AnnotatedString(value)) },
                modifier = Modifier
                    .size(24.dp)
                    .minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = AccentText,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// ====================================================================
// 5. Create Chat / Group Dialog
// ====================================================================
@Composable
fun CreateChatDialog(
    contacts: List<UserEntity>,
    onDismiss: () -> Unit,
    onCreateDirect: (UserEntity) -> Unit,
    onCreateGroup: (name: String, List<UserEntity>) -> Unit
) {
    var isGroupMode by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }
    val selectedMembers = remember { mutableStateListOf<UserEntity>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateSurface,
        title = {
            Text(
                text = if (isGroupMode) "Создать секретную группу" else "Новый защищенный чат",
                color = SoftWhite,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Mode Toggle Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Групповой чат до 10 человек", color = AccentText, fontSize = 13.sp)
                    Switch(
                        checked = isGroupMode,
                        onCheckedChange = { isGroupMode = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SpaceBg,
                            checkedTrackColor = ElectricCyan,
                            uncheckedThumbColor = AccentText,
                            uncheckedTrackColor = SlateBorder
                        ),
                        modifier = Modifier.testTag("switch_group_mode")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isGroupMode) {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("Название группы") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SoftWhite,
                            unfocusedTextColor = SoftWhite,
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = SlateBorder,
                            focusedLabelColor = ElectricCyan,
                            unfocusedLabelColor = AccentText
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_group_name")
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Выберите участников группы:", color = AccentText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyColumn(modifier = Modifier.height(150.dp)) {
                        items(contacts) { user ->
                            val isSelected = selectedMembers.contains(user)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelected) selectedMembers.remove(user) else selectedMembers.add(user)
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        if (isSelected) selectedMembers.remove(user) else selectedMembers.add(user)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = ElectricCyan,
                                        checkmarkColor = ElectricTeal,
                                        uncheckedColor = AccentText
                                    ),
                                    modifier = Modifier.testTag("checkbox_member_${user.id}")
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(user.username, color = SoftWhite)
                            }
                        }
                    }
                } else {
                    Text("Выберите собеседника для начала прямого E2E диалога X25519:", color = AccentText, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(modifier = Modifier.height(180.dp)) {
                        items(contacts) { user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCreateDirect(user) }
                                    .padding(vertical = 10.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(SpaceBg, shape = RoundedCornerShape(8.dp))
                                        .border(1.dp, SlateBorder, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(user.username.take(1).uppercase(), color = ElectricCyan, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(user.username, color = SoftWhite)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isGroupMode) {
                Button(
                    onClick = {
                        if (groupName.isNotBlank() && selectedMembers.isNotEmpty()) {
                            onCreateGroup(groupName, selectedMembers.toList())
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = ElectricTeal
                    ),
                    modifier = Modifier.testTag("btn_create_group_confirm")
                ) {
                    Text("Создать группу", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = AccentText)
            }
        }
    )
}
