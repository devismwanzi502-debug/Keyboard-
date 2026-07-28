package com.example.repository

import com.example.BuildConfig
import com.example.data.local.MessageDao
import com.example.data.local.MessageEntity
import com.example.data.local.SettingsRepository
import com.example.data.remote.Content
import com.example.data.remote.GenerateContentRequest
import com.example.data.remote.Part
import com.example.data.remote.RetrofitClient
import com.example.data.remote.GroqRetrofitClient
import com.example.data.remote.GroqRequest
import com.example.data.remote.GroqMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class RizzRepository(
    private val messageDao: MessageDao,
    private val settingsRepository: SettingsRepository
) {
    val recentMessages: Flow<List<MessageEntity>> = messageDao.getRecentMessages()
    
    suspend fun insertMessage(text: String, isIncoming: Boolean) {
        messageDao.insertMessage(MessageEntity(text = text, isIncoming = isIncoming))
    }
    
    suspend fun clearHistory() {
        messageDao.clearHistory()
    }
    
    suspend fun generateReply(
        message: String,
        style: String,
        contextMessages: List<MessageEntity>,
        useMemory: Boolean
    ): String {
        val provider = settingsRepository.apiProviderFlow.first()
        
        return if (provider == "Groq") {
            generateGroqReply(message, style, contextMessages, useMemory)
        } else {
            generateGeminiReply(message, style, contextMessages, useMemory)
        }
    }
    
    private suspend fun generateGeminiReply(
        message: String,
        style: String,
        contextMessages: List<MessageEntity>,
        useMemory: Boolean
    ): String {
        val userKey = settingsRepository.apiKeyFlow.first()
        val defaultKey = BuildConfig.GEMINI_API_KEY
        val keyToUse = if (!userKey.isNullOrBlank()) userKey else defaultKey
        
        if (keyToUse.isEmpty()) {
            return "Please configure your Gemini API Key in Settings."
        }
        
        val systemPrompt = "You are a witty, smart chat assistant for an Android keyboard. " +
            "Generate a short, natural reply to the user's message in the following style: $style. " +
            "Do not include quotes or extra formatting, just the raw text of the reply. " +
            "Keep it strictly under 3 sentences."
            
        val contextPrompt = if (useMemory && contextMessages.isNotEmpty()) {
            val history = contextMessages.take(5).reversed().joinToString("\n") { 
                if (it.isIncoming) "Them: ${it.text}" else "Me: ${it.text}"
            }
            "Recent context:\n$history\n\nLatest message: Them: $message\n\nReply as Me:"
        } else {
            "Latest message: Them: $message\n\nReply as Me:"
        }

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = contextPrompt)))),
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
        )

        return try {
            val response = RetrofitClient.service.generateContent(keyToUse, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim() ?: "No response generated."
        } catch (e: Exception) {
            "Error generating reply: ${e.message}"
        }
    }

    private suspend fun generateGroqReply(
        message: String,
        style: String,
        contextMessages: List<MessageEntity>,
        useMemory: Boolean
    ): String {
        val userKey = settingsRepository.groqApiKeyFlow.first()
        if (userKey.isNullOrBlank()) {
            return "Please configure your Groq API Key in Settings."
        }
        
        val systemPrompt = "You are a witty, smart chat assistant for an Android keyboard. " +
            "Generate a short, natural reply to the user's message in the following style: $style. " +
            "Do not include quotes or extra formatting, just the raw text of the reply. " +
            "Keep it strictly under 3 sentences."
            
        val contextPrompt = if (useMemory && contextMessages.isNotEmpty()) {
            val history = contextMessages.take(5).reversed().joinToString("\n") { 
                if (it.isIncoming) "Them: ${it.text}" else "Me: ${it.text}"
            }
            "Recent context:\n$history\n\nLatest message: Them: $message\n\nReply as Me:"
        } else {
            "Latest message: Them: $message\n\nReply as Me:"
        }

        val messages = listOf(
            GroqMessage(role = "system", content = systemPrompt),
            GroqMessage(role = "user", content = contextPrompt)
        )

        val request = GroqRequest(
            model = "llama3-8b-8192",
            messages = messages
        )

        return try {
            val response = GroqRetrofitClient.service.generateContent("Bearer $userKey", request)
            response.choices?.firstOrNull()?.message?.content?.trim() ?: "No response generated."
        } catch (e: Exception) {
            "Error generating reply: ${e.message}"
        }
    }
}
