package com.nexbytes.h7skertool.utils

import android.util.Base64
import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import java.nio.charset.Charset

object DecodeUtils {

    private val TAG = "DecodeUtils"
    private val gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()

    data class DecodedField(
        val key: String,
        val value: String,
        val type: String,
        val path: String
    )

    /**
     * Try to decode body as JSON first, then try Protobuf, then return as string
     */
    fun smartDecode(bodyBytes: ByteArray?): String {
        if (bodyBytes == null || bodyBytes.isEmpty()) return ""

        // Try JSON first
        val jsonStr = try {
            String(bodyBytes, Charsets.UTF_8)
        } catch (e: Exception) { null }

        jsonStr?.let {
            if (isJson(it)) {
                return prettyPrintJson(it)
            }
        }

        // Try Protobuf (FreeFire uses protobuf)
        try {
            val decoded = decodeProtobuf(bodyBytes)
            if (decoded.isNotEmpty() && !decoded.contains("Unable to decode")) {
                return decoded
            }
        } catch (e: Exception) {
            Log.d(TAG, "Not protobuf: ${e.message}")
        }

        // Try Base64 decode
        try {
            val base64Decoded = Base64.decode(bodyBytes, Base64.DEFAULT)
            if (base64Decoded.isNotEmpty()) {
                val result = smartDecode(base64Decoded)
                if (result.isNotEmpty() && !result.contains("Unable to decode")) {
                    return result
                }
            }
        } catch (e: Exception) { /* ignore */ }

        // Try GZip
        try {
            val decompressed = GzipUtils.decompress(bodyBytes)
            if (decompressed != null && decompressed.isNotEmpty()) {
                val result = smartDecode(decompressed)
                if (result.isNotEmpty() && !result.contains("Unable to decode")) {
                    return result
                }
            }
        } catch (e: Exception) { /* ignore */ }

        // Fallback: try to show as hex + ascii
        return buildString {
            appendLine("⚠️ Unable to decode as JSON/Protobuf")
            appendLine()
            appendLine("--- Hex Dump ---")
            appendLine(HexUtils.toHexDump(bodyBytes))
            appendLine()
            appendLine("--- ASCII ---")
            val ascii = bodyBytes.toString(Charsets.UTF_8)
            appendLine(ascii.take(500) + if (ascii.length > 500) "..." else "")
        }
    }

