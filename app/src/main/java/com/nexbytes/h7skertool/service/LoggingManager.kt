package com.nexbytes.h7skertool.service

import android.content.Context
import android.util.Log
import com.nexbytes.h7skertool.model.CapturedRequest
import com.nexbytes.h7skertool.model.CapturedResponse
import com.nexbytes.h7skertool.utils.ExportUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class LoggingManager(private val context: Context) {
    private val TAG = "LoggingManager"
    private val sessionDir: File by lazy {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        File(context.getExternalFilesDir(null), "captures/session_$ts").also { it.mkdirs() }
    }

    suspend fun logCapture(request: CapturedRequest, response: CapturedResponse?) =
        withContext(Dispatchers.IO) {
            try {
                val name = sanitize(request.endpoint)
                File(sessionDir, "$name.txt").appendText(ExportUtils.buildUnifiedLog(request, response))
            } catch (e: Exception) { Log.e(TAG, "logCapture: ${e.message}") }
        }

    suspend fun logBinary(request: CapturedRequest, response: CapturedResponse?) =
        withContext(Dispatchers.IO) {
            try {
                val name = sanitize(request.endpoint)
                request.body?.let { File(sessionDir, "${name}_req.bin").writeBytes(it) }
                response?.body?.let { File(sessionDir, "${name}_res.bin").writeBytes(it) }
            } catch (e: Exception) { Log.e(TAG, "logBinary: ${e.message}") }
        }

    // FIX: Function ka naam badal diya taake platform clash na ho
    fun getSessionDirectory(): File = sessionDir

    private fun sanitize(name: String): String {
        val c = name.trimStart('/').replace('/', '_').replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        return c.ifEmpty { "unknown" }
    }
}
