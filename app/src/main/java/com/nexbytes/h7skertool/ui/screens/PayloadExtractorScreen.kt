package com.nexbytes.h7skertool.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import com.nexbytes.h7skertool.ui.theme.*
import com.nexbytes.h7skertool.utils.DecodeUtils
import com.nexbytes.h7skertool.utils.HexUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private fun parseApiResp(raw: String?): String {
    if (raw.isNullOrBlank()) return "⚠️ Empty API response"
    val t = raw.trim()
    return try {
        val j = JSONObject(t)
        listOf("decoded","result","data","output","text").mapNotNull { k -> j.optString(k).ifEmpty { null } }.firstOrNull()
            ?.let { DecodeUtils.prettyPrintJson(it).ifEmpty { it } } ?: DecodeUtils.prettyPrintJson(t).ifEmpty { t }
    } catch (_: Exception) { t }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayloadExtractorScreen(onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val http = remember { OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).build() }

    var hexInput by remember { mutableStateOf("") }
    var extractedHex by remember { mutableStateOf("") }
    var extractedRaw by remember { mutableStateOf("") }
    var decodedOutput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var mode by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var snack by remember { mutableStateOf<String?>(null) }

    fun extract() {
        val raw=hexInput.trim(); if(raw.isEmpty()){error="Paste hex data first";return}
        error=null; extractedHex=raw.replace("\n"," ").replace("  "," ").trim()
        val bytes=HexUtils.hexToBytes(extractedHex)
        extractedRaw=if(bytes!=null) try{String(bytes,Charsets.UTF_8)}catch(_:Exception){"(binary data, ${bytes.size} bytes)"}else "(invalid hex — check format)"
    }

    fun decodeApi() {
        val hex=extractedHex.trim(); if(hex.isEmpty()){error="Extract first";return}
        isLoading=true; error=null
        scope.launch {
            try {
                val url=if(mode==0)"http://node.mrkalpha.tech:19140/request" else "http://node.mrkalpha.tech:19140/response"
                val reqBody=JSONObject().put("hex",hex).toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val resp=withContext(Dispatchers.IO){http.newCall(Request.Builder().url(url).post(reqBody).build()).execute()}
                val rawBody=withContext(Dispatchers.IO){resp.body?.string()}
                withContext(Dispatchers.Main){isLoading=false;decodedOutput=parseApiResp(rawBody)}
            } catch(e:Exception){withContext(Dispatchers.Main){isLoading=false;error="Decode failed: ${e.message}"}}
        }
    }

    Scaffold(
        containerColor=DeepBlack,
        topBar={TopAppBar(title={Text("PAYLOAD EXTRACTOR",color=Amber,fontFamily=FontFamily.Monospace,fontSize=14.sp,fontWeight=FontWeight.Bold,letterSpacing=2.sp)},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null,tint=TextPrimary)}},colors=TopAppBarDefaults.topAppBarColors(containerColor=CardBlack))}
    ) { pv ->
        Column(Modifier.fillMaxSize().padding(pv).verticalScroll(rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardBlack).border(1.dp,DividerGray,RoundedCornerShape(10.dp))) {
                listOf("REQUEST" to NeonGreen,"RESPONSE" to ElectricBlue).forEachIndexed{i,(label,col)->
                    val sel=mode==i
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if(sel)col.copy(0.1f)else Color.Transparent).clickable{mode=i;decodedOutput=""}.padding(vertical=12.dp),Alignment.Center){Text(label,color=if(sel)col else TextSecondary,fontFamily=FontFamily.Monospace,fontSize=12.sp,fontWeight=if(sel)FontWeight.Bold else FontWeight.Normal)}
                }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBlack).border(1.dp,DividerGray,RoundedCornerShape(12.dp)).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(),Arrangement.SpaceBetween,Alignment.CenterVertically){Text("HEX INPUT",color=Amber,fontSize=10.sp,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Bold,letterSpacing=2.sp);IconButton(onClick={clipboard.getText()?.text?.let{hexInput=it;error=null}},modifier=Modifier.size(32.dp)){Icon(Icons.Default.ContentPaste,null,tint=Amber,modifier=Modifier.size(16.dp))}}
                OutlinedTextField(value=hexInput,onValueChange={hexInput=it;extractedHex="";extractedRaw="";decodedOutput=""},modifier=Modifier.fillMaxWidth(),placeholder={Text("Paste hex dump here…",color=TextDim,fontSize=11.sp,fontFamily=FontFamily.Monospace)},textStyle=LocalTextStyle.current.copy(fontFamily=FontFamily.Monospace,fontSize=11.sp,color=TextBright),colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Amber,unfocusedBorderColor=DividerGray,cursorColor=Amber),shape=RoundedCornerShape(8.dp),minLines=3,maxLines=7)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Button(onClick=::extract,modifier=Modifier.weight(1f),shape=RoundedCornerShape(8.dp),colors=ButtonDefaults.buttonColors(containerColor=Amber)){Icon(Icons.Default.ContentCut,null,tint=Color.Black,modifier=Modifier.size(14.dp));Spacer(Modifier.width(6.dp));Text("EXTRACT",color=Color.Black,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Bold,fontSize=12.sp)}
                    val dc=if(mode==0)NeonGreen else ElectricBlue
                    Button(onClick=::decodeApi,enabled=!isLoading&&extractedHex.isNotEmpty(),modifier=Modifier.weight(1f),shape=RoundedCornerShape(8.dp),colors=ButtonDefaults.buttonColors(containerColor=dc)){
                        if(isLoading)CircularProgressIndicator(modifier=Modifier.size(14.dp),strokeWidth=2.dp,color=Color.Black) else Icon(Icons.Default.Code,null,tint=Color.Black,modifier=Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp));Text("DECODE API",color=Color.Black,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Bold,fontSize=12.sp)
                    }
                }
            }
            error?.let{e->Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(AlertRed.copy(0.08f)).border(1.dp,AlertRed.copy(0.3f),RoundedCornerShape(8.dp)).padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){Icon(Icons.Default.Error,null,tint=AlertRed,modifier=Modifier.size(16.dp));Text(e,color=AlertRed,fontSize=12.sp,fontFamily=FontFamily.Monospace,modifier=Modifier.weight(1f));IconButton(onClick={error=null},modifier=Modifier.size(24.dp)){Icon(Icons.Default.Close,null,tint=AlertRed,modifier=Modifier.size(12.dp))}}}
            if(extractedHex.isNotEmpty()) OutBlock("EXTRACTED HEX",extractedHex,Amber,clipboard){snack="Hex copied!"}
            if(extractedRaw.isNotEmpty()) OutBlock("RAW TEXT",extractedRaw,NeonGreen,clipboard){snack="Raw copied!"}
            if(decodedOutput.isNotEmpty()) OutBlock("DECODED",decodedOutput,if(mode==0)NeonGreen else ElectricBlue,clipboard){snack="Decoded copied!"}
            Spacer(Modifier.height(80.dp))
        }
    }
    snack?.let{msg->LaunchedEffect(msg){kotlinx.coroutines.delay(1500);snack=null};Box(Modifier.fillMaxSize().padding(bottom=24.dp),Alignment.BottomCenter){Snackbar(containerColor=ElevatedBlack){Text(msg,color=NeonGreen,fontSize=12.sp)}}}
}

@Composable
private fun OutBlock(label:String,content:String,color:androidx.compose.ui.graphics.Color,clipboard:androidx.compose.ui.platform.ClipboardManager,onCopied:()->Unit){
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBlack).border(1.dp,color.copy(0.3f),RoundedCornerShape(12.dp)).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(Modifier.fillMaxWidth(),Arrangement.SpaceBetween,Alignment.CenterVertically){Text(label,color=color,fontSize=10.sp,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Bold,letterSpacing=2.sp);IconButton(onClick={clipboard.setText(AnnotatedString(content));onCopied()},modifier=Modifier.size(28.dp)){Icon(Icons.Default.ContentCopy,null,tint=color,modifier=Modifier.size(14.dp))}}
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(ElevatedBlack).padding(10.dp)){Text(content,color=TextBright,fontSize=11.sp,fontFamily=FontFamily.Monospace,lineHeight=17.sp)}
    }
}
