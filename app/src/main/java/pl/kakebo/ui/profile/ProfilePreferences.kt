package pl.kakebo.ui.profile

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
import java.time.LocalDateTime
import java.time.ZoneId
import org.json.JSONObject
import pl.kakebo.MainActivity
import pl.kakebo.R

data class ReminderSettings(val enabled: Boolean = false, val hour: Int = 20, val minute: Int = 0)

private const val PROFILE_PREFS = "profile_preferences"
private const val REMINDER_ENABLED = "reminder_enabled"
private const val REMINDER_HOUR = "reminder_hour"
private const val REMINDER_MINUTE = "reminder_minute"
private const val APP_LOCK = "app_lock"
private const val BOOK_NAME = "book_name"
private const val START_TAB = "start_tab"

fun savedReminderSettings(context: Context): ReminderSettings = context.getSharedPreferences(PROFILE_PREFS, 0).run {
    ReminderSettings(getBoolean(REMINDER_ENABLED, false), getInt(REMINDER_HOUR, 20), getInt(REMINDER_MINUTE, 0))
}

fun saveReminderSettings(context: Context, settings: ReminderSettings) {
    context.getSharedPreferences(PROFILE_PREFS, 0).edit()
        .putBoolean(REMINDER_ENABLED, settings.enabled)
        .putInt(REMINDER_HOUR, settings.hour)
        .putInt(REMINDER_MINUTE, settings.minute).apply()
    ReminderScheduler.update(context, settings)
}

fun savedAppLock(context: Context): Boolean = context.getSharedPreferences(PROFILE_PREFS, 0).getBoolean(APP_LOCK, false)
fun saveAppLock(context: Context, enabled: Boolean) = context.getSharedPreferences(PROFILE_PREFS, 0).edit().putBoolean(APP_LOCK, enabled).apply()

fun savedBookName(context: Context): String = context.getSharedPreferences(PROFILE_PREFS, 0)
    .getString(BOOK_NAME, "").orEmpty().trim().take(40)

fun saveBookName(context: Context, name: String) {
    context.getSharedPreferences(PROFILE_PREFS, 0).edit().putString(BOOK_NAME, name.trim().take(40)).apply()
}

fun savedStartTab(context: Context): Int = context.getSharedPreferences(PROFILE_PREFS, 0)
    .getInt(START_TAB, 0).coerceIn(0, 3)

fun saveStartTab(context: Context, tab: Int) {
    context.getSharedPreferences(PROFILE_PREFS, 0).edit().putInt(START_TAB, tab.coerceIn(0, 3)).apply()
}

internal fun profileSettingsBackup(context: Context): JSONObject {
    val reminder = savedReminderSettings(context)
    return JSONObject()
        .put("bookName", savedBookName(context))
        .put("startTab", savedStartTab(context))
        .put("reminderEnabled", reminder.enabled)
        .put("reminderHour", reminder.hour)
        .put("reminderMinute", reminder.minute)
}

internal fun restoreProfileSettings(context: Context, json: JSONObject?) {
    if (json == null) return
    saveBookName(context, json.optString("bookName", ""))
    saveStartTab(context, json.optInt("startTab", 0))
    saveReminderSettings(
        context,
        ReminderSettings(
            enabled = json.optBoolean("reminderEnabled", false),
            hour = json.optInt("reminderHour", 20).coerceIn(0, 23),
            minute = json.optInt("reminderMinute", 0).coerceIn(0, 59),
        ),
    )
}

object ReminderScheduler {
    fun update(context: Context, settings: ReminderSettings) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val intent = PendingIntent.getBroadcast(
            context, 42, Intent(context, ExpenseReminderReceiver::class.java).setAction("pl.kakebo.REMIND"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarm.cancel(intent)
        if (!settings.enabled) return
        var next = LocalDateTime.now().withHour(settings.hour).withMinute(settings.minute).withSecond(0).withNano(0)
        if (!next.isAfter(LocalDateTime.now())) next = next.plusDays(1)
        alarm.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            AlarmManager.INTERVAL_DAY,
            intent,
        )
    }
}

class ExpenseReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.update(context, savedReminderSettings(context)); return
        }
        val settings = savedReminderSettings(context)
        if (!settings.enabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel("expense_reminders", context.getString(R.string.profile_reminder), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(context, 43, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, "expense_reminders")
            .setSmallIcon(R.drawable.ic_hero_book_open)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(context.getString(R.string.reminder_notification_text))
            .setContentIntent(open).setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(context).notify(42, notification) }
    }
}
