package app.refundavio

import java.io.Writer
import java.time.LocalDate
import java.util.Currency

internal data class ReportTable(val title: String, val headings: List<String>, val rows: List<List<String>>)

internal fun reportTables(
    cases: List<CaseSnapshot>, today: LocalDate, label: (Int) -> String,
): List<ReportTable> {
    val dashboard = Dashboard.from(cases, today)
    fun table(title: Int, headings: List<Int>, rows: List<List<String>>) =
        ReportTable(label(title), headings.map(label), rows)
    fun amount(value: java.math.BigDecimal, currency: String) =
        value.scaleByPowerOfTen(-Currency.getInstance(currency).defaultFractionDigits).toPlainString()
    fun CaseSnapshot.identifiers() = listOf(record.id.toString(), record.item, record.merchant)
    val keys = listOf(R.string.report_record, R.string.item, R.string.merchant)
    val currencies = (dashboard.outstanding.keys + dashboard.received.keys).toSortedSet()
    return listOf(
        table(R.string.recovery_title, listOf(R.string.currency, R.string.waiting_for, R.string.received), currencies.map { code ->
            listOf(code, amount(dashboard.outstanding[code] ?: java.math.BigDecimal.ZERO, code),
                amount(dashboard.received[code] ?: java.math.BigDecimal.ZERO, code))
        }),
        table(R.string.purchases, keys + listOf(R.string.show, R.string.purchase_date, R.string.order_ref,
            R.string.currency, R.string.purchase_amount, R.string.return_status, R.string.expected_amount,
            R.string.received, R.string.waiting_for, R.string.notes), cases.map { entry ->
            val record = entry.record
            entry.identifiers() + listOf(label(if (record.archived) R.string.archived_purchases else R.string.active_purchases),
                record.purchaseDate?.toString().orEmpty(), record.orderReference,
                record.currency, record.purchaseAmount?.let { amount(it.toBigDecimal(), record.currency) }.orEmpty(),
                label(entry.stage.label), amount(record.expected.toBigDecimal(), record.currency),
                amount(entry.received, record.currency), amount(entry.outstanding, record.currency), record.note)
        }),
        table(R.string.pending, keys + listOf(R.string.currency, R.string.waiting_for, R.string.followup_date),
            dashboard.pending.map { it.identifiers() + listOf(it.record.currency,
                amount(it.outstanding, it.record.currency), it.record.followUp?.toString().orEmpty()) }),
        table(R.string.refund_history, keys + listOf(R.string.received_date, R.string.currency, R.string.amount_received),
            cases.flatMap { entry -> entry.receipts.map { receipt ->
                entry.identifiers() + listOf(receipt.day.toString(), entry.record.currency,
                    amount(receipt.amount.toBigDecimal(), entry.record.currency))
            } }.sortedByDescending { it[3] }),
        table(R.string.deadlines, keys + listOf(R.string.return_deadline, R.string.return_status),
            dashboard.returns.map { it.identifiers() + listOf(it.record.deadline.toString(), label(it.stage.label)) }),
        table(R.string.followups, keys + listOf(R.string.followup_date, R.string.return_status),
            dashboard.followUps.map { it.identifiers() + listOf(it.record.followUp.toString(), label(it.stage.label)) }),
        table(R.string.contact_history, keys + listOf(R.string.contact_date, R.string.notes),
            cases.flatMap { entry -> entry.contacts.map { contact ->
                entry.identifiers() + listOf(contact.day.toString(), contact.note)
            } }.sortedByDescending { it[3] }),
        table(R.string.warranties, keys + R.string.warranty_expiry,
            cases.filter { it.record.warranty != null }.sortedBy { it.record.warranty }
                .map { it.identifiers() + it.record.warranty.toString() }),
    )
}

internal fun writeReportCsv(writer: Writer, tables: List<ReportTable>, section: String) {
    val headings = tables.flatMap { it.headings }.distinct()
    fun row(cells: List<String>) {
        writer.write(cells.joinToString(",") { cell ->
            val prefix = if (cell.trimStart().firstOrNull() in listOf('=', '+', '-', '@') ||
                cell.firstOrNull() in listOf('\t', '\r', '\n')) "'" else ""
            "\"$prefix${cell.replace("\"", "\"\"")}\""
        })
        writer.write("\r\n")
    }
    row(listOf(section) + headings)
    for (table in tables) {
        for (values in table.rows) {
            val fields = table.headings.zip(values).toMap()
            row(listOf(table.title) + headings.map { fields[it].orEmpty() })
        }
    }
}

internal fun reportLines(title: String, tables: List<ReportTable>, empty: String): Sequence<String> = sequence {
    yield(title)
    for (table in tables) {
        yield("")
        yield(table.title)
        if (table.rows.isEmpty()) yield(empty)
        for (row in table.rows) {
            for ((heading, value) in table.headings.zip(row)) {
                if (value.isNotBlank()) yield("$heading: $value")
            }
            yield("")
        }
    }
}
