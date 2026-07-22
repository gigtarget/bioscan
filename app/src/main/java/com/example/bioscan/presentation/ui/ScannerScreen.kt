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
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.bioscan.presentation.viewmodel.ConfirmationCardState
import com.example.bioscan.presentation.viewmodel.KioskMainViewModel
import kotlinx.coroutines.delay

private val KioskInk = Color(0xFF07111F)
private val KioskPanel = Color(0xED0B1628)
private val KioskPanelSoft = Color(0xE1142238)
private val KioskCyan = Color(0xFF5EE7F2)
private val KioskBlue = Color(0xFF60A5FA)
private val KioskGreen = Color(0xFF4ADE80)
private val KioskAmber = Color(0xFFFBBF24)
private val KioskRose = Color(0xFFFB7185)
private val KioskMuted = Color(0xFFA7B4C8)

private data class ScannerPresentation(
    val label: String,
    val title: String,
    val message: String,
    val accent: Color
)

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

    var timeText by remember {
        mutableStateOf(TimeUtils.formatHourMinute(System.currentTimeMillis()))
    }
    var dateText by remember {
        mutableStateOf(TimeUtils.formatDateOnly(System.currentTimeMillis()))
    }

    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            timeText = TimeUtils.formatHourMinute(now)
            dateText = TimeUtils.formatDateOnly(now)
            delay(1_000L)
        }
    }

    val decision = analysisResult?.candidateMatch?.decision
    val presentation = scannerPresentation(
        decision = decision,
        faceDetected = analysisResult?.faceDetected == true,
        qualityMessage = analysisResult?.quality?.rejectionReason
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KioskInk)
    ) {
        AndroidView(
            factory = { previewContext ->
                PreviewView(previewContext).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    cameraManager.startCamera(
                        lifecycleOwner = lifecycleOwner,
                        surfaceProvider = surfaceProvider,
                        onFrameAvailable = viewModel::processFrame
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        DisposableEffect(cameraManager, lifecycleOwner) {
            onDispose { cameraManager.release() }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color(0xE607111F),
                            0.24f to Color(0x3307111F),
                            0.56f to Color.Transparent,
                            0.74f to Color(0x4D07111F),
                            1f to Color(0xFA07111F)
                        )
                    )
                )
        )

        FaceGuide(
            decision = decision,
            faceDetected = analysisResult?.faceDetected == true
        )

        KioskHeader(
            dateText = dateText,
            timeText = timeText,
            onOpenAdmin = onOpenAdmin,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        StatusPanel(
            presentation = presentation,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp)
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
    onOpenAdmin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        color = KioskPanel,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        shadowElevation = 14.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column(modifier = Modifier.padding(end = 54.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                Brush.linearGradient(listOf(KioskCyan, KioskBlue)),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Face,
                            contentDescription = null,
                            tint = KioskInk,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(11.dp))
                    Column {
                        Text(
                            text = "BioScan Attendance",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Private on-device verification",
                            color = KioskMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(11.dp))
                Text(
                    text = "$dateText  •  $timeText",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeaderChip("FaceNet ready", KioskGreen)
                    HeaderChip("Offline-first", KioskCyan)
                }
            }

            IconButton(
                onClick = onOpenAdmin,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(44.dp)
                    .testTag("admin_trigger_btn")
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.08f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.13f))
                ) {
                    Box(
                        modifier = Modifier.size(42.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = "Open administrator area",
                            tint = Color.White,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderChip(label: String, accent: Color) {
    Surface(
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(50.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.20f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(accent, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun FaceGuide(
    decision: IdentityDecision?,
    faceDetected: Boolean
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val guideWidth = (size.width * 0.70f).coerceAtMost(320.dp.toPx())
        val guideHeight = (guideWidth * 1.24f).coerceAtMost(size.height * 0.44f)
        val left = (size.width - guideWidth) / 2f
        val top = (size.height * 0.49f - guideHeight / 2f).coerceAtLeast(size.height * 0.23f)
        val radius = 42.dp.toPx()
        val accentLength = 34.dp.toPx()
        val accentWidth = 5.dp.toPx()

        val accent = when (decision) {
            IdentityDecision.MATCH -> KioskGreen
            IdentityDecision.LOW_QUALITY, IdentityDecision.UNKNOWN -> KioskRose
            IdentityDecision.MULTIPLE_FACES -> KioskAmber
            IdentityDecision.AMBIGUOUS -> KioskBlue
            else -> if (faceDetected) KioskBlue else KioskCyan
        }

        val dim = Color.Black.copy(alpha = 0.38f)
        drawRect(dim, Offset.Zero, Size(size.width, top))
        drawRect(dim, Offset(0f, top + guideHeight), Size(size.width, size.height - top - guideHeight))
        drawRect(dim, Offset(0f, top), Size(left, guideHeight))
        drawRect(dim, Offset(left + guideWidth, top), Size(size.width - left - guideWidth, guideHeight))

        drawRoundRect(
            color = Color.White.copy(alpha = 0.28f),
            topLeft = Offset(left, top),
            size = Size(guideWidth, guideHeight),
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = 2.dp.toPx())
        )

        drawLine(accent, Offset(left, top + radius), Offset(left, top + radius + accentLength), accentWidth)
        drawLine(accent, Offset(left + radius, top), Offset(left + radius + accentLength, top), accentWidth)
        drawLine(accent, Offset(left + guideWidth - radius, top), Offset(left + guideWidth - radius - accentLength, top), accentWidth)
        drawLine(accent, Offset(left + guideWidth, top + radius), Offset(left + guideWidth, top + radius + accentLength), accentWidth)
        drawLine(accent, Offset(left, top + guideHeight - radius), Offset(left, top + guideHeight - radius - accentLength), accentWidth)
        drawLine(accent, Offset(left + radius, top + guideHeight), Offset(left + radius + accentLength, top + guideHeight), accentWidth)
        drawLine(accent, Offset(left + guideWidth - radius, top + guideHeight), Offset(left + guideWidth - radius - accentLength, top + guideHeight), accentWidth)
        drawLine(accent, Offset(left + guideWidth, top + guideHeight - radius), Offset(left + guideWidth, top + guideHeight - radius - accentLength), accentWidth)
    }
}

@Composable
private fun StatusPanel(
    presentation: ScannerPresentation,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = KioskPanel,
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        shadowElevation = 18.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            presentation.accent.copy(alpha = 0.14f),
                            RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = presentation.accent,
                        modifier = Modifier.size(23.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = presentation.label.uppercase(),
                        color = presentation.accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = presentation.title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(9.dp))
            Text(
                text = presentation.message,
                color = KioskMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = KioskPanelSoft,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        text = "First scan = clock-in  •  Latest scan = clock-out",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "No blink or head-turn required",
                        color = KioskMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private fun scannerPresentation(
    decision: IdentityDecision?,
    faceDetected: Boolean,
    qualityMessage: String?
): ScannerPresentation = when {
    decision == IdentityDecision.MATCH -> ScannerPresentation(
        "Confirmed",
        "Identity verified",
        "Saving the employee's daily attendance summary.",
        KioskGreen
    )
    decision == IdentityDecision.MULTIPLE_FACES -> ScannerPresentation(
        "Attention",
        "One person at a time",
        "Ask other people to move outside the camera frame.",
        KioskAmber
    )
    decision == IdentityDecision.LOW_QUALITY -> ScannerPresentation(
        "Image quality",
        "Improve the camera view",
        qualityMessage ?: "Move closer, face the camera, and avoid strong backlight.",
        KioskRose
    )
    decision == IdentityDecision.UNKNOWN -> ScannerPresentation(
        "Not recognised",
        "No matching employee found",
        "Look directly at the camera or contact an administrator for enrollment.",
        KioskRose
    )
    decision == IdentityDecision.AMBIGUOUS -> ScannerPresentation(
        "Verifying",
        "Hold still for a moment",
        "Multiple frames are being compared before the identity is accepted.",
        KioskBlue
    )
    faceDetected -> ScannerPresentation(
        "Analysing",
        "Face detected",
        "Keep a natural expression and remain inside the guide.",
        KioskBlue
    )
    else -> ScannerPresentation(
        "Ready",
        "Step into view",
        "Stand at a comfortable distance and look naturally toward the camera.",
        KioskCyan
    )
}

@Composable
private fun AttendanceConfirmation(
    state: ConfirmationCardState,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state.isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .padding(22.dp)
                .fillMaxWidth()
                .widthIn(max = 360.dp),
            color = Color(0xFF0A1626),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, KioskGreen.copy(alpha = 0.35f)),
            shadowElevation = 22.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(KioskGreen.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = KioskGreen,
                        modifier = Modifier.size(38.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = state.actionTitle.ifBlank { "Attendance saved" },
                    color = KioskGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.employeeName,
                    color = Color.White,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.department,
                    color = KioskMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(15.dp))
                Surface(
                    color = if (state.eventType == "CLOCK_IN") {
                        KioskGreen.copy(alpha = 0.14f)
                    } else {
                        KioskBlue.copy(alpha = 0.14f)
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = state.formattedTime,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}
