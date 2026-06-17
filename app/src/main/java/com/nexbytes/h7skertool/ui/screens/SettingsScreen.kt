package com.nexbytes.h7skertool.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexbytes.h7skertool.viewmodel.AppUiState
import com.nexbytes.h7skertool.ui.theme.*

@Composable
fun SettingsScreen(
    state: AppUiState,
    onChangeClientUrl: () -> Unit,
    onClearCaptures: () -> Unit,
    onClearMods: () -> Unit,
    onLogout: () -> Unit,
    onResetAll: () -> Unit
) {
    var showResetDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().background(DeepBlack),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("SETTINGS", color = NeonGreen, fontSize = 12.sp,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        }

        // Account
        item {
            SettingsSection("ACCOUNT") {
                SettingsInfoRow(Icons.Default.Person, "User", state.username.ifEmpty { "unknown" }, NeonGreen)
                Divider(color = DividerGray, thickness = 0.5.dp)
                SettingsInfoRow(Icons.Default.VerifiedUser, "Status",
                    if (state.isVerified) "Verified ✓" else "Not verified", if (state.isVerified) SuccessGreen else AlertRed)
            }
        }

        // Proxy config
        item {
            SettingsSection("PROXY CONFIGURATION") {
                SettingsInfoRow(Icons.Default.Http, "Client URL", state.clientUrl.ifEmpty { "(not set)" }, ElectricBlue)
                Divider(color = DividerGray, thickness = 0.5.dp)
                SettingsActionRow(Icons.Default.Edit, "Change Client URL", ElectricBlue, onChangeClientUrl)
                Divider(color = DividerGray, thickness = 0.5.dp)
                SettingsInfoRow(Icons.Default.Router, "Proxy Address", "127.0.0.1:8080", TextSecondary)
            }
        }

        // Capture stats
        item {
            SettingsSection("CAPTURE") {
                SettingsInfoRow(Icons.Default.Api, "Captured Requests", "${state.requests.size}", NeonGreen)
                Divider(color = DividerGray, thickness = 0.5.dp)
                SettingsInfoRow(Icons.Default.PlayArrow, "Status",
                    if (state.isCapturing) "Active" else "Idle",
                    if (state.isCapturing) SuccessGreen else TextSecondary)
                Divider(color = DividerGray, thickness = 0.5.dp)
                SettingsActionRow(Icons.Default.DeleteSweep, "Clear All Captures", Amber, onClearCaptures)
            }
        }

        // Saved modifications
        if (state.savedMods.isNotEmpty()) {
            item {
                SettingsSection("SAVED MODIFICATIONS (${state.savedMods.size})") {
                    state.savedMods.entries.forEachIndexed { idx, (ep, body) ->
                        if (idx > 0) Divider(color = DividerGray, thickness = 0.5.dp)
                        Row(
                            Modifier.fillMaxWidth().padding(10.dp),
                            Arrangement.SpaceBetween, Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(ep, color = PurpleAccent, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium)
                                Text(body.take(60) + if (body.length > 60) "…" else "",
                                    color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Divider(color = DividerGray, thickness = 0.5.dp)
                    SettingsActionRow(Icons.Default.DeleteForever, "Clear All Modifications", AlertRed, onClearMods)
                }
            }
        }

        // Danger zone
        item {
            SettingsSection("DANGER ZONE") {
                SettingsActionRow(Icons.Default.Logout, "Log Out", Amber) { showLogoutDialog = true }
                Divider(color = DividerGray, thickness = 0.5.dp)
                SettingsActionRow(Icons.Default.RestartAlt, "Factory Reset", AlertRed) { showResetDialog = true }
            }
        }

        // App info
        item {
            SettingsSection("ABOUT") {
                SettingsInfoRow(Icons.Default.Apps, "App Name", "H7skER TOOL", NeonGreen)
                Divider(color = DividerGray, thickness = 0.5.dp)
                SettingsInfoRow(Icons.Default.Info, "Version", "2.0 (build 200)", TextSecondary)
                Divider(color = DividerGray, thickness = 0.5.dp)
                SettingsInfoRow(Icons.Default.Domain, "Package", "com.nexbytes.h7skertool", TextDim)
            }
        }
    }

    // Logout dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = ElevatedBlack,
            title = { Text("Log Out?", color = TextBright, fontWeight = FontWeight.Bold) },
            text = { Text("Your session will end. You'll need to verify your password again.", color = TextSecondary, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { showLogoutDialog = false; onLogout() }) {
                    Text("Log Out", color = Amber, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }

    // Reset dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = ElevatedBlack,
            title = { Text("Factory Reset?", color = AlertRed, fontWeight = FontWeight.Bold) },
            text = { Text("This will clear ALL data including your session, client URL, and all captures. This cannot be undone.", color = TextSecondary, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { showResetDialog = false; onResetAll() }) {
                    Text("RESET EVERYTHING", color = AlertRed, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(CardBlack).border(1.dp, DividerGray, RoundedCornerShape(14.dp)),
            content = content
        )
    }
}

@Composable
private fun SettingsInfoRow(icon: ImageVector, label: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            Text(label, color = TextPrimary, fontSize = 13.sp)
        }
        Text(value, color = valueColor, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
    }
}

@Composable
private fun SettingsActionRow(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp)
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.Start, Alignment.CenterVertically,) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}
