package com.nexbytes.h7skertool.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexbytes.h7skertool.ui.theme.*

@Composable
fun ShizukuCheckScreen(
    shizukuAvailable: Boolean,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit
) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1200, easing = EaseInOutSine)),
        label = "s"
    )

    Box(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(DeepBlack, Color(0xFF0D0D1A)))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Logo
            Box(
                Modifier.size(110.dp).scale(scale)
                    .clip(CircleShape)
                    .background(AlertRed.copy(alpha = 0.08f))
                    .border(1.5.dp, AlertRed.copy(0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(80.dp).clip(CircleShape)
                        .background(AlertRed.copy(0.12f))
                        .border(1.dp, AlertRed.copy(0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Shield, null, tint = AlertRed, modifier = Modifier.size(38.dp))
                }
            }

            Text("SHIZUKU REQUIRED", color = AlertRed, fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace,
                letterSpacing = 3.sp)

            Text("Elevated privileges required to access\nrestricted Android/data directories",
                color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)

            // Status cards
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBlack)
                    .border(1.dp, DividerGray, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                StatusRow("Shizuku Service", shizukuAvailable,
                    if (!shizukuAvailable) "Not running — open Shizuku app" else "Running")
                Divider(color = DividerGray, thickness = 0.5.dp)
                StatusRow("Permission Granted", permissionGranted,
                    if (!permissionGranted) "Tap below to grant" else "Granted")
            }

            if (!shizukuAvailable) {
                // ADB instructions
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = ElevatedBlack),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DividerGray)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("ADB Setup", color = ElectricBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace)
                        Text("adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh",
                            color = NeonGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            if (shizukuAvailable && !permissionGranted) {
                Button(
                    onClick = onRequestPermission,
                    Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                ) {
                    Icon(Icons.Default.VpnKey, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Grant Shizuku Permission", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            OutlinedButton(
                onClick = onRetry, Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                border = androidx.compose.foundation.BorderStroke(1.dp, DividerGray)
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Re-check Status", fontSize = 14.sp)
            }

            Text("v2.0 • H7skER TOOL", color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean, note: String) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Column {
            Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(note, color = if (ok) NeonGreen else TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        Icon(
            if (ok) Icons.Default.CheckCircle else Icons.Default.Cancel,
            null, tint = if (ok) NeonGreen else AlertRed, modifier = Modifier.size(22.dp)
        )
    }
}
