package com.nexbytes.h7skertool.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexbytes.h7skertool.ui.theme.*

@Composable
fun PasswordScreen(
    isVerifying: Boolean,
    error: String?,
    onVerify: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(DeepBlack, Color(0xFF0A0D14)))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header badge
            Box(
                Modifier.clip(RoundedCornerShape(12.dp))
                    .background(NeonGreen.copy(0.08f))
                    .border(1.dp, NeonGreen.copy(0.3f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("H7skER TOOL v2.0", color = NeonGreen, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Lock, null, tint = ElectricBlue, modifier = Modifier.size(48.dp))
                Text("Authorization Required", color = TextBright, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Enter your access password to continue", color = TextSecondary, fontSize = 13.sp,
                    textAlign = TextAlign.Center)
            }

            // Password field card
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBlack)
                    .border(1.dp, if (error != null) AlertRed.copy(0.5f) else DividerGray, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("PASSWORD", color = TextSecondary, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter password...", color = TextDim, fontSize = 14.sp) },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                null, tint = TextSecondary
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (!isVerifying) onVerify(password) }),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonGreen,
                        unfocusedBorderColor = DividerGray,
                        focusedTextColor = TextBright,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = NeonGreen
                    ),
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace, fontSize = 15.sp
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                AnimatedVisibility(visible = error != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Error, null, tint = AlertRed, modifier = Modifier.size(14.dp))
                        Text(error ?: "", color = AlertRed, fontSize = 12.sp)
                    }
                }
            }

            Button(
                onClick = { onVerify(password) },
                enabled = !isVerifying && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonGreen,
                    disabledContainerColor = NeonGreen.copy(0.3f)
                )
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Verifying...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                } else {
                    Icon(Icons.Default.Login, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Verify & Enter", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            Text("Unauthorized access is prohibited", color = TextDim, fontSize = 10.sp,
                fontFamily = FontFamily.Monospace)
        }
    }
}
