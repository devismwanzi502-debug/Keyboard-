package com.example.keyboard

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.SettingsRepository
import com.example.repository.RizzRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardComposeView(
    repository: RizzRepository,
    settingsRepository: SettingsRepository,
    onKeyPress: (String) -> Unit,
    onBackspace: () -> Unit,
    onInsertText: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isShiftEnabled by remember { mutableStateOf(false) }
    var generatedReply by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var activePanel by remember { mutableStateOf<KeyboardPanel>(KeyboardPanel.None) }
    
    val recentMessages by repository.recentMessages.collectAsState(initial = emptyList())
    
    // AMOLED Dark Theme colors
    val keyboardBackground = Color(0xFF000000)
    val keyBackground = Color(0xFF1E1E1E)
    val actionKeyBackground = Color(0xFF2C2C2C)
    val textColor = Color(0xFFE3E3E3)
    val primaryAccent = Color(0xFF8AB4F8)
    val panelBackground = Color(0xFF121212)
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(keyboardBackground)
            .padding(bottom = 4.dp)
    ) {
        // Smart Bar
        SmartBar(
            onActionClick = { panel ->
                activePanel = if (activePanel == panel) KeyboardPanel.None else panel
            },
            activePanel = activePanel,
            backgroundColor = keyboardBackground,
            accentColor = primaryAccent,
            textColor = textColor
        )

        // Dynamic Panels (Rizz, Reply, AI, etc)
        AnimatedVisibility(
            visible = activePanel != KeyboardPanel.None || generatedReply != null || isGenerating,
            enter = expandVertically(animationSpec = tween(300)) + fadeIn(tween(300)),
            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(tween(300))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(panelBackground)
                    .padding(vertical = 8.dp)
            ) {
                if (activePanel == KeyboardPanel.RizzModes) {
                    RizzModeSelector(
                        onModeSelected = { style ->
                            coroutineScope.launch {
                                isGenerating = true
                                generatedReply = null
                                activePanel = KeyboardPanel.None
                                val latestMessage = recentMessages.firstOrNull { it.isIncoming }?.text ?: "Hello"
                                val memoryEnabled = settingsRepository.conversationMemoryFlow.first()
                                
                                val reply = repository.generateReply(
                                    message = latestMessage,
                                    style = style,
                                    contextMessages = recentMessages,
                                    useMemory = memoryEnabled
                                )
                                generatedReply = reply
                                isGenerating = false
                            }
                        },
                        textColor = textColor,
                        accentColor = primaryAccent
                    )
                }

                if (isGenerating || generatedReply != null) {
                    AiReplyPanel(
                        isGenerating = isGenerating,
                        generatedReply = generatedReply,
                        onInsert = {
                            if (generatedReply != null) {
                                onInsertText(generatedReply!!)
                                generatedReply = null
                            }
                        },
                        onClear = { generatedReply = null },
                        backgroundColor = actionKeyBackground,
                        textColor = textColor,
                        accentColor = primaryAccent
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        
        // QWERTY Keys
        val row1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
        val row2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
        val row3 = listOf("z", "x", "c", "v", "b", "n", "m")
        
        KeyboardRow(keys = row1, isShiftEnabled = isShiftEnabled, onKeyPress = onKeyPress, keyColor = keyBackground, textColor = textColor)
        KeyboardRow(keys = row2, isShiftEnabled = isShiftEnabled, onKeyPress = onKeyPress, modifier = Modifier.padding(horizontal = 16.dp), keyColor = keyBackground, textColor = textColor)
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeyButton(
                icon = Icons.Default.KeyboardArrowUp,
                modifier = Modifier.weight(1.25f),
                containerColor = if (isShiftEnabled) primaryAccent else actionKeyBackground,
                iconColor = if (isShiftEnabled) keyboardBackground else textColor,
                onClick = { isShiftEnabled = !isShiftEnabled }
            )
            KeyboardRow(keys = row3, isShiftEnabled = isShiftEnabled, onKeyPress = onKeyPress, modifier = Modifier.weight(7f), keyColor = keyBackground, textColor = textColor)
            KeyButton(
                icon = Icons.AutoMirrored.Filled.Backspace,
                modifier = Modifier.weight(1.25f),
                containerColor = actionKeyBackground,
                iconColor = textColor,
                onClick = onBackspace
            )
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            KeyButton(text = "?123", modifier = Modifier.weight(1.5f), containerColor = actionKeyBackground, textColor = textColor, onClick = {})
            KeyButton(text = ",", modifier = Modifier.weight(1f), containerColor = actionKeyBackground, textColor = textColor, onClick = { onKeyPress(",") })
            KeyButton(text = "Space", modifier = Modifier.weight(4f), containerColor = keyBackground, textColor = textColor, onClick = { onKeyPress(" ") }) // Space bar
            KeyButton(text = ".", modifier = Modifier.weight(1f), containerColor = actionKeyBackground, textColor = textColor, onClick = { onKeyPress(".") })
            KeyButton(icon = Icons.AutoMirrored.Filled.KeyboardReturn, modifier = Modifier.weight(1.5f), containerColor = primaryAccent, iconColor = keyboardBackground, onClick = { onKeyPress("\n") })
        }
    }
}

enum class KeyboardPanel {
    None, AI, RizzModes, Reply, Emoji, Translate, Tools
}

@Composable
fun SmartBar(
    onActionClick: (KeyboardPanel) -> Unit,
    activePanel: KeyboardPanel,
    backgroundColor: Color,
    accentColor: Color,
    textColor: Color
) {
    val items = listOf(
        SmartBarItem(Icons.Default.AutoAwesome, "AI", KeyboardPanel.AI),
        SmartBarItem(Icons.Default.Star, "Rizz", KeyboardPanel.RizzModes),
        SmartBarItem(Icons.Default.ChatBubbleOutline, "Reply", KeyboardPanel.Reply),
        SmartBarItem(Icons.Default.SentimentVerySatisfied, "Funny", KeyboardPanel.Emoji),
        SmartBarItem(Icons.Default.FavoriteBorder, "Flirt", KeyboardPanel.RizzModes),
        SmartBarItem(Icons.Default.Translate, "Translate", KeyboardPanel.Translate),
        SmartBarItem(Icons.Default.Edit, "Rewrite", KeyboardPanel.Tools),
        SmartBarItem(Icons.Default.ContentPaste, "Clipboard", KeyboardPanel.Tools),
        SmartBarItem(Icons.Default.Mic, "Voice", KeyboardPanel.Tools),
        SmartBarItem(Icons.Default.Settings, "Settings", KeyboardPanel.Tools)
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(backgroundColor)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items) { item ->
            val isActive = activePanel == item.panel
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isActive) accentColor.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable { onActionClick(item.panel) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = if (isActive) accentColor else textColor.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = item.label,
                        color = if (isActive) accentColor else textColor.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

data class SmartBarItem(val icon: ImageVector, val label: String, val panel: KeyboardPanel)

@Composable
fun RizzModeSelector(
    onModeSelected: (String) -> Unit,
    textColor: Color,
    accentColor: Color
) {
    val styles = listOf("Gentle", "Smooth", "Confident", "Funny", "Romantic", "Intelligent", "Savage", "Gen Z", "Friendly")
    
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(styles) { style ->
            Surface(
                modifier = Modifier.clickable { onModeSelected(style) },
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF2C2C2C),
                border = null
            ) {
                Text(
                    text = style,
                    color = textColor,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
fun AiReplyPanel(
    isGenerating: Boolean,
    generatedReply: String?,
    onInsert: () -> Unit,
    onClear: () -> Unit,
    backgroundColor: Color,
    textColor: Color,
    accentColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (isGenerating) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = accentColor, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Generating rizz...", color = textColor, fontSize = 14.sp)
                }
            } else if (generatedReply != null) {
                Text(
                    text = generatedReply,
                    color = textColor,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onClear) {
                        Text("Dismiss", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onInsert,
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        Text("Insert", color = Color.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun KeyboardRow(
    keys: List<String>,
    isShiftEnabled: Boolean,
    onKeyPress: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyColor: Color,
    textColor: Color
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (key in keys) {
            val displayKey = if (isShiftEnabled) key.uppercase() else key
            KeyButton(
                text = displayKey, 
                modifier = Modifier.weight(1f),
                containerColor = keyColor,
                textColor = textColor
            ) {
                onKeyPress(displayKey)
            }
        }
    }
}

@Composable
fun KeyButton(
    text: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    containerColor: Color = Color(0xFF2B2D30),
    textColor: Color = Color(0xFFE2E2E2),
    iconColor: Color = Color(0xFFE2E2E2),
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(52.dp) // Premium height, similar to Gboard
            .shadow(1.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (text != null) {
            Text(text = text, color = textColor, fontSize = 22.sp, fontWeight = FontWeight.Normal)
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
        }
    }
}
