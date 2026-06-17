package com.nexbytes.h7skertool.ui.screens

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.nexbytes.h7skertool.ui.theme.*
import com.nexbytes.h7skertool.utils.ModFile
import com.nexbytes.h7skertool.utils.ModType
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModsScreen(
    mods: List<ModFile>,
    onBack: () -> Unit,
    onDeleteMod: (String) -> Unit,
    onApplyMod: (ModFile) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit = { _, _ -> },
    onExportMod: (ModFile) -> File? = { null },
    onImportMod: (android.net.Uri) -> Unit = {},
    importResult: String? = null,
    onClearImportResult: () -> Unit = {}
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(SortMode.NEWEST) }
    var expandedName by remember { mutableStateOf<String?>(null) }
    var deleteConfirm by remember { mutableStateOf<String?>(null) }
    var snack by remember { mutableStateOf<String?>(null) }
    val fmt = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.US) }

    // Import file picker
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onImportMod(uri)
        }
    }

    // Show import result as snack
    LaunchedEffect(importResult) {
        if (importResult != null) {
            snack = importResult
            onClearImportResult()
        }
    }

    val filtered = remember(mods, searchQuery, sortMode) {
        val q = searchQuery.trim().lowercase()
        var list = if (q.isEmpty()) mods
        else mods.filter {
            it.name.lowercase().contains(q) ||
            it.endpoint.lowercase().contains(q) ||
            it.type.lowercase().contains(q)
        }
        list = when (sortMode) {
            SortMode.NEWEST -> list.sortedByDescending { it.createdAt }
            SortMode.OLDEST -> list.sortedBy { it.createdAt }
            SortMode.NAME   -> list.sortedBy { it.name.lowercase() }
            SortMode.TYPE   -> list.sortedBy { it.type }
        }
        list
    }

    Scaffold(
        containerColor = DeepBlack,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("MOD MANAGER", color = NeonGreen, fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Text(
                                if (mods.isEmpty()) "no mods"
                                else "${mods.count { it.enabled }} active / ${mods.size} total",
                                color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = TextPrimary) }
                    },
                    actions = {
                        // Sort menu
                        var showSort by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showSort = true }) {
                                Icon(Icons.Default.Sort, "Sort", tint = TextSecondary)
                            }
                            DropdownMenu(expanded = showSort, onDismissRequest = { showSort = false },
                                modifier = Modifier.background(ElevatedBlack)) {
                                Text("Sort by", color = TextSecondary, fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                    fontFamily = FontFamily.Monospace)
                                SortMode.values().forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.label, color = if (sortMode == mode) NeonGreen else TextPrimary, fontSize = 13.sp) },
                                        onClick = { sortMode = mode; showSort = false },
                                        leadingIcon = {
                                            if (sortMode == mode)
                                                Icon(Icons.Default.Check, null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                                        }
                                    )
                                }
                            }
                        }
                        // Import button
                        IconButton(onClick = { importLauncher.launch("application/json") }) {
                            Icon(Icons.Default.FileDownload, "Import", tint = ElectricBlue)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBlack)
                )
                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).height(46.dp),
                    placeholder = { Text("Search mods…", color = TextDim, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty())
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Clear, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonGreen, unfocusedBorderColor = DividerGray,
                        focusedTextColor = TextBright, unfocusedTextColor = TextPrimary, cursorColor = NeonGreen
                    ),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }
    ) { pv ->
        if (mods.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pv), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Default.Build, null, tint = TextDim, modifier = Modifier.size(64.dp))
                    Text("No mods saved yet", color = TextSecondary, fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Open a captured request → Edit Fields\nthen create a mod from the decoded data.",
                        color = TextDim, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    OutlinedButton(
                        onClick = { importLauncher.launch("application/json") },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricBlue)
                    ) {
                        Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Import Mod", fontSize = 13.sp)
                    }
                }
            }
        } else {
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier.fillMaxSize().padding(pv),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (filtered.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                            Text("No mods match \"$searchQuery\"", color = TextDim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                items(filtered, key = { it.name + it.createdAt }) { mod ->
                    ModCard(
                        mod = mod,
                        isExpanded = expandedName == mod.name,
                        fmt = fmt,
                        clipboard = clipboard,
                        onToggleExpand = { expandedName = if (expandedName == mod.name) null else mod.name },
                        onToggleEnabled = { onToggleEnabled(mod.name, !mod.enabled) },
                        onApply = { onApplyMod(mod); snack = "✓ '${mod.name}' applied!" },
                        onCopy = { clipboard.setText(AnnotatedString(mod.rawContent)); snack = "Copied content!" },
                        onExport = {
                            val file = onExportMod(mod)
                            if (file != null) {
                                shareFile(context, file)
                                snack = "Exported: ${file.name}"
                            } else snack = "Export failed"
                        },
                        onDelete = { deleteConfirm = mod.name }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    // Delete confirm dialog
    deleteConfirm?.let { name ->
        AlertDialog(
            onDismissRequest = { deleteConfirm = null },
            containerColor = ElevatedBlack,
            title = { Text("Delete mod?", color = AlertRed, fontWeight = FontWeight.Bold) },
            text = { Text("'$name' will be permanently deleted.", color = TextSecondary, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { onDeleteMod(name); deleteConfirm = null; expandedName = null; snack = "Deleted '$name'" }) {
                    Text("DELETE", color = AlertRed, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = { TextButton(onClick = { deleteConfirm = null }) { Text("Cancel", color = TextSecondary) } }
        )
    }

    // Snackbar
    snack?.let { msg ->
        LaunchedEffect(msg) { kotlinx.coroutines.delay(2200); snack = null }
        Box(Modifier.fillMaxSize().padding(bottom = 24.dp), Alignment.BottomCenter) {
            Snackbar(containerColor = ElevatedBlack) {
                Text(msg, color = NeonGreen, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ModCard(
    mod: ModFile,
    isExpanded: Boolean,
    fmt: SimpleDateFormat,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    onToggleExpand: () -> Unit,
    onToggleEnabled: () -> Unit,
    onApply: () -> Unit,
    onCopy: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val typeColor = when (mod.type) {
        ModType.REQUEST.name  -> NeonGreen
        ModType.HEADER.name   -> ElectricBlue
        else                  -> NeonGreen.copy(0.75f)
    }
    val enabledColor by animateColorAsState(
        if (mod.enabled) NeonGreen else TextDim,
        animationSpec = tween(200), label = "enabled"
    )

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBlack)
            .border(1.dp, if (isExpanded) typeColor.copy(0.4f) else if (mod.enabled) NeonGreen.copy(0.15f) else DividerGray, RoundedCornerShape(12.dp))
    ) {
        // Header row
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggleExpand).padding(12.dp),
            Arrangement.SpaceBetween, Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Enable/disable switch
                Switch(
                    checked = mod.enabled,
                    onCheckedChange = { onToggleEnabled() },
                    modifier = Modifier.size(36.dp, 22.dp),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = NeonGreen,
                        uncheckedThumbColor = TextDim,
                        uncheckedTrackColor = DividerGray
                    )
                )
                Column(Modifier.widthIn(max = 160.dp)) {
                    Text(mod.name, color = TextBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (mod.endpoint.isNotBlank()) {
                            Text(mod.endpoint, color = ElectricBlue, fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 100.dp))
                        }
                        Box(Modifier.clip(RoundedCornerShape(4.dp)).background(typeColor.copy(0.12f)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                            Text(mod.type, color = typeColor, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(fmt.format(Date(mod.createdAt)), color = TextDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Apply button
                IconButton(onClick = onApply, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.PlayArrow, "Apply", tint = enabledColor, modifier = Modifier.size(18.dp))
                }
                // Export button
                IconButton(onClick = onExport, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.FileUpload, "Export", tint = Amber, modifier = Modifier.size(16.dp))
                }
                // Expand toggle
                IconButton(onClick = onToggleExpand, modifier = Modifier.size(34.dp)) {
                    Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Expanded content
        AnimatedVisibility(visible = isExpanded) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider(color = DividerGray, thickness = 0.5.dp)

                // Rules preview
                if (mod.rawContent.isNotEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(ElevatedBlack)
                            .border(1.dp, typeColor.copy(0.2f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            mod.rawContent.take(500) + if (mod.rawContent.length > 500) "\n…(${mod.rawContent.length - 500} more)" else "",
                            color = typeColor.copy(0.9f), fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace, lineHeight = 15.sp
                        )
                    }
                }

                // Rules list if available
                if (mod.rules.isNotEmpty()) {
                    Text("Rules (${mod.rules.size})", color = TextSecondary, fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                    mod.rules.forEach { rule ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                                .background(ElevatedBlack).padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.clip(RoundedCornerShape(4.dp)).background(NeonGreen.copy(0.1f)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                                Text("F:${rule.field}", color = NeonGreen, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            }
                            Text("→", color = TextDim, fontSize = 10.sp)
                            Text(rule.value.take(60), color = TextBright, fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        }
                    }
                }

                // Action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCopy, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onExport, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber)
                    ) {
                        Icon(Icons.Default.FileUpload, null, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Export", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onDelete, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed)
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun shareFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Export Mod"))
    } catch (_: Exception) {}
}

enum class SortMode(val label: String) {
    NEWEST("Newest First"),
    OLDEST("Oldest First"),
    NAME("Name A→Z"),
    TYPE("By Type")
}
