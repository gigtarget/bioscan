package com.example.bioscan.presentation.ui

import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bioscan.core.camera.CameraManager
import com.example.bioscan.core.common.TimeUtils
import com.example.bioscan.core.recognition.IdentityDecision
import com.example.bioscan.presentation.viewmodel.KioskMainViewModel
import kotlinx.coroutines.delay

private val KioskNavy = Color(0xE6121B2E)
private val KioskCyan = Color(0xFF22D3EE)
private val KioskGreen = Color(0xFF34D399)
private val KioskMuted = Color(0xFF94A3B8)

@Composable
fun ScannerScreen(
    viewModel: KioskMainViewModel,
    onOpenAdmin: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val analysisResult by viewModel.analysisResult.collectAsStateWithLifecycle()
    val confirmationState by viewModel.confirmationState.collectAsStateWithLifecycle()

    val cameraManager = remember(context.applicationContext) {
        CameraManager(context.applicationContext)
    }

    var currentTimeText by remember {
        mutableStateOf(TimeUtils.formatHourMinute(System.currentTimeMillis()))
    }
    var currentDateText by remember {
        mutableStateOf(TimeUtils.formatDateOnly(System.currentTimeMillis()))
    }

    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            currentTimeText = TimeUtils.formatHourMinute(now)
            currentDateText = TimeUtils.formatDateOnly(now)
            delay(1_000L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    cameraManager.startCamera(
                        lifecycleOwner = lifecycleOwner,
                        surfaceProvider = surfaceProvider,
                        onFrameAvailable = { bitmap, rotation ->
                            viewModel.processFrame(bitmap, rotation)
                        }
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        DisposableEffect(cameraManager, lifecycleOwner) {
            onDispose { cameraManager.release() }
        }

        FaceReticleOverlay(
            decision = analysisResult?.candidateMatch?.decision
        )

        KioskHeader(
            dateText = currentDateText,
            timeText = currentTimeText,
            onOpenAdmin = onOpenAdmin
        )

        val livenessState = analysisResult?.liveness
        val quality = analysisResult?.quality
        val guidanceMessage = when {
            livenessState != null && !livenessState.isCompleted -> livenessState.instructionMessage
            quality?.rejectionReason != null -> quality.rejectionReason
            analysisResult?.candidateMatch?.decision == IdentityDecision.MULTIPLE_FACES -> "Only one person should be inside the frame"
            analysisResult?.candidateMatch?.decision == IdentityDecision.UNKNOWN -> "Face not recognised. Contact an administrator."
            analysisResult?.candidateMatch?.decision == IdentityDecision.AMBIGUOUS -> "Move closer and look directly at the camera"
            else -> "Position your face inside the frame"
        }

        GuidanceBanner(
            message = guidanceMessage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 18.dp)
        )

        AttendanceConfirmation(
            state = confirmationState,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun KioskHeader(
    dateText: String,
    timeText: String,
    onOpenAdmin: () -> Unit
) {
    Surface(
        modifier = Modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        color = KioskNavy,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.widthIn(max = 260.dp)) {
                    Text(
                        text = "BioScan Kiosk",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$dateText  •  $timeText",
                        color = KioskMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onOpenAdmin,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("admin_trigger_btn")
                ) {
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, KioskCyan.copy(alpha = 0.55f))
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = "Open administrator area",
                                tint = KioskCyan,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactStatusPill(
                    label = "Camera ready",
                    color = KioskGreen
                )
                CompactStatusPill(
                    label = "Offline",
                    color = KioskCyan
                )
            }
        }
    }
}

@Composable
private fun CompactStatusPill(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.13f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = label,
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FaceReticleOverlay(decision: IdentityDecision?) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasW = size.width
        val canvasH = size.height
        val boxW = canvasW * 0.76f
        val boxH = (canvasH * 0.42f).coerceAtMost(boxW * 1.35f)
        val left = (canvasW - boxW) / 2f
        val top = canvasH * 0.27f

        val strokeWidth = 4.dp.toPx()
        val cornerLength = 34.dp.toPx()
        val reticleColor = when (decision) {
            IdentityDecision.MATCH -> KioskGreen
            IdentityDecision.LIVENESS_FAILED -> Color(0xFFFBBF24)
            IdentityDecision.LOW_QUALITY -> Color(0xFFFB7185)
            else -> KioskCyan
        }

        val dimColor = Color.Black.copy(alpha = 0.42f)
        drawRect(dimColor, Offset.Zero, Size(canvasW, top))
        drawRect(
            dimColor,
            Offset(0f, top + boxH),
            Size(canvasW, (canvasH - top - boxH).coerceAtLeast(0f))
        )
        drawRect(dimColor, Offset(0f, top), Size(left, boxH))
        drawRect(
            dimColor,
            Offset(left + boxW, top),
            Size((canvasW - left - boxW).coerceAtLeast(0f), boxH)
        )

        drawLine(reticleColor, Offset(left, top), Offset(left + cornerLength, top), strokeWidth)
        drawLine(reticleColor, Offset(left, top), Offset(left, top + cornerLength), strokeWidth)
        drawLine(reticleColor, Offset(left + boxW, top), Offset(left + boxW - cornerLength, top), strokeWidth)
        drawLine(reticleColor, Offset(left + boxW, top), Offset(left + boxW, top + cornerLength), strokeWidth)
        drawLine(reticleColor, Offset(left, top + boxH), Offset(left + cornerLength, top + boxH), strokeWidth)
        drawLine(reticleColor, Offset(left, top + boxH), Offset(left, top + boxH - cornerLength), strokeWidth)
        drawLine(reticleColor, Offset(left + boxW, top + boxH), Offset(left + boxW - cornerLength, top + boxH), strokeWidth)
        drawLine(reticleColor, Offset(left + boxW, top + boxH), Offset(left + boxW, top + boxH - cornerLength), strokeWidth)
    }
}

@Composable
private fun GuidanceBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = KioskNavy,
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 13.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = KioskCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Face attendance",
                    color = KioskCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = message,
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AttendanceConfirmation(
    state: com.example.bioscan.presentation.viewmodel.ConfirmationCardState,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state.isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .widthIn(max = 360.dp),
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
                        .size(68.dp)
                        .background(Color(0xFF059669), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = state.employeeName,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.department,
                    color = KioskMuted,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = if (state.eventType == "CLOCK_IN") Color(0xFF065F46) else Color(0xFF991B1B),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (state.eventType == "CLOCK_IN") "CLOCKED IN" else "CLOCKED OUT",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = state.formattedTime,
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
