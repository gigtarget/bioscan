package com.example.bioscan.presentation.ui

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bioscan.core.kiosk.KioskController
import com.example.bioscan.presentation.viewmodel.AdminViewModel

private val AdminInk = Color(0xFF07111F)
private val AdminPanel = Color(0xFF0E1B2D)
private val AdminPanelRaised = Color(0xFF14243A)
private val AdminCyan = Color(0xFF5EE7F2)
private val AdminBlue = Color(0xFF60A5FA)
private val AdminGreen = Color(0xFF4ADE80)
private val AdminAmber = Color(0xFFFBBF24)
private val AdminMuted = Color(0xFF9FB0C7)

private data class AdminSection(
    val title: String,
    val shortTitle: String,
    val icon: ImageVector
)

@Composable
fun ProductionAdminDashboardScreen(
    adminViewModel: AdminViewModel,
    kioskController: KioskController,
    onReturnToScanner: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var selectedSection by remember { mutableIntStateOf(0) }

    val activeCount by adminViewModel.employeeRepo.activeEmployeeCount.collectAsStateWithLifecycle(initialValue = 0)
    val clockedInCount by adminViewModel.attendanceRepo.clockedInCount.collectAsStateWithLifecycle(initialValue = 0)
    val employees by adminViewModel.employeeRepo.allEmployees.collectAsStateWithLifecycle(initialValue = emptyList())
    val events by adminViewModel.attendanceRepo.allEvents.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by adminViewModel.settingsRepo.settingsFlow.collectAsStateWithLifecycle(
        initialValue = com.example.bioscan.core.common.KioskSettings()
    )
    val backupMessage by adminViewModel.backupStatusMessage.collectAsStateWithLifecycle()

    val sections = remember {
        listOf(
            AdminSection("Operations overview", "Overview", Icons.Default.Dashboard),
            AdminSection("Employee directory", "Employees", Icons.Default.People),
            AdminSection("Guided enrollment", "Enroll", Icons.Default.Add),
            AdminSection("Attendance records", "Records", Icons.Default.History),
            AdminSection("Recognition diagnostics", "Diagnostics", Icons.Default.Analytics),
            AdminSection("Device settings", "Settings", Icons.Default.Settings)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF07111F), Color(0xFF0A1728), Color(0xFF07111F))
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AdminConsoleHeader(
                title = sections[selectedSection].title,
                onReturnToScanner = onReturnToScanner
            )

            AdminSectionSelector(
                sections = sections,
                selectedIndex = selectedSection,
                onSelected = { selectedSection = it }
            )

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                color = AdminPanel.copy(alpha = 0.96f),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    when (selectedSection) {
                        0 -> ProductionOverview(
                            activeCount = activeCount,
                            clockedInCount = clockedInCount,
                            totalEvents = events.size
                        )
                        1 -> EmployeesDirectoryTab(employees, adminViewModel)
                        2 -> GuidedEnrollmentTab(adminViewModel) { selectedSection = 1 }
                        3 -> AttendanceLogsTab(events, adminViewModel, context)
                        4 -> DiagnosticsTab(adminViewModel)
                        5 -> SettingsAndBackupTab(
                            settings = settings,
                            viewModel = adminViewModel,
                            kioskController = kioskController,
                            activity = activity,
                            backupMessage = backupMessage
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminConsoleHeader(
    title: String,
    onReturnToScanner: () -> Unit
) {
    Surface(
        modifier = Modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        color = AdminPanel,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
        shadowElevation = 10.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            IconButton(
                onClick = onReturnToScanner,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(44.dp)
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.07f),
                    shape = CircleShape
                ) {
                    Box(
                        modifier = Modifier.size(42.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Return to scanner",
                            tint = Color.White
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 56.dp, end = 50.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = null,
                        tint = AdminCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = "ADMIN CONSOLE",
                        color = AdminCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "BioScan local operations",
                    color = AdminMuted,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun AdminSectionSelector(
    sections: List<AdminSection>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        sections.forEachIndexed { index, section ->
            val selected = index == selectedIndex
            Surface(
                onClick = { onSelected(index) },
                color = if (selected) AdminCyan else AdminPanelRaised,
                contentColor = if (selected) AdminInk else AdminMuted,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp,
                    if (selected) AdminCyan else Color.White.copy(alpha = 0.08f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = section.icon,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = section.shortTitle,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductionOverview(
    activeCount: Int,
    clockedInCount: Int,
    totalEvents: Int
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "System at a glance",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Live local status for this attendance terminal",
            color = AdminMuted,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(16.dp))

        OverviewMetric("Active employees", activeCount.toString(), AdminBlue)
        Spacer(modifier = Modifier.height(10.dp))
        OverviewMetric("Open workdays", clockedInCount.toString(), AdminGreen)
        Spacer(modifier = Modifier.height(10.dp))
        OverviewMetric("Stored attendance entries", totalEvents.toString(), AdminAmber)

        Spacer(modifier = Modifier.height(18.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = AdminPanelRaised,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, AdminCyan.copy(alpha = 0.18f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Daily attendance policy",
                    color = AdminCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                PolicyLine("1", "The earliest verified scan becomes clock-in.")
                PolicyLine("2", "The latest verified scan becomes clock-out.")
                PolicyLine("3+", "Later scans update clock-out instead of creating extra entries.")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = AdminPanelRaised,
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Recognition safeguards",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                SystemStatus("FaceNet embedding model", "Ready", AdminGreen)
                SystemStatus("Four-frame identity consensus", "Enabled", AdminGreen)
                SystemStatus("Multi-view employee templates", "Required", AdminBlue)
                SystemStatus("Blink / head-turn challenge", "Not required", AdminMuted)
            }
        }
    }
}

@Composable
private fun OverviewMetric(label: String, value: String, accent: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AdminPanelRaised,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 17.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = label,
                    color = AdminMuted,
                    fontSize = 12.sp
                )
                Text(
                    text = value,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(accent.copy(alpha = 0.13f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(23.dp)
                )
            }
        }
    }
}

@Composable
private fun PolicyLine(number: String, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            color = AdminCyan.copy(alpha = 0.13f),
            shape = RoundedCornerShape(9.dp)
        ) {
            Text(
                text = number,
                color = AdminCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            color = Color(0xFFD5DEEB),
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun SystemStatus(label: String, value: String, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFFD5DEEB),
            fontSize = 13.sp
        )
        Text(
            text = value,
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
