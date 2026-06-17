package com.nexbytes.h7skertool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexbytes.h7skertool.model.CapturedRequest
import com.nexbytes.h7skertool.model.CapturedResponse
import com.nexbytes.h7skertool.ui.theme.*
import com.nexbytes.h7skertool.utils.DecodeUtils
import com.nexbytes.h7skertool.utils.HexUtils
import com.nexbytes.h7skertool.utils.ProtoField
import com.nexbytes.h7skertool.utils.ProtoModifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// ─── API response parser ─────────────────────────────────────────────────────
private fun parseDecodeApiResponse(raw: String?): String {
    if (raw.isNullOrBlank()) return "⚠️ Empty response from decode API"
    val t = raw.trim()
    return try {
        val j = JSONObject(t)
        listOf("decoded", "result", "data", "output", "text")
            .mapNotNull { k -> j.optString(k).ifEmpty { null } }
            .firstOrNull()?.let { DecodeUtils.prettyPrintJson(it).ifEmpty { it } }
            ?: DecodeUtils.prettyPrintJson(t).ifEmpty { t }
    } catch (_: Exception) { t }
}

// ─── JSON Field Editor ────────────────────────────────────────────────────────
/**
 * Full JSON text editor that shows decoded protobuf data.
 * User can edit any field/value directly — the editor preserves the full
 * decoded structure and validates JSON before saving.
 */
