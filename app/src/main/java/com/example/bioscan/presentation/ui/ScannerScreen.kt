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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
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

private val Ink = Color(0xFF07111F)
private val Panel = Color(0xEB0B1628)
private val PanelSoft = Color(0xD9142238)
private val Cyan = Color(0xFF5EE7F2)
private val Blue = Color(0xFF60A5FA)
private val Green = Color(0xFF4ADE80)
private val Amber = Color(0xFFFBBF24)
private val Rose = Color(0xFFFB7185)
private val Muted = Color(0xFFA7B4C8)
private val Border = Color.White.copy(alpha = 0.14f)

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

    val presentation = scannerPresentation(
        decision = analysisResult?.candidateMatch?.decision,
        faceDetected = analysisResult?.faceDetected == true,
        qualityMessage = analysisResult?.quality?.rejectionReason
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
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
                            0f to Color(0xD907111F),
                            0.22f to Color(0x4D07111F),
                            0.52f to Color.Transparent,
                            0.72f to Color(0x4D07111F),
                            1f to Color(0xF207111F)
                        )
                    )
                )
        )

        FaceGuideOverlay(
            decision = analysisResult?.candidateMatch?.decision,
            faceDetected = analysisResult?.faceDetected == true
        )

        ProductionHeader(
            dateText = currentDateText,
            timeText = currentTimeText,
            onOpenAdmin = onOpenAdmin,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        ScannerStatusPanel(
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
private fun ProductionHeader(
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
        color = Panel,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Border),
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
                                brush = Brush.linearGradient(listOf(Cyan, Blue)),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Face,
                            contentDescription = null,
                            tint = Ink,
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
                            text = "Private on-device identity verification",
                            color = Muted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "$currentDateText  •  $timeText",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(label = "FaceNet ready", accent = Green)
                    StatusChip(label = "Offline-first", accent = Cyan)
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
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
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
private fun StatusChip(label: String, accent: Color) {
    Surface(
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(50.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.20f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(accent, CircleShape)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = label,
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun FaceGuideOverlay(
    decision: IdentityDecision?,
    faceDetected: Boolean
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val guideWidth = (canvasWidth * 0.70f).coerceAtMost(320.dp.toPx())
        val guideHeight = (guideWidth * 1.26f).coerceAtMost(canvasHeight * 0.45f)
        val left = (canvasWidth - guideWidth) / 2f
        val top = (canvasHeight * 0.49f - guideHeight / 2f).coerceAtLeast(canvasHeight * 0.23f)
        val cornerRadius = 42.dp.toPx()
        val strokeWidth = 2.dp.toPx()
        val accentWidth = 5.dp.toPx()
        val accentLength = 34.dp.toPx()

        val accent = when (decision) {
            IdentityDecision.MATCH -> Green
            IdentityDecision.LOW_QUALITY -> Rose
            IdentityDecision.MULTIPLE_FACES -> Amber
            IdentityDecision.UNKNOWN -> Rose
            IdentityDecision.AMBIGUOUS -> Blue
            else -> if (faceDetected) Blue else Cyan
        }

        val dim = Color.Black.copy(alpha = 0.38f)
        drawRect(dim, Offset.Zero, Size(canvasWidth, top))
        drawRect(
            dim,
            Offset(0f, top + guideHeight),
            Size(canvasWidth, (canvasHeight - top - guideHeight).coerceAtLeast(0f))
        )
        drawRect(dim, Offset(0f, top), Size(left, guideHeight))
        drawRect(
            dim,
            Offset(left + guideWidth, top),
            Size((canvasWidth - left - guideWidth).coerceAtLeast(0f), guideHeight)
        )

        drawRoundRect(
            color = Color.White.copy(alpha = 0.30f),
            topLeft = Offset(left, top),
            size = Size(guideWidth, guideHeight),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            style = Stroke(
                width = strokeWidth,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12.dp.toPx(), 10.dp.toPx()))
            )
        )

        drawLine(accent, Offset(left, top + cornerRadius), Offset(left, top + accentLength + cornerRadius), accentWidth)
        drawLine(accent, Offset(left + cornerRadius, top), Offset(left + accentLength + cornerRadius, top), accentWidth)
        drawLine(accent, Offset(left + guideWidth - cornerRadius, top), Offset(left + guideWidth - cornerRadius - accentLength, top), accentWidth)
        drawLine(accent, Offset(left + guideWidth, top + cornerRadius), Offset(left + guideWidth, top + cornerRadius + accentLength), accentWidth)
        drawLine(accent, Offset(left, top + guideHeight - cornerRadius), Offset(left, top + guideHeight - cornerRadius - accentLength), accentWidth)
        drawLine(accent, Offset(left + cornerRadius, top + guideHeight), Offset(left + cornerRadius + accentLength, top + guideHeight), accentWidth)
        drawLine(accent, Offset(left + guideWidth - cornerRadius, top + guideHeight), Offset(left + guideWidth - cornerRadius - accentLength, top + guideHeight), accentWidth)
        drawLine(accent, Offset(left + guideWidth, top + guideHeight - cornerRadius), Offset(left + guideWidth, top + guideHeight - cornerRadius - accentLength), accentWidth)
    }
}

@Composable
private fun ScannerStatusPanel(
    presentation: ScannerPresentation,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Panel,
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, Border),
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
                        .background(presentation.accent.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
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
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp
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

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = presentation.message,
                color = Muted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(13.dp))
            Surface(
                color = PanelSoft,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
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
                        text = "Hands-free verification — no blink or head-turn required",
                        color = Muted,
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
        label = "Confirmed",
        title = "Identity verified",
        message = "Saving the employee's daily attendance summary.",
        accent = Green
    )

    decision == IdentityDecision.MULTIPLE_FACES -> ScannerPresentation(
        label = "Attention",
        title = "One person at a time",
        message = "Ask other people to move outside the camera frame.",
        accent = Amber
    )

    decision == IdentityDecision.LOW_QUALITY -> ScannerPresentation(
        label = "Image quality",
        title = "Improve the camera view",
        message = qualityMessage ?: "Move closer, face the camera, and avoid strong backlight.",
        accent = Rose
    )

    decision == IdentityDecision.UNKNOWN -> ScannerPresentation(
        label = "Not recognised",
        title = "No matching employee found",
        message = "Look directly at the camera or contact an administrator for enrollment.",
        accent = Rose
    )

    decision == IdentityDecision.AMBIGUOUS -> ScannerPresentation(
        label = "Verifying",
        title = "Hold still for a moment",
        message = "The app is comparing multiple frames before accepting the identity.",
        accent = Blue
    )

    faceDetected -> ScannerPresentation(
        label = "Analysing",
        title = "Face detected",
        message = "Keep a natural expression and remain inside the guide.",
        accent = Blue
    )

    else -> ScannerPresentation(
        label = "Ready",
        title = "Step into view",
        message = "Stand at a comfortable distance and look naturally toward the camera.",
        accent = Cyan
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
        Card(
            modifier = Modifier
                .padding(22.dp)
                .fillMaxWidth()
                .widthIn(max = 360.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1626)),
            border = BorderStroke(1.dp, Green.copy(alpha = 0.35f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 22.dp)
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
                        .background(Green.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Green,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(15.dp))
                Text(
                    text = state.actionTitle.ifBlank { "Attendance saved" },
                    color = Green,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(5.dp))
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
                    color = Muted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = if (state.eventType == "CLOCK_IN") {
                        Green.copy(alpha = 0.14f)
                    } else {
                        Blue.copy(alpha = 0.14f)
                    },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        if (state.eventType == "CLOCK_IN") Green.copy(alpha = 0.30f) else Blue.copy(alpha = 0.30f)
                    )
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
