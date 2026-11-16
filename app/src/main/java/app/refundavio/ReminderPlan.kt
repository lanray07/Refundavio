package app.refundavio

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

enum class ReminderKind {
    DEADLINE, FOLLOW_UP, WARRANTY
}

data class ReminderSettings(
    val deadlines: Boolean = false,
    val followUps: Boolean = false,
    val leadDays: Set<Int> = setOf(7, 3, 1),
    val customLead: Int? = null,
    val time: LocalTime = LocalTime.of(9, 0),
    val warranties: Boolean = false,
    val quietHours: QuietHours? = null,
) {
    val enabled get() = deadlines || followUps || warranties
    val allLeadDays get() = leadDays + listOfNotNull(customLead)
    fun enables(kind: ReminderKind) = when (kind) {
        ReminderKind.DEADLINE -> deadlines
        ReminderKind.FOLLOW_UP -> followUps
        ReminderKind.WARRANTY -> warranties
    }
}

data class QuietHours(val start: LocalTime, val end: LocalTime) {
    init { require(start != end) }

    fun defer(time: LocalDateTime): LocalDateTime {
        val clock = time.toLocalTime()
        val day = time.toLocalDate()
        return if (start < end) {
            if (clock >= start && clock < end) day.atTime(end) else time
        } else when {
            clock >= start -> day.plusDays(1).atTime(end)
            clock < end -> day.atTime(end)
            else -> time
        }
    }
}

data class ReminderKey(val caseId: Long, val kind: ReminderKind)
data class ReminderEvent(val key: ReminderKey, val target: LocalDate, val day: LocalDate)
data class ReminderPlan(val due: List<ReminderEvent>, val next: Instant?, val existingNotices: Set<ReminderKind>) {
    companion object {
        fun calculate(
            cases: List<CaseSnapshot>, delivered: Map<ReminderKey, ReminderEvent>, settings: ReminderSettings,
            now: Instant, zone: ZoneId
        ): ReminderPlan {
            val today = now.atZone(zone).toLocalDate()
            val holdUntil = settings.quietHours?.defer(now.atZone(zone).toLocalDateTime())
                ?.atZone(zone)?.toInstant()?.takeIf { it > now }
            val due = mutableListOf<ReminderEvent>()
            val existingNotices = mutableSetOf<ReminderKind>()
            var next: Instant? = null
            fun deliveryAt(day: LocalDate): LocalDateTime {
                val intended = day.atTime(settings.time)
                return settings.quietHours?.defer(intended) ?: intended
            }
            fun consider(key: ReminderKey, target: LocalDate, days: List<LocalDate>, expires: Boolean = false) {
                val possible = if (expires && target < today) days.filter { deliveryAt(it).toLocalDate() == today } else days
                if (possible.isEmpty()) return
                val mostRecent = delivered[key]?.takeIf { it.target == target }?.day
                if (mostRecent != null) existingNotices += key.kind
                var latestDue: LocalDate? = null
                for (day in possible) {
                    if (mostRecent != null && day <= mostRecent) continue
                    val instant = deliveryAt(day).atZone(zone).toInstant()
                    if (instant <= now) {
                        if (holdUntil != null) {
                            if (next == null || holdUntil < next) next = holdUntil
                        } else if (latestDue == null || day > latestDue) latestDue = day
                    } else if (next == null || instant < next) {
                        next = instant
                    }
                }
                latestDue?.let { due += ReminderEvent(key, target, it) }
            }

            for (item in cases) {
                val record = item.record
                if (record.archived) continue
                if (settings.warranties) {
                    record.warranty?.let { expiry ->
                        consider(ReminderKey(record.id, ReminderKind.WARRANTY), expiry, listOf(expiry.minusDays(7), expiry), expires = true)
                    }
                }
                if (item.stage.terminal) continue
                if (settings.deadlines && item.stage.canDispatch) {
                    record.deadline?.let { deadline ->
                        consider(ReminderKey(record.id, ReminderKind.DEADLINE), deadline,
                            settings.allLeadDays.map { deadline.minusDays(it.toLong()) }, expires = true)
                    }
                }
                if (settings.followUps && item.stage != Stage.NOT_REQUESTED) {
                    record.followUp?.let { date ->
                        consider(ReminderKey(record.id, ReminderKind.FOLLOW_UP), date, listOf(date))
                    }
                }
            }
            return ReminderPlan(due, next, existingNotices)
        }
    }
}
