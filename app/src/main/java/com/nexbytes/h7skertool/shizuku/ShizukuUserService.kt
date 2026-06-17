package com.nexbytes.h7skertool.shizuku

import android.util.Log
import java.io.File

class ShizukuUserService : IRemoteService.Stub() {

    private val TAG = "ShizukuUserService"

    override fun getVersion(): Int = 2

    override fun createFile(path: String, content: String) {
        try {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeText(content, Charsets.UTF_8)
            Log.i(TAG, "Created: $path")
        } catch (e: Exception) {
            Log.e(TAG, "createFile failed: ${e.message}")
            throw RuntimeException("createFile failed: ${e.message}")
        }
    }

    override fun fileExists(path: String): Boolean = try {
        File(path).exists()
    } catch (e: Exception) { false }

    override fun deleteFile(path: String) {
        try { File(path).delete() } catch (e: Exception) {
            Log.w(TAG, "deleteFile failed: ${e.message}")
        }
    }

    override fun readFile(path: String): String = try {
        File(path).readText(Charsets.UTF_8)
    } catch (e: Exception) {
        throw RuntimeException("readFile failed: ${e.message}")
    }
}
