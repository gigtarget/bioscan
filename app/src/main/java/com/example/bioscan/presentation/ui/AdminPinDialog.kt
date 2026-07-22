package com.example.bioscan.presentation.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bioscan.core.security.AdminSecurityManager
import kotlinx.coroutines.launch

@Composable
fun AdminPinDialog(
    securityManager: AdminSecurityManager,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var pinText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Administrator Access",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter Administrator PIN (Default PIN: 123456):",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = pinText,
                    onValueChange = {
                        pinText = it
                        errorMessage = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_pin_input"),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = Color(0xFFEF4444),
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        val valid = securityManager.authenticatePin(pinText)
                        if (valid) {
                            onSuccess()
                        } else {
                            errorMessage = "Invalid PIN. Please try again."
                        }
                    }
                },
                modifier = Modifier.testTag("admin_pin_submit_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
            ) {
                Text("Unlock Admin Area", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        },
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(20.dp)
    )
}
