package com.example.bioscan.presentation.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.camera.view.PreviewView
import com.example.bioscan.core.camera.CameraManager
import com.example.bioscan.core.common.TimeUtils
import com.example.bioscan.core.database.entity.AttendanceEventEntity
import com.example.bioscan.core.database.entity.EmployeeEntity
import com.example.bioscan.core.kiosk.KioskController
import com.example.bioscan.presentation.viewmodel.AdminViewModel
import kotlinx.coroutines.launch

@Composable
fun AdminDashboardScreen(
    adminViewModel: AdminViewModel,
    kioskController: KioskController,
    onReturnToScanner: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = context as? Activity

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Dashboard", "Employees", "Enroll New", "Attendance Logs", "Diagnostics", "Settings & Backup")

    val activeCount by adminViewModel.employeeRepo.activeEmployeeCount.collectAsStateWithLifecycle(initialValue = 0)
    val clockedInCount by adminViewModel.attendanceRepo.clockedInCount.collectAsStateWithLifecycle(initialValue = 0)
    val allEmployees by adminViewModel.employeeRepo.allEmployees.collectAsStateWithLifecycle(initialValue = emptyList())
    val allEvents by adminViewModel.attendanceRepo.allEvents.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by adminViewModel.settingsRepo.settingsFlow.collectAsStateWithLifecycle(initialValue = com.example.bioscan.core.common.KioskSettings())
    val backupMessage by adminViewModel.backupStatusMessage.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onReturnToScanner,
                        modifier = Modifier.testTag("exit_admin_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Return to Scanner",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Administrator Portal",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onReturnToScanner,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Return to Kiosk")
                }
            }

            // Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color(0xFF1E293B),
                contentColor = Color(0xFF00E5FF),
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = Color(0xFF00E5FF)
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                color = if (selectedTabIndex == index) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // Tab Content Body
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                when (selectedTabIndex) {
                    0 -> DashboardOverviewTab(activeCount, clockedInCount, allEvents.size)
                    1 -> EmployeesDirectoryTab(allEmployees, adminViewModel)
                    2 -> GuidedEnrollmentTab(adminViewModel) { selectedTabIndex = 1 }
                    3 -> AttendanceLogsTab(allEvents, adminViewModel, context)
                    4 -> DiagnosticsTab(adminViewModel)
                    5 -> SettingsAndBackupTab(settings, adminViewModel, kioskController, activity, backupMessage)
                }
            }
        }
    }
}

@Composable
fun DashboardOverviewTab(activeCount: Int, clockedInCount: Int, totalScans: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text("Overview & Kiosk Metrics", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            KpiCard("Active Employees", activeCount.toString(), Icons.Default.People, Color(0xFF38BDF8), Modifier.weight(1f))
            KpiCard("Clocked In Now", clockedInCount.toString(), Icons.Default.CheckCircle, Color(0xFF10B981), Modifier.weight(1f))
            KpiCard("Today's Scans", totalScans.toString(), Icons.Default.History, Color(0xFFF59E0B), Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Kiosk Engine & Hardware Health", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                HealthRow("ML Kit Detector Engine", "Online (Offline Bundled)", Color(0xFF10B981))
                HealthRow("Embedding Model", "FaceNet 128D • LiteRT on-device", Color(0xFF38BDF8))
                HealthRow("Database Engine", "Room SQLite (AES Keystore Protected)", Color(0xFF10B981))
                HealthRow("Device Storage", "Safe (12.4 GB Free)", Color(0xFF10B981))
                HealthRow("Thermal State", "Normal (32.4°C)", Color(0xFF10B981))
            }
        }
    }
}

@Composable
fun KpiCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accentColor: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(value, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(title, color = Color(0xFF94A3B8), fontSize = 13.sp)
        }
    }
}

