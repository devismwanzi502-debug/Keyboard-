package com.example.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.SettingsRepository
import com.example.repository.RizzRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class KeyboardMode {
    ALPHA, NUMERIC, SYMBOL
}

enum class ShiftState {
    OFF, ON, CAPS_LOCK
}

enum class KeyboardPanel {
    None, AI, RizzModes, Emoji, Clipboard, Settings
}

enum class TypeTarget {
    APP, AI_PROMPT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardComposeView(
    repository: RizzRepository,
    settingsRepository: SettingsRepository,
    onKeyPress: (String) -> Unit,
    onBackspace: () -> Unit,
    onInsertText: (String) -> Unit,
    onGetContextText: () -> CharSequence = { "" },
    onSwitchKeyboard: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var keyboardMode by remember { mutableStateOf(KeyboardMode.ALPHA) }
    var shiftState by remember { mutableStateOf(ShiftState.OFF) }
    
    var generatedReply by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var activePanel by remember { mutableStateOf(KeyboardPanel.None) }
    var customPromptInput by remember { mutableStateOf("") }
    var typeTarget by remember { mutableStateOf(TypeTarget.APP) }
    
    // Clipboard history state
    val clipList = remember { mutableStateListOf<String>() }
    
    val recentMessages by repository.recentMessages.collectAsState(initial = emptyList())
    val autoTypeEnabled by settingsRepository.autoTypeFlow.collectAsState(initial = false)
    
    // Theme Palette
    val keyboardBackground = Color(0xFF0D0E15)
    val keyBackground = Color(0xFF1B1D2A)
    val actionKeyBackground = Color(0xFF272A3D)
    val textColor = Color(0xFFF0F2FA)
    val primaryAccent = Color(0xFF9D7BFF)
    val secondaryAccent = Color(0xFF00E5FF)
    val panelBackground = Color(0xFF141622)

    val isShifted = shiftState != ShiftState.OFF
    
    // Unified Key Processing Logic
    val handleKeyInput: (String) -> Unit = { char ->
        if (activePanel == KeyboardPanel.AI && typeTarget == TypeTarget.AI_PROMPT) {
            customPromptInput += char
        } else {
            onKeyPress(char)
        }
    }

    val handleBackspaceInput: () -> Unit = {
        if (activePanel == KeyboardPanel.AI && typeTarget == TypeTarget.AI_PROMPT) {
            if (customPromptInput.isNotEmpty()) {
                customPromptInput = customPromptInput.dropLast(1)
            }
        } else {
            onBackspace()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(keyboardBackground)
            .padding(bottom = 6.dp, top = 2.dp)
    ) {
        // Smart Bar Header
        SmartBar(
            onActionClick = { panel ->
                activePanel = if (activePanel == panel) KeyboardPanel.None else panel
                if (activePanel == KeyboardPanel.AI) {
                    typeTarget = TypeTarget.AI_PROMPT
                } else {
                    typeTarget = TypeTarget.APP
                }
            },
            activePanel = activePanel,
            backgroundColor = keyboardBackground,
            accentColor = primaryAccent,
            textColor = textColor
        )

        // Dynamic AI Panels (Rizz, Reply, Custom Prompt, Emoji Grid, Clipboard)
        AnimatedVisibility(
            visible = activePanel != KeyboardPanel.None || generatedReply != null || isGenerating,
            enter = expandVertically(animationSpec = tween(250)) + fadeIn(tween(250)),
            exit = shrinkVertically(animationSpec = tween(250)) + fadeOut(tween(250))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(panelBackground)
                    .padding(vertical = 6.dp, horizontal = 8.dp)
            ) {
                if (activePanel == KeyboardPanel.RizzModes) {
                    RizzModeSelector(
                        onModeSelected = { style ->
                            coroutineScope.launch {
                                isGenerating = true
                                generatedReply = null
                                activePanel = KeyboardPanel.None
                                try {
                                    val textBeforeCursor = onGetContextText().toString()
                                    val latestMessage = if (textBeforeCursor.isNotBlank()) {
                                        textBeforeCursor
                                    } else {
                                        recentMessages.firstOrNull { it.isIncoming }?.text ?: "Hey, what's up?"
                                    }
                                    
                                    val memoryEnabled = settingsRepository.conversationMemoryFlow.first()
                                    
                                    if (memoryEnabled && textBeforeCursor.isNotBlank()) {
                                        repository.insertMessage(latestMessage, isIncoming = true)
                                    }
                                    
                                    val reply = repository.generateReply(
                                        message = latestMessage,
                                        style = style,
                                        contextMessages = recentMessages,
                                        useMemory = memoryEnabled
                                    )
                                    
                                    if (memoryEnabled && !reply.startsWith("Error generating reply:")) {
                                        repository.insertMessage(reply, isIncoming = false)
                                    }
                                    
                                    if (autoTypeEnabled && !reply.startsWith("Error generating reply:")) {
                                        onInsertText(reply)
                                        generatedReply = null
                                    } else {
                                        generatedReply = reply
                                    }
                                } catch (e: Exception) {
                                    generatedReply = "Rizz AI: ${e.localizedMessage}"
                                } finally {
                                    isGenerating = false
                                }
                            }
                        },
                        textColor = textColor,
                        accentColor = primaryAccent
                    )
                }

                if (activePanel == KeyboardPanel.AI) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "✨ Ask Rizz AI Anything",
                                color = primaryAccent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(
                                    selected = typeTarget == TypeTarget.AI_PROMPT,
                                    onClick = { typeTarget = TypeTarget.AI_PROMPT },
                                    label = { Text("Type to AI Box", fontSize = 11.sp) }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                FilterChip(
                                    selected = typeTarget == TypeTarget.APP,
                                    onClick = { typeTarget = TypeTarget.APP },
                                    label = { Text("Type to App", fontSize = 11.sp) }
                                )
                            }
                        }

                        // Display Prompt Preview Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(actionKeyBackground)
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (customPromptInput.isEmpty()) "Tap keys below to type question here..." else customPromptInput,
                                    color = if (customPromptInput.isEmpty()) Color.Gray else textColor,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                if (customPromptInput.isNotEmpty()) {
                                    IconButton(
                                        onClick = { customPromptInput = "" },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                                    }
                                }
                            }
                        }

                        // Quick Question Preset Chips
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val presets = listOf(
                                "Give me a smooth opening line for...",
                                "Draft a witty comeback to...",
                                "Translate my text into Spanish",
                                "Make my last sentence sound professional",
                                "Write a romantic goodnight text"
                            )
                            items(presets) { preset ->
                                SuggestionChip(
                                    onClick = { customPromptInput = preset },
                                    label = { Text(preset, fontSize = 11.sp, color = textColor) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    if (customPromptInput.isNotBlank()) {
                                        val prompt = customPromptInput
                                        customPromptInput = ""
                                        activePanel = KeyboardPanel.None
                                        typeTarget = TypeTarget.APP
                                        coroutineScope.launch {
                                            isGenerating = true
                                            generatedReply = null
                                            try {
                                                val memoryEnabled = settingsRepository.conversationMemoryFlow.first()
                                                if (memoryEnabled) {
                                                    repository.insertMessage(prompt, isIncoming = true)
                                                }
                                                
                                                val textBeforeCursor = onGetContextText().toString()
                                                val contextMessagesList = if (memoryEnabled) recentMessages else emptyList()
                                                val finalMessage = if (textBeforeCursor.isNotBlank()) {
                                                    "User text: $textBeforeCursor\nPrompt: $prompt"
                                                } else {
                                                    prompt
                                                }

                                                val reply = repository.generateReply(
                                                    message = finalMessage,
                                                    style = "Creative",
                                                    contextMessages = contextMessagesList,
                                                    useMemory = memoryEnabled
                                                )
                                                
                                                if (memoryEnabled && !reply.startsWith("Error generating reply:")) {
                                                    repository.insertMessage(reply, isIncoming = false)
                                                }
                                                
                                                if (autoTypeEnabled && !reply.startsWith("Error generating reply:")) {
                                                    onInsertText(reply)
                                                    generatedReply = null
                                                } else {
                                                    generatedReply = reply
                                                }
                                            } catch (e: Exception) {
                                                generatedReply = "Error: ${e.localizedMessage}"
                                            } finally {
                                                isGenerating = false
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = primaryAccent),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ask Rizz AI", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (activePanel == KeyboardPanel.Emoji) {
                    GboardEmojiPanel(
                        onEmojiClick = { emoji ->
                            handleKeyInput(emoji)
                        },
                        textColor = textColor,
                        accentColor = primaryAccent
                    )
                }

                if (activePanel == KeyboardPanel.Clipboard) {
                    ClipboardManagerPanel(
                        context = context,
                        clipList = clipList,
                        onInsertClip = { text ->
                            onInsertText(text)
                            activePanel = KeyboardPanel.None
                        },
                        onSendToAi = { text ->
                            customPromptInput = "Reply to: $text"
                            activePanel = KeyboardPanel.AI
                            typeTarget = TypeTarget.AI_PROMPT
                        },
                        textColor = textColor,
                        accentColor = secondaryAccent
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
                        accentColor = secondaryAccent
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        
        // Dynamic Keypad depending on mode (ALPHA, NUMERIC, SYMBOL)
        when (keyboardMode) {
            KeyboardMode.ALPHA -> {
                val row1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
                val row2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
                val row3 = listOf("z", "x", "c", "v", "b", "n", "m")

                KeyboardRow(
                    keys = row1,
                    isShifted = isShifted,
                    onKeyPress = { key ->
                        handleKeyInput(key)
                        if (shiftState == ShiftState.ON) shiftState = ShiftState.OFF
                    },
                    keyColor = keyBackground,
                    textColor = textColor
                )
                
                KeyboardRow(
                    keys = row2,
                    isShifted = isShifted,
                    onKeyPress = { key ->
                        handleKeyInput(key)
                        if (shiftState == ShiftState.ON) shiftState = ShiftState.OFF
                    },
                    modifier = Modifier.padding(horizontal = 14.dp),
                    keyColor = keyBackground,
                    textColor = textColor
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    KeyButton(
                        icon = if (shiftState == ShiftState.CAPS_LOCK) Icons.Default.Lock else Icons.Default.KeyboardArrowUp,
                        modifier = Modifier.weight(1.3f),
                        containerColor = if (shiftState != ShiftState.OFF) primaryAccent else actionKeyBackground,
                        iconColor = if (shiftState != ShiftState.OFF) Color.Black else textColor,
                        onClick = {
                            shiftState = when (shiftState) {
                                ShiftState.OFF -> ShiftState.ON
                                ShiftState.ON -> ShiftState.CAPS_LOCK
                                ShiftState.CAPS_LOCK -> ShiftState.OFF
                            }
                        }
                    )
                    
                    KeyboardRow(
                        keys = row3,
                        isShifted = isShifted,
                        onKeyPress = { key ->
                            handleKeyInput(key)
                            if (shiftState == ShiftState.ON) shiftState = ShiftState.OFF
                        },
                        modifier = Modifier.weight(7f),
                        keyColor = keyBackground,
                        textColor = textColor
                    )
                    
                    KeyButton(
                        icon = Icons.AutoMirrored.Filled.Backspace,
                        modifier = Modifier.weight(1.3f),
                        containerColor = actionKeyBackground,
                        iconColor = textColor,
                        onClick = handleBackspaceInput
                    )
                }
            }

            KeyboardMode.NUMERIC -> {
                val row1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
                val row2 = listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/")
                val row3 = listOf("*", "\"", "'", ":", ";", "!", "?")

                KeyboardRow(keys = row1, isShifted = false, onKeyPress = handleKeyInput, keyColor = keyBackground, textColor = textColor)
                KeyboardRow(keys = row2, isShifted = false, onKeyPress = handleKeyInput, keyColor = keyBackground, textColor = textColor)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    KeyButton(
                        text = "=<\n#",
                        modifier = Modifier.weight(1.3f),
                        containerColor = actionKeyBackground,
                        textColor = secondaryAccent,
                        onClick = { keyboardMode = KeyboardMode.SYMBOL }
                    )
                    
                    KeyboardRow(keys = row3, isShifted = false, onKeyPress = handleKeyInput, modifier = Modifier.weight(7f), keyColor = keyBackground, textColor = textColor)
                    
                    KeyButton(
                        icon = Icons.AutoMirrored.Filled.Backspace,
                        modifier = Modifier.weight(1.3f),
                        containerColor = actionKeyBackground,
                        iconColor = textColor,
                        onClick = handleBackspaceInput
                    )
                }
            }

            KeyboardMode.SYMBOL -> {
                val row1 = listOf("[", "]", "{", "}", "%", "^", "~", "`", "|", "\\")
                val row2 = listOf("<", ">", "=", "€", "£", "¥", "§", "°", "•", "±")
                val row3 = listOf("«", "»", "©", "®", "™", "¡", "¿")

                KeyboardRow(keys = row1, isShifted = false, onKeyPress = handleKeyInput, keyColor = keyBackground, textColor = textColor)
                KeyboardRow(keys = row2, isShifted = false, onKeyPress = handleKeyInput, keyColor = keyBackground, textColor = textColor)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    KeyButton(
                        text = "?123",
                        modifier = Modifier.weight(1.3f),
                        containerColor = actionKeyBackground,
                        textColor = primaryAccent,
                        onClick = { keyboardMode = KeyboardMode.NUMERIC }
                    )
                    
                    KeyboardRow(keys = row3, isShifted = false, onKeyPress = handleKeyInput, modifier = Modifier.weight(7f), keyColor = keyBackground, textColor = textColor)
                    
                    KeyButton(
                        icon = Icons.AutoMirrored.Filled.Backspace,
                        modifier = Modifier.weight(1.3f),
                        containerColor = actionKeyBackground,
                        iconColor = textColor,
                        onClick = handleBackspaceInput
                    )
                }
            }
        }

        // Bottom Row: [Mode Switcher] [Globe/Switch IME] [,] [SPACE BAR] [.] [ENTER]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            KeyButton(
                text = if (keyboardMode == KeyboardMode.ALPHA) "?123" else "ABC",
                modifier = Modifier.weight(1.4f),
                containerColor = actionKeyBackground,
                textColor = primaryAccent,
                onClick = {
                    keyboardMode = if (keyboardMode == KeyboardMode.ALPHA) KeyboardMode.NUMERIC else KeyboardMode.ALPHA
                }
            )

            if (onSwitchKeyboard != null) {
                KeyButton(
                    icon = Icons.Default.Language,
                    modifier = Modifier.weight(1f),
                    containerColor = actionKeyBackground,
                    iconColor = textColor,
                    onClick = { onSwitchKeyboard() }
                )
            }

            KeyButton(
                text = ",",
                modifier = Modifier.weight(1f),
                containerColor = actionKeyBackground,
                textColor = textColor,
                onClick = { handleKeyInput(",") }
            )

            KeyButton(
                text = "Space",
                modifier = Modifier.weight(3.8f),
                containerColor = keyBackground,
                textColor = textColor,
                onClick = { handleKeyInput(" ") }
            )

            KeyButton(
                text = ".",
                modifier = Modifier.weight(1f),
                containerColor = actionKeyBackground,
                textColor = textColor,
                onClick = { handleKeyInput(".") }
            )

            KeyButton(
                icon = Icons.AutoMirrored.Filled.KeyboardReturn,
                modifier = Modifier.weight(1.4f),
                containerColor = primaryAccent,
                iconColor = Color.Black,
                onClick = { handleKeyInput("\n") }
            )
        }
    }
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
        SmartBarItem(Icons.Default.AutoAwesome, "Ask AI", KeyboardPanel.AI),
        SmartBarItem(Icons.Default.Star, "Rizz Modes", KeyboardPanel.RizzModes),
        SmartBarItem(Icons.Default.SentimentVerySatisfied, "Emojis", KeyboardPanel.Emoji),
        SmartBarItem(Icons.Default.ContentPaste, "Clipboard", KeyboardPanel.Clipboard)
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(backgroundColor)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(items) { item ->
            val isActive = activePanel == item.panel
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isActive) accentColor.copy(alpha = 0.25f) else Color(0xFF1B1D2A))
                    .clickable { onActionClick(item.panel) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = if (isActive) accentColor else textColor.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = item.label,
                        color = if (isActive) accentColor else textColor.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
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
    val styles = listOf("Smooth", "Flirt", "Confident", "Funny", "Romantic", "Intelligent", "Savage", "Gen Z", "Comeback", "Apology")
    
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(styles) { style ->
            Surface(
                modifier = Modifier.clickable { onModeSelected(style) },
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF272A3D)
            ) {
                Text(
                    text = "✨ $style",
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

// Gboard-style Categorized Emoji Window
@Composable
fun GboardEmojiPanel(
    onEmojiClick: (String) -> Unit,
    textColor: Color,
    accentColor: Color
) {
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }

    val categories = listOf(
        "Smileys" to listOf("😀", "😂", "😍", "🥳", "😎", "🥺", "🤪", "💀", "💩", "🤩", "🤯", "🤫", "😴", "😇", "😷", "😈", "🤡", "👻", "👽", "🤖", "🙈", "🙉", "🙊"),
        "Gestures" to listOf("👍", "👎", "👏", "🙌", "🤝", "🤌", "👊", "✌️", "🤞", "🤟", "💪", "🫡", "🙏", "👋", "✍️", "🖐️", "💅", "👈", "👉", "👆", "👇", "🖕"),
        "Hearts & Vibe" to listOf("❤️", "💔", "💖", "🔥", "✨", "💯", "⚡", "🌟", "💜", "💙", "💚", "💛", "🧡", "🤍", "🤎", "🖤", "💘", "💝", "🎉", "🎊", "💋"),
        "Animals" to listOf("🐶", "🐱", "🦊", "🦁", "🦄", "🐼", "🐒", "🦅", "🐙", "🐸", "🐝", "🦋", "🐍", "🐢", "🐬", "🐋", "🐅", "🦩", "🦥", "🦉"),
        "Food" to listOf("🍕", "🍔", "🍟", "🌮", "🍣", "🍦", "🍩", "☕", "🧋", "🍿", "🍹", "🍎", "🥑", "🥐", "🧀", "🍫", "🧃", "🍺", "🥂", "🎂"),
        "Activities" to listOf("⚽", "🏀", "🏈", "🎾", "🥊", "🎯", "🎮", "🎲", "🎨", "🎬", "🎤", "🎧", "🎷", "🎸", "⛷️", "🏄", "🚴", "🧩", "♟️"),
        "Travel" to listOf("🚗", "🚀", "✈️", "⛵", "🗽", "🎡", "🌋", "🏖️", "🌅", "🏙️", "🛸", "🚲", "🏍️", "🚂", "🏥", "⛺", "🌌", "🌍", "🗺️"),
        "Objects" to listOf("💡", "📱", "💻", "💵", "💎", "🔑", "🔒", "💣", "🔮", "🎈", "🎁", "🕯️", "📦", "✉️", "📌", "🖊️", "📷", "🔔", "⏰")
    )

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Category Tabs Header
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(categories.indices.toList()) { index ->
                val categoryName = categories[index].first
                val isSelected = selectedCategoryIndex == index
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) accentColor else Color(0xFF272A3D))
                        .clickable { selectedCategoryIndex = index }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = categoryName,
                        color = if (isSelected) Color.Black else textColor,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Emoji Grid
        val currentEmojis = categories[selectedCategoryIndex].second
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(currentEmojis) { emoji ->
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onEmojiClick(emoji) }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emoji, fontSize = 22.sp)
                }
            }
        }
    }
}

