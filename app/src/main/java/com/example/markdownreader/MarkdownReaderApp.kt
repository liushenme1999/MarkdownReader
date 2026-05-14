package com.example.markdownreader

import android.app.Application
import com.example.markdownreader.platform.MainThreadCrashGuard
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MarkdownReaderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 兜住 Android framework 已知 Editor / Selection setSpan 越界 crash，
        // 详见 [MainThreadCrashGuard] 注释；其他异常仍然正常 rethrow。
        MainThreadCrashGuard.install()
    }
}
