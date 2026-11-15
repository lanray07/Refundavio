package app.refundavio

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.ParsePosition
import java.time.LocalDate
import java.util.Currency
import java.util.Locale

enum class Stage {
    NOT_REQUESTED, REQUESTED, APPROVED, AWAITING_DISPATCH, DISPATCHED, MERCHANT_RECEIVED,
    REFUND_APPROVED, PROCESSING, PARTIAL, REFUNDED, REJECTED, CANCELLED, DISPUTED, CLOSED;

    val terminal get() = this in setOf(REFUNDED, REJECTED, CANCELLED, CLOSED)
    val awaitingDispatch get() = this in setOf(APPROVED, AWAITING_DISPATCH)
    val canDispatch get() = this in setOf(NOT_REQUESTED, REQUESTED, APPROVED, AWAITING_DISPATCH)
    val expectsRefund get() = !terminal && this != NOT_REQUESTED
}

data class RecoveryCase(
    val id: Long = 0, val item: String, val merchant: String, val currency: String,
    val expected: Long, val stage: Stage, val deadline: LocalDate? = null,
    val followUp: LocalDate? = null, val note: String = "", val warranty: LocalDate? = null,
    val purchaseAmount: Long? = null, val purchaseDate: LocalDate? = null, val orderReference: String = "",
    val carrier: String = "", val tracking: String = "", val attachment: String? = null
)
data class Receipt(val id: Long = 0, val amount: Long, val day: LocalDate)
data class Contact(val id: Long = 0, val day: LocalDate, val note: String)

data class CaseSnapshot(
    val record: RecoveryCase,
    val receipts: List<Receipt> = emptyList(),
    val contacts: List<Contact> = emptyList()
) {
    val received: BigDecimal get() = receipts.fold(BigDecimal.ZERO) { sum, receipt -> sum + receipt.amount.toBigDecimal() }
    val balance: BigDecimal get() = (record.expected.toBigDecimal() - received).max(BigDecimal.ZERO)
    val outstanding: BigDecimal get() = if (record.stage.expectsRefund) balance else BigDecimal.ZERO
    val stage: Stage get() = when {
        record.stage.terminal -> record.stage
        record.expected > 0 && balance.signum() == 0 -> Stage.REFUNDED
        received.signum() > 0 && balance.signum() > 0 &&
            record.stage !in setOf(Stage.DISPUTED, Stage.NOT_REQUESTED) -> Stage.PARTIAL
        else -> record.stage
    }
}

data class Dashboard(
    val pending: List<CaseSnapshot>, val returns: List<CaseSnapshot>, val followUps: List<CaseSnapshot>,
    val dispatch: List<CaseSnapshot>, val waitingForResponse: List<CaseSnapshot>,
    val recent: List<CaseSnapshot>, val warranties: List<CaseSnapshot>,
    val outstanding: Map<String, BigDecimal>, val received: Map<String, BigDecimal>
) {
    companion object {
        fun from(cases: List<CaseSnapshot>, today: LocalDate): Dashboard {
            val pending = cases.filter { it.outstanding.signum() > 0 }
                .sortedWith(compareBy<CaseSnapshot> { it.record.followUp ?: LocalDate.MAX }.thenBy { it.record.id })
            val active = cases.filter { !it.stage.terminal }
            val returns = active.filter { it.stage.canDispatch && it.record.deadline != null }
                .sortedWith(compareBy<CaseSnapshot> { it.record.deadline!!.isBefore(today) }
                    .thenBy { if (it.record.deadline!!.isBefore(today)) -it.record.deadline.toEpochDay() else it.record.deadline.toEpochDay() }
                    .thenBy { it.record.id })
            val followUps = active.filter { it.record.followUp != null && it.stage != Stage.NOT_REQUESTED }
                .sortedWith(compareBy<CaseSnapshot> { it.record.followUp }.thenBy { it.record.id })
            fun totals(items: List<CaseSnapshot>, amount: (CaseSnapshot) -> BigDecimal) =
                items.groupBy { it.record.currency }.toSortedMap()
                    .mapValues { (_, values) -> values.fold(BigDecimal.ZERO) { sum, entry -> sum + amount(entry) } }
                    .filterValues { it.signum() > 0 }
            return Dashboard(
                pending, returns, followUps,
                active.filter { it.stage.awaitingDispatch },
                active.filter { it.stage == Stage.REQUESTED },
                cases.filter { it.stage == Stage.REFUNDED && it.receipts.isNotEmpty() }
                    .sortedWith(compareByDescending<CaseSnapshot> { it.receipts.maxOf { receipt -> receipt.day } }.thenByDescending { it.record.id })
                    .take(5),
                cases.filter { it.record.warranty?.let { date -> !date.isBefore(today) && !date.isAfter(today.plusDays(30)) } == true }
                    .sortedBy { it.record.warranty },
                totals(pending) { it.outstanding }, totals(cases) { it.received }
            )
        }
    }
}

object Money {
    private val codes = Currency.getAvailableCurrencies().filter { it.defaultFractionDigits in 0..3 }.map { it.currencyCode }.toSet()
    fun valid(code: String) = code in codes
    fun parse(value: String, code: String, locale: Locale): Long? = runCatching {
        require(valid(code))
        val candidate = value.trim()
        val parser = NumberFormat.getNumberInstance(locale) as DecimalFormat
        parser.isGroupingUsed = false
        parser.isParseBigDecimal = true
        val position = ParsePosition(0)
        val number = parser.parse(candidate, position) as? BigDecimal ?: return null
        require(position.index == candidate.length && number.signum() >= 0)
        number.scaleByPowerOfTen(Currency.getInstance(code).defaultFractionDigits).longValueExact()
    }.getOrNull()
    fun input(minor: Long, code: String, locale: Locale): String {
        val digits = Currency.getInstance(code).defaultFractionDigits
        return NumberFormat.getNumberInstance(locale).apply {
            isGroupingUsed = false
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }.format(BigDecimal.valueOf(minor, digits))
    }
    fun format(minor: BigDecimal, code: String, locale: Locale): String {
        val unit = Currency.getInstance(code)
        return NumberFormat.getCurrencyInstance(locale).apply {
            currency = unit
            minimumFractionDigits = unit.defaultFractionDigits
            maximumFractionDigits = unit.defaultFractionDigits
            roundingMode = RoundingMode.UNNECESSARY
        }.format(minor.scaleByPowerOfTen(-unit.defaultFractionDigits))
    }
}
