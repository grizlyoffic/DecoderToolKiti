package com.nexbytes.h7skertool.ui.screens

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexbytes.h7skertool.ui.theme.*

@Composable
fun ClientUrlScreen(
    currentUrl: String,
    onContinue: (String) -> Unit
) {
    var url by remember { mutableStateOf(currentUrl.ifEmpty { "https://clientbp.ggpolarbear.com" }) }
    var urlError by remember { mutableStateOf<String?>(null) }

    fun validate(): Boolean {
        return when {
            url.isBlank() -> { urlError = "URL cannot be empty"; false }
            !url.startsWith("http://") && !url.startsWith("https://") -> { urlError = "Must start with http:// or https://"; false }
            else -> { urlError = null; true }
        }
    }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(DeepBlack, Color(0xFF0A0D14)))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // Icon
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(20.dp))
                    .background(ElectricBlue.copy(0.08f))
                    .border(1.dp, ElectricBlue.copy(0.3f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Link, null, tint = ElectricBlue, modifier = Modifier.size(36.dp))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Client Server URL", color = TextBright, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Set the target server that proxy will forward requests to",
                    color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
            }

            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBlack)
                    .border(1.dp, if (urlError != null) AlertRed.copy(0.5f) else DividerGray, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("CLIENT BASE URL", color = TextSecondary, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; urlError = null },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://example.com", color = TextDim) },
                    leadingIcon = { Icon(Icons.Default.Http, null, tint = TextSecondary) },
                    trailingIcon = {
                        if (url.isNotEmpty()) {
                            IconButton(onClick = { url = "" }) {
                                Icon(Icons.Default.Clear, null, tint = TextSecondary)
                            }
                        }
                    },
                    isError = urlError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (validate()) onContinue(url.trimEnd('/')) }),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = DividerGray,
                        focusedTextColor = TextBright,
                        unfocusedTextColor = TextPrimary,
                        errorBorderColor = AlertRed,
                        cursorColor = ElectricBlue
                    ),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    shape = RoundedCornerShape(10.dp)
                )

                urlError?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Error, null, tint = AlertRed, modifier = Modifier.size(14.dp))
                        Text(it, color = AlertRed, fontSize = 12.sp)
                    }
                }

                // Quick presets
                Text("Quick presets:", color = TextSecondary, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PresetChip("PolarBear") { url = "https://clientbp.ggpolarbear.com" }
                    PresetChip("Local") { url = "http://192.168.1.1:7777" }
                }
            }

            Button(
                onClick = { if (validate()) onContinue(url.trimEnd('/')) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
            ) {
                Icon(Icons.Default.ArrowForward, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Continue", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun PresetChip(label: String, onClick: () -> Unit) {
    FilterChip(
        selected = false, onClick = onClick, label = { Text(label, fontSize = 11.sp) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = ElevatedBlack, labelColor = TextSecondary
        ),
        border = FilterChipDefaults.filterChipBorder(false, false,
            borderColor = DividerGray, selectedBorderColor = ElectricBlue)
    )
}
