package com.example.pcremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pcremote.auth.AuthViewModel
import com.example.pcremote.data.*
import com.example.pcremote.ui.*
import com.example.pcremote.ui.theme.PcRemoteTheme
import kotlinx.coroutines.launch
import com.example.pcremote.data.SettingsStore

// --- ЗАЩИТА FIREBASE ---
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.database.FirebaseDatabase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ВКЛЮЧАЕМ КЭШ FIREBASE (Чтобы не вылетало при 100/100)
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (e: Exception) {
            // Если уже включен - игнорируем
        }

        // ЗАЩИТА FIREBASE APP CHECK
        val appCheck = FirebaseAppCheck.getInstance()
        if (BuildConfig.DEBUG) {
            appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        } else {
            appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
        }

        setContent {
            val context = LocalContext.current
            val settingsStore = remember { SettingsStore(context) }
            val isDarkTheme by settingsStore.isDarkTheme.collectAsState(initial = true)
            val scope = rememberCoroutineScope()

            val authVm: AuthViewModel = viewModel()
            val authState by authVm.state.collectAsStateWithLifecycle()

            PcRemoteTheme(darkTheme = isDarkTheme) {
                if (authState.loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    MainScreen(
                        authVm = authVm,
                        authState = authState,
                        isDarkTheme = isDarkTheme,
                        onThemeChange = { scope.launch { settingsStore.setDarkTheme(it) } }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    authVm: AuthViewModel,
    authState: com.example.pcremote.auth.AuthState,
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var currentDevice by remember { mutableStateOf<UserDevice?>(null) }

    val repo = remember { RemoteRepository() }
    val scope = rememberCoroutineScope()
    val devices by repo.devicesFlow().collectAsState(initial = emptyList())

    var showPairDialog by remember { mutableStateOf(false) }
    var showAddShortcutDialog by remember { mutableStateOf(false) }

    val shortcuts by if (currentDevice != null) {
        repo.shortcutsFlow(currentDevice!!.id).collectAsState(initial = emptyList())
    } else { remember { mutableStateOf(emptyList<Shortcut>()) } }

    val apps by if (currentDevice != null) {
        repo.appsFlow(currentDevice!!.id).collectAsState(initial = emptyList())
    } else { remember { mutableStateOf(emptyList<AppItem>()) } }

    val hiddenApps by if (currentDevice != null) {
        repo.hiddenAppsFlow(currentDevice!!.id).collectAsState(initial = emptyList())
    } else { remember { mutableStateOf(emptyList<String>()) } }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(icon = { Icon(Icons.Default.Tune, null) }, label = { Text("Управление", fontSize = 11.sp) }, selected = selectedTab == 0, onClick = { selectedTab = 0 })
                NavigationBarItem(icon = { Icon(Icons.Default.Computer, null) }, label = { Text("Устройства", fontSize = 11.sp) }, selected = selectedTab == 1, onClick = { selectedTab = 1 })
                NavigationBarItem(icon = { Icon(Icons.Default.Person, null) }, label = { Text("Профиль", fontSize = 11.sp) }, selected = selectedTab == 2, onClick = { selectedTab = 2 })
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (selectedTab) {
                0 -> {
                    if (authState.user == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Button(onClick = { selectedTab = 2 }) { Text("Войти") }
                        }
                    } else if (currentDevice == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Компьютер не выбран")
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = { selectedTab = 1 }) { Text("Выбрать ПК") }
                            }
                        }
                    } else {
                        RemoteScreen(
                            device = currentDevice!!,
                            shortcuts = shortcuts,
                            apps = apps,
                            hiddenApps = hiddenApps,
                            onBack = { currentDevice = null },
                            onAddShortcutClick = { showAddShortcutDialog = true },
                            onVolumeChange = { newVol -> scope.launch { repo.setSystemVolume(currentDevice!!.id, newVol) } },
                            onShortcutClick = { shortcut -> scope.launch { repo.launchShortcut(currentDevice!!.id, shortcut.target, shortcut.type) } },
                            onAppVolumeChange = { appName, newVol -> scope.launch { repo.setAppVolume(currentDevice!!.id, appName, newVol) } },
                            onDeleteShortcut = { shortcut -> scope.launch { repo.removeShortcut(currentDevice!!.id, shortcut.id) } },
                            onHideApp = { appName -> scope.launch { repo.hideApp(currentDevice!!.id, appName) } },
                            onMedia = { cmd -> scope.launch { repo.sendMedia(currentDevice!!.id, cmd) } },
                            onAction = { action -> scope.launch { repo.sendAction(currentDevice!!.id, action) } }
                        )
                    }
                }
                1 -> {
                    if (authState.user == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Button(onClick = { selectedTab = 2 }) { Text("Войти") }
                        }
                    } else {
                        DevicesScreen(
                            devices = devices,
                            userEmail = authState.user?.email,
                            onDeviceSelected = { device -> currentDevice = device; selectedTab = 0 },
                            onAddClick = { showPairDialog = true },
                            onDeleteClick = { device ->
                                if (currentDevice?.id == device.id) currentDevice = null
                                scope.launch { repo.removeDevice(device.id) }
                            }
                        )
                    }
                }
                2 -> {
                    if (authState.user == null) LoginScreen(authVm = authVm)
                    else ProfileScreen(
                        userEmail = authState.user?.email ?: "",
                        onLogout = { authVm.logout(); currentDevice = null },
                        isDarkTheme = isDarkTheme,
                        onThemeChange = onThemeChange
                    )
                }
            }
        }
    }

    if (showPairDialog) {
        PairDeviceDialog(onDismiss = { showPairDialog = false }, onPair = { code, name -> scope.launch { repo.pairDeviceByCode(code, name); showPairDialog = false } })
    }

    if (showAddShortcutDialog && currentDevice != null) {
        AddShortcutDialog(onDismiss = { showAddShortcutDialog = false }, onAdd = { name, target, type -> scope.launch { repo.addShortcut(currentDevice!!.id, name, target, type); showAddShortcutDialog = false } })
    }
}

@Composable
fun ProfileScreen(
    userEmail: String,
    onLogout: () -> Unit,
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Person, null, Modifier.size(100.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Вы вошли как:", fontSize = 16.sp)
        Text(userEmail, fontSize = 20.sp, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(32.dp))

        // ПЕРЕКЛЮЧАТЕЛЬ ТЕМЫ
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(if (isDarkTheme) "Темная тема" else "Светлая тема", fontWeight = FontWeight.Medium)
                }
                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = { onThemeChange(it) }
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Выйти") }
    }
}