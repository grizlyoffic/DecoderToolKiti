package com.nexbytes.h7skertool.utils

import android.util.Log

/** Represents a single protobuf field parsed from raw bytes */
data class ProtoField(
    val fieldNum: Int,
    val wireType: Int,
    val wireTypeName: String,
    val rawValue: String,
    val bytesHex: String = ""
)

object ProtoModifier {
    private const val TAG = "ProtoModifier"

    // ── Varint codec ─────────────────────────────────────────────────────────

    fun encodeVarint(value: Long): ByteArray {
        val out = mutableListOf<Byte>()
        var v = value and 0xFFFFFFFFFFFFFFFFL.toLong()
        while (v > 127L) {
            out.add(((v and 0x7F) or 0x80).toByte())
            v = v ushr 7
        }
        out.add((v and 0x7F).toByte())
        return out.toByteArray()
    }

    fun readVarint(data: ByteArray, offset: Int): Pair<Long, Int> {
        var value = 0L; var shift = 0; var i = offset
        while (i < data.size) {
            val b = data[i].toLong() and 0xFF; i++
            value = value or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0L) break
            shift += 7
            if (shift > 63) break
        }
        return Pair(value, i)
    }

    // ── Parse protobuf bytes → UI field list ─────────────────────────────────

    fun parseFields(data: ByteArray): List<ProtoField> {
        val result = mutableListOf<ProtoField>()
        if (data.isEmpty()) return result
        var i = 0
        try {
            while (i < data.size) {
                val startI = i
                val (tag, afterTag) = readVarint(data, i)
                if (tag == 0L || afterTag >= data.size && (tag and 0x7) != 0L) break
                val fieldNumber = (tag shr 3).toInt()
                if (fieldNumber <= 0) break
                val wireType = (tag and 0x07).toInt()
                val (wireName, rawVal, nextI, rawHex) = when (wireType) {
                    0 -> {
                        val (v, end) = readVarint(data, afterTag)
                        Quad("varint", v.toString(), end, "")
                    }
                    1 -> {
                        val end = afterTag + 8
                        if (end > data.size) break
                        val hex = data.slice(afterTag until end).joinToString("") { "%02x".format(it) }
                        var v = 0L
                        for (x in 0 until 8) v = v or ((data[afterTag + x].toLong() and 0xFF) shl (x * 8))
                        Quad("fixed64", v.toString(), end, hex)
                    }
                    2 -> {
                        val (length, afterLen) = readVarint(data, afterTag)
                        val len = length.toInt()
                        if (len < 0 || afterLen + len > data.size) break
                        val bytes = data.slice(afterLen until afterLen + len).toByteArray()
                        val hex = bytes.joinToString("") { "%02x".format(it) }
                        val str = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull()
                        val isPrintable = str != null && str.length < 500 &&
                            str.all { it.code in 32..126 || it == '\n' || it == '\r' || it == '\t' }
                        val disp = if (isPrintable && str != null) "\"${str.take(80)}${if (str.length > 80) "…" else "\"" }" else "<bytes:${bytes.size}>"
                        Quad("bytes/str", disp, afterLen + len, hex)
                    }
                    5 -> {
                        val end = afterTag + 4
                        if (end > data.size) break
                        val hex = data.slice(afterTag until end).joinToString("") { "%02x".format(it) }
                        var v = 0L
                        for (x in 0 until 4) v = v or ((data[afterTag + x].toLong() and 0xFF) shl (x * 8))
                        Quad("fixed32", v.toString(), end, hex)
                    }
                    else -> {
                        // Unknown wire type — skip rest to avoid corrupting more
                        Log.w(TAG, "Unknown wire type $wireType at offset $startI, aborting parse")
                        break
                    }
                }
                result.add(ProtoField(fieldNumber, wireType, wireName, rawVal, rawHex))
                i = nextI
            }
        } catch (e: Exception) { Log.w(TAG, "parseFields stopped at $i: ${e.message}") }
        return result
    }

    private data class Quad(val a: String, val b: String, val c: Int, val d: String)

    // ── Apply field overrides – preserves all unmodified fields ──────────────

    /**
     * Rebuilds protobuf bytes, applying [modFields] (fieldNum → newValue).
     * Fields not in [modFields] are copied verbatim (byte-for-byte), so
     * unknown fields, nested messages, and arrays are preserved intact.
     * On any parse/encode error the ORIGINAL bytes are returned unchanged.
     */
    fun modifyProtoBytes(data: ByteArray, modFields: Map<Int, String>): ByteArray {
        if (data.isEmpty() || modFields.isEmpty()) return data
        return try {
            val out = mutableListOf<Byte>()
            var i = 0
            while (i < data.size) {
                val startI = i
                val (tag, afterTag) = readVarint(data, i)
                if (tag == 0L) break
                val fieldNumber = (tag shr 3).toInt()
                if (fieldNumber <= 0) {
                    // copy remainder verbatim
                    out.addAll(data.slice(i until data.size))
                    break
                }
                val wireType = (tag and 0x07).toInt()
                val tagBytes = encodeVarint(tag)
                val modValue = if (fieldNumber in modFields) modFields[fieldNumber] else null

                when (wireType) {
                    0 -> {
                        val (orig, end) = readVarint(data, afterTag)
                        val nv = modValue?.trim()?.toLongOrNull() ?: orig
                        out.addAll(tagBytes.toList())
                        out.addAll(encodeVarint(nv).toList())
                        i = end
                    }
                    1 -> {
                        if (afterTag + 8 > data.size) { out.addAll(data.slice(i until data.size)); break }
                        out.addAll(tagBytes.toList())
                        val b = ByteArray(8)
                        if (modValue != null) {
                            val nv = modValue.trim().toLongOrNull()
                            if (nv != null) for (x in 0..7) b[x] = (nv ushr (x * 8)).toByte()
                            else data.copyInto(b, 0, afterTag, afterTag + 8)
                        } else {
                            data.copyInto(b, 0, afterTag, afterTag + 8)
                        }
                        out.addAll(b.toList())
                        i = afterTag + 8
                    }
                    2 -> {
                        val (length, afterLen) = readVarint(data, afterTag)
                        val len = length.toInt()
                        val end2 = afterLen + len
                        if (len < 0 || end2 > data.size) { out.addAll(data.slice(i until data.size)); break }
                        out.addAll(tagBytes.toList())
                        if (modValue != null) {
                            val stripped = modValue.let {
                                if (it.length >= 2 && it.startsWith("\"") && it.endsWith("\"")) it.substring(1, it.length - 1) else it
                            }
                            val nb = stripped.toByteArray(Charsets.UTF_8)
                            out.addAll(encodeVarint(nb.size.toLong()).toList())
                            out.addAll(nb.toList())
                        } else {
                            // Preserve verbatim (nested messages included)
                            out.addAll(encodeVarint(length).toList())
                            out.addAll(data.slice(afterLen until end2))
                        }
                        i = end2
                    }
                    5 -> {
                        if (afterTag + 4 > data.size) { out.addAll(data.slice(i until data.size)); break }
                        out.addAll(tagBytes.toList())
                        if (modValue != null) {
                            val nv = modValue.trim().toLongOrNull()
                            val b = ByteArray(4)
                            if (nv != null) for (x in 0..3) b[x] = (nv ushr (x * 8)).toByte()
                            else data.copyInto(b, 0, afterTag, afterTag + 4)
                            out.addAll(b.toList())
                        } else {
                            out.addAll(data.slice(afterTag until afterTag + 4))
                        }
                        i = afterTag + 4
                    }
                    else -> {
                        // Unknown wire type → copy rest verbatim to preserve integrity
                        Log.w(TAG, "Unknown wire type $wireType at field $fieldNumber – preserving tail")
                        out.addAll(data.slice(i until data.size))
                        break
                    }
                }
            }
            out.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "modifyProtoBytes error: ${e.message} – returning original")
            data  // ← SAFETY: never return corrupted bytes
        }
    }

    /**
     * Parse a JSON-like mod map from string.
     * Supports both {"1":"value"} and {"field":"1","value":"v"} styles.
     */
    fun parseModFields(modJson: String): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        if (modJson.isBlank()) return result
        return try {
            val gson = com.google.gson.Gson()
            @Suppress("UNCHECKED_CAST")
            val map = gson.fromJson(modJson, Map::class.java) as? Map<String, Any> ?: return result
            for ((k, v) in map) {
                val fn = k.trim().toIntOrNull() ?: continue
                result[fn] = v?.toString() ?: continue
            }
            result
        } catch (_: Exception) { result }
    }

    /**
     * Convert decoded JSON map back to protobuf bytes.
     * Input JSON: {"1": 9999, "2": 1, "4": "NAME", ...}
     * Returns null if conversion fails (caller should keep original).
     */
    fun jsonToProtoBytes(json: String, originalBytes: ByteArray): ByteArray? {
        return try {
            val modFields = parseModFields(json)
            if (modFields.isEmpty()) return originalBytes
            modifyProtoBytes(originalBytes, modFields)
        } catch (e: Exception) {
            Log.e(TAG, "jsonToProtoBytes: ${e.message}")
            null
        }
    }
}
