package com.example.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.SettingsRepository
import com.example.repository.RizzRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun KeyboardScreen(
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
    
    val recentMessages by repository.recentMessages.collectAsState(initial = emptyList())
    val styles = listOf("Gentle", "Funny", "Smooth", "Savage", "Romantic", "Gen Z", "Confident")
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E))
            .padding(bottom = 8.dp)
    ) {
        // AI Actions Toolbar
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(styles) { style ->
                FilterChip(
                    selected = false,
                    onClick = {
                        coroutineScope.launch {
                            isGenerating = true
                            generatedReply = null
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
                            
                            val autoType = settingsRepository.autoTypeFlow.first()
                            if (autoType) {
                                onInsertText(reply)
                            }
                        }
                    },
                    label = { Text(style) },
                    leadingIcon = {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color(0xFF2C2C2C),
                        labelColor = Color.White,
                        iconColor = Color(0xFFBB86FC)
                    )
                )
            }
        }
        
        // Generated Reply Box
        if (isGenerating || generatedReply != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF333333))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFFBB86FC))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Generating rizz...", color = Color.White)
                    } else {
                        Text(
                            text = generatedReply ?: "",
                            modifier = Modifier.weight(1f),
                            color = Color.White
                        )
                        IconButton(onClick = { onInsertText(generatedReply ?: "") }) {
                            Icon(Icons.Default.Send, contentDescription = "Insert", tint = Color(0xFFBB86FC))
                        }
                        IconButton(onClick = { generatedReply = null }) {
                            Icon(Icons.Default.Backspace, contentDescription = "Clear", tint = Color.Gray)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        
        // QWERTY Keys
        val row1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
        val row2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
        val row3 = listOf("z", "x", "c", "v", "b", "n", "m")
        
        KeyboardRow(keys = row1, isShiftEnabled = isShiftEnabled, onKeyPress = onKeyPress)
        KeyboardRow(keys = row2, isShiftEnabled = isShiftEnabled, onKeyPress = onKeyPress, modifier = Modifier.padding(horizontal = 16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeyButton(
                icon = Icons.Default.KeyboardArrowUp,
                modifier = Modifier.weight(1.5f),
                containerColor = if (isShiftEnabled) Color(0xFFBB86FC) else Color(0xFF333333),
                onClick = { isShiftEnabled = !isShiftEnabled }
            )
            KeyboardRow(keys = row3, isShiftEnabled = isShiftEnabled, onKeyPress = onKeyPress, modifier = Modifier.weight(7f))
            KeyButton(
                icon = Icons.Default.Backspace,
                modifier = Modifier.weight(1.5f),
                onClick = onBackspace
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            KeyButton(text = "?123", modifier = Modifier.weight(1.5f), onClick = {})
            KeyButton(text = ",", modifier = Modifier.weight(1f), onClick = { onKeyPress(",") })
            KeyButton(text = "Space", modifier = Modifier.weight(4f), onClick = { onKeyPress(" ") })
            KeyButton(text = ".", modifier = Modifier.weight(1f), onClick = { onKeyPress(".") })
            KeyButton(icon = Icons.Default.KeyboardReturn, modifier = Modifier.weight(1.5f), onClick = { onKeyPress("\n") })
        }
    }
}

@Composable
fun KeyboardRow(
    keys: List<String>,
    isShiftEnabled: Boolean,
    onKeyPress: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (key in keys) {
            val displayKey = if (isShiftEnabled) key.uppercase() else key
            KeyButton(text = displayKey, modifier = Modifier.weight(1f)) {
                onKeyPress(displayKey)
            }
        }
    }
}

@Composable
fun KeyButton(
    text: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    modifier: Modifier = Modifier,
    containerColor: Color = Color(0xFF333333),
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (text != null) {
            Text(text = text, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Normal)
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White)
        }
    }
}