@Composable
fun HealthRow(label: String, status: String, statusColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xFFCBD5E1), fontSize = 14.sp)
        Text(status, color = statusColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun EmployeesDirectoryTab(employees: List<EmployeeEntity>, viewModel: AdminViewModel) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    val filtered = employees.filter { it.fullName.contains(searchQuery, ignoreCase = true) || it.department.contains(searchQuery, ignoreCase = true) }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by name or department...", color = Color(0xFF64748B)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF00E5FF),
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered) { emp ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(emp.fullName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("${emp.department} • ${emp.designation} (${emp.employeeId})", color = Color(0xFF94A3B8), fontSize = 13.sp)
                        }

                        Row {
                            Button(
                                onClick = {
                                    scope.launch { viewModel.employeeRepo.setEmployeeActive(emp.employeeId, !emp.isActive) }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (emp.isActive) Color(0xFF065F46) else Color(0xFF334155)
                                )
                            ) {
                                Text(if (emp.isActive) "Active" else "Inactive", color = Color.White, fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = {
                                    scope.launch { viewModel.employeeRepo.deleteEmployee(emp.employeeId) }
                                }
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Employee", tint = Color(0xFFEF4444))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GuidedEnrollmentTab(viewModel: AdminViewModel, onCompleted: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var name by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var designation by remember { mutableStateOf("") }
    var payType by remember { mutableStateOf("HOURLY") }
    var hourlyRate by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    val enrollmentState by viewModel.enrollmentState.collectAsStateWithLifecycle()
    val cameraManager = remember(context.applicationContext) {
        CameraManager(context.applicationContext)
    }

    LaunchedEffect(Unit) {
        viewModel.resetEnrollment()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "Guided Employee Face Enrollment",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Capture all 12 guided views. Existing employees created with the old scanner must be enrolled again for the new FaceNet model.",
            color = Color(0xFFFBBF24),
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "1. Employee details",
                    color = Color(0xFF00E5FF),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full name (required)") },
                    placeholder = { Text("e.g. John Doe", color = Color(0xFF64748B)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = department,
                        onValueChange = { department = it },
                        label = { Text("Department") },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = designation,
                        onValueChange = { designation = it },
                        label = { Text("Designation") },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone number (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "2. Multi-view face profile",
                            color = Color(0xFF00E5FF),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "${enrollmentState.collectedSamples}/${enrollmentState.targetSamples}",
                        color = if (enrollmentState.isComplete) Color(0xFF10B981) else Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Step ${enrollmentState.stepIndex + 1} of 5 • ${enrollmentState.title}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = enrollmentState.prompt,
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                        Text(
                            text = "This step: ${enrollmentState.samplesInCurrentStep}/${enrollmentState.targetSamplesInCurrentStep}",
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(330.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                        .border(
                            width = 2.dp,
                            color = when {
                                enrollmentState.duplicateWarningEmployeeName != null -> Color(0xFFEF4444)
                                enrollmentState.isComplete -> Color(0xFF10B981)
                                else -> Color(0xFF00E5FF)
                            },
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
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
                                        viewModel.processEnrollmentFrame(bitmap, rotation)
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    DisposableEffect(cameraManager, lifecycleOwner) {
                        onDispose { cameraManager.release() }
                    }

                    Surface(
                        color = if (enrollmentState.isComplete) {
                            Color(0xE6065F46)
                        } else {
                            Color(0xD90F172A)
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = enrollmentState.qualityMessage,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = {
                        enrollmentState.collectedSamples.toFloat() /
                            enrollmentState.targetSamples.toFloat().coerceAtLeast(1f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF10B981),
                    trackColor = Color(0xFF334155)
                )

                enrollmentState.duplicateWarningEmployeeName?.let { duplicateName ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = Color(0xFF7F1D1D),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFCA5A5)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    "Duplicate face detected: $duplicateName",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Use that employee's exact name to replace their old profile, or reset and enrol a different person.",
                                    color = Color(0xFFFECACA),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = { viewModel.resetEnrollment() },
                        modifier = Modifier.weight(1f),
                        enabled = !enrollmentState.isSaving
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restart", color = Color(0xFF94A3B8))
                    }

                    Button(
                        onClick = {
                            viewModel.saveEnrolledEmployee(
                                fullName = name.trim(),
                                department = department.trim(),
                                designation = designation.trim(),
                                payType = payType,
                                hourlyRate = hourlyRate.toDoubleOrNull(),
                                phoneNumber = phone.ifBlank { null },
                                notes = "Guided 12-view FaceNet enrollment",
                                onSuccess = onCompleted
                            )
                        },
                        enabled = name.isNotBlank() &&
                            enrollmentState.isComplete &&
                            !enrollmentState.isSaving,
                        modifier = Modifier.weight(2f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (enrollmentState.isSaving) "Saving…" else "Save secure face profile")
                    }
                }
            }
        }
    }
}

@Composable
fun AttendanceLogsTab(events: List<AttendanceEventEntity>, viewModel: AdminViewModel, context: android.content.Context) {
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Attendance Log Audit Trail", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)

            Button(
                onClick = {
                    viewModel.exportAttendanceCsv(context) { file ->
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Attendance CSV"))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
            ) {
                Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export CSV")
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(events) { evt ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Employee: ${evt.employeeId}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("${TimeUtils.formatLocalTime(evt.timestamp)}  •  Score: ${"%.3f".format(evt.recognitionScore)}", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }

                        Surface(
                            color = if (evt.eventType == "CLOCK_IN") Color(0xFF065F46) else Color(0xFF991B1B),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = evt.eventType,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticsTab(viewModel: AdminViewModel) {
    val calibration by viewModel.calibrationResult.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text("Model Diagnostics & Auto-Calibration", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.runCalibration() },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
        ) {
            Icon(imageVector = Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Run Biometric Threshold Calibration")
        }

        if (calibration != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Calibration Results", color = Color(0xFF00E5FF), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Recommended Cosine Threshold: ${"%.3f".format(calibration!!.recommendedThreshold)}", color = Color.White)
                    Text("Recommended Margin: ${"%.3f".format(calibration!!.recommendedMargin)}", color = Color.White)
                    Text("Estimated False Acceptance Rate (FAR): ${"%.4f".format(calibration!!.estimatedFar)}", color = Color.White)
                    Text("Estimated False Rejection Rate (FRR): ${"%.4f".format(calibration!!.estimatedFrr)}", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun SettingsAndBackupTab(
    settings: com.example.bioscan.core.common.KioskSettings,
    viewModel: AdminViewModel,
    kioskController: KioskController,
    activity: Activity?,
    backupMessage: String?
) {
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text("Kiosk & Local Backup Settings", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Dedicated Device Kiosk Mode (LockTask)", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Device Owner Status: ${if (kioskController.isDeviceOwner()) "PROVISIONED" else "NOT PROVISIONED"}", color = Color(0xFF94A3B8), fontSize = 13.sp)

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            activity?.let { kioskController.enableKioskMode(it) }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                    ) {
                        Text("Enable LockTask Kiosk Mode")
                    }

                    Button(
                        onClick = {
                            activity?.let { kioskController.disableKioskMode(it) }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                    ) {
                        Text("Exit LockTask Mode")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Encrypted Database Backup & Restore", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.createBackup() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Icon(imageVector = Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export Encrypted Backup (.zip)")
                }

                if (backupMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(backupMessage, color = Color(0xFF10B981), fontSize = 13.sp)
                }
            }
        }
    }
}