// Clipboard Manager Panel
@Composable
fun ClipboardManagerPanel(
    context: Context,
    clipList: MutableList<String>,
    onInsertClip: (String) -> Unit,
    onSendToAi: (String) -> Unit,
    textColor: Color,
    accentColor: Color
) {
    LaunchedEffect(Unit) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.hasPrimaryClip()) {
                val clipData = clipboard.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val text = clipData.getItemAt(0).text?.toString()
                    if (!text.isNullOrBlank() && !clipList.contains(text)) {
                        clipList.add(0, text)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📋 Clipboard Manager",
                color = accentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            if (clipList.isNotEmpty()) {
                TextButton(onClick = { clipList.clear() }) {
                    Text("Clear Clips", color = Color.Gray, fontSize = 11.sp)
                }
            }
        }

        if (clipList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No copied text found. Copy text anywhere to see it here!",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(clipList) { clip ->
                    Card(
                        modifier = Modifier
                            .width(200.dp)
                            .height(80.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF272A3D)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = clip,
                                color = textColor,
                                fontSize = 12.sp,
                                maxLines = 2
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { onSendToAi(clip) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Ask AI", color = accentColor, fontSize = 11.sp)
                                }
                                Button(
                                    onClick = { onInsertClip(clip) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                                ) {
                                    Text("Paste", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
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
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (isGenerating) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = accentColor, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Rizz AI is generating response...", color = textColor, fontSize = 13.sp)
                }
            } else if (generatedReply != null) {
                Text(
                    text = generatedReply,
                    color = textColor,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onClear) {
                        Text("Dismiss", color = Color.Gray, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = onInsert,
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("Insert Text", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun KeyboardRow(
    keys: List<String>,
    isShifted: Boolean,
    onKeyPress: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyColor: Color,
    textColor: Color
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        for (key in keys) {
            val displayKey = if (isShifted) key.uppercase() else key
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
    containerColor: Color = Color(0xFF1B1D2A),
    textColor: Color = Color(0xFFF0F2FA),
    iconColor: Color = Color(0xFFF0F2FA),
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 80),
        label = "KeyScale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.65f else 1f,
        animationSpec = tween(durationMillis = 80),
        label = "KeyAlpha"
    )

    Box(
        modifier = modifier
            .height(50.dp)
            .shadow(1.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor.copy(alpha = alpha))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
            contentAlignment = Alignment.Center
        ) {
            if (text != null) {
                Text(
                    text = text,
                    color = textColor,
                    fontSize = if (text.length > 2) 13.sp else 20.sp,
                    fontWeight = if (text.length > 2) FontWeight.SemiBold else FontWeight.Medium
                )
            } else if (icon != null) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
            }
        }
    }
}
