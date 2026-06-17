package com.nexbytes.h7skertool.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexbytes.h7skertool.model.CapturedRequest
import com.nexbytes.h7skertool.model.CapturedResponse
import com.nexbytes.h7skertool.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UnifiedCaptureItem(
    request: CapturedRequest,
    response: CapturedResponse?,
    onTap: () -> Unit,
    onCopyRequest: () -> Unit,
    onCopyResponse: () -> Unit,
    onOpenDecode: () -> Unit,
    onSaveMod: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val statusColor = when {
        response == null -> TextSecondary
        response.statusCode in 200..299 -> NeonGreen
        response.statusCode in 400..499 -> Amber
        response.statusCode >= 500 -> AlertRed
        else -> ElectricBlue
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBlack)
            .border(1.dp, DividerGray, RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = { expanded = !expanded; onTap() },
                onLongClick = { showMenu = true }
            )
    ) {
        // Header row
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            Arrangement.SpaceBetween, Alignment.CenterVertically
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MethodBadge(request.method)
                Text(request.endpoint, color = TextBright, fontSize = 13.sp,
                    fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (response != null) {
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp))
                            .background(statusColor.copy(0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("${response.statusCode}", color = statusColor, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Text("${response.durationMs}ms", color = TextSecondary, fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace)
                } else {
                    CircularProgressIndicator(Modifier.size(12.dp), color = TextSecondary, strokeWidth = 1.5.dp)
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, tint = TextSecondary, modifier = Modifier.size(16.dp)
                )
            }
        }

        // Timestamp + size
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmtTime(request.timestamp), color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            val sizes = buildString {
                request.body?.let { append("Req: ${it.size}B") }
                response?.body?.let { append(" • Res: ${it.size}B") }
            }
            Text(sizes, color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }

        // Expandable body preview
        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(
                Modifier.fillMaxWidth()
                    .background(ElevatedBlack)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (request.bodyText?.isNotEmpty() == true) {
                    SectionLabel("REQUEST BODY")
                    Text(
                        request.bodyText.take(300) + if (request.bodyText.length > 300) "…" else "",
                        color = NeonGreen.copy(0.9f), fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )
                }
                if (response?.bodyText?.isNotEmpty() == true) {
                    Divider(color = DividerGray, thickness = 0.5.dp)
                    SectionLabel("RESPONSE BODY")
                    Text(
                        response.bodyText.take(300) + if (response.bodyText.length > 300) "…" else "",
                        color = ElectricBlue.copy(0.9f), fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // Context menu
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false },
            modifier = Modifier.background(ElevatedBlack)) {
            DropdownMenuItem(
                text = { Text("Copy Request", color = TextPrimary, fontSize = 13.sp) },
                onClick = { showMenu = false; onCopyRequest() },
                leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = NeonGreen, modifier = Modifier.size(16.dp)) }
            )
            DropdownMenuItem(
                text = { Text("Copy Response", color = TextPrimary, fontSize = 13.sp) },
                onClick = { showMenu = false; onCopyResponse() },
                leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = ElectricBlue, modifier = Modifier.size(16.dp)) }
            )
            Divider(color = DividerGray)
            DropdownMenuItem(
                text = { Text("Open in Decode Window", color = TextPrimary, fontSize = 13.sp) },
                onClick = { showMenu = false; onOpenDecode() },
                leadingIcon = { Icon(Icons.Default.Code, null, tint = Amber, modifier = Modifier.size(16.dp)) }
            )
            DropdownMenuItem(
                text = { Text("Save Body as Modification", color = TextPrimary, fontSize = 13.sp) },
                onClick = { showMenu = false; onSaveMod() },
                leadingIcon = { Icon(Icons.Default.Save, null, tint = PurpleAccent, modifier = Modifier.size(16.dp)) }
            )
        }
    }
}

@Composable
fun MethodBadge(method: String) {
    val color = when (method.uppercase()) {
        "GET" -> MethodGET
        "POST" -> MethodPOST
        "PUT" -> MethodPUT
        "DELETE" -> MethodDELETE
        "PATCH" -> MethodPATCH
        else -> TextSecondary
    }
    Box(
        Modifier.clip(RoundedCornerShape(5.dp)).background(color.copy(0.12f)).padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(method.take(6), color = color, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = TextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
        letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
}

private fun fmtTime(ts: Long) = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(ts))
