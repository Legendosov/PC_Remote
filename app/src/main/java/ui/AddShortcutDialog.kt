package com.example.pcremote.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AddShortcutDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, target: String, type: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("url") } // "url" или "app"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить ярлык") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Выбор типа
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == "url",
                        onClick = { type = "url" },
                        label = { Text("Сайт") }
                    )
                    FilterChip(
                        selected = type == "app",
                        onClick = { type = "app" },
                        label = { Text("Приложение") }
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название (например, YouTube)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text(if (type == "url") "Ссылка (https://...)" else "Путь (C:\\...\\app.exe)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name, target, type) },
                enabled = name.isNotBlank() && target.isNotBlank()
            ) { Text("Добавить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}