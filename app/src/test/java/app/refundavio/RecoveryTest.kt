package app.refundavio

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

class RecoveryTest {
    private val today = LocalDate.of(2026, 11, 15)
    private fun record(id: Long, expected: Long, stage: Stage = Stage.PROCESSING,
                       code: String = "GBP", deadline: Long? = null, followUp: Long? = null,
                       vararg payments: Long) = CaseSnapshot(
        RecoveryCase(id, "Item $id", "Merchant $id", code, expected, stage,
            deadline?.let(today::plusDays), followUp?.let(today::plusDays)),
        payments.mapIndexed { i, n -> Receipt(i.toLong(), n, today.minusDays(i.toLong())) }
    )

    @Test fun multiplePaymentsAndClosedCasesHaveSeparateCurrencyTotals() {
        val partial = record(1, 8900, payments = longArrayOf(1200, 400))
        val euro = record(2, 2000, code = "EUR")
        val cancelled = record(3, 1700, Stage.CANCELLED, payments = longArrayOf(500))
        val notRequested = record(4, 100, Stage.NOT_REQUESTED)
        val full = record(5, 300, payments = longArrayOf(300))
        val dashboard = Dashboard.from(listOf(partial, euro, cancelled, notRequested, full), today)
        assertEquals(mapOf("EUR" to BigDecimal(2000), "GBP" to BigDecimal(7300)), dashboard.outstanding)
        assertEquals(mapOf("GBP" to BigDecimal(2400)), dashboard.received)
        assertEquals(Stage.PARTIAL, partial.stage)
        assertEquals(listOf(5L), dashboard.recent.map { it.record.id })
    }

    @Test fun overpaymentNeverCreatesNegativeBalanceAndReversalReopensDerivedStatus() {
        val original = record(1, 1500, payments = longArrayOf(2000))
        assertEquals(Stage.REFUNDED, original.stage)
        assertEquals(BigDecimal.ZERO, original.outstanding)
        val corrected = original.copy(receipts = emptyList())
        assertEquals(Stage.PROCESSING, corrected.stage)
        assertEquals(BigDecimal(1500), corrected.outstanding)
    }

    @Test fun dispatchAndCompletionSuppressInapplicableActions() {
        val imminent = record(1, 0, Stage.NOT_REQUESTED, deadline = 0)
        val expired = record(2, 300, Stage.AWAITING_DISPATCH, deadline = -1, followUp = 1)
        val shipped = record(3, 900, Stage.DISPATCHED, deadline = 2, followUp = -1)
        val completed = record(4, 100, Stage.PROCESSING, deadline = 1, followUp = 0, payments = longArrayOf(100))
        val dashboard = Dashboard.from(listOf(shipped, completed, expired, imminent), today)
        assertEquals(listOf(1L, 2L), dashboard.returns.map { it.record.id })
        assertEquals(listOf(3L, 2L), dashboard.followUps.map { it.record.id })
        assertEquals(listOf(2L), dashboard.dispatch.map { it.record.id })
    }

    @Test fun parsesLocalNumbersWithoutRoundingOrGrouping() {
        assertEquals(1099L, Money.parse("10,99", "EUR", Locale.FRANCE))
        assertEquals(890L, Money.parse("890", "JPY", Locale.JAPAN))
        assertEquals(1250L, Money.parse("1.250", "KWD", Locale.UK))
        assertNull(Money.parse("12.30", "JPY", Locale.JAPAN))
        assertNull(Money.parse("1.999", "GBP", Locale.UK))
        assertNull(Money.parse("1,000.2", "GBP", Locale.UK))
        assertNull(Money.parse("-2", "GBP", Locale.UK))
        assertNull(Money.parse("99999999999999999999999999", "GBP", Locale.UK))
        assertEquals("10,99", Money.input(1099, "EUR", Locale.FRANCE))
    }

    @Test fun hugeTotalsRetainAllDigitsBeyondLongRange() {
        val data = Dashboard.from(listOf(record(1, Long.MAX_VALUE), record(2, Long.MAX_VALUE)), today)
        assertEquals(BigDecimal("18446744073709551614"), data.outstanding.getValue("GBP"))
    }
}
