package app.refundavio

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

internal const val REMINDER_DESTINATION = "app.refundavio.reminder_destination"
private const val ALARM_ACTION = "app.refundavio.REMINDER_ALARM"

internal fun localizedAppContext(base: Context): Context {
    val tag = base.getSharedPreferences("preferences", Context.MODE_PRIVATE).getString("locale", "").orEmpty()
    if (tag.isEmpty()) return base
    val requested = Locale.forLanguageTag(tag)
    val device = base.resources.configuration.locales[0]
    val locale = if (device.language == requested.language) device else requested
    val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
    return base.createConfigurationContext(config)
}

internal object ReminderPreferences {
    val presetDays = listOf(7, 3, 1, 0)

    fun read(context: Context): ReminderSettings {
        val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
        val defaults = ReminderSettings()
        val offsets = prefs.getStringSet("lead_days", defaults.leadDays.map(Int::toString).toSet()).orEmpty()
        return ReminderSettings(
            deadlines = prefs.getBoolean("deadlines", false),
            followUps = prefs.getBoolean("follow_ups", false),
            leadDays = offsets.mapNotNull(String::toIntOrNull).filter { it in presetDays }.toSet(),
            customLead = prefs.getInt("custom_lead", -1).takeIf { it in 0..30 },
            time = LocalTime.ofSecondOfDay(prefs.getLong("second_of_day", defaults.time.toSecondOfDay().toLong())
                .coerceIn(0, 86_399))
        )
    }

    fun save(context: Context, settings: ReminderSettings) {
        context.getSharedPreferences("reminders", Context.MODE_PRIVATE).edit()
            .putBoolean("deadlines", settings.deadlines)
            .putBoolean("follow_ups", settings.followUps)
            .putStringSet("lead_days", settings.leadDays.map(Int::toString).toSet())
            .putInt("custom_lead", settings.customLead ?: -1)
            .putLong("second_of_day", settings.time.toSecondOfDay().toLong())
            .apply()
    }
}

internal object ReminderScheduler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    fun request(context: Context, finished: () -> Unit = {}) {
        val app = context.applicationContext
        scope.launch {
            try {
                reconcile(app)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("RefundavioReminders", "Could not refresh reminders: ${e.javaClass.simpleName}")
                mutex.withLock {
                    if (ReminderPreferences.read(app).enabled) {
                        runCatching { arm(app, Instant.now().plus(Duration.ofMinutes(15))) }
                    }
                }
            } finally {
                finished()
            }
        }
    }

    internal suspend fun reconcile(context: Context) = mutex.withLock {
        val settings = ReminderPreferences.read(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!settings.enabled) {
            arm(context, null)
            ReminderKind.entries.forEach { manager.cancel(it.notificationId) }
            return@withLock
        }

        val localized = localizedAppContext(context)
        createChannels(localized)
        val db = LedgerDb.open(context)
        val plan = db.withTransaction {
            val dao = db.ledger()
            val now = Instant.now()
            val result = ReminderPlan.calculate(
                dao.reminderCases().map(Joined::snapshot),
                dao.reminderHistory().map(ReminderHistory::event).associateBy(ReminderEvent::key),
                settings, now, ZoneId.systemDefault()
            )
            for (kind in ReminderKind.entries) {
                val due = result.due.filter { it.key.kind == kind }
                if (!canPost(context, kind) || (due.isEmpty() && kind !in result.existingNotices)) {
                    manager.cancel(kind.notificationId)
                    continue
                }
                if (due.isNotEmpty() && post(localized, kind)) {
                    dao.markReminders(due.map(ReminderHistory::from))
                }
            }
            result
        }
        arm(context, plan.next)
    }

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        for (kind in ReminderKind.entries) {
            manager.createNotificationChannel(NotificationChannel(
                kind.channel, context.getString(kind.title), NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(kind.body)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                setShowBadge(false)
            })
        }
    }

    fun canPost(context: Context, kind: ReminderKind): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(kind.channel)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun arm(context: Context, at: Instant?) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val broadcast = Intent(context, ReminderReceiver::class.java).setAction(ALARM_ACTION)
        val existing = PendingIntent.getBroadcast(context, 0, broadcast,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (at == null) {
            if (existing != null) {
                alarms.cancel(existing)
                existing.cancel()
            }
        } else {
            val pending = existing ?: PendingIntent.getBroadcast(context, 0, broadcast, PendingIntent.FLAG_IMMUTABLE)
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending)
        }
    }

    @SuppressLint("MissingPermission")
    private fun post(context: Context, kind: ReminderKind): Boolean {
        if (!canPost(context, kind)) return false
        val open = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(REMINDER_DESTINATION, kind.name)
        val content = PendingIntent.getActivity(context, kind.notificationId, open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(context, kind.channel)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(context.getString(kind.title))
            .setContentText(context.getString(kind.body))
            .setContentIntent(content)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setTimeoutAfter(Duration.ofDays(1).toMillis())
            .build()
        return try {
            context.getSystemService(NotificationManager::class.java).notify(kind.notificationId, notification)
            true
        } catch (_: SecurityException) {
            false // Notification permission was revoked between the check and posting.
        }
    }
}

private val ReminderKind.channel get() = if (this == ReminderKind.DEADLINE) "return_deadlines" else "merchant_follow_ups"
private val ReminderKind.title get() = if (this == ReminderKind.DEADLINE) R.string.deadlines else R.string.followups
private val ReminderKind.body get() = if (this == ReminderKind.DEADLINE) R.string.reminder_deadline_notice else R.string.reminder_followup_notice
internal val ReminderKind.notificationId get() = if (this == ReminderKind.DEADLINE) 1001 else 1002

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(ALARM_ACTION, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) return
        val result = goAsync()
        ReminderScheduler.request(context) { result.finish() }
    }
}
