package com.agentflow.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.agentflow.ui.MainActivity

object AttentionNotifier {
    const val CHANNEL_ID = "agentflow-attention"
    const val KIND_INPUT = "input"
    const val KIND_DONE = "done"
    const val KIND_FAIL = "fail"
    const val KIND_ESCALATED = "escalated"

    fun key(missionId: String, kind: String, eventId: String = "") = "$missionId:$kind:$eventId"

    fun notificationId(key: String): Int = key.hashCode()

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Mission attention", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    fun notify(
        context: Context,
        missionId: String,
        kind: String,
        title: String,
        text: String,
        route: String,
        eventId: String = "",
    ) {
        ensureChannel(context)
        val key = key(missionId, kind, eventId)
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_ROUTE, route)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            notificationId(key),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(notificationId(key), notification)
    }

    const val EXTRA_ROUTE = "agentflow.route"
}
