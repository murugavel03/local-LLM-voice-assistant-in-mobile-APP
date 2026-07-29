package com.muruga.localvoiceassistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

// ---- Palette: dark navy background, purple-to-blue accent gradient ----
private val BgTop = Color(0xFF0B0E1A)
private val BgBottom = Color(0xFF12162B)
private val AccentPurple = Color(0xFF8B5CF6)
private val AccentBlue = Color(0xFF3B82F6)
private val BubbleUser = Color(0xFF2A2F4A)
private val BubbleAssistant = Color(0xFF1B2036)
private val TextPrimary = Color(0xFFF2F2F7)
private val TextSecondary = Color(0xFF9CA3C4)

class MainActivity : ComponentActivity() {

    private val viewModel: VoiceAssistantViewModel by viewModels()
    private var hasMicPermission = false

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) viewModel.onMicTapped()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hasMicPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = BgTop)) {
                AssistantScreen(
                    viewModel = viewModel,
                    onMicTapped = {
                        if (hasMicPermission) {
                            viewModel.onMicTapped()
                        } else {
                            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AssistantScreen(viewModel: VoiceAssistantViewModel, onMicTapped: () -> Unit) {
    val state by viewModel.state
    val messages = viewModel.messages
    val error by viewModel.errorMessage
    val textInput by viewModel.textInput
    val serverRunning by viewModel.serverRunning
    val serverAddress by viewModel.serverAddress
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-scroll to the latest message as they stream in.
    LaunchedEffect(messages.size, messages.lastOrNull()?.text) {
        if (messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(messages.size - 1) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgBottom)))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // --- Header ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Voice Assistant",
                            color = TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        StatusPill(state = state)
                    }
                    ServerToggle(
                        running = serverRunning,
                        onToggle = { viewModel.toggleServer() }
                    )
                }
                if (serverRunning && serverAddress.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = serverAddress,
                        color = AccentBlue,
                        fontSize = 12.sp
                    )
                }
            }

            // --- Chat history ---
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        EmptyState()
                    }
                }
                itemsIndexed(messages) { _, msg ->
                    ChatBubble(msg)
                }

                if (state == AssistantState.ERROR && error.isNotBlank()) {
                    item {
                        Text(
                            text = error,
                            color = Color(0xFFFF6B6B),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // --- Text input row ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { viewModel.textInput.value = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message…", color = TextSecondary) },
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentPurple,
                        unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                        cursorColor = AccentPurple
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { viewModel.onSendText() },
                    enabled = textInput.isNotBlank() && state != AssistantState.THINKING
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (textInput.isNotBlank()) AccentPurple else TextSecondary
                    )
                }
            }

            // --- Mic control ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                MicButton(state = state, onClick = onMicTapped)
            }
        }
    }
}

@Composable
fun ServerToggle(running: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                if (running) AccentPurple.copy(alpha = 0.18f) else BubbleAssistant,
                RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = if (running) "Server ON" else "Server OFF",
            color = if (running) AccentPurple else TextSecondary,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.width(6.dp))
        Switch(
            checked = running,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = AccentPurple,
                checkedTrackColor = AccentPurple.copy(alpha = 0.4f)
            )
        )
    }
}

@Composable
fun StatusPill(state: AssistantState) {
    val (label, color) = when (state) {
        AssistantState.IDLE -> "Ready" to TextSecondary
        AssistantState.LISTENING -> "Listening…" to AccentBlue
        AssistantState.THINKING -> "Thinking…" to AccentPurple
        AssistantState.SPEAKING -> "Speaking…" to AccentPurple
        AssistantState.ERROR -> "Error" to Color(0xFFFF6B6B)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, color = TextSecondary, fontSize = 13.sp)
    }
}

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Tap the mic and say something",
            color = TextSecondary,
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Everything runs fully offline on your device",
            color = TextSecondary.copy(alpha = 0.6f),
            fontSize = 12.sp
        )
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.sender == Sender.USER
    val bubbleColor = if (isUser) BubbleUser else BubbleAssistant
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .background(bubbleColor, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = if (isUser) "You" else "Assistant",
                color = if (isUser) AccentBlue else AccentPurple,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = message.text.ifBlank { "…" },
                color = TextPrimary,
                fontSize = 15.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun MicButton(state: AssistantState, onClick: () -> Unit) {
    val busy = state == AssistantState.LISTENING || state == AssistantState.THINKING
    val infinite = rememberInfiniteTransition(label = "pulse")

    val pulseScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (state == AssistantState.LISTENING) 1.18f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micPulse"
    )

    Box(contentAlignment = Alignment.Center) {
        // Outer glow ring, only pulses while listening
        Box(
            modifier = Modifier
                .size(96.dp)
                .scale(pulseScale)
                .background(
                    Brush.radialGradient(
                        listOf(AccentPurple.copy(alpha = 0.35f), Color.Transparent)
                    ),
                    CircleShape
                )
        )

        Box(
            modifier = Modifier
                .size(72.dp)
                .background(
                    Brush.linearGradient(listOf(AccentPurple, AccentBlue)),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = onClick, enabled = !busy) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "Tap to speak",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}
