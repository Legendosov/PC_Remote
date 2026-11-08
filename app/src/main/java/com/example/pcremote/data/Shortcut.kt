package com.example.pcremote.data

data class Shortcut(
    val id: String = "",
    val name: String = "",
    val target: String = "", // Ссылка (https://...) или путь (C:\...)
    val type: String = "url" // "url" (сайт) или "app" (программа)
)