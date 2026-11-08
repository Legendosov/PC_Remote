package com.example.pcremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pcremote.data.AppItem
import com.example.pcremote.data.Shortcut
import com.example.pcremote.data.UserDevice

@Composable
fun RemoteScreen(
    device: UserDevice,
    shortcuts: List<Shortcut>,
    apps: List<AppItem>,
    hiddenApps: List<String>,
    onBack: () -> Unit,
    onAddShortcutClick: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onShortcutClick: (Shortcut) -> Unit,
    onAppVolumeChange: (String, Float) -> Unit,
    onDeleteShortcut: (Shortcut) -> Unit,
    onHideApp: (String) -> Unit,
    onMedia: (String) -> Unit,
    onAction: (String) -> Unit
) {
    var sysVol by remember { mutableFloatStateOf(0.5f) }
    val visibleApps = apps.filter { it.name !in hiddenApps }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Шапка
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") }
                Column {
                    Text(device.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(if (device.online) "● В сети" else "○ Не в сети", fontSize = 12.sp, color = if (device.online) Color.Green else Color.Gray)
                }
            }
            IconButton(onClick = { onAction("shutdown") }) {
                Icon(Icons.Default.PowerSettingsNew, "Питание", tint = Color.Red)
            }
        }

        Spacer(Modifier.height(16.dp))

        // СЕКЦИЯ ЯРЛЫКОВ
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ЯРЛЫКИ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = onAddShortcutClick) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Добавить")
            }
        }

        if (shortcuts.isEmpty()) {
            Text("Нет ярлыков. Добавьте сайт или приложение.", fontSize = 13.sp, color = Color.Gray)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                userScrollEnabled = false
            ) {
                items(shortcuts, key = { it.id }) { s ->
                    ShortcutTile(
                        shortcut = s,
                        onClick = { onShortcutClick(s) },
                        onDelete = { onDeleteShortcut(s) }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // МЕДИА
        Text("MEDIA", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onMedia("prev") }) { Icon(Icons.Default.SkipPrevious, "Назад", modifier = Modifier.size(36.dp)) }
            IconButton(
                onClick = { onMedia("playpause") },
                modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50))
            ) {
                Icon(Icons.Default.PlayArrow, "Плей", modifier = Modifier.size(40.dp), tint = Color.White)
            }
            IconButton(onClick = { onMedia("next") }) { Icon(Icons.Default.SkipNext, "Вперед", modifier = Modifier.size(36.dp)) }
        }
        Spacer(Modifier.height(16.dp))

        // СЕКЦИЯ БЫСТРЫХ ДЕЙСТВИЙ
        Text("QUICK ACTIONS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            QuickActionButton(Icons.Default.Lock, "Блок", Color.Blue) { onAction("lock") }
            QuickActionButton(Icons.Default.RestartAlt, "Рестарт", Color(0xFFFFA500)) { onAction("restart") }
            QuickActionButton(Icons.Default.PowerSettingsNew, "Выкл", Color.Red) { onAction("shutdown") }
            QuickActionButton(Icons.Default.Bedtime, "Сон", Color(0xFF673AB7)) { onAction("sleep") }
        }
        Spacer(Modifier.height(16.dp))

        // СЕКЦИЯ СИСТЕМНОЙ ГРОМКОСТИ
        Text("SYSTEM VOLUME", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VolumeUp, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Text("${(sysVol * 100).toInt()}%", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }

                // ИСПРАВЛЕННЫЙ ПОЛЗУНОК
                Slider(
                    value = sysVol,
                    onValueChange = { newValue -> sysVol = newValue }, // Только двигаем на экране
                    onValueChangeFinished = { onVolumeChange(sysVol) } // Отправляем на ПК при отпускании
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // СЕКЦИЯ ПРИЛОЖЕНИЙ (МИКШЕР)
        Text("APPLICATIONS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        if (visibleApps.isEmpty()) {
            Text("Нет запущенных приложений со звуком.", fontSize = 13.sp, color = Color.Gray)
        } else {
            visibleApps.forEach { app ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Apps, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.name, fontWeight = FontWeight.SemiBold)

                            // ИСПРАВЛЕННЫЙ ПОЛЗУНОК ПРИЛОЖЕНИЯ
                            Slider(
                                value = app.volume,
                                onValueChange = { newVol -> /* Можно добавить локальное обновление, но не обязательно */ },
                                onValueChangeFinished = { onAppVolumeChange(app.name, app.volume) }
                            )
                        }
                        Text("${(app.volume * 100).toInt()}%", fontSize = 12.sp)
                        IconButton(onClick = { onHideApp(app.name) }) {
                            Icon(Icons.Default.Close, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ShortcutTile(
    shortcut: Shortcut,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.clickable { onClick() }.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (shortcut.type == "url") Icons.Default.Language else Icons.Default.Apps,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                shortcut.name,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                lineHeight = 12.sp
            )
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .size(20.dp)
                .align(Alignment.TopEnd)
                .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(50))
        ) {
            Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
fun QuickActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }.padding(8.dp)
    ) {
        Box(
            modifier = Modifier.size(56.dp).background(color.copy(alpha = 0.2f), shape = RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}