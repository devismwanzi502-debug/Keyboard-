package com.example

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.local.AppDatabase
import com.example.data.local.SettingsRepository
import com.example.repository.RizzRepository
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val settingsRepository = SettingsRepository(this)
        val database = AppDatabase.getDatabase(this)
        val rizzRepository = RizzRepository(database.messageDao(), settingsRepository)
        
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SettingsScreen(
                        modifier = Modifier.padding(innerPadding),
                        settingsRepository = settingsRepository,
                        rizzRepository = rizzRepository,
                        onOpenAccessibility = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onOpenKeyboardSettings = {
                            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    settingsRepository: SettingsRepository,
    rizzRepository: RizzRepository,
    onOpenAccessibility: () -> Unit,
    onOpenKeyboardSettings: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    
    val apiProvider by settingsRepository.apiProviderFlow.collectAsState(initial = "Gemini")
    val apiKey by settingsRepository.apiKeyFlow.collectAsState(initial = "")
    val groqApiKey by settingsRepository.groqApiKeyFlow.collectAsState(initial = "")
    
    val autoType by settingsRepository.autoTypeFlow.collectAsState(initial = false)
    val memory by settingsRepository.conversationMemoryFlow.collectAsState(initial = true)
    
    var apiKeyInput by remember(apiKey) { mutableStateOf(apiKey ?: "") }
    var groqApiKeyInput by remember(groqApiKey) { mutableStateOf(groqApiKey ?: "") }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "RizzBoard AI Settings",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("AI Provider:", modifier = Modifier.weight(1f))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = apiProvider == "Gemini",
                    onClick = { coroutineScope.launch { settingsRepository.setApiProvider("Gemini") } }
                )
                Text("Gemini")
                Spacer(modifier = Modifier.width(8.dp))
                RadioButton(
                    selected = apiProvider == "Groq",
                    onClick = { coroutineScope.launch { settingsRepository.setApiProvider("Groq") } }
                )
                Text("Groq")
            }
        }
        
        if (apiProvider == "Gemini") {
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text("Gemini API Key (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Button(
                onClick = {
                    coroutineScope.launch { settingsRepository.saveApiKey(apiKeyInput) }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Gemini API Key")
            }
        } else {
            OutlinedTextField(
                value = groqApiKeyInput,
                onValueChange = { groqApiKeyInput = it },
                label = { Text("Groq API Key") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Button(
                onClick = {
                    coroutineScope.launch { settingsRepository.saveGroqApiKey(groqApiKeyInput) }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Groq API Key")
            }
        }
        
        HorizontalDivider()
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Auto-Type AI Reply")
            Switch(
                checked = autoType,
                onCheckedChange = { 
                    coroutineScope.launch { settingsRepository.setAutoType(it) } 
                }
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Conversation Memory")
            Switch(
                checked = memory,
                onCheckedChange = { 
                    coroutineScope.launch { settingsRepository.setConversationMemory(it) } 
                }
            )
        }
        
        HorizontalDivider()
        
        Button(
            onClick = onOpenKeyboardSettings,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Enable Keyboard in Settings")
        }
        
        Button(
            onClick = onOpenAccessibility,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Enable Message Reader (Accessibility)")
        }
        
        Button(
            onClick = {
                coroutineScope.launch { rizzRepository.clearHistory() }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Clear Conversation History")
        }
    }
}
