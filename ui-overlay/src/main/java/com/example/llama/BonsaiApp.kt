package com.example.llama

import android.app.Application
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BonsaiApp : Application() {

    companion object {
        val crashFile: File by lazy {
            File(app.filesDir, "crash_log.txt")
        }

        private lateinit var app: BonsaiApp
    }

    override fun onCreate() {
        super.onCreate()
        app = this

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val cause = throwable.cause
                crashFile.appendText(
                    buildString {
                        appendLine("===== $ts thread=${thread.name} =====")
                        appendLine(throwable.javaClass.name + ": " + throwable.message)
                        appendLine(throwable.stackTraceToString())
                        if (cause != null) {
                            appendLine("Caused by: " + cause.javaClass.name + ": " + cause.message)
                            appendLine(cause.stackTraceToString())
                        }
                        appendLine()
                    }
                )
                Log.e("BonsaiCrash", "Uncaught exception", throwable)
            } catch (_: Exception) {
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
