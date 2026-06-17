package com.nexbytes.h7skertool.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku

object ShizukuManager {

    private const val TAG = "ShizukuManager"
    private const val REQUEST_CODE = 1001

    private var remoteService: IRemoteService? = null
    private var serviceConnected = false

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName("com.nexbytes.h7skertool", ShizukuUserService::class.java.name)
    ).daemon(false).processNameSuffix("service").debuggable(false).version(2)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                remoteService = IRemoteService.Stub.asInterface(binder)
                serviceConnected = true
                Log.i(TAG, "UserService connected")
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            remoteService = null
            serviceConnected = false
            Log.w(TAG, "UserService disconnected")
        }
    }

    fun isShizukuAvailable(): Boolean = try { Shizuku.pingBinder() } catch (e: Exception) { false }

    fun hasPermission(): Boolean = try {
        if (Shizuku.isPreV11()) false
        else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) { false }

    fun requestPermission(listener: Shizuku.OnRequestPermissionResultListener) {
        try {
            if (Shizuku.isPreV11()) return
            Shizuku.addRequestPermissionResultListener(listener)
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (e: Exception) { Log.e(TAG, "requestPermission: ${e.message}") }
    }

    fun removePermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        try { Shizuku.removeRequestPermissionResultListener(listener) } catch (_: Exception) {}
    }

    fun bindService() {
        try {
            if (!serviceConnected && hasPermission())
                Shizuku.bindUserService(userServiceArgs, serviceConnection)
        } catch (e: Exception) { Log.e(TAG, "bindService: ${e.message}") }
    }

    fun unbindService() {
        try {
            Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
            remoteService = null; serviceConnected = false
        } catch (_: Exception) {}
    }

    fun createFileWithShizuku(path: String, content: String): Result<Unit> {
        return try {
            val svc = ensureService() ?: return Result.failure(Exception("UserService not connected"))
            svc.createFile(path, content)
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    fun deleteFile(path: String): Result<Unit> {
        return try {
            val svc = ensureService() ?: return Result.failure(Exception("UserService not connected"))
            svc.deleteFile(path)
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    fun pathExists(path: String): Boolean = try {
        ensureService()?.fileExists(path) ?: false
    } catch (_: Exception) { false }

    fun isServiceConnected(): Boolean = serviceConnected

    private fun ensureService(): IRemoteService? {
        if (remoteService == null && hasPermission()) bindService()
        return remoteService
    }
}
