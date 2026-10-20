package ai.opencode.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import ai.opencode.mobile.engine.EngineRunner
import ai.opencode.mobile.engine.MuslBinaryEngine
import ai.opencode.mobile.engine.NodeBundleEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that owns one long-lived engine process. Survives WebView
 * navigation; binds via [EngineStateBus] instead of an IPC binder (scaffold simplification).
 */
class EngineService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var runner: EngineRunner? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (runner == null && EngineStateBus.status.value !is EngineStatus.Ready) {
            scope.launch {
                EngineStateBus.set(EngineStatus.Starting)
                try {
                    // Track A for M1–M3; swap to NodeBundleEngine(applicationContext) at M4.
                    val engine = selectRunner()
                    val runtime = engine.start()
                    runner = engine
                    EngineStateBus.set(EngineStatus.Ready(runtime.port))
                } catch (t: Throwable) {
                    EngineStateBus.set(EngineStatus.Failed(t.message ?: t.javaClass.simpleName))
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun selectRunner(): EngineRunner =
        MuslBinaryEngine(applicationContext) // TODO(M4): NodeBundleEngine(applicationContext)

    override fun onDestroy() {
        runner?.stop()
        scope.cancel()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "OpenCode engine", NotificationManager.IMPORTANCE_MIN)
        )
        val note: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("OpenCode engine running")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, note)
        }
    }

    private companion object {
        const val CHANNEL_ID = "opencode-engine"
        const val NOTIF_ID = 1
    }
}
