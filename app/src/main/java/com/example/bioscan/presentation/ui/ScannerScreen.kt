package com.example.bioscan.presentation.ui

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bioscan.core.camera.CameraManager
import com.example.bioscan.core.common.TimeUtils
import com.example.bioscan.core.recognition.IdentityDecision
import com.example.bioscan.presentation.viewmodel.KioskMainViewModel
import kotlinx.coroutines.delay

@Composable
fun ScannerScreen(
    viewModel: KioskMainViewModel,
    onOpenAdmin: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val analysisResult by viewModel.analysisResult.collectAsStateWithLifecycle()
    val confirmationState by viewModel.confirmationState.collectAsStateWithLifecycle()
    val settings by viewModel.kioskSettings.collectAsStateWithLifecycle()

    val cameraManager = remember { CameraManager(context) }

    var currentTimeText by remember { mutableStateOf(TimeUtils.formatHourMinute(System.currentTimeMillis())) }
    var currentDateText by remember { mutableStateOf(TimeUtils.formatDateOnly(System.currentTimeMillis())) }

    // Admin trigger gesture counters
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTimeText = TimeUtils.formatHourMinute(System.currentTimeMillis())
            currentDateText = TimeUtils.formatDateOnly(System.currentTimeMillis())
            delay(1000L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Camera Preview
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                cameraManager.startCamera(
                    lifecycleOwner = lifecycleOwner,
                    surfaceProvider = previewView.surfaceProvider,
                    onFrameAvailable = { bitmap, rotation ->
                        viewModel.processFrame(bitmap, rotation)
                    }
                )
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        DisposableEffect(lifecycleOwner) {
            onDispose {
                cameraManager.stopCamera()
            }
        }

        // 2. Camera Overlay Reticle Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            val boxW = canvasW * 0.68f
            val boxH = canvasH * 0.48f
            val left = (canvasW - boxW) / 2f
            val top = (canvasH - boxH) / 2.2f

            val strokeWidth = 6.dp.toPx()
            val cornerLen = 40.dp.toPx()

            val reticleColor = when (analysisResult?.candidateMatch?.decision) {
                IdentityDecision.MATCH -> Color(0xFF10B981) // Emerald Green
                IdentityDecision.LIVENESS_FAILED -> Color(0xFFF59E0B) // Amber
                IdentityDecision.LOW_QUALITY -> Color(0xFFEF4444) // Red
                else -> Color(0xFF00E5FF) // Cyan
            }

            val dimColor = Color.Black.copy(alpha = 0.50f)

            // 1. Top Dim Rect
            drawRect(
                color = dimColor,
                topLeft = Offset(0f, 0f),
                size = Size(canvasW, top)
            )
            // 2. Bottom Dim Rect
            drawRect(
                color = dimColor,
                topLeft = Offset(0f, top + boxH),
                size = Size(canvasW, (canvasH - (top + boxH)).coerceAtLeast(0f))
            )
            // 3. Left Dim Rect
            drawRect(
                color = dimColor,
                topLeft = Offset(0f, top),
                size = Size(left, boxH)
            )
            // 4. Right Dim Rect
            drawRect(
                color = dimColor,
                topLeft = Offset(left + boxW, top),
                size = Size((canvasW - (left + boxW)).coerceAtLeast(0f), boxH)
            )

            // Draw Corner Brackets
            // Top Left
            drawLine(reticleColor, Offset(left, top), Offset(left + cornerLen, top), strokeWidth)
            drawLine(reticleColor, Offset(left, top), Offset(left, top + cornerLen), strokeWidth)

            // Top Right
            drawLine(reticleColor, Offset(left + boxW, top), Offset(left + boxW - cornerLen, top), strokeWidth)
            drawLine(reticleColor, Offset(left + boxW, top), Offset(left + boxW, top + cornerLen), strokeWidth)

            // Bottom Left
            drawLine(reticleColor, Offset(left, top + boxH), Offset(left + cornerLen, top + boxH), strokeWidth)
            drawLine(reticleColor, Offset(left, top + boxH), Offset(left, top + boxH - cornerLen), strokeWidth)

            // Bottom Right
            drawLine(reticleColor, Offset(left + boxW, top + boxH), Offset(left + boxW - cornerLen, top + boxH), strokeWidth)
            drawLine(reticleColor, Offset(left + boxW, top + boxH), Offset(left + boxW, top + boxH - cornerLen), strokeWidth)
        }

        // 3. Top Kiosk Status Bar
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(16.dp),
            color = Color(0xCC0F172A),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "BioScan Kiosk",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$currentDateText  •  $currentTimeText",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // System Status Badges
                    StatusBadge(icon = Icons.Default.PhotoCamera, label = "Ready", color = Color(0xFF10B981))
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusBadge(icon = Icons.Default.CloudDone, label = "Offline Ready", color = Color(0xFF38BDF8))
                    Spacer(modifier = Modifier.width(12.dp))

                    // Prominent Admin Portal Button
                    Surface(
                        onClick = { onOpenAdmin() },
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f)),
                        modifier = Modifier.testTag("admin_trigger_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = "Admin Area Access",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Admin Settings",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "PIN: 123456",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Scanning Guidance & Active Liveness Prompt Banner
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
        ) {
            val livenessState = analysisResult?.liveness
            val qual = analysisResult?.quality

            val message = when {
                livenessState != null && !livenessState.isCompleted -> livenessState.instructionMessage
                qual?.rejectionReason != null -> qual.rejectionReason
                analysisResult?.candidateMatch?.decision == IdentityDecision.UNKNOWN -> "Unknown face. Please see Admin."
                analysisResult?.candidateMatch?.decision == IdentityDecision.AMBIGUOUS -> "Position face clearly..."
                else -> "Position face inside frame to scan"
            }

            Surface(
                color = Color(0xDD0F172A),
                shape = RoundedCornerShape(30.dp),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = message,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 5. Successful Attendance Splash Card (3-second popover)
        AnimatedVisibility(
            visible = confirmationState.isVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Card(
                modifier = Modifier
                    .padding(24.dp)
                    .width(340.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color(0xFF059669), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = confirmationState.employeeName,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = confirmationState.department,
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        color = if (confirmationState.eventType == "CLOCK_IN") Color(0xFF065F46) else Color(0xFF991B1B),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (confirmationState.eventType == "CLOCK_IN") "CLOCKED IN" else "CLOCKED OUT",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = confirmationState.formattedTime,
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
