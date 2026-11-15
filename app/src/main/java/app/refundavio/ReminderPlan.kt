package app.refundavio

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class ReminderKind {
    DEADLINE, FOLLOW_UP
}

data class ReminderSettings(
    val deadlines: Boolean = false,
    val followUps: Boolean = false,
    val leadDays: Set<Int> = setOf(7, 3, 1),
    val customLead: Int? = null,
    val time: LocalTime = LocalTime.of(9, 0)
) {
    val enabled get() = deadlines || followUps
    val allLeadDays get() = leadDays + listOfNotNull(customLead)
    fun enables(kind: ReminderKind) = if (kind == ReminderKind.DEADLINE) deadlines else followUps
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
            val due = mutableListOf<ReminderEvent>()
            val existingNotices = mutableSetOf<ReminderKind>()
            var next: Instant? = null
            fun consider(key: ReminderKey, target: LocalDate, days: List<LocalDate>) {
                if (days.isEmpty()) return
                val mostRecent = delivered[key]?.takeIf { it.target == target }?.day
                if (mostRecent != null) existingNotices += key.kind
                var latestDue: LocalDate? = null
                for (day in days) {
                    if (mostRecent != null && day <= mostRecent) continue
                    val instant = day.atTime(settings.time).atZone(zone).toInstant()
                    if (instant <= now) {
                        if (latestDue == null || day > latestDue) latestDue = day
                    } else if (next == null || instant < next) {
                        next = instant
                    }
                }
                latestDue?.let { due += ReminderEvent(key, target, it) }
            }

            for (item in cases) {
                val record = item.record
                if (item.stage.terminal) continue
                if (settings.deadlines && item.stage.canDispatch) {
                    record.deadline?.takeIf { it >= today }?.let { deadline ->
                        consider(ReminderKey(record.id, ReminderKind.DEADLINE), deadline,
                            settings.allLeadDays.map { deadline.minusDays(it.toLong()) })
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