@Composable
private fun JsonFieldEditor(
    decodedJson: String,
    requestHeaders: Map<String, String>,
    onCreateMod: (fieldJson: String) -> Unit,
    onCreateHeaderMod: (headerJson: String) -> Unit
) {
    var editorTab by remember { mutableIntStateOf(0) }
    var jsonText by remember(decodedJson) { mutableStateOf(decodedJson) }
    var headersText by remember(requestHeaders) {
        mutableStateOf(requestHeaders.entries.joinToString("\n") { "${it.key}: ${it.value}" })
    }
    var jsonError by remember { mutableStateOf<String?>(null) }
    var headerError by remember { mutableStateOf<String?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showHeaderSaveDialog by remember { mutableStateOf(false) }
    var modName by remember { mutableStateOf("mod_${System.currentTimeMillis()}") }
    var headerModName by remember { mutableStateOf("headers_${System.currentTimeMillis()}") }

    // Validate JSON in real-time
    val isJsonValid = remember(jsonText) {
        jsonText.isBlank() || DecodeUtils.isJson(jsonText.trim()) ||
        jsonText.trim().let { t -> t.startsWith("{") && runCatching { com.google.gson.JsonParser.parseString(t) }.isSuccess }
    }

    Column(Modifier.fillMaxSize()) {
        // Editor tabs: DECODED FIELDS | REQUEST HEADERS
        TabRow(selectedTabIndex = editorTab, containerColor = CardBlack, contentColor = NeonGreen,
            indicator = { tp -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tp[editorTab]), color = NeonGreen) }) {
            Tab(selected = editorTab == 0, onClick = { editorTab = 0 }, text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.DataObject, null, modifier = Modifier.size(13.dp))
                    Text("DECODED FIELDS", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            })
            Tab(selected = editorTab == 1, onClick = { editorTab = 1 }, text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Http, null, modifier = Modifier.size(13.dp))
                    Text("HEADERS", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            })
        }

        when (editorTab) {
            // ── Decoded Fields JSON editor ─────────────────────────────────────
            0 -> {
                Column(Modifier.fillMaxSize()) {
                    // Editor info strip
                    Row(Modifier.fillMaxWidth().background(NeonGreen.copy(0.06f)).padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Edit, null, tint = NeonGreen, modifier = Modifier.size(13.dp))
                        Text("Edit field values directly in JSON", color = NeonGreen, fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.weight(1f))
                        // Format / pretty print button
                        TextButton(onClick = {
                            jsonText = try { DecodeUtils.prettyPrintJson(jsonText).ifEmpty { jsonText } } catch (_: Exception) { jsonText }
                        }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("Format", color = Amber, fontSize = 10.sp)
                        }
                    }

                    // JSON validity indicator
                    if (jsonText.isNotBlank() && !isJsonValid) {
                        Row(Modifier.fillMaxWidth().background(AlertRed.copy(0.08f)).padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Error, null, tint = AlertRed, modifier = Modifier.size(12.dp))
                            Text("Invalid JSON — fix before saving", color = AlertRed, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }

                    if (decodedJson.isBlank()) {
                        Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.SearchOff, null, tint = TextDim, modifier = Modifier.size(36.dp))
                                Text("No decoded data", color = TextSecondary, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                                Text("Tap DECODE API first (HEX tab)", color = TextDim, fontSize = 11.sp)
                            }
                        }
                    } else {
                        // The main editable text field
                        OutlinedTextField(
                            value = jsonText,
                            onValueChange = { jsonText = it; jsonError = null },
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp),
                            textStyle = LocalTextStyle.current.copy(
                                fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                                color = if (isJsonValid) TextBright else AlertRed, lineHeight = 17.sp
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (isJsonValid) NeonGreen else AlertRed,
                                unfocusedBorderColor = if (isJsonValid) DividerGray else AlertRed.copy(0.5f),
                                focusedTextColor = TextBright, unfocusedTextColor = TextPrimary,
                                cursorColor = NeonGreen,
                                focusedContainerColor = ElevatedBlack, unfocusedContainerColor = ElevatedBlack
                            ),
                            shape = RoundedCornerShape(8.dp),
                            isError = !isJsonValid
                        )
                    }

                    jsonError?.let { err ->
                        Row(Modifier.fillMaxWidth().background(AlertRed.copy(0.08f)).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Error, null, tint = AlertRed, modifier = Modifier.size(12.dp))
                            Text(err, color = AlertRed, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        }
                    }

                    // Bottom action bar
                    HorizontalDivider(color = DividerGray)
                    Row(Modifier.fillMaxWidth().background(ElevatedBlack).padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isJsonValid) "Edit field values above" else "⚠ Fix JSON errors",
                            color = if (isJsonValid) TextDim else AlertRed,
                            fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        OutlinedButton(
                            onClick = { jsonText = decodedJson },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp)); Text("Reset", fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                if (!isJsonValid) { jsonError = "Cannot save: invalid JSON structure"; return@Button }
                                modName = "mod_${System.currentTimeMillis()}"
                                showSaveDialog = true
                            },
                            enabled = isJsonValid && jsonText.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, disabledContainerColor = DividerGray)
                        ) {
                            Icon(Icons.Default.Build, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("CREATE MOD", color = Color.Black, fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // ── Headers editor ───────────────────────────────────────────────
            1 -> {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().background(ElectricBlue.copy(0.06f)).padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Http, null, tint = ElectricBlue, modifier = Modifier.size(13.dp))
                        Text("Edit HTTP headers only — never touches body", color = ElectricBlue,
                            fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                    Text("Format: Header-Name: value  (one per line)",
                        color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))

                    OutlinedTextField(
                        value = headersText,
                        onValueChange = { headersText = it; headerError = null },
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp),
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextBright, lineHeight = 17.sp
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricBlue, unfocusedBorderColor = DividerGray,
                            focusedTextColor = TextBright, unfocusedTextColor = TextPrimary, cursorColor = ElectricBlue,
                            focusedContainerColor = ElevatedBlack, unfocusedContainerColor = ElevatedBlack
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    headerError?.let { err ->
                        Row(Modifier.fillMaxWidth().background(AlertRed.copy(0.08f)).padding(8.dp)) {
                            Text(err, color = AlertRed, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }

                    HorizontalDivider(color = DividerGray)
                    Row(Modifier.fillMaxWidth().background(ElevatedBlack).padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Modifies HTTP headers only", color = TextDim, fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        Button(
                            onClick = {
                                headerModName = "headers_${System.currentTimeMillis()}"
                                showHeaderSaveDialog = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                        ) {
                            Icon(Icons.Default.Http, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("SAVE HEADERS", color = Color.Black, fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // ── Save mod name dialog ──────────────────────────────────────────────────
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = ElevatedBlack,
            title = { Text("Name this Mod", color = NeonGreen, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Give this modification a name:", color = TextSecondary, fontSize = 13.sp)
                    OutlinedTextField(
                        value = modName,
                        onValueChange = { modName = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen, unfocusedBorderColor = DividerGray,
                            focusedTextColor = TextBright, unfocusedTextColor = TextPrimary, cursorColor = NeonGreen
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (modName.isBlank()) return@Button
                        onCreateMod(jsonText)
                        showSaveDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                ) {
                    Text("SAVE MOD", color = Color.Black, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }

    if (showHeaderSaveDialog) {
        AlertDialog(
            onDismissRequest = { showHeaderSaveDialog = false },
            containerColor = ElevatedBlack,
            title = { Text("Name this Header Mod", color = ElectricBlue, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = headerModName,
                        onValueChange = { headerModName = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricBlue, unfocusedBorderColor = DividerGray,
                            focusedTextColor = TextBright, unfocusedTextColor = TextPrimary, cursorColor = ElectricBlue
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                val json = headersTextToJson(headersText)
                Button(
                    onClick = { onCreateHeaderMod(json); showHeaderSaveDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Text("SAVE", color = Color.Black, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showHeaderSaveDialog = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }
}

private fun headersTextToJson(text: String): String {
    val map = mutableMapOf<String, String>()
    text.lines().forEach { line ->
        val idx = line.indexOf(':')
        if (idx > 0) {
            val key = line.substring(0, idx).trim()
            val value = line.substring(idx + 1).trim()
            if (key.isNotBlank()) map[key] = value
        }
    }
    return com.google.gson.Gson().toJson(map)
}

// ─── Main FloatingDecodeOverlay ───────────────────────────────────────────────

@Composable
fun FloatingDecodeOverlay(
    request: CapturedRequest,
    response: CapturedResponse?,
    onDismiss: () -> Unit,
    onSaveMod: (body: String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val http = remember {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    var tabIdx by remember { mutableIntStateOf(0) }
    // viewMode: 0=TEXT  1=HEX  2=DECODED(API result)  3=EDIT FIELDS
    var viewMode by remember { mutableIntStateOf(0) }
    var isDecoding by remember { mutableStateOf(false) }
    var decodedResult by remember { mutableStateOf<String?>(null) }
    var decodeError by remember { mutableStateOf<String?>(null) }
    var protoFields by remember { mutableStateOf<List<ProtoField>>(emptyList()) }
    var isParsingFields by remember { mutableStateOf(false) }
    var snack by remember { mutableStateOf<String?>(null) }

    val currentBytes = if (tabIdx == 0) request.body else response?.body
    val currentText = if (tabIdx == 0) request.bodyText else response?.bodyText
    val currentHexDump = remember(tabIdx, request, response) {
        if (tabIdx == 0) HexUtils.toHexDump(request.body) else HexUtils.toHexDump(response?.body)
    }
    val currentHexClean = remember(tabIdx, request, response) {
        if (tabIdx == 0) request.body?.let { HexUtils.toCleanHex(it) } ?: ""
        else response?.body?.let { HexUtils.toCleanHex(it) } ?: ""
    }

    // Decode via API
    fun decodeViaApi() {
        val hex = currentHexClean.ifEmpty { decodeError = "No hex data available"; return }
        isDecoding = true; decodeError = null; decodedResult = null
        scope.launch {
            try {
                val url = if (tabIdx == 0) "http://node.mrkalpha.tech:19140/request" else "http://node.mrkalpha.tech:19140/response"
                val jsonBody = JSONObject().put("hex", hex).toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val resp = withContext(Dispatchers.IO) {
                    http.newCall(Request.Builder().url(url).post(jsonBody).build()).execute()
                }
                val rawBody = withContext(Dispatchers.IO) { resp.body?.string() }
                withContext(Dispatchers.Main) {
                    isDecoding = false
                    decodedResult = parseDecodeApiResponse(rawBody)
                    viewMode = 2
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { isDecoding = false; decodeError = "Network error: ${e.message}" }
            }
        }
    }

    // Enter edit mode — parse proto fields AND use decoded JSON if available
    fun enterEditMode() {
        viewMode = 3
        val bytes = currentBytes
        if (bytes != null && bytes.isNotEmpty()) {
            isParsingFields = true
            scope.launch(Dispatchers.Default) {
                val fields = ProtoModifier.parseFields(bytes)
                // Also build decoded JSON from fields for the editor
                val decoded = decodedResult ?: try {
                    val json = buildString {
                        append("{")
                        fields.forEachIndexed { i, f ->
                            val rawVal = f.rawValue.trim('"').trim()
                            val isNum = rawVal.toLongOrNull() != null
                            append("\"${f.fieldNum}\": ${if (isNum) rawVal else "\"$rawVal\""}")
                            if (i < fields.size - 1) append(", ")
                        }
                        append("}")
                    }
                    DecodeUtils.prettyPrintJson(json).ifEmpty { json }
                } catch (_: Exception) { "{}" }
                withContext(Dispatchers.Main) {
                    protoFields = fields
                    isParsingFields = false
                    // Store decoded JSON if not already set
                    if (decodedResult == null && decoded != "{}") {
                        decodedResult = decoded
                    }
                }
            }
        } else {
            protoFields = emptyList()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.98f).fillMaxHeight(0.95f)
                .clip(RoundedCornerShape(18.dp))
                .background(SheetBlack)
                .border(1.dp, NeonGreen.copy(0.25f), RoundedCornerShape(18.dp))
        ) {
            // Drag handle
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), Alignment.Center) {
                Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(DividerGray))
            }

            // Top bar
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("DECODE WINDOW", color = NeonGreen, fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text(request.endpoint, color = TextSecondary, fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace, maxLines = 1)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (viewMode == 1) {
                        IconButton(onClick = ::decodeViaApi, modifier = Modifier.size(36.dp)) {
                            if (isDecoding) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Amber)
                            else Icon(Icons.Default.Api, "Decode via API", tint = Amber, modifier = Modifier.size(18.dp))
                        }
                    }
                    IconButton(onClick = { if (viewMode == 3) viewMode = 1 else enterEditMode() }, modifier = Modifier.size(36.dp)) {
                        Icon(if (viewMode == 3) Icons.Default.ArrowBack else Icons.Default.Edit,
                            null, tint = if (viewMode == 3) NeonGreen else TextSecondary,
                            modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = {
                        val txt = when (viewMode) { 1 -> currentHexDump; 2 -> decodedResult ?: ""; else -> currentText ?: "" }
                        clipboard.setText(AnnotatedString(txt))
                        snack = "Copied!"
                    }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.ContentCopy, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            HorizontalDivider(color = DividerGray, thickness = 0.5.dp)

            // REQUEST / RESPONSE tabs
            TabRow(selectedTabIndex = tabIdx, containerColor = SheetBlack, contentColor = NeonGreen,
                indicator = { tp -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tp[tabIdx]), color = NeonGreen) }) {
                listOf("REQUEST", "RESPONSE").forEachIndexed { i, t ->
                    Tab(selected = tabIdx == i, onClick = {
                        tabIdx = i; decodedResult = null; decodeError = null
                        if (viewMode == 3) viewMode = 1 else if (viewMode != 2) viewMode = 0
                    }, text = { Text(t, fontSize = 11.sp, fontFamily = FontFamily.Monospace) })
                }
            }

            // View mode chips / Edit mode header
            if (viewMode != 3) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("TEXT", "HEX", "DECODED").forEachIndexed { i, m ->
                        val enabled = i != 2 || decodedResult != null
                        FilterChip(selected = viewMode == i, onClick = { if (enabled) viewMode = i }, enabled = enabled,
                            label = { Text(m, fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonGreen.copy(0.12f), selectedLabelColor = NeonGreen,
                                containerColor = ElevatedBlack, labelColor = TextSecondary),
                            border = FilterChipDefaults.filterChipBorder(enabled = enabled, selected = viewMode == i,
                                selectedBorderColor = NeonGreen.copy(0.4f), borderColor = DividerGray)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (viewMode == 1) {
                        if (isDecoding) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Amber)
                        else TextButton(onClick = ::decodeViaApi, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                            Icon(Icons.Default.Api, null, tint = Amber, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("DECODE API", color = Amber, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(onClick = ::enterEditMode, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                        Icon(Icons.Default.Edit, null, tint = NeonGreen, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("EDIT FIELDS", color = NeonGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider(color = DividerGray, thickness = 0.5.dp)
            } else {
                // Edit mode header
                Row(Modifier.fillMaxWidth().background(NeonGreen.copy(0.06f)).padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Edit, null, tint = NeonGreen, modifier = Modifier.size(13.dp))
                    Text("FIELD EDITOR", color = NeonGreen, fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.weight(1f))
                    if (isParsingFields) {
                        CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = NeonGreen)
                        Text("Parsing…", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    } else {
                        Text("${protoFields.size} proto fields", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        if (decodedResult != null)
                            Text("• decoded", color = NeonGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                HorizontalDivider(color = NeonGreen.copy(0.15f))
            }

            // Error banner
            decodeError?.let { err ->
                Row(Modifier.fillMaxWidth().background(AlertRed.copy(0.08f)).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Error, null, tint = AlertRed, modifier = Modifier.size(13.dp))
                    Text(err, color = AlertRed, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                    IconButton(onClick = { decodeError = null }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, null, tint = AlertRed, modifier = Modifier.size(11.dp))
                    }
                }
            }

            // ── Content ───────────────────────────────────────────────────────
            when (viewMode) {
                3 -> {
                    // ── EDIT FIELDS: show decoded JSON in full text editor ──────
                    // Use API decoded result if available, else build from proto fields
                    val editorJson = decodedResult ?: if (protoFields.isNotEmpty()) {
                        try {
                            val json = buildString {
                                append("{")
                                protoFields.forEachIndexed { i, f ->
                                    val rawVal = f.rawValue.trim()
                                    val isNum = rawVal.toLongOrNull() != null
                                    if (i > 0) append(", ")
                                    append("\"${f.fieldNum}\": ${if (isNum) rawVal else "\"${rawVal.trim('"')}\"" }")
                                }
                                append("}")
                            }
                            DecodeUtils.prettyPrintJson(json).ifEmpty { json }
                        } catch (_: Exception) { "" }
                    } else ""

                    JsonFieldEditor(
                        decodedJson = editorJson,
                        requestHeaders = if (tabIdx == 0) request.headers else response?.headers ?: emptyMap(),
                        onCreateMod = { json ->
                            onSaveMod(json)
                            snack = "✓ Mod saved!"
                            viewMode = 2  // go back to decoded view
                        },
                        onCreateHeaderMod = { json ->
                            onSaveMod(json)
                            snack = "✓ Header mod saved!"
                            viewMode = 0
                        }
                    )
                }
                else -> {
                    val displayContent = when (viewMode) {
                        1 -> currentHexDump.ifEmpty { "(no hex data)" }
                        2 -> decodedResult ?: "(tap DECODE API in HEX view first)"
                        else -> currentText ?: "(empty body)"
                    }
                    val textColor = when {
                        viewMode == 1 -> Amber.copy(0.9f)
                        viewMode == 2 -> NeonGreen.copy(0.9f)
                        tabIdx == 0 -> NeonGreen.copy(0.85f)
                        else -> ElectricBlue.copy(0.85f)
                    }
                    Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp)) {
                        Text(displayContent, color = textColor, fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace, lineHeight = 17.sp)
                    }
                }
            }
        }

        // Snackbar
        snack?.let { msg ->
            LaunchedEffect(msg) { kotlinx.coroutines.delay(1800); snack = null }
            Box(Modifier.fillMaxSize().padding(bottom = 8.dp), Alignment.BottomCenter) {
                Snackbar(containerColor = ElevatedBlack) { Text(msg, color = NeonGreen, fontSize = 12.sp) }
            }
        }
    }
}
