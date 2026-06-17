package com.nexbytes.h7skertool.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nexbytes.h7skertool.MainActivity
import com.nexbytes.h7skertool.model.CapturedRequest
import com.nexbytes.h7skertool.model.CapturedResponse
import com.nexbytes.h7skertool.utils.ModFile
import kotlinx.coroutines.*

class ProxyForegroundService : Service() {
    private val TAG = "ProxyForegroundService"
    private val CHANNEL_ID = "h7sker_proxy"
    private val NOTIF_ID = 7001
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var proxy: ProxyServer? = null
    private lateinit var logging: LoggingManager

    companion object {
        const val ACTION_START = "com.nexbytes.h7skertool.START"
        const val ACTION_STOP  = "com.nexbytes.h7skertool.STOP"
        const val EXTRA_CLIENT_URL = "client_url"

        @Volatile var onCapture: ((CapturedRequest, CapturedResponse) -> Unit)? = null
        @Volatile var onLog: ((String) -> Unit)? = null
        @Volatile var savedMods: Map<String, String> = emptyMap()
        @Volatile var activeMods: List<ModFile> = emptyList()

        fun start(ctx: Context, clientUrl: String) {
            ctx.startForegroundService(Intent(ctx, ProxyForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CLIENT_URL, clientUrl)
            })
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, ProxyForegroundService::class.java).apply { action = ACTION_STOP })
        }
    }

    override fun onCreate() {
        super.onCreate(); logging = LoggingManager(this); createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startProxy(intent.getStringExtra(EXTRA_CLIENT_URL) ?: "https://clientbp.ggpolarbear.com")
            ACTION_STOP  -> stopProxy()
        }
        return START_STICKY
    }

    private fun startProxy(clientUrl: String) {
        startForeground(NOTIF_ID, buildNotif("Capturing → $clientUrl"))
        proxy = ProxyServer(
            clientBaseUrl = clientUrl,
            scope = scope,
            savedMods = savedMods,
            activeMods = activeMods,
            onCapture = { req, res ->
                onCapture?.invoke(req, res)
                scope.launch {
                    runCatching { logging.logCapture(req, res); logging.logBinary(req, res) }
                }
            },
            onLog = { msg -> onLog?.invoke(msg) }
        )
        runCatching {
            proxy!!.start()
            onLog?.invoke("Proxy started 127.0.0.1:8080 → $clientUrl")
        }.onFailure { e ->
            onLog?.invoke("ERROR: ${e.message}")
            Log.e(TAG, "startProxy error", e); stopSelf()
        }
    }

    private fun stopProxy() {
        runCatching { proxy?.stop() }
        proxy = null
        onLog?.invoke("Proxy stopped")
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy(); runCatching { proxy?.stop() }; scope.cancel()
    }

    override fun onBind(i: Intent?): IBinder? = null

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "H7skER Capture", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Active proxy capture" }
        )
    }

    private fun buildNotif(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("H7skER TOOL")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }
}
