package com.example.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.local.AppDatabase
import com.example.repository.RizzRepository
import com.example.data.local.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class RizzAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var repository: RizzRepository? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            val database = AppDatabase.getDatabase(this)
            repository = RizzRepository(database.messageDao(), SettingsRepository(this))
        } catch (e: Exception) {
            Log.e("RizzAccessibility", "Error initializing database in service", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val source = event.source ?: return
                handleNodeContent(source)
            }
        }
    }

    private fun handleNodeContent(node: AccessibilityNodeInfo) {
        val text = node.text?.toString() ?: node.contentDescription?.toString()
        if (!text.isNullOrBlank()) {
            Log.d("RizzAccessibility", "Intercepted text: $text")
            val repo = repository ?: return
            serviceScope.launch {
                try {
                    repo.insertMessage(text, isIncoming = true)
                } catch (e: Exception) {
                    Log.e("RizzAccessibility", "Error saving intercepted text", e)
                }
            }
        }
    }

    override fun onInterrupt() {
        // Handle interrupt
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}

