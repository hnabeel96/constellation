package com.eko.constellation

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.ZoneId

/** The daily nudge: an OS alarm wakes us once a day to say "N thoughts are back". */
object Reminders {
    const val CHANNEL = "resurface"
    const val ACTION_OPEN_TODAY = "com.eko.constellation.OPEN_TODAY"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Daily resurfacing", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Old thoughts coming back for a decision"
                }
            )
        }
    }

    private fun alarmIntent(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 7, Intent(ctx, ReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun schedule(ctx: Context) {
        Repo.init(ctx)
        val s = Repo.graph.settings
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(ctx)
        am.cancel(pi)
        if (!s.notify) return
        val zone = ZoneId.systemDefault()
        var at = LocalDate.now().atTime(s.hour, s.minute).atZone(zone).toInstant().toEpochMilli()
        if (at <= System.currentTimeMillis()) {
            at = LocalDate.now().plusDays(1).atTime(s.hour, s.minute).atZone(zone).toInstant().toEpochMilli()
        }
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun post(ctx: Context) {
        Repo.init(ctx)
        Repo.ensureQueue()
        val q = Repo.graph.queue.ids.mapNotNull { Repo.byId(it) }.filter { it.status == Status.ACTIVE }
        if (q.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 8,
            Intent(ctx, MainActivity::class.java)
                .setAction(ACTION_OPEN_TODAY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = if (q.size == 1) "1 thought is back" else "${q.size} thoughts are back"
        val lines = q.joinToString("\n") { "• " + it.text.take(90) }
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_star)
            .setContentTitle(title)
            .setContentText(q.first().text.take(90))
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines))
            .setColor(0xFF5EE7F2.toInt())
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(42, n)
        } catch (e: SecurityException) {
            // Notifications not allowed; nothing to do.
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.post(context)
        Reminders.schedule(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.schedule(context)
    }
}
