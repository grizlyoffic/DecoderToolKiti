package com.nexbytes.h7skertool.utils

import android.util.Base64

object ConversionUtils {
    fun hexToBase64(hex: String): String? {
        val b = HexUtils.hexToBytes(hex) ?: return null
        return Base64.encodeToString(b, Base64.NO_WRAP)
    }

    fun base64ToHex(b64: String): String? {
        val b = try { Base64.decode(b64, Base64.DEFAULT) } catch (_: Exception) { return null }
        return HexUtils.toCleanHex(b)
    }

    fun hexToBinary(hex: String): String? {
        val b = HexUtils.hexToBytes(hex) ?: return null
        return b.joinToString(" ") { x ->
            String.format("%8s", Integer.toBinaryString(x.toInt() and 0xFF)).replace(' ', '0')
        }
    }

    fun binaryToHex(binary: String): String? {
        return try {
            val c = binary.replace(" ", "").replace("\n", "")
            if (c.length % 8 != 0) null
            else HexUtils.toCleanHex(ByteArray(c.length / 8) { i ->
                c.substring(i * 8, i * 8 + 8).toInt(2).toByte()
            })
        } catch (_: Exception) {
            null
        }
    }

    fun hexToUtf8(hex: String): String? {
        val b = HexUtils.hexToBytes(hex) ?: return null
        return try { String(b, Charsets.UTF_8) } catch (_: Exception) { null }
    }

    fun utf8ToHex(text: String): String = HexUtils.toCleanHex(text.toByteArray(Charsets.UTF_8))

    fun hexToDecimal(hex: String): String? {
        val b = HexUtils.hexToBytes(hex.replace(" ", "")) ?: return null
        if (b.size > 8) return null
        var v = 0L
        for (x in b) {
            v = (v shl 8) or (x.toLong() and 0xFF)
        }
        return v.toString()
    }

    fun decimalToHex(decimal: String): String? {
        val v = decimal.trim().toLongOrNull() ?: return null
        val h = java.lang.Long.toHexString(v)
        return if (h.length % 2 != 0) "0$h" else h
    }

    fun hexToAscii(hex: String): String? {
        val b = HexUtils.hexToBytes(hex) ?: return null
        return b.map { x ->
            val c = x.toInt() and 0xFF
            if (c in 32..126) c.toChar() else '.'
        }.joinToString("")
    }

    fun asciiToHex(ascii: String): String = HexUtils.toCleanHex(ascii.map { it.code.toByte() }.toByteArray())

    fun base64ToBinary(b64: String): String? {
        val b = try { Base64.decode(b64, Base64.DEFAULT) } catch (_: Exception) { return null }
        return b.joinToString(" ") { x ->
            String.format("%8s", Integer.toBinaryString(x.toInt() and 0xFF)).replace(' ', '0')
        }
    }

    fun binaryToBase64(binary: String): String? {
        val h = binaryToHex(binary) ?: return null
        return hexToBase64(h)
    }

    fun hexToInt32LE(hex: String): String? {
        val b = HexUtils.hexToBytes(hex.replace(" ", "")) ?: return null
        if (b.size < 4) return null
        return ((b[0].toInt() and 0xFF) or
                ((b[1].toInt() and 0xFF) shl 8) or
                ((b[2].toInt() and 0xFF) shl 16) or
                ((b[3].toInt() and 0xFF) shl 24)).toString()
    }

    fun hexToInt32BE(hex: String): String? {
        val b = HexUtils.hexToBytes(hex.replace(" ", "")) ?: return null
        if (b.size < 4) return null
        return (((b[0].toInt() and 0xFF) shl 24) or
                ((b[1].toInt() and 0xFF) shl 16) or
                ((b[2].toInt() and 0xFF) shl 8) or
                (b[3].toInt() and 0xFF)).toString()
    }

    fun hexToOctal(hex: String): String? {
        val b = HexUtils.hexToBytes(hex.replace(" ", "")) ?: return null
        if (b.size > 8) return null
        var v = 0L
        for (x in b) {
            v = (v shl 8) or (x.toLong() and 0xFF)
        }
        return java.lang.Long.toOctalString(v)
    }

    fun reverseHex(hex: String): String? {
        val b = HexUtils.hexToBytes(hex) ?: return null
        return HexUtils.toCleanHex(b.reversedArray())
    }

    fun xorHex(hex1: String, hex2: String): String? {
        val b1 = HexUtils.hexToBytes(hex1) ?: return null
        val b2 = HexUtils.hexToBytes(hex2) ?: return null
        val len = minOf(b1.size, b2.size)
        return HexUtils.toCleanHex(ByteArray(len) { i ->
            (b1[i].toInt() xor b2[i].toInt()).toByte()
        })
    }

    fun hexToVarint(hex: String): String? {
        val b = HexUtils.hexToBytes(hex) ?: return null
        return try { ProtoModifier.readVarint(b, 0).first.toString() } catch (_: Exception) { null }
    }

    fun varintToHex(value: String): String? {
        val v = value.trim().toLongOrNull() ?: return null
        return HexUtils.toCleanHex(ProtoModifier.encodeVarint(v))
    }

    fun hexToProto(hex: String): String? {
        val b = HexUtils.hexToBytes(hex) ?: return null
        return try { DecodeUtils.decodeProtobuf(b) } catch (_: Exception) { null }
    }

    fun urlToHex(url: String): String = HexUtils.toCleanHex(java.net.URLEncoder.encode(url, "UTF-8").toByteArray())
}