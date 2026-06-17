package com.nexbytes.h7skertool.utils

object HexUtils {
    fun toCleanHex(bytes: ByteArray?): String {
        if (bytes == null || bytes.isEmpty()) return ""
        return bytes.joinToString(" ") { String.format("%02x", it) }
    }
    fun toHexDump(bytes: ByteArray?): String {
        if (bytes == null || bytes.isEmpty()) return ""
        val sb = StringBuilder()
        bytes.forEachIndexed { i, b ->
            if (i > 0 && i % 16 == 0) sb.append("\n")
            sb.append(String.format("%02x", b))
            if (i % 16 != 15) sb.append(" ")
        }
        return sb.toString()
    }
    fun hexToBytes(hex: String): ByteArray? = try {
        val clean = hex.replace(" ", "").replace("\n", "").trim()
        if (clean.isEmpty() || clean.length % 2 != 0) null
        else ByteArray(clean.length / 2) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    } catch (_: Exception) { null }
    fun bytesToBase64(bytes: ByteArray): String = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    fun base64ToBytes(b64: String): ByteArray? = try { android.util.Base64.decode(b64, android.util.Base64.DEFAULT) } catch (_: Exception) { null }
}
