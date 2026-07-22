package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.bioscan.core.kiosk.KioskController
import com.example.bioscan.presentation.ui.AdminDashboardScreen
import com.example.bioscan.presentation.ui.AdminPinDialog
import com.example.bioscan.presentation.ui.ScannerScreen
import com.example.bioscan.presentation.viewmodel.AdminViewModel
import com.example.bioscan.presentation.viewmodel.KioskMainViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val kioskMainViewModel: KioskMainViewModel by viewModels()
    private val adminViewModel: AdminViewModel by viewModels()
    private lateinit var kioskController: KioskController

    private var hasCameraPermission by mutableStateOf(false)

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            hasCameraPermission = isGranted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Ensure screen stays on for kiosk mount
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        kioskController = KioskController(this)

        checkCameraPermission()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    if (hasCameraPermission) {
                        BioScanKioskApp(
                            kioskMainViewModel = kioskMainViewModel,
                            adminViewModel = adminViewModel,
                            kioskController = kioskController
                        )
                    } else {
                        CameraPermissionScreen(
                            onRequestPermission = {
                                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun checkCameraPermission() {
        hasCameraPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCameraPermission) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        kioskController.hideSystemUi(this)
    }
}

@Composable
fun BioScanKioskApp(
    kioskMainViewModel: KioskMainViewModel,
    adminViewModel: AdminViewModel,
    kioskController: KioskController
) {
    val navController = rememberNavController()
    var showPinDialog by remember { mutableStateOf(false) }

    NavHost(navController = navController, startDestination = "scanner") {
        composable("scanner") {
            ScannerScreen(
                viewModel = kioskMainViewModel,
                onOpenAdmin = {
                    showPinDialog = true
                }
            )
        }

        composable("admin") {
            AdminDashboardScreen(
                adminViewModel = adminViewModel,
                kioskController = kioskController,
                onReturnToScanner = {
                    kioskMainViewModel.refreshTemplateIndex()
                    navController.popBackStack()
                }
            )
        }
    }

    if (showPinDialog) {
        AdminPinDialog(
            securityManager = adminViewModel.securityManager,
            onDismiss = { showPinDialog = false },
            onSuccess = {
                showPinDialog = false
                navController.navigate("admin")
            }
        )
    }
}

@Composable
fun CameraPermissionScreen(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Camera Permission Required",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "BioScan Kiosk requires front camera access to perform real-time facial recognition and liveness verification.",
                color = Color(0xFF94A3B8),
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
            ) {
                Text("Grant Camera Permission")
            }
        }
    }
}
