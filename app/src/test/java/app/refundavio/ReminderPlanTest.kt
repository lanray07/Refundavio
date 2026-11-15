package app.refundavio

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ReminderPlanTest {
    private val zone = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 11, 15)
    private val now = Instant.parse("2026-11-15T09:00:00Z")
    private val enabled = ReminderSettings(deadlines = true, followUps = true)
    private val deadline = ReminderKey(1, ReminderKind.DEADLINE)
    private val followUp = ReminderKey(1, ReminderKind.FOLLOW_UP)
    private fun case(returnDate: LocalDate? = null, followUpDate: LocalDate? = null,
                     stage: Stage = Stage.AWAITING_DISPATCH, paid: Boolean = false) =
        CaseSnapshot(RecoveryCase(1, "Item", "Merchant", "GBP", 100, stage, returnDate, followUpDate),
            if (paid) listOf(Receipt(amount = 100, day = today)) else emptyList())

    @Test fun selectsLatestMissedDeadlineSlotAndPreservesFutureSlot() {
        val item = case(today.plusDays(3), today)
        val plan = ReminderPlan.calculate(listOf(item), emptyMap(), enabled, now, zone)
        assertEquals(
            listOf(ReminderEvent(deadline, today.plusDays(3), today), ReminderEvent(followUp, today, today)),
            plan.due
        )
        assertEquals(Instant.parse("2026-11-17T09:00:00Z"), plan.next)
        val afterDelivery = ReminderPlan.calculate(listOf(item), plan.due.associateBy { it.key }, enabled, now, zone)
        assertTrue(afterDelivery.due.isEmpty())
        assertEquals(plan.next, afterDelivery.next)
    }

    @Test fun customLeadIsCoalescedWithPresetAndTimeMovesTheAlarm() {
        val item = case(today.plusDays(5))
        val settings = enabled.copy(leadDays = setOf(5), customLead = 5, time = LocalTime.of(10, 30))
        val before = ReminderPlan.calculate(listOf(item), emptyMap(), settings, now, zone)
        assertTrue(before.due.isEmpty())
        assertEquals(Instant.parse("2026-11-15T10:30:00Z"), before.next)
        val after = ReminderPlan.calculate(listOf(item), emptyMap(), settings, before.next!!, zone)
        assertEquals(listOf(ReminderEvent(deadline, today.plusDays(5), today)), after.due)
    }

    @Test fun completedDispatchedAndExpiredCasesAreHandledIndependently() {
        fun plan(item: CaseSnapshot) = ReminderPlan.calculate(listOf(item), emptyMap(), enabled, now, zone)
        assertTrue(plan(case(today, today, stage = Stage.CLOSED)).due.isEmpty())
        assertTrue(plan(case(today, today, paid = true)).due.isEmpty())
        assertEquals(listOf(followUp), plan(case(today, today.minusDays(2), stage = Stage.DISPATCHED)).due.map { it.key })
        assertEquals(listOf(followUp), plan(case(today.minusDays(1), today)).due.map { it.key })
        assertEquals(listOf(deadline), plan(case(today, today, stage = Stage.NOT_REQUESTED)).due.map { it.key })
    }

    @Test fun rescheduledContactUsesNewDateAndTurningOffRemovesItFromThePlan() {
        val oldDue = ReminderEvent(followUp, today.minusDays(1), today.minusDays(1))
        val rescheduled = case(followUpDate = today.plusDays(2))
        val prior = mapOf(followUp to oldDue)
        val plan = ReminderPlan.calculate(listOf(rescheduled), prior, enabled, now, zone)
        assertTrue(plan.due.isEmpty())
        assertEquals(Instant.parse("2026-11-17T09:00:00Z"), plan.next)
        val off = ReminderPlan.calculate(listOf(rescheduled), prior, enabled.copy(followUps = false), now, zone)
        assertNull(off.next)
        assertTrue(off.existingNotices.isEmpty())
        assertEquals(listOf(followUp), ReminderPlan.calculate(listOf(rescheduled), prior, enabled, plan.next!!, zone).due.map { it.key })
    }

    @Test fun springClockChangeUsesTheNextValidLocalTime() {
        val date = LocalDate.of(2026, 3, 29)
        val settings = enabled.copy(time = LocalTime.of(1, 30))
        val before = Instant.parse("2026-03-29T00:45:00Z")
        val plan = ReminderPlan.calculate(listOf(case(followUpDate = date)), emptyMap(), settings, before, zone)
        assertTrue(plan.due.isEmpty())
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), plan.next)
    }
}
