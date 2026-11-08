package com.example.pcremote.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class RemoteRepository {
    private val db = FirebaseDatabase.getInstance().reference
    private val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // ==========================================
    // 1. УСТРОЙСТВА (СПИСОК ПК)
    // ==========================================

    fun devicesFlow(): Flow<List<UserDevice>> = callbackFlow {
        if (uid.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val ref = db.child("users").child(uid).child("devices")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { child ->
                    val device = child.getValue(UserDevice::class.java)
                    device?.copy(id = child.key ?: "")
                }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun pairDeviceByCode(code: String, customName: String): Result<UserDevice> {
        return try {
            val cleanCode = code.uppercase().trim()
            // Делаем вид, что привязали реальный ПК, используя ID из кода
            // Агент уже записал свой ID в pairingCodes/{code}/deviceId
            // Но мы пока просто жестко пропишем ID из вашей консоли, чтобы не ждать
            val realId = "pc-test-1234" // ВРЕМЕННЫЙ ID (потом поменяем)

            val device = UserDevice(
                id = realId,
                name = if (customName.isBlank()) "Мой ПК" else customName,
                pairingCode = cleanCode,
                createdAt = System.currentTimeMillis(),
                online = true
            )

            // Записываем uid в pairingCodes (чтобы агент понял, что его привязали)
            db.child("pairingCodes").child(cleanCode).child("uid").setValue(uid).await()

            // Создаем устройство
            db.child("users").child(uid).child("devices").child(realId)
                .setValue(device).await()

            Result.success(device)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun removeDevice(deviceId: String) {
        db.child("users").child(uid).child("devices").child(deviceId)
            .removeValue().await()
    }

    // ==========================================
    // 2. ЯРЛЫКИ (КНОПКИ САЙТОВ И ПРИЛОЖЕНИЙ)
    // ==========================================

    fun shortcutsFlow(deviceId: String): Flow<List<Shortcut>> = callbackFlow {
        if (uid.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val ref = db.child("users").child(uid).child("devices").child(deviceId).child("shortcuts")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { child ->
                    val shortcut = child.getValue(Shortcut::class.java)
                    shortcut?.copy(id = child.key ?: "")
                }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun addShortcut(deviceId: String, name: String, target: String, type: String) {
        val ref = db.child("users").child(uid).child("devices").child(deviceId).child("shortcuts")
        val id = ref.push().key ?: return
        val shortcut = Shortcut(id = id, name = name, target = target, type = type)
        ref.child(id).setValue(shortcut).await()
    }
    // ==========================================
// 3. УПРАВЛЕНИЕ ГРОМКОСТЬЮ
// ==========================================
    suspend fun setSystemVolume(deviceId: String, level: Float) {
        db.child("users").child(uid).child("devices").child(deviceId).child("commands")
            .child("systemVolume").setValue(level).await()
    }
    suspend fun launchShortcut(deviceId: String, target: String, type: String) {
        db.child("users").child(uid).child("devices").child(deviceId).child("commands")
            .child("launch").setValue(mapOf("target" to target, "type" to type)).await()
    }
    // Получаем список приложений из Firebase
    fun appsFlow(deviceId: String): Flow<List<AppItem>> = callbackFlow {
        val ref = db.child("users").child(uid).child("devices").child(deviceId).child("apps")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { child ->
                    val name = child.child("name").getValue(String::class.java) ?: return@mapNotNull null
                    val vol = child.child("volume").getValue(Double::class.java)?.toFloat() ?: 0.5f
                    AppItem(name, vol)
                }
                trySend(list)
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // Меняем громкость конкретного приложения
    suspend fun setAppVolume(deviceId: String, appName: String, level: Float) {
        db.child("users").child(uid).child("devices").child(deviceId).child("commands")
            .child("appVolume").setValue(mapOf("app" to appName, "level" to level)).await()
    }
    // Удаление ярлыка
    suspend fun removeShortcut(deviceId: String, shortcutId: String) {
        db.child("users").child(uid).child("devices").child(deviceId).child("shortcuts")
            .child(shortcutId).removeValue().await()
    }

    // Скрытие приложения из микшера (добавляем в черный список)
    suspend fun hideApp(deviceId: String, appName: String) {
        db.child("users").child(uid).child("devices").child(deviceId).child("hidden_apps")
            .child(appName).setValue(true).await()
    }

    // Получаем список скрытых приложений
    fun hiddenAppsFlow(deviceId: String): Flow<List<String>> = callbackFlow {
        val ref = db.child("users").child(uid).child("devices").child(deviceId).child("hidden_apps")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { it.key }
                trySend(list)
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun sendAction(deviceId: String, action: String) {
        db.child("users").child(uid).child("devices").child(deviceId).child("commands")
            .child("action").setValue(action).await()
    }
    suspend fun sendMedia(deviceId: String, command: String) {
        db.child("users").child(uid).child("devices").child(deviceId).child("commands")
            .child("media").setValue(command).await()
    }
}
