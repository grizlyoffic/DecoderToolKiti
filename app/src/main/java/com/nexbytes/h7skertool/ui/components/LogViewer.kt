package com.nexbytes.h7skertool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexbytes.h7skertool.model.LogEntry
import com.nexbytes.h7skertool.model.LogLevel
import com.nexbytes.h7skertool.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LogViewer(logs: List<LogEntry>, onClear: () -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        // Toolbar
        Row(
            Modifier.fillMaxWidth().background(CardBlack)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            Arrangement.SpaceBetween, Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Terminal, null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                Text("LOG STREAM", color = NeonGreen, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Badge(containerColor = NeonGreen.copy(0.12f)) {
                    Text("${logs.size}", color = NeonGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { scope.launch { if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1) } },
                    modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.VerticalAlignBottom, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.DeleteSweep, null, tint = AlertRed, modifier = Modifier.size(16.dp))
                }
            }
        }
        Divider(color = DividerGray, thickness = 0.5.dp)

        if (logs.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Terminal, null, tint = TextDim, modifier = Modifier.size(36.dp))
                    Text("No logs yet — start capturing", color = TextDim, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().background(DeepBlack),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(logs, key = { it.id }) { entry ->
                    LogLine(entry)
                }
            }
        }
    }
}

@Composable
private fun LogLine(entry: LogEntry) {
    val (color, prefix) = when (entry.level) {
        LogLevel.DEBUG   -> Pair(TextSecondary, "D")
        LogLevel.INFO    -> Pair(NeonGreen,     "I")
        LogLevel.WARNING -> Pair(Amber,         "W")
        LogLevel.ERROR   -> Pair(AlertRed,      "E")
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
            .background(if (entry.level == LogLevel.ERROR) AlertRed.copy(0.04f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(entry.timestamp)),
            color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(52.dp))
        Text("$prefix/${entry.tag}".padEnd(14),
            color = color, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium, modifier = Modifier.width(84.dp))
        Text(entry.message, color = if (entry.level == LogLevel.ERROR) AlertRed.copy(0.9f) else TextPrimary.copy(0.85f),
            fontSize = 10.sp, fontFamily = FontFamily.Monospace, lineHeight = 14.sp, modifier = Modifier.weight(1f))
    }
}
