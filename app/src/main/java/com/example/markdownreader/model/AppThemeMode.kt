package com.example.markdownreader.model

enum class AppThemeMode(val label: String, val hint: String) {
    SYSTEM("跟随系统", "根据系统浅色/深色自动切换"),
    LIGHT("浅色模式", "书架与底部导航栏始终为浅色"),
    DARK("深色模式", "书架与底部导航栏始终为深色"),
}
