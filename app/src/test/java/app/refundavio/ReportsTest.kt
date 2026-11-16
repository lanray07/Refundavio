package app.refundavio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter
import java.time.LocalDate

class ReportsTest {
    @Test fun separatesExpectedReceivedAndOutstandingByCurrency() {
        val today = LocalDate.of(2026, 11, 16)
        val pending = CaseSnapshot(RecoveryCase(1, "Bag", "Shop", "GBP", 7500, Stage.PROCESSING,
            followUp = today), listOf(Receipt(amount = 2500, day = today)))
        val cancelled = CaseSnapshot(RecoveryCase(2, "Shoes", "Shop", "EUR", 3000, Stage.CANCELLED),
            listOf(Receipt(amount = 500, day = today)))
        val tables = reportTables(listOf(pending, cancelled), today, Int::toString)

        assertEquals(listOf(listOf("EUR", "0.00", "5.00"), listOf("GBP", "50.00", "25.00")),
            tables.single { it.title == R.string.recovery_title.toString() }.rows)
        assertEquals(listOf("1", "Bag", "Shop", "GBP", "50.00", today.toString()),
            tables.single { it.title == R.string.pending.toString() }.rows.single())
        assertEquals(listOf("25.00", "5.00"), tables.single { it.title == R.string.refund_history.toString() }
            .rows.map { it.last() })
    }

    @Test fun csvPreservesQuotedTextAndNeutralizesSpreadsheetFormulas() {
        val table = ReportTable("Purchases", listOf("Item", "Note"),
            listOf(listOf("Bag, \"Blue\"", "  =1+1"), listOf("Shoes", "Line 1\nLine 2")))
        val writer = StringWriter()
        writeReportCsv(writer, listOf(table), "Section")

        assertTrue(writer.toString().contains("\"Purchases\",\"Bag, \"\"Blue\"\"\",\"'  =1+1\"\r\n"))
        assertTrue(writer.toString().contains("\"Line 1\nLine 2\""))
    }
}
