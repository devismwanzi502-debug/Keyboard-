package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppDatabase
import com.example.data.local.SettingsRepository
import com.example.keyboard.KeyboardComposeView
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
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        settingsRepository = settingsRepository,
                        rizzRepository = rizzRepository,
                        onOpenAccessibility = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onOpenKeyboardSettings = {
                            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                        },
                        onSwitchKeyboard = {
                            try {
                                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                imm.showInputMethodPicker()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    settingsRepository: SettingsRepository,
    rizzRepository: RizzRepository,
    onOpenAccessibility: () -> Unit,
    onOpenKeyboardSettings: () -> Unit,
    onSwitchKeyboard: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    val apiProvider by settingsRepository.apiProviderFlow.collectAsState(initial = "Gemini")
    val apiKey by settingsRepository.apiKeyFlow.collectAsState(initial = "")
    val groqApiKey by settingsRepository.groqApiKeyFlow.collectAsState(initial = "")
    
    val autoType by settingsRepository.autoTypeFlow.collectAsState(initial = false)
    val memory by settingsRepository.conversationMemoryFlow.collectAsState(initial = true)
    
    var apiKeyInput by remember(apiKey) { mutableStateOf(apiKey ?: "") }
    var groqApiKeyInput by remember(groqApiKey) { mutableStateOf(groqApiKey ?: "") }
    
    var testText by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Setup & Test, 1: Settings
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Keyboard Test", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("AI Settings", fontWeight = FontWeight.Bold) }
            )
        }
        
        if (selectedTab == 0) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "🚀 Activate RizzBoard AI Keyboard",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Text(
                            text = "To use RizzBoard inside WhatsApp, Instagram, or any other app:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Text(
                            text = "1️⃣ Tap '1. Enable in Settings' and toggle RizzBoard ON.\n2️⃣ Tap '2. Switch Keyboard' and select 'RizzBoard' as active keyboard.\n3️⃣ Go to WhatsApp or Instagram, tap any text input, and RizzBoard will pop up!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onOpenKeyboardSettings,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("1. Enable Settings")
                            }
                            
                            Button(
                                onClick = onSwitchKeyboard,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("2. Switch Keyboard")
                            }
                        }
                    }
                }
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "💬 System Typing Test Box",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap below to open system keyboard (or RizzBoard once selected):",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = testText,
                            onValueChange = { testText = it },
                            placeholder = { Text("Click here to test system keyboard typing...") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }
                }
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "✨ Interactive Keyboard Playground",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = { testText = "" }) {
                                Text("Clear Text")
                            }
                        }
                        
                        Text(
                            text = "Try key press scale/fade animations and AI rizz generation live below:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(4.dp)
                        ) {
                            KeyboardComposeView(
                                repository = rizzRepository,
                                settingsRepository = settingsRepository,
                                onKeyPress = { char ->
                                    testText += char
                                },
                                onBackspace = {
                                    if (testText.isNotEmpty()) {
                                        testText = testText.dropLast(1)
                                    }
                                },
                                onInsertText = { text ->
                                    testText += text
                                },
                                onSwitchKeyboard = onSwitchKeyboard
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "AI Engine Configuration",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
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
    }
}

