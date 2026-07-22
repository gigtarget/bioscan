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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.bioscan.core.kiosk.KioskController
import com.example.bioscan.presentation.ui.AdminPinDialog
import com.example.bioscan.presentation.ui.ProductionAdminDashboardScreen
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
                onOpenAdmin = { showPinDialog = true }
            )
        }

        composable("admin") {
            ProductionAdminDashboardScreen(
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
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF07111F), Color(0xFF0C1B2F), Color(0xFF07111F))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            color = Color(0xFF0E1B2D),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
            shadowElevation = 18.dp
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 26.dp, vertical = 30.dp)
            ) {
                Surface(
                    color = Color(0xFF5EE7F2).copy(alpha = 0.13f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Box(
                        modifier = Modifier.size(64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Face,
                            contentDescription = null,
                            tint = Color(0xFF5EE7F2),
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "Camera access required",
                    color = Color.White,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(9.dp))
                Text(
                    text = "BioScan uses the front camera to create on-device face embeddings and verify enrolled employees. Camera images are processed locally.",
                    color = Color(0xFFA7B4C8),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF5EE7F2),
                        contentColor = Color(0xFF07111F)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Allow camera access",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 5.dp)
                    )
                }
            }
        }
    }
}
