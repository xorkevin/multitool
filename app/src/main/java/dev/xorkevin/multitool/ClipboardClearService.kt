@file:OptIn(ExperimentalPermissionsApi::class)

package dev.xorkevin.multitool

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startForegroundService
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

internal fun enqueueClipboardClear(context: Context) {
    val intent = Intent(context, ClipboardClearService::class.java)
    startForegroundService(context, intent)
}

class ClipboardClearService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val clearRunnable = Runnable { clearClipboardAndStop() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.action == ACTION_DISMISS) {
            clearClipboardAndStop()
            return START_NOT_STICKY
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        handler.removeCallbacks(clearRunnable)
        handler.postDelayed(clearRunnable, CLIPBOARD_CLEAR_TIMEOUT)
        return START_NOT_STICKY
    }

    private fun clearClipboardAndStop() {
        clearClipboard(applicationContext)
        stopSelf()
    }

    private fun createNotification(): Notification {
        val dismissIntent = Intent(this, ClipboardClearService::class.java).apply {
            action = ACTION_DISMISS
        }
        val dismissPendingIntent = PendingIntent.getService(
            this,
            0,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notifications = applicationContext.getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Password store clipboard clear",
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        return Notification.Builder(applicationContext, NOTIFICATION_CHANNEL_ID).run {
            setContentTitle("Password store clipboard")
            setContentText("Clearing clipboard")
            setSmallIcon(R.drawable.ic_launcher_foreground)
            setContentIntent(dismissPendingIntent)
            setDeleteIntent(dismissPendingIntent)
            addAction(
                Notification.Action.Builder(
                    R.drawable.ic_launcher_foreground, "Clear", dismissPendingIntent
                ).build()
            )
            setUsesChronometer(true)
            setChronometerCountDown(true)
            setShowWhen(true)
            setWhen(System.currentTimeMillis() + CLIPBOARD_CLEAR_TIMEOUT)
            setAutoCancel(true)
            build()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        clearClipboard(applicationContext)
        handler.removeCallbacks(clearRunnable)
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "password-store-clipboard-clear"
        private const val NOTIFICATION_ID = 1
        private const val CLIPBOARD_CLEAR_TIMEOUT = 30_000L
        private const val ACTION_DISMISS = "dismiss"
    }
}

internal fun clearClipboard(appContext: Context) {
    val clipboard = appContext.getSystemService(ClipboardManager::class.java)
    clipboard.clearPrimaryClip()
}

@Composable
fun NotificationPermission(content: @Composable () -> Unit) {
    val notifPermissionState =
        rememberPermissionState(android.Manifest.permission.POST_NOTIFICATIONS)
    if (notifPermissionState.status.isGranted) {
        content()
    } else {
        if (notifPermissionState.status.shouldShowRationale) {
            Text(
                text = "Notification permission is needed to clear the clipboard",
                modifier = Modifier
                    .padding(16.dp, 8.dp)
                    .fillMaxWidth()
            )
        }
        Button(
            onClick = { notifPermissionState.launchPermissionRequest() },
            modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth()
        ) {
            Text(text = "Grant notification permission")
        }
    }
}
