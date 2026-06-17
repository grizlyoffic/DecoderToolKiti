package com.nexbytes.h7skertool.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.nexbytes.h7skertool.model.CapturedRequest
import com.nexbytes.h7skertool.model.CapturedResponse
import com.nexbytes.h7skertool.model.LogEntry
import com.nexbytes.h7skertool.model.LogLevel
import com.nexbytes.h7skertool.service.ProxyForegroundService
import com.nexbytes.h7skertool.service.ShizukuFileService
import com.nexbytes.h7skertool.session.SessionManager
import com.nexbytes.h7skertool.shizuku.ShizukuManager
import com.nexbytes.h7skertool.utils.ModFile
import com.nexbytes.h7skertool.utils.ModManager
import com.nexbytes.h7skertool.utils.ModType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import rikka.shizuku.Shizuku
import java.io.File
import java.util.concurrent.TimeUnit

data class AppUiState(
    val shizukuAvailable: Boolean = false,
    val shizukuPermissionGranted: Boolean = false,
    val isVerified: Boolean = false,
    val isVerifying: Boolean = false,
    val verifyError: String? = null,
    val clientUrl: String = "",
    val isCapturing: Boolean = false,
    val requests: List<CapturedRequest> = emptyList(),
    val responses: Map<String, CapturedResponse> = emptyMap(),
    val logs: List<LogEntry> = emptyList(),
    val savedMods: Map<String, String> = emptyMap(),
    val searchQuery: String = "",
    val endpointFilter: String? = null,
    val fileWriteStatus: List<String> = emptyList(),
    val errorMessage: String? = null,
    val username: String = ""
) {
    val needsShizuku   get() = !shizukuAvailable || !shizukuPermissionGranted
    val needsPassword  get() = shizukuAvailable && shizukuPermissionGranted && !isVerified
    val needsClientUrl get() = shizukuAvailable && shizukuPermissionGranted && isVerified && clientUrl.isEmpty()

    val filteredRequests: List<CapturedRequest> get() {
        var list = requests
        if (searchQuery.isNotBlank())
            list = list.filter { r ->
                r.endpoint.contains(searchQuery, true) ||
                r.url.contains(searchQuery, true) ||
                r.bodyText?.contains(searchQuery, true) == true
            }
        if (endpointFilter != null) list = list.filter { it.endpoint == endpointFilter }
        return list
    }

    val allEndpoints: List<String> get() = requests.map { it.endpoint }.distinct().sorted()
}

class CaptureViewModel(app: Application) : AndroidViewModel(app) {
    private val TAG = "CaptureVM"
    private val session = SessionManager(app)
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private val _savedModFiles = MutableStateFlow<List<ModFile>>(emptyList())
    val savedModFiles: StateFlow<List<ModFile>> = _savedModFiles.asStateFlow()

    private val _selectedMod = MutableStateFlow<ModFile?>(null)
    val selectedMod: StateFlow<ModFile?> = _selectedMod.asStateFlow()

    private val _importResult = MutableStateFlow<String?>(null)
    val importResult: StateFlow<String?> = _importResult.asStateFlow()

