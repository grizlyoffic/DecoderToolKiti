package com.nexbytes.h7skertool.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexbytes.h7skertool.model.CapturedRequest
import com.nexbytes.h7skertool.model.CapturedResponse
import com.nexbytes.h7skertool.ui.components.FloatingDecodeOverlay
import com.nexbytes.h7skertool.ui.theme.*
import com.nexbytes.h7skertool.utils.DecodeUtils
import com.nexbytes.h7skertool.utils.HexUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestDetailScreen(
    request: CapturedRequest,
    response: CapturedResponse?,
    onBack: () -> Unit,
    onSaveMod: (String, String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val http = remember {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    var mainTab by remember { mutableIntStateOf(0) }
    var subTab by remember { mutableIntStateOf(0) }
    var showDecodeOverlay by remember { mutableStateOf(false) }
    var splitDecoded by remember { mutableStateOf<String?>(null) }
    var isSplitDecoding by remember { mutableStateOf(false) }
    var splitError by remember { mutableStateOf<String?>(null) }
    var snack by remember { mutableStateOf<String?>(null) }

    // ── Edit state for each section (separate, never cross-contaminating) ─────
    var editingHeaders by remember(mainTab) { mutableStateOf(false) }
    var editingBody by remember(mainTab) { mutableStateOf(false) }

    var headersEditText by remember(mainTab) {
        mutableStateOf(
            if (mainTab == 0) request.headersAsString() else response?.headersAsString() ?: ""
        )
    }
    var bodyEditText by remember(mainTab) {
        mutableStateOf(
            if (mainTab == 0) request.bodyText ?: "" else response?.bodyText ?: ""
        )
    }

    val reqHexClean = remember(request) { request.body?.let { HexUtils.toCleanHex(it) } ?: "" }
    val respHexClean = remember(response) { response?.body?.let { HexUtils.toCleanHex(it) } ?: "" }

    fun decodeSplit() {
        val hex = (if (mainTab == 0) reqHexClean else respHexClean).ifEmpty { snack = "No hex data"; return }
        isSplitDecoding = true; splitError = null; splitDecoded = null
        scope.launch {
            try {
                val url = if (mainTab == 0) "http://node.mrkalpha.tech:19140/request" else "http://node.mrkalpha.tech:19140/response"
                val body = JSONObject().put("hex", hex).toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val resp = withContext(Dispatchers.IO) {
                    http.newCall(okhttp3.Request.Builder().url(url).post(body).build()).execute()
                }
                val rawBody = withContext(Dispatchers.IO) { resp.body?.string() }
                withContext(Dispatchers.Main) { isSplitDecoding = false; splitDecoded = parseDecodeApiResponse(rawBody) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { isSplitDecoding = false; splitError = "Decode failed: ${e.message}" }
            }
        }
    }

    Scaffold(
        containerColor = DeepBlack,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(request.endpoint, color = TextBright, fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val mc = when (request.method) {
                                "GET" -> MethodGET; "POST" -> MethodPOST; "PUT" -> MethodPUT;
                                "DELETE" -> MethodDELETE; else -> TextSecondary
                            }
                            Text(request.method, color = mc, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            if (response != null) {
                                val sc = when {
                                    response.statusCode in 200..299 -> NeonGreen
                                    response.statusCode in 400..499 -> Amber; else -> AlertRed
                                }
                                Text("${response.statusCode}", color = sc, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text("${response.durationMs}ms", color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = TextPrimary) } },
                actions = {
                    if (subTab == 2) {
                        IconButton(onClick = { showDecodeOverlay = true }) {
                            Icon(Icons.Default.Code, "Decode window", tint = Amber)
                        }
                        IconButton(onClick = ::decodeSplit) {
                            if (isSplitDecoding) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ElectricBlue)
                            else Icon(Icons.Default.VerticalSplit, "Split decode", tint = ElectricBlue)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBlack)
            )
        }
    ) { pv ->
        Column(Modifier.fillMaxSize().padding(pv)) {
            // Split decoded panel
            if (splitDecoded != null) {
                Column(Modifier.weight(0.4f).fillMaxWidth().background(ElevatedBlack)) {
                    Row(Modifier.fillMaxWidth().background(CardBlack).padding(horizontal = 12.dp, vertical = 6.dp),
                        Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Api, null, tint = NeonGreen, modifier = Modifier.size(14.dp))
                            Text("DECODED", color = NeonGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                        Row {
                            IconButton(onClick = { clipboard.setText(AnnotatedString(splitDecoded ?: "")); snack = "Copied!" }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.ContentCopy, null, tint = NeonGreen, modifier = Modifier.size(14.dp))
                            }
                            IconButton(onClick = { splitDecoded = null; splitError = null }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    splitError?.let { e ->
                        Row(Modifier.fillMaxWidth().background(AlertRed.copy(0.08f)).padding(8.dp)) {
                            Text(e, color = AlertRed, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp)) {
                        Text(splitDecoded ?: "", color = NeonGreen.copy(0.9f), fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 17.sp)
                    }
                }
                HorizontalDivider(color = NeonGreen, thickness = 2.dp)
            }

            // Main body
            val bodyWeight = if (splitDecoded != null) 0.6f else 1f
            Column(Modifier.weight(bodyWeight).fillMaxWidth()) {
                // REQUEST / RESPONSE main tabs
                TabRow(selectedTabIndex = mainTab, containerColor = CardBlack, contentColor = NeonGreen,
                    indicator = { tp -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tp[mainTab]), color = NeonGreen) }) {
                    listOf("REQUEST", "RESPONSE").forEachIndexed { i, t ->
                        Tab(selected = mainTab == i, onClick = {
                            mainTab = i; subTab = 0; splitDecoded = null
                            editingHeaders = false; editingBody = false
                        }, text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(t, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                if (i == 1 && response != null) {
                                    val c = when { response.statusCode in 200..299 -> NeonGreen; response.statusCode in 400..499 -> Amber; else -> AlertRed }
                                    Badge(containerColor = c.copy(0.15f)) { Text("${response.statusCode}", color = c, fontSize = 9.sp) }
                                }
                            }
                        })
                    }
                }

                // BODY / HEADERS / HEX sub-tabs
                ScrollableTabRow(selectedTabIndex = subTab, containerColor = ElevatedBlack, contentColor = ElectricBlue,
                    edgePadding = 0.dp, divider = {},
                    indicator = { tp -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tp[subTab]), color = ElectricBlue, height = 1.5.dp) }) {
                    listOf("BODY", "HEADERS", "HEX").forEachIndexed { i, t ->
                        Tab(selected = subTab == i, onClick = {
                            subTab = i
                            editingHeaders = false; editingBody = false
                            // Reset edit text when switching tabs
                            if (i == 1) headersEditText = if (mainTab == 0) request.headersAsString() else response?.headersAsString() ?: ""
                            if (i == 0) bodyEditText = if (mainTab == 0) request.bodyText ?: "" else response?.bodyText ?: ""
                        }, text = { Text(t, fontSize = 11.sp, fontFamily = FontFamily.Monospace) })
                    }
                }

                HorizontalDivider(color = DividerGray, thickness = 0.5.dp)

                // ── Content area ──────────────────────────────────────────
                val isReq = mainTab == 0

                // Action strip
                Row(Modifier.fillMaxWidth().background(ElevatedBlack).padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {

                    when (subTab) {
                        // BODY tab actions
                        0 -> {
                            if (!editingBody) {
                                TextButton(onClick = {
                                    bodyEditText = if (isReq) request.bodyText ?: "" else response?.bodyText ?: ""
                                    editingBody = true
                                }, colors = ButtonDefaults.textButtonColors(contentColor = NeonGreen)) {
                                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(4.dp)); Text("Edit", fontSize = 11.sp)
                                }
                            } else {
                                TextButton(onClick = {
                                    val section = if (isReq) "request" else "response"
                                    onSaveMod(request.endpoint, bodyEditText)
                                    editingBody = false; snack = "✓ Body mod saved!"
                                }, colors = ButtonDefaults.textButtonColors(contentColor = NeonGreen)) {
                                    Icon(Icons.Default.Save, null, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(4.dp)); Text("Save Mod", fontSize = 11.sp)
                                }
                                TextButton(onClick = { editingBody = false }, colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)) {
                                    Icon(Icons.Default.Close, null, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(4.dp)); Text("Cancel", fontSize = 11.sp)
                                }
                            }
                            TextButton(onClick = {
                                val content = if (isReq) request.bodyText ?: "" else response?.bodyText ?: ""
                                clipboard.setText(AnnotatedString(content)); snack = "Copied!"
                            }, colors = ButtonDefaults.textButtonColors(contentColor = ElectricBlue)) {
                                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp)); Text("Copy", fontSize = 11.sp)
                            }
                        }
                        // HEADERS tab actions
                        1 -> {
                            if (!editingHeaders) {
                                TextButton(onClick = {
                                    headersEditText = if (isReq) request.headersAsString() else response?.headersAsString() ?: ""
                                    editingHeaders = true
                                }, colors = ButtonDefaults.textButtonColors(contentColor = ElectricBlue)) {
                                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(4.dp)); Text("Edit Headers", fontSize = 11.sp)
                                }
                            } else {
                                TextButton(onClick = {
                                    // Convert edited header text to JSON map and save as HEADER mod
                                    val headerJson = headersTextToJson(headersEditText)
                                    onSaveMod(request.endpoint, headerJson)
                                    editingHeaders = false; snack = "✓ Header mod saved!"
                                }, colors = ButtonDefaults.textButtonColors(contentColor = ElectricBlue)) {
                                    Icon(Icons.Default.Save, null, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(4.dp)); Text("Save Headers", fontSize = 11.sp)
                                }
                                TextButton(onClick = { editingHeaders = false }, colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)) {
                                    Icon(Icons.Default.Close, null, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(4.dp)); Text("Cancel", fontSize = 11.sp)
                                }
                            }
                            TextButton(onClick = {
                                val content = if (isReq) request.headersAsString() else response?.headersAsString() ?: ""
                                clipboard.setText(AnnotatedString(content)); snack = "Copied!"
                            }, colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)) {
                                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp)); Text("Copy", fontSize = 11.sp)
                            }
                        }
                        // HEX tab actions
                        2 -> {
                            TextButton(onClick = {
                                val content = if (isReq) request.body?.let { HexUtils.toHexDump(it) } ?: "" else response?.body?.let { HexUtils.toHexDump(it) } ?: ""
                                clipboard.setText(AnnotatedString(content)); snack = "Copied!"
                            }, colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)) {
                                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp)); Text("Copy Hex", fontSize = 11.sp)
                            }
                            TextButton(onClick = { showDecodeOverlay = true }, colors = ButtonDefaults.textButtonColors(contentColor = Amber)) {
                                Icon(Icons.Default.Code, null, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp)); Text("Decode Window", fontSize = 11.sp)
                            }
                        }
                    }
                }

                HorizontalDivider(color = DividerGray, thickness = 0.5.dp)

                // Content display / editor
                AnimatedContent(
                    targetState = Triple(subTab, editingBody, editingHeaders),
                    transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) },
                    label = "content"
                ) { (tab, isEditingBody, isEditingHeaders) ->
                    when (tab) {
                        0 -> { // BODY
                            if (isEditingBody) {
                                // EDITABLE body editor
                                Column(Modifier.fillMaxSize()) {
                                    Row(Modifier.fillMaxWidth().background(NeonGreen.copy(0.06f)).padding(horizontal = 12.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Edit, null, tint = NeonGreen, modifier = Modifier.size(13.dp))
                                        Text("EDITING ${if (isReq) "REQUEST" else "RESPONSE"} BODY",
                                            color = NeonGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.weight(1f))
                                        TextButton(onClick = {
                                            bodyEditText = try {
                                                DecodeUtils.prettyPrintJson(bodyEditText).ifEmpty { bodyEditText }
                                            } catch (_: Exception) { bodyEditText }
                                        }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                                            Text("Format JSON", color = Amber, fontSize = 10.sp)
                                        }
                                    }
                                    OutlinedTextField(
                                        value = bodyEditText,
                                        onValueChange = { bodyEditText = it },
                                        modifier = Modifier.fillMaxSize().padding(8.dp),
                                        textStyle = LocalTextStyle.current.copy(
                                            fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextBright, lineHeight = 17.sp
                                        ),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = NeonGreen, unfocusedBorderColor = DividerGray,
                                            focusedTextColor = TextBright, unfocusedTextColor = TextPrimary, cursorColor = NeonGreen,
                                            focusedContainerColor = ElevatedBlack, unfocusedContainerColor = ElevatedBlack
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            } else {
                                val content = if (isReq) request.bodyText ?: "(empty)" else response?.bodyText ?: "(waiting…)"
                                val color = if (isReq) NeonGreen.copy(0.9f) else ElectricBlue.copy(0.9f)
                                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
                                    Text(content, color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 17.sp)
                                }
                            }
                        }
                        1 -> { // HEADERS
                            if (isEditingHeaders) {
                                Column(Modifier.fillMaxSize()) {
                                    Row(Modifier.fillMaxWidth().background(ElectricBlue.copy(0.06f)).padding(horizontal = 12.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Http, null, tint = ElectricBlue, modifier = Modifier.size(13.dp))
                                        Text("EDITING ${if (isReq) "REQUEST" else "RESPONSE"} HEADERS",
                                            color = ElectricBlue, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    }
                                    Text("Format: Header-Name: value  (one per line)",
                                        color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
                                    OutlinedTextField(
                                        value = headersEditText,
                                        onValueChange = { headersEditText = it },
                                        modifier = Modifier.fillMaxSize().padding(8.dp),
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
                                }
                            } else {
                                val content = if (isReq) request.headersAsString() else response?.headersAsString() ?: "(no response)"
                                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
                                    Text(content, color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 17.sp)
                                }
                            }
                        }
                        else -> { // HEX
                            val content = if (isReq) request.body?.let { HexUtils.toHexDump(it) } ?: "(no hex)" else response?.body?.let { HexUtils.toHexDump(it) } ?: "(no hex)"
                            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
                                Text(content, color = Amber.copy(0.85f), fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 17.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Decode overlay
    if (showDecodeOverlay) {
        FloatingDecodeOverlay(
            request = request,
            response = response,
            onDismiss = { showDecodeOverlay = false },
            onSaveMod = { body ->
                onSaveMod(request.endpoint, body)
                showDecodeOverlay = false; snack = "✓ Mod saved!"
            }
        )
    }

    snack?.let { msg ->
        LaunchedEffect(msg) { kotlinx.coroutines.delay(1800); snack = null }
        Box(Modifier.fillMaxSize().padding(bottom = 16.dp), Alignment.BottomCenter) {
            Snackbar(containerColor = ElevatedBlack) { Text(msg, color = NeonGreen, fontSize = 12.sp) }
        }
    }
}

/** Convert "Key: Value\nKey2: Value2" text to JSON map for header mod */
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