    /**
     * Improved Protobuf decoder - converts protobuf to JSON-like format
     */
    fun decodeProtobuf(data: ByteArray): String {
        if (data.isEmpty()) return "{}"
        
        val result = StringBuilder()
        var i = 0
        var indentLevel = 1
        var isFirstField = true
        
        result.appendLine("{")
        
        while (i < data.size) {
            if (i >= data.size) break
            
            val tag = data[i].toInt() and 0xFF
            i++
            
            val fieldNumber = tag shr 3
            val wireType = tag and 0x07
            
            val key = fieldNumber.toString()
            val indentStr = "  ".repeat(indentLevel)
            
            if (!isFirstField) {
                // Remove trailing comma from previous field
                val lastIdx = result.lastIndexOf(",")
                if (lastIdx > 0 && result[lastIdx] == ',') {
                    result.deleteCharAt(lastIdx)
                }
                result.appendLine(",")
            }
            isFirstField = false
            
            when (wireType) {
                0 -> { // Varint
                    var value = 0L
                    var shift = 0
                    var bytesRead = 0
                    while (i < data.size && bytesRead < 10) {
                        val b = data[i].toInt() and 0xFF
                        i++
                        bytesRead++
                        value = value or ((b and 0x7F).toLong() shl shift)
                        if (b and 0x80 == 0) break
                        shift += 7
                    }
                    result.append("$indentStr\"$key\": $value")
                }
                1 -> { // 64-bit (fixed64)
                    if (i + 8 <= data.size) {
                        val value = data.sliceArray(i until i + 8).toLong()
                        i += 8
                        result.append("$indentStr\"$key\": $value")
                    } else {
                        result.append("$indentStr\"$key\": \"truncated\"")
                    }
                }
                2 -> { // Length-delimited (string, bytes, nested message)
                    var length = 0
                    var shift = 0
                    var bytesRead = 0
                    while (i < data.size && bytesRead < 10) {
                        val b = data[i].toInt() and 0xFF
                        i++
                        bytesRead++
                        length = length or ((b and 0x7F) shl shift)
                        if (b and 0x80 == 0) break
                        shift += 7
                    }
                    
                    if (i + length <= data.size) {
                        val valueBytes = data.sliceArray(i until i + length)
                        i += length
                        
                        // Try to decode as string
                        val str = try {
                            String(valueBytes, Charsets.UTF_8)
                        } catch (e: Exception) { null }
                        
                        // Check if it's a valid UTF-8 string
                        val isPrintable = str != null && str.all { 
                            it.isLetterOrDigit() || it.isWhitespace() || 
                            it in ".,!?;:'\"()[]{}<>/\\|`~@#$%^&*+-=" 
                        }
                        
                        if (isPrintable && str != null && str.length < 500) {
                            // Escape special characters
                            val escaped = str.replace("\"", "\\\"")
                            result.append("$indentStr\"$key\": \"$escaped\"")
                        } else {
                            // Check if it's nested protobuf
                            try {
                                val nested = decodeProtobuf(valueBytes)
                                if (nested.isNotEmpty() && !nested.contains("Unable to decode") && nested != "{}") {
                                    result.append("$indentStr\"$key\": {")
                                    indentLevel++
                                    result.append(nested.trim())
                                    indentLevel--
                                    result.append("$indentStr}")
                                } else {
                                    // Store as Base64
                                    val b64 = Base64.encodeToString(valueBytes, Base64.NO_WRAP)
                                    result.append("$indentStr\"$key\": \"$b64\"")
                                }
                            } catch (e: Exception) {
                                val b64 = Base64.encodeToString(valueBytes, Base64.NO_WRAP)
                                result.append("$indentStr\"$key\": \"$b64\"")
                            }
                        }
                    } else {
                        result.append("$indentStr\"$key\": \"truncated\"")
                    }
                }
                5 -> { // 32-bit (fixed32)
                    if (i + 4 <= data.size) {
                        val value = data.sliceArray(i until i + 4).toLong()
                        i += 4
                        result.append("$indentStr\"$key\": $value")
                    } else {
                        result.append("$indentStr\"$key\": \"truncated\"")
                    }
                }
                else -> {
                    result.append("$indentStr\"$key\": \"unknown_wire_type_$wireType\"")
                }
            }
        }
        
        // Close the JSON
        result.appendLine()
        result.append("}")
        
        return result.toString()
    }

    private fun ByteArray.toLong(): Long {
        var value = 0L
        for (i in indices) {
            value = value or ((this[i].toLong() and 0xFF) shl (i * 8))
        }
        return value
    }

    fun prettyPrintJson(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return try {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return ""
            val element = JsonParser.parseString(trimmed)
            gson.toJson(element)
        } catch (e: JsonSyntaxException) {
            // Try to fix common JSON issues
            try {
                // Sometimes protobuf decoded output has trailing commas
                var fixed = raw.replace(Regex(",\\s*}"), "}")
                fixed = fixed.replace(Regex(",\\s*]"), "]")
                if (fixed != raw) {
                    val element = JsonParser.parseString(fixed)
                    gson.toJson(element)
                } else {
                    raw
                }
            } catch (e2: Exception) {
                raw
            }
        } catch (e: Exception) {
            raw
        }
    }

    fun isJson(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        return try {
            val trimmed = raw.trim()
            if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return false
            JsonParser.parseString(trimmed)
            true
        } catch (_: Exception) { false }
    }

