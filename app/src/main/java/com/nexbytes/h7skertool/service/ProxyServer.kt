package com.nexbytes.h7skertool.service

import android.util.Log
import com.nexbytes.h7skertool.model.CapturedRequest
import com.nexbytes.h7skertool.model.CapturedResponse
import com.nexbytes.h7skertool.utils.HexUtils
import com.nexbytes.h7skertool.utils.ModFile
import com.nexbytes.h7skertool.utils.ProtoModifier
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * ProxyServer — intercepts HTTP traffic, applies mods, and fires capture callbacks.
 *
 * Mod key conventions (savedMods):
 *   "<endpoint>__req"     → request body override  (proto JSON map)
 *   "<endpoint>__resp"    → response body override (proto JSON map)
 *   "<endpoint>__headers" → header override        (plain key:value JSON map)
 *   "__active_mod__"      → currently selected ModFile serialized as JSON
 *
 * Headers and bodies are ALWAYS kept in separate maps and NEVER cross-contaminate.
 */
class ProxyServer(
    private val clientBaseUrl: String,
    private val scope: CoroutineScope,
    private val savedMods: Map<String, String>,
    private val activeMods: List<ModFile> = emptyList(),
    private val onCapture: (CapturedRequest, CapturedResponse) -> Unit,
    private val onLog: (String) -> Unit
) : NanoHTTPD("127.0.0.1", 8080) {

    private val TAG = "ProxyServer"

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)   // avoid double-processing
        .build()

    override fun serve(session: IHTTPSession): Response {
        val method = session.method.name
        val path   = session.uri
        val endpoint = extractEndpoint(path)
        val start  = System.currentTimeMillis()
        onLog("→ $method $endpoint")

        // ── Read original request headers ──────────────────────────────────
        val reqHeaders = session.headers.toMutableMap()

        // ── Read request body ──────────────────────────────────────────────
        val bodyBytes: ByteArray? = try {
            val len = reqHeaders["content-length"]?.toLongOrNull() ?: 0L
            if (len in 1..10_000_000L) {
                val buf = ByteArray(len.toInt()); var off = 0
                while (off < buf.size) {
                    val r = session.inputStream.read(buf, off, buf.size - off)
                    if (r == -1) break; off += r
                }
                buf
            } else null
        } catch (_: Exception) { null }

        // ── Apply HEADER mods (only touches headers, never touches body) ───
        val finalHeaders = applyHeaderMod(endpoint, reqHeaders)

        // ── Apply REQUEST BODY mods (only touches body, never touches headers)
        val finalBody = applyRequestBodyMod(endpoint, bodyBytes)

        val capturedReq = CapturedRequest(
            method = method,
            url    = "$clientBaseUrl$path",
            endpoint = endpoint,
            headers = finalHeaders,
            body    = finalBody,
            bodyText = finalBody?.let { runCatching { String(it, Charsets.UTF_8) }.getOrNull() },
            bodyHex  = HexUtils.toHexDump(finalBody)
        )

        return try {
            val realResp = forwardRequest(method, path, finalHeaders, finalBody)
            val duration = System.currentTimeMillis() - start

            // ── Apply RESPONSE BODY mod ────────────────────────────────────
            val modResult = applyResponseMod(endpoint, realResp)

            val capturedRes = CapturedResponse(
                requestId     = capturedReq.id,
                statusCode    = realResp.code,
                statusMessage = realResp.message ?: "",
                endpoint      = endpoint,
                headers       = modResult.headers,
                body          = modResult.bytes,
                bodyText      = modResult.text,
                bodyHex       = modResult.hex,
                durationMs    = duration
            )
            onLog("← ${realResp.code} $endpoint (${duration}ms)")
            scope.launch { onCapture(capturedReq, capturedRes) }
            realResp.close()

            val mime = modResult.headers["content-type"] ?: "application/octet-stream"
            val resp = newFixedLengthResponse(
                Response.Status.lookup(realResp.code) ?: Response.Status.OK,
                mime,
                modResult.bytes?.inputStream(),
                (modResult.bytes?.size ?: 0).toLong()
            )
            modResult.headers.forEach { (k, v) ->
                if (!k.equals("content-length", true) && !k.equals("transfer-encoding", true))
                    resp.addHeader(k, v)
            }
            resp
        } catch (e: IOException) {
            onLog("✗ $endpoint: ${e.message}")
            val errRes = CapturedResponse(
                requestId = capturedReq.id, statusCode = 503, statusMessage = "Proxy Error",
                endpoint = endpoint, headers = emptyMap(), body = null,
                bodyText = e.message, bodyHex = null, durationMs = -1L
            )
            scope.launch { onCapture(capturedReq, errRes) }
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Proxy error: ${e.message}")
        }
    }

    // ── Forward to real server ────────────────────────────────────────────────

    private fun forwardRequest(
        method: String, path: String,
        headers: Map<String, String>, body: ByteArray?
    ): okhttp3.Response {
        val url = "$clientBaseUrl$path"
        val ct  = headers["content-type"]?.toMediaTypeOrNull()
        val reqBody = when {
            body != null && method !in listOf("GET", "HEAD") -> body.toRequestBody(ct)
            method !in listOf("GET", "HEAD") -> ByteArray(0).toRequestBody(ct)
            else -> null
        }
        val builder = okhttp3.Request.Builder().url(url)
        val host = clientBaseUrl.removePrefix("https://").removePrefix("http://").split("/").first()
        val skipHeaders = setOf("host", "connection", "transfer-encoding", "content-length", "keep-alive", "proxy-connection")
        headers.forEach { (k, v) ->
            if (k.lowercase() !in skipHeaders) runCatching { builder.addHeader(k, v) }
        }
        builder.header("Host", host)
        return http.newCall(builder.method(method, reqBody).build()).execute()
    }

    // ── Header mod (ONLY updates headers map, never touches body) ────────────

    private fun applyHeaderMod(endpoint: String, headers: MutableMap<String, String>): MutableMap<String, String> {
        // Check active mods list for header-type mods on this endpoint
        val activeHeaderMod = activeMods.firstOrNull {
            it.enabled && it.endpoint == endpoint && it.type == "HEADER"
        }
        val modJson = activeHeaderMod?.rawContent
            ?: savedMods["${endpoint}__headers"]
            ?: return headers

        val modified = headers.toMutableMap()
        return try {
            val gson = com.google.gson.Gson()
            @Suppress("UNCHECKED_CAST")
            val map = gson.fromJson(modJson, Map::class.java) as? Map<String, Any> ?: return modified
            for ((k, v) in map) {
                val headerKey = k.trim()
                val headerVal = v?.toString()?.trim() ?: ""
                if (headerVal.isEmpty() || headerVal == "null") {
                    modified.remove(headerKey)
                    modified.remove(headerKey.lowercase())
                } else {
                    // Set/replace the header — using lowercase key consistent with NanoHTTPD
                    modified[headerKey.lowercase()] = headerVal
                }
            }
            Log.d("ProxyServer", "Applied header mod for $endpoint: ${map.keys}")
            modified
        } catch (e: Exception) {
            Log.e("ProxyServer", "applyHeaderMod error: ${e.message}")
            modified
        }
    }

    // ── Request body mod (ONLY updates body bytes, never touches headers) ─────

    private fun applyRequestBodyMod(endpoint: String, body: ByteArray?): ByteArray? {
        if (body == null) return null
        val activeReqMod = activeMods.firstOrNull {
            it.enabled && it.endpoint == endpoint && it.type == "REQUEST"
        }
        val modJson = activeReqMod?.let {
            if (it.rawContent.isNotBlank()) it.rawContent
            else buildJsonFromRules(it)
        } ?: savedMods["${endpoint}__req"] ?: return body

        return safeApplyProtoMod(body, modJson, "request body")
    }

    // ── Response body mod ─────────────────────────────────────────────────────

    private fun applyResponseMod(endpoint: String, response: okhttp3.Response): ModResult {
        val origBytes = try { response.body?.bytes() } catch (_: Exception) { null }
        val responseHeaders = mutableMapOf<String, String>()
        response.headers.forEach { (k, v) -> responseHeaders[k] = v }

        if (origBytes == null) return ModResult(null, null, null, responseHeaders)

        val activeRespMod = activeMods.firstOrNull {
            it.enabled && it.endpoint == endpoint && it.type == "RESPONSE"
        }
        val modJson = activeRespMod?.let {
            if (it.rawContent.isNotBlank()) it.rawContent
            else buildJsonFromRules(it)
        } ?: savedMods["${endpoint}__resp"]

        if (modJson.isNullOrEmpty()) {
            return ModResult(origBytes, safeString(origBytes), HexUtils.toHexDump(origBytes), responseHeaders)
        }

        val modBytes = safeApplyProtoMod(origBytes, modJson, "response body")
        responseHeaders["content-length"] = modBytes.size.toString()
        return ModResult(modBytes, safeString(modBytes), HexUtils.toHexDump(modBytes), responseHeaders)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildJsonFromRules(mod: ModFile): String {
        val map = mod.rules
            .filter { it.action == "SET" && it.field.isNotBlank() }
            .associate { it.field to it.value }
        if (map.isEmpty()) return ""
        return com.google.gson.Gson().toJson(map)
    }

    private fun safeApplyProtoMod(body: ByteArray, modJson: String, context: String): ByteArray {
        return try {
            val fields = ProtoModifier.parseModFields(modJson)
            if (fields.isNotEmpty()) {
                val result = ProtoModifier.modifyProtoBytes(body, fields)
                Log.d(TAG, "Applied $context mod: ${fields.size} fields")
                result
            } else body
        } catch (e: Exception) {
            Log.e(TAG, "safeApplyProtoMod ($context): ${e.message} – keeping original")
            body // NEVER corrupt the packet
        }
    }

    private fun safeString(bytes: ByteArray) =
        runCatching { String(bytes, Charsets.UTF_8) }.getOrNull()

    private fun extractEndpoint(path: String) =
        "/" + (path.split("?").first().trimStart('/').split("/").firstOrNull { it.isNotEmpty() } ?: "")

    private data class ModResult(
        val bytes: ByteArray?,
        val text: String?,
        val hex: String?,
        val headers: MutableMap<String, String>
    )
}
