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
        
        val firstChoice = if (provider == "Groq") {
            generateGroqReply(message, style, contextMessages, useMemory)
        } else {
            generateGeminiReply(message, style, contextMessages, useMemory)
        }

        // If primary provider hit an error, attempt fallback provider
        if (firstChoice.startsWith("Error generating reply:")) {
            val fallbackChoice = if (provider == "Groq") {
                generateGeminiReply(message, style, contextMessages, useMemory)
            } else {
                generateGroqReply(message, style, contextMessages, useMemory)
            }
            if (!fallbackChoice.startsWith("Error generating reply:") && !fallbackChoice.startsWith("Please configure")) {
                return fallbackChoice
            }
        }

        return firstChoice
    }
    
    private suspend fun generateGeminiReply(
        message: String,
        style: String,
        contextMessages: List<MessageEntity>,
        useMemory: Boolean
    ): String {
        val userKey = settingsRepository.apiKeyFlow.first()
        val keyToUse = when {
            !userKey.isNullOrBlank() -> userKey
            BuildConfig.GEMINI_API_KEY.isNotEmpty() -> BuildConfig.GEMINI_API_KEY
            else -> SettingsRepository.DEFAULT_GEMINI_API_KEY
        }
        
        if (keyToUse.isEmpty()) {
            return "Please configure your Gemini API Key in Settings."
        }
        
        val systemPrompt = "You are a witty, smart chat assistant for an Android keyboard. " +
            "Answer the user's prompt or generate a natural reply in the style: $style. " +
            "Do not include quotes or extra formatting, just the direct answer or reply text. " +
            "Keep it concise, helpful, and natural (under 3 sentences)."
            
        val contextPrompt = if (useMemory && contextMessages.isNotEmpty()) {
            val history = contextMessages.take(5).reversed().joinToString("\n") { 
                if (it.isIncoming) "Them: ${it.text}" else "Me: ${it.text}"
            }
            "Context:\n$history\n\nPrompt or Message: $message"
        } else {
            "Prompt or Message: $message"
        }

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = contextPrompt)))),
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
        )

        return try {
            val response = RetrofitClient.service.generateContent(keyToUse, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim() 
                ?: "No response generated."
        } catch (e: retrofit2.HttpException) {
            val errorDetails = e.response()?.errorBody()?.string() ?: e.message()
            "Error generating reply: HTTP ${e.code()} - $errorDetails"
        } catch (e: Exception) {
            "Error generating reply: ${e.localizedMessage ?: e.message}"
        }
    }

    private suspend fun generateGroqReply(
        message: String,
        style: String,
        contextMessages: List<MessageEntity>,
        useMemory: Boolean
    ): String {
        val userKey = settingsRepository.groqApiKeyFlow.first()
        val keyToUse = if (!userKey.isNullOrBlank()) userKey else SettingsRepository.DEFAULT_GROQ_API_KEY
        
        if (keyToUse.isEmpty()) {
            return "Please configure your Groq API Key in Settings."
        }
        
        val systemPrompt = "You are a witty, smart chat assistant for an Android keyboard. " +
            "Answer the user's prompt or generate a natural reply in the style: $style. " +
            "Do not include quotes or extra formatting, just the direct answer or reply text. " +
            "Keep it concise, helpful, and natural (under 3 sentences)."
            
        val contextPrompt = if (useMemory && contextMessages.isNotEmpty()) {
            val history = contextMessages.take(5).reversed().joinToString("\n") { 
                if (it.isIncoming) "Them: ${it.text}" else "Me: ${it.text}"
            }
            "Context:\n$history\n\nPrompt or Message: $message"
        } else {
            "Prompt or Message: $message"
        }

        val messages = listOf(
            GroqMessage(role = "system", content = systemPrompt),
            GroqMessage(role = "user", content = contextPrompt)
        )

        val request = GroqRequest(
            model = "llama-3.1-8b-instant",
            messages = messages
        )

        return try {
            val response = GroqRetrofitClient.service.generateContent("Bearer $keyToUse", request)
            response.choices?.firstOrNull()?.message?.content?.trim() ?: "No response generated."
        } catch (e: retrofit2.HttpException) {
            val errorDetails = e.response()?.errorBody()?.string() ?: e.message()
            "Error generating reply: HTTP ${e.code()} - $errorDetails"
        } catch (e: Exception) {
            "Error generating reply: ${e.localizedMessage ?: e.message}"
        }
    }
}
