package com.example.pcremote.data

data class UserDevice(
    val id: String = "",
    val name: String = "",
    val pairingCode: String = "",
    val online: Boolean = false,
    val lastSeen: Long = 0L,
    val createdAt: Long = 0L
)