    private val captureChannel = Channel<Pair<CapturedRequest, CapturedResponse>>(capacity = Channel.UNLIMITED)

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        _state.update { it.copy(shizukuPermissionGranted = result == PackageManager.PERMISSION_GRANTED) }
    }
    private var callbacksRegistered = false
    private val gson = GsonBuilder()
        .setLenient()
        .create()

    init {
        observeSession()
        startShizukuPoller()
        startCaptureConsumer()
        viewModelScope.launch(Dispatchers.IO) { refreshModFiles() }
    }

    // ── Capture batching ──────────────────────────────────────────────────────

    private fun startCaptureConsumer() {
        viewModelScope.launch {
            val batch = mutableListOf<Pair<CapturedRequest, CapturedResponse>>()
            while (true) {
                val first = captureChannel.receiveCatching().getOrNull() ?: break
                batch.add(first)
                delay(80)
                while (true) {
                    val next = captureChannel.tryReceive().getOrNull() ?: break
                    batch.add(next)
                }
                if (batch.isNotEmpty()) {
                    val snapshot = batch.toList(); batch.clear()
                    _state.update { s ->
                        var reqs = s.requests
                        var resps = s.responses.toMutableMap()
                        for ((req, res) in snapshot) {
                            reqs = listOf(req) + reqs
                            resps[req.id] = res
                        }
                        val entries = resps.entries.toList()
                        val limited = if (entries.size > 1000) {
                            entries.takeLast(1000).associate { it.toPair() }
                        } else {
                            resps
                        }
                        s.copy(
                            requests = reqs.take(1000),
                            responses = limited
                        )
                    }
                }
            }
        }
    }

    // ── Session ───────────────────────────────────────────────────────────────

    private fun observeSession() {
        viewModelScope.launch { session.isVerified.collect { v -> _state.update { it.copy(isVerified = v) } } }
        viewModelScope.launch { session.clientUrl.collect { url -> _state.update { it.copy(clientUrl = url) } } }
        viewModelScope.launch { session.username.collect { u -> _state.update { it.copy(username = u) } } }
    }

    private fun setupCallbacks() {
        if (callbacksRegistered) return; callbacksRegistered = true
        ProxyForegroundService.onCapture = { req, res ->
            captureChannel.trySend(req to res)
        }
        ProxyForegroundService.onLog = { msg ->
            viewModelScope.launch(Dispatchers.Main) { log(LogLevel.INFO, "Proxy", msg) }
        }
    }

    private fun startShizukuPoller() {
        viewModelScope.launch {
            while (true) {
                val avail = ShizukuManager.isShizukuAvailable()
                val granted = if (avail) ShizukuManager.hasPermission() else false
                _state.update { it.copy(shizukuAvailable = avail, shizukuPermissionGranted = granted) }
                delay(2000)
            }
        }
    }

    // ── Shizuku ───────────────────────────────────────────────────────────────

    fun checkShizuku() {
        val avail = ShizukuManager.isShizukuAvailable()
        _state.update { it.copy(shizukuAvailable = avail, shizukuPermissionGranted = if (avail) ShizukuManager.hasPermission() else false) }
    }

    fun requestShizukuPermission() { ShizukuManager.requestPermission(permissionListener) }

    // ── Auth ──────────────────────────────────────────────────────────────────

    fun verifyPassword(password: String) {
        if (password.isBlank()) { _state.update { it.copy(verifyError = "Password cannot be empty") }; return }
        _state.update { it.copy(isVerifying = true, verifyError = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = "http://node.mrkalpha.tech:19140/password=$password"
                val response = http.newCall(Request.Builder().url(url).get().build()).execute()
                val body = response.body?.string() ?: ""
                if (body.contains("\"result\":\"0\"") || body.contains("\"result\": \"0\"")) {
                    session.setVerified(true, username = password)
                    _state.update { it.copy(isVerifying = false, verifyError = null) }
                    log(LogLevel.INFO, "Auth", "Verified ✓")
                } else {
                    _state.update { it.copy(isVerifying = false, verifyError = "Invalid password. Access denied.") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isVerifying = false, verifyError = "Network error: ${e.message}") }
            }
        }
    }

    fun setClientUrl(url: String) {
        viewModelScope.launch { session.setClientUrl(url.trim().trimEnd('/')) }
    }

    // ── Capture control ───────────────────────────────────────────────────────

    fun startCapture() {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { setupCallbacks() }

            val activeMods = _savedModFiles.value.filter { it.enabled }
            val modsMap = _state.value.savedMods.toMutableMap()

            val results = ShizukuFileService.writeLocalConfigFiles()
            _state.update { it.copy(fileWriteStatus = results.map { r -> if (r.success) "✓ ${r.path}" else "✗ ${r.path}: ${r.error}" }) }

            ProxyForegroundService.savedMods = modsMap
            ProxyForegroundService.activeMods = activeMods
            withContext(Dispatchers.Main) {
                ProxyForegroundService.start(getApplication(), _state.value.clientUrl)
            }
            _state.update { it.copy(isCapturing = true) }
            log(LogLevel.INFO, "Proxy", "Started → ${_state.value.clientUrl}")
        }
    }

    fun stopCapture() {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { ProxyForegroundService.stop(getApplication()) }
            ShizukuFileService.removeLocalConfigFiles()
            _state.update { it.copy(isCapturing = false) }
            log(LogLevel.INFO, "Capture", "Stopped.")
        }
    }

    fun clearCaptures() { _state.update { it.copy(requests = emptyList(), responses = emptyMap()) } }
    fun setSearch(q: String) { _state.update { it.copy(searchQuery = q) } }
    fun setEndpointFilter(ep: String?) { _state.update { it.copy(endpointFilter = ep) } }

    // ── Helper: Extract ONLY changed fields ──────────────────────────────────

    /**
     * Extract only the fields that changed between original and modified response.
     * Converts numbers properly (no scientific notation, no .0)
     */
    private fun getChangedFields(original: String, modified: String): Map<String, Any> {
        return try {
            val originalMap = gson.fromJson(original, Map::class.java) as Map<String, Any>
            val modifiedMap = gson.fromJson(modified, Map::class.java) as Map<String, Any>
            
            val changed = mutableMapOf<String, Any>()
            for ((key, value) in modifiedMap) {
                if (originalMap[key] != value) {
                    val cleanedValue = cleanValue(value)
                    changed[key] = cleanedValue
                }
            }
            Log.d(TAG, "Changed fields: ${changed.keys}")
            changed
        } catch (e: Exception) {
            Log.e(TAG, "getChangedFields error: ${e.message}")
            emptyMap()
        }
    }

    /**
     * Clean value: Convert Double to Long if it's a whole number
     */
    private fun cleanValue(value: Any): Any {
        return when (value) {
            is Double -> {
                if (value % 1.0 == 0.0) {
                    value.toLong()
                } else {
                    value
                }
            }
            is Map<*, *> -> {
                val cleaned = mutableMapOf<String, Any>()
                for ((k, v) in value) {
                    if (v != null) {
                        cleaned[k.toString()] = cleanValue(v)
                    }
                }
                cleaned
            }
            is List<*> -> {
                val cleaned = mutableListOf<Any>()
                for (item in value) {
                    if (item != null) {
                        cleaned.add(cleanValue(item))
                    }
                }
                cleaned
            }
            else -> value
        }
    }

    /**
     * Get original response body for an endpoint from cache
     */
    private fun getOriginalResponseBody(endpoint: String): String? {
        return _state.value.responses.values
            .find { it.endpoint == endpoint }
            ?.bodyText
    }

    // ── Mod management ────────────────────────────────────────────────────────

    /**
     * Save a modification - ONLY changed fields are saved!
     */
    fun saveModification(endpoint: String, body: String, section: String = "response") {
        viewModelScope.launch {
            val key = when (section) {
                "headers"  -> "${endpoint}__headers"
                "request"  -> "${endpoint}__req"
                else       -> "${endpoint}__resp"
            }
            
            val originalBody = if (section == "response") {
                getOriginalResponseBody(endpoint)
            } else null
            
            val changedFields = if (originalBody != null && section == "response") {
                getChangedFields(originalBody, body)
            } else {
                try {
                    val map = gson.fromJson(body, Map::class.java) as Map<String, Any>
                    map.mapValues { cleanValue(it.value) }
                } catch (_: Exception) {
                    mapOf("_raw" to body)
                }
            }
            
            if (changedFields.isEmpty()) {
                log(LogLevel.INFO, "Mod", "No changes detected for $endpoint")
                return@launch
            }
            
            val modName = endpoint.trimStart('/').replace("/", "_").take(40)
                .ifEmpty { "mod_${System.currentTimeMillis()}" }
            val modType = when (section) {
                "headers"  -> ModType.HEADER
                "request"  -> ModType.REQUEST
                else       -> ModType.RESPONSE
            }
            
            val modContent = gson.toJson(changedFields)
            
            withContext(Dispatchers.IO) {
                ModManager.saveModFromContent(
                    getApplication(), 
                    modName, 
                    endpoint, 
                    modContent,
                    modType
                )
                refreshModFiles()
            }
            
            val mods = _state.value.savedMods.toMutableMap()
            mods[key] = modContent
            _state.update { it.copy(savedMods = mods) }
            ProxyForegroundService.savedMods = mods
            
            log(LogLevel.INFO, "Mod", "Saved '$modName' with ${changedFields.size} field changes [$section]")
        }
    }

    /** Called from the FloatingDecodeOverlay when creating a full mod */
    fun saveModFile(name: String, content: String, endpoint: String = "", type: ModType = ModType.RESPONSE) {
        viewModelScope.launch(Dispatchers.IO) {
            val finalContent = if (endpoint.isNotBlank() && type == ModType.RESPONSE) {
                val original = getOriginalResponseBody(endpoint)
                if (original != null) {
                    val changed = getChangedFields(original, content)
                    if (changed.isNotEmpty()) {
                        gson.toJson(changed)
                    } else {
                        content
                    }
                } else {
                    content
                }
            } else {
                content
            }
            
            ModManager.saveModFromContent(getApplication(), name, endpoint, finalContent, type)
            refreshModFiles()
            log(LogLevel.INFO, "ModFile", "Saved: $name with changes")
        }
    }

    fun saveMod(context: android.content.Context, mod: ModFile) {
        viewModelScope.launch(Dispatchers.IO) {
            ModManager.saveMod(context, mod)
            refreshModFiles()
        }
    }

    fun deleteModFile(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ModManager.deleteMod(getApplication(), name)
            if (_selectedMod.value?.name == name) _selectedMod.value = null
            refreshModFiles()
        }
    }

    fun toggleModEnabled(name: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            ModManager.setEnabled(getApplication(), name, enabled)
            refreshModFiles()
            if (_state.value.isCapturing) {
                ProxyForegroundService.activeMods = _savedModFiles.value.filter { it.enabled }
            }
        }
    }

    fun exportMod(mod: ModFile): File? =
        ModManager.exportMod(getApplication(), mod)

    fun importMod(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val mod = ModManager.importMod(getApplication(), uri)
            if (mod != null) {
                refreshModFiles()
                _importResult.value = "Imported: ${mod.name}"
            } else {
                _importResult.value = "Import failed — invalid file"
            }
        }
    }

    fun clearImportResult() { _importResult.value = null }

    fun loadModFiles() {
        viewModelScope.launch(Dispatchers.IO) { refreshModFiles() }
    }

    private suspend fun refreshModFiles() {
        val mods = ModManager.loadMods(getApplication())
        Log.d(TAG, "Loaded ${mods.size} mods")
        _savedModFiles.value = mods
    }

    fun selectMod(mod: ModFile?) { _selectedMod.value = mod }

    fun applyModToProxy(mod: ModFile) {
        val mods = _state.value.savedMods.toMutableMap()
        mods["${mod.endpoint}__resp"] = mod.rawContent
        _state.update { it.copy(savedMods = mods) }
        ProxyForegroundService.savedMods = mods
        ProxyForegroundService.activeMods = _savedModFiles.value.filter { it.enabled }
        log(LogLevel.INFO, "Mod", "Applied: ${mod.name}")
    }

    fun clearModifications() {
        _state.update { it.copy(savedMods = emptyMap()) }
        ProxyForegroundService.savedMods = emptyMap()
        ProxyForegroundService.activeMods = emptyList()
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    fun logout() {
        viewModelScope.launch {
            if (_state.value.isCapturing) stopCapture()
            session.logout()
            _state.update { AppUiState() }
            _selectedMod.value = null
        }
    }

    fun resetAll() {
        viewModelScope.launch {
            if (_state.value.isCapturing) stopCapture()
            session.resetAll()
            _state.update { AppUiState() }
            _selectedMod.value = null
        }
    }

    private fun log(level: LogLevel, tag: String, msg: String) {
        _state.update { s ->
            s.copy(logs = (s.logs + LogEntry(level = level, tag = tag, message = msg)).takeLast(500))
        }
    }

    override fun onCleared() {
        super.onCleared()
        ShizukuManager.removePermissionListener(permissionListener)
        ProxyForegroundService.onCapture = null
        ProxyForegroundService.onLog = null
        callbacksRegistered = false
    }
}