    fun flattenFields(raw: String?, parentPath: String = ""): List<DecodedField> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val element = JsonParser.parseString(raw.trim())
            flattenElement(element, parentPath)
        } catch (_: Exception) { emptyList() }
    }

    private fun flattenElement(
        element: com.google.gson.JsonElement,
        path: String
    ): List<DecodedField> {
        val fields = mutableListOf<DecodedField>()
        when {
            element.isJsonObject -> {
                element.asJsonObject.entrySet().forEach { (k, v) ->
                    val childPath = if (path.isEmpty()) k else "$path.$k"
                    fields.addAll(flattenElement(v, childPath))
                }
            }
            element.isJsonArray -> {
                element.asJsonArray.forEachIndexed { i, v ->
                    val childPath = "$path[$i]"
                    fields.addAll(flattenElement(v, childPath))
                }
            }
            element.isJsonPrimitive -> {
                val prim = element.asJsonPrimitive
                val type = when {
                    prim.isBoolean -> "boolean"
                    prim.isNumber -> "number"
                    else -> "string"
                }
                val key = path.substringAfterLast('.').substringAfterLast('[').trimEnd(']')
                fields.add(DecodedField(key = key, value = prim.asString, type = type, path = path))
            }
            element.isJsonNull -> {
                val key = path.substringAfterLast('.').substringAfterLast('[').trimEnd(']')
                fields.add(DecodedField(key = key, value = "null", type = "null", path = path))
            }
        }
        return fields
    }

    fun applyFieldEdit(original: String, path: String, newValue: String): String {
        return try {
            val root = JsonParser.parseString(original.trim())
            setValueAtPath(root, path.split('.', '[').filter { it.isNotEmpty() && it != "]" }, newValue)
            gson.toJson(root)
        } catch (_: Exception) { original }
    }

    private fun setValueAtPath(
        element: com.google.gson.JsonElement,
        parts: List<String>,
        newValue: String
    ) {
        if (parts.isEmpty()) return
        val key = parts.first()
        val rest = parts.drop(1)
        when {
            element.isJsonObject && rest.isEmpty() -> {
                val obj = element.asJsonObject
                val existing = obj.get(key)
                val newElement = when {
                    existing?.isJsonPrimitive == true && existing.asJsonPrimitive.isBoolean ->
                        com.google.gson.JsonPrimitive(newValue.toBooleanStrictOrNull() ?: (newValue == "true"))
                    existing?.isJsonPrimitive == true && existing.asJsonPrimitive.isNumber ->
                        com.google.gson.JsonPrimitive(newValue.toDoubleOrNull() ?: newValue.toLongOrNull() ?: 0)
                    else -> com.google.gson.JsonPrimitive(newValue)
                }
                obj.add(key, newElement)
            }
            element.isJsonObject && rest.isNotEmpty() -> {
                setValueAtPath(element.asJsonObject.get(key) ?: return, rest, newValue)
            }
            element.isJsonArray -> {
                val idx = key.trimEnd(']').toIntOrNull() ?: return
                if (rest.isEmpty()) {
                    element.asJsonArray.set(idx, com.google.gson.JsonPrimitive(newValue))
                } else {
                    setValueAtPath(element.asJsonArray[idx], rest, newValue)
                }
            }
        }
    }

    // ============================================================
    // CONVERSION FUNCTIONS - For Settings Plugin
    // ============================================================
    
    fun hexToBase64(hex: String): String {
        return try {
            val cleanHex = hex.replace(" ", "").replace("\n", "")
            val bytes = cleanHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            "Invalid Hex: ${e.message}"
        }
    }

    fun base64ToHex(base64: String): String {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            bytes.joinToString("") { String.format("%02X", it) }
        } catch (e: Exception) {
            "Invalid Base64: ${e.message}"
        }
    }

    fun hexToText(hex: String): String {
        return try {
            val cleanHex = hex.replace(" ", "").replace("\n", "")
            val bytes = cleanHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            "Invalid Hex or not UTF-8 text: ${e.message}"
        }
    }

    fun textToHex(text: String): String {
        return text.toByteArray(Charsets.UTF_8).joinToString("") { String.format("%02X", it) }
    }

    fun base64ToText(base64: String): String {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            "Invalid Base64: ${e.message}"
        }
    }

    fun textToBase64(text: String): String {
        return Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    /**
     * Extract payload from hex dump (like from PCAPdroid/Wireshark)
     */
    fun extractPayloadFromHexDump(hexDump: String): String {
        val lines = hexDump.split("\n")
        val hexPattern = Regex("^[0-9a-fA-F]{8}\\s+((?:[0-9a-fA-F]{2}\\s*){1,16})")
        val cleanHexList = mutableListOf<String>()
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val match = hexPattern.find(trimmed)
            if (match != null) {
                val bytesPart = match.groupValues[1].trim().replace(" ", "")
                cleanHexList.add(bytesPart)
            } else {
                // Try alternative pattern: just hex bytes separated by spaces
                val altPattern = Regex("^((?:[0-9a-fA-F]{2}\\s*)+)")
                val altMatch = altPattern.find(trimmed)
                if (altMatch != null) {
                    val bytesPart = altMatch.groupValues[1].trim().replace(" ", "")
                    if (bytesPart.length >= 2) {
                        cleanHexList.add(bytesPart)
                    }
                }
            }
        }
        
        return cleanHexList.joinToString("")
    }
}
