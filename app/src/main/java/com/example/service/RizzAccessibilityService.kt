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
import kotlinx.coroutines.launch

class RizzAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private lateinit var repository: RizzRepository

    override fun onServiceConnected() {
        super.onServiceConnected()
        val database = AppDatabase.getDatabase(this)
        repository = RizzRepository(database.messageDao(), SettingsRepository(this))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                val source = event.source ?: return
                handleNodeContent(source)
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val source = event.source ?: return
                // Check if it's a copy action or just clicking on a message
                // We'll optionally capture text from clicked views as well
                handleNodeContent(source)
            }
        }
    }

    private fun handleNodeContent(node: AccessibilityNodeInfo) {
        val text = node.text?.toString() ?: node.contentDescription?.toString()
        if (!text.isNullOrBlank()) {
            Log.d("RizzAccessibility", "Intercepted text: $text")
            // We consider text captured via long click as an incoming message
            serviceScope.launch {
                repository.insertMessage(text, isIncoming = true)
            }
        }
        node.recycle()
    }

    override fun onInterrupt() {
        // Handle interrupt
    }
}
