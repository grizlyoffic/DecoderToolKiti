package com.nexbytes.h7skertool.utils

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object GzipUtils {
    
    fun decompress(data: ByteArray?): ByteArray? {
        if (data == null || data.isEmpty()) return null
        return try {
            GZIPInputStream(data.inputStream()).use { it.readBytes() }
        } catch (e: Exception) { 
            null 
        }
    }
    
    fun compress(data: ByteArray?): ByteArray? {
        if (data == null || data.isEmpty()) return null
        return try {
            ByteArrayOutputStream().use { baos ->
                GZIPOutputStream(baos).use { gzip ->
                    gzip.write(data)
                }
                baos.toByteArray()
            }
        } catch (e: Exception) { 
            null 
        }
    }
}
