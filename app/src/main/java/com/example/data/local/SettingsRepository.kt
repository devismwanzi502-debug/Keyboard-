package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {

    private val appContext = context.applicationContext

    private val API_KEY = stringPreferencesKey("api_key")
    private val GROQ_API_KEY = stringPreferencesKey("groq_api_key")
    private val API_PROVIDER = stringPreferencesKey("api_provider")
    
    private val AUTO_TYPE = booleanPreferencesKey("auto_type")
    private val CONVERSATION_MEMORY = booleanPreferencesKey("conversation_memory")
    private val AUTO_CORRECT = booleanPreferencesKey("auto_correct")
    private val REPLY_STYLE = stringPreferencesKey("reply_style")

    private val sharedPrefs = appContext.getSharedPreferences("rizzboard_prefs", Context.MODE_PRIVATE)

    private val safeData: Flow<Preferences> = appContext.dataStore.data.catch { exception ->
        if (exception is IOException) {
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }

    val apiKeyFlow: Flow<String?> = safeData.map { preferences ->
        preferences[API_KEY] ?: sharedPrefs.getString("api_key", null)
    }

    val groqApiKeyFlow: Flow<String?> = safeData.map { preferences ->
        preferences[GROQ_API_KEY] ?: sharedPrefs.getString("groq_api_key", null)
    }
    
    val apiProviderFlow: Flow<String> = safeData.map { preferences ->
        preferences[API_PROVIDER] ?: sharedPrefs.getString("api_provider", "Gemini") ?: "Gemini"
    }

    val autoTypeFlow: Flow<Boolean> = safeData.map { preferences ->
        preferences[AUTO_TYPE] ?: sharedPrefs.getBoolean("auto_type", false)
    }

    val conversationMemoryFlow: Flow<Boolean> = safeData.map { preferences ->
        preferences[CONVERSATION_MEMORY] ?: sharedPrefs.getBoolean("conversation_memory", true)
    }

    val autoCorrectFlow: Flow<Boolean> = safeData.map { preferences ->
        preferences[AUTO_CORRECT] ?: sharedPrefs.getBoolean("auto_correct", true)
    }
    
    val replyStyleFlow: Flow<String> = safeData.map { preferences ->
        preferences[REPLY_STYLE] ?: sharedPrefs.getString("reply_style", "Smooth") ?: "Smooth"
    }

    suspend fun saveApiKey(apiKey: String) {
        sharedPrefs.edit().putString("api_key", apiKey).apply()
        try {
            appContext.dataStore.edit { preferences ->
                preferences[API_KEY] = apiKey
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun saveGroqApiKey(apiKey: String) {
        sharedPrefs.edit().putString("groq_api_key", apiKey).apply()
        try {
            appContext.dataStore.edit { preferences ->
                preferences[GROQ_API_KEY] = apiKey
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    suspend fun setApiProvider(provider: String) {
        sharedPrefs.edit().putString("api_provider", provider).apply()
        try {
            appContext.dataStore.edit { preferences ->
                preferences[API_PROVIDER] = provider
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun setAutoType(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("auto_type", enabled).apply()
        try {
            appContext.dataStore.edit { preferences ->
                preferences[AUTO_TYPE] = enabled
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun setConversationMemory(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("conversation_memory", enabled).apply()
        try {
            appContext.dataStore.edit { preferences ->
                preferences[CONVERSATION_MEMORY] = enabled
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun setAutoCorrect(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("auto_correct", enabled).apply()
        try {
            appContext.dataStore.edit { preferences ->
                preferences[AUTO_CORRECT] = enabled
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    suspend fun setReplyStyle(style: String) {
        sharedPrefs.edit().putString("reply_style", style).apply()
        try {
            appContext.dataStore.edit { preferences ->
                preferences[REPLY_STYLE] = style
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

