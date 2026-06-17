package com.nexbytes.h7skertool.service

import android.util.Log
import com.nexbytes.h7skertool.shizuku.ShizukuManager

object ShizukuFileService {
    private const val TAG = "ShizukuFileService"

    // NOTE: http:// — NOT https://
    private val LOCAL_CONFIG_CONTENT = """{"serverLoginUrl":"http://127.0.0.1:8080/"}"""

    private val TARGET_DIRS = listOf(
        "/storage/emulated/0/Android/data/com.dts.freefireth/files",
        "/storage/emulated/0/Android/data/com.dts.freefireth.max/files"
    )

    data class FileWriteResult(val path: String, val success: Boolean, val error: String? = null)

    fun writeLocalConfigFiles(): List<FileWriteResult> {
        ShizukuManager.bindService()
        val deadline = System.currentTimeMillis() + 3000L
        while (!ShizukuManager.isServiceConnected() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
        }
        return TARGET_DIRS.map { dir ->
            val path = "$dir/localconfig.json"
            ShizukuManager.createFileWithShizuku(path, LOCAL_CONFIG_CONTENT).fold(
                onSuccess = { FileWriteResult(path, true).also { Log.i(TAG, "✓ $path") } },
                onFailure = { e -> FileWriteResult(path, false, e.message).also { Log.e(TAG, "✗ $path: ${e.message}") } }
            )
        }
    }

    fun removeLocalConfigFiles(): List<FileWriteResult> =
        TARGET_DIRS.map { dir ->
            val path = "$dir/localconfig.json"
            ShizukuManager.deleteFile(path).fold(
                onSuccess = { FileWriteResult(path, true) },
                onFailure = { e -> FileWriteResult(path, false, e.message) }
            )
        }
}
