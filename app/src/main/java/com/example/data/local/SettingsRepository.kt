package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {

    private val appContext = context.applicationContext

    private val API_KEY = stringPreferencesKey("api_key")
    private val GROQ_API_KEY = stringPreferencesKey("groq_api_key")
    private val API_PROVIDER = stringPreferencesKey("api_provider")
    
    private val AUTO_TYPE = booleanPreferencesKey("auto_type")
    private val CONVERSATION_MEMORY = booleanPreferencesKey("conversation_memory")
    private val REPLY_STYLE = stringPreferencesKey("reply_style")

    val apiKeyFlow: Flow<String?> = appContext.dataStore.data.map { preferences ->
        preferences[API_KEY]
    }

    val groqApiKeyFlow: Flow<String?> = appContext.dataStore.data.map { preferences ->
        preferences[GROQ_API_KEY]
    }
    
    val apiProviderFlow: Flow<String> = appContext.dataStore.data.map { preferences ->
        preferences[API_PROVIDER] ?: "Gemini"
    }

    val autoTypeFlow: Flow<Boolean> = appContext.dataStore.data.map { preferences ->
        preferences[AUTO_TYPE] ?: false
    }

    val conversationMemoryFlow: Flow<Boolean> = appContext.dataStore.data.map { preferences ->
        preferences[CONVERSATION_MEMORY] ?: true
    }
    
    val replyStyleFlow: Flow<String> = appContext.dataStore.data.map { preferences ->
        preferences[REPLY_STYLE] ?: "Smooth"
    }

    suspend fun saveApiKey(apiKey: String) {
        appContext.dataStore.edit { preferences ->
            preferences[API_KEY] = apiKey
        }
    }

    suspend fun saveGroqApiKey(apiKey: String) {
        appContext.dataStore.edit { preferences ->
            preferences[GROQ_API_KEY] = apiKey
        }
    }
    
    suspend fun setApiProvider(provider: String) {
        appContext.dataStore.edit { preferences ->
            preferences[API_PROVIDER] = provider
        }
    }

    suspend fun setAutoType(enabled: Boolean) {
        appContext.dataStore.edit { preferences ->
            preferences[AUTO_TYPE] = enabled
        }
    }

    suspend fun setConversationMemory(enabled: Boolean) {
        appContext.dataStore.edit { preferences ->
            preferences[CONVERSATION_MEMORY] = enabled
        }
    }
    
    suspend fun setReplyStyle(style: String) {
        appContext.dataStore.edit { preferences ->
            preferences[REPLY_STYLE] = style
        }
    }
}
