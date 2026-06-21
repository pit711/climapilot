package com.climapilot.free

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * EN: An ongoing notification that shows the live sleep/off-timer countdown and lets the user power
 *     off now or cancel — straight from the status bar, without opening the app. Posted when a timer is
 *     armed (see [SleepTimerScheduler.schedule]) and removed when it fires or is cancelled (see
 *     [SleepTimerScheduler.clear]). The countdown is rendered by the system via a count-down chronometer
 *     anchored at the absolute trigger time, so it stays correct with zero updates from us.
 * DE: Eine laufende Benachrichtigung mit Live-Countdown des Sleep-/Off-Timers samt „Jetzt aus" und
 *     „Abbrechen" — direkt aus der Statusleiste, ohne die App zu öffnen. Wird beim Aktivieren eines
 *     Timers gepostet (siehe [SleepTimerScheduler.schedule]) und beim Auslösen/Abbrechen entfernt
 *     (siehe [SleepTimerScheduler.clear]). Der Countdown wird vom System über ein Countdown-Chronometer
 *     an der absoluten Auslösezeit gezeichnet — bleibt also ohne ein einziges Update von uns korrekt.
 */
object TimerNotification {
    private const val CHANNEL = "climapilot_timer"
    private const val NOTIF_ID = 0x51EE

    /** EN: Show/refresh the countdown notification for a timer ending at [triggerAt]. DE: Countdown-Benachrichtigung für einen um [triggerAt] endenden Timer zeigen/aktualisieren. */
    fun show(ctx: Context, triggerAt: Long) {
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_w_power)
            .setContentTitle(ctx.getString(R.string.notif_timer_title))
            .setContentText(ctx.getString(R.string.notif_timer_text))
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(triggerAt)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, ctx.getString(R.string.notif_timer_off_now), action(ctx, TimerActionReceiver.ACTION_OFF_NOW, 1))
            .addAction(0, ctx.getString(R.string.notif_timer_cancel), action(ctx, TimerActionReceiver.ACTION_CANCEL, 2))
            .build()
        // EN: notify() is a no-op if the user denied notifications — fine, the timer still fires. DE: notify() ist wirkungslos, wenn der Nutzer Benachrichtigungen verweigert hat — egal, der Timer feuert trotzdem.
        NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n)
    }

    /** EN: Remove the countdown notification. DE: Die Countdown-Benachrichtigung entfernen. */
    fun cancel(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(NOTIF_ID)

    private fun action(ctx: Context, act: String, req: Int): PendingIntent {
        val i = Intent(ctx, TimerActionReceiver::class.java).setAction(act)
        return PendingIntent.getBroadcast(ctx, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun ensureChannel(ctx: Context) {
        val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, ctx.getString(R.string.notif_channel_timer), NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) },
            )
        }
    }
}
