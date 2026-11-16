package app.refundavio

import android.content.Context
import android.content.res.Configuration
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.io.File
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class DashboardJourneyTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private var fixtureId: Long? = null

    @After fun removeFixture() = runBlocking {
        fixtureId?.let { id ->
            withFixtureLedger { it.ledger().remove(id) }
        }
        Unit
    }

    @Test fun recordPartialRefundAndReopenSavedLedger() {
        val item = "Refundavio QA backpack"
        val merchant = "Refundavio QA merchant"
        val context = ApplicationProvider.getApplicationContext<Context>()
        val add = context.getString(R.string.add_purchase)
        ui.onNodeWithContentDescription(add).performClick()
        ui.onNodeWithText(context.getString(R.string.item)).performTextInput(item)
        ui.onNodeWithText(context.getString(R.string.merchant)).performTextInput(merchant)
        ui.onNodeWithText(context.getString(R.string.label_value, context.getString(R.string.return_status),
            context.getString(R.string.status_not_requested))).performClick()
        ui.onNodeWithText(context.getString(R.string.status_processing)).performScrollTo().performClick()
        ui.onNodeWithText(context.getString(R.string.currency)).performTextClearance()
        ui.onNodeWithText(context.getString(R.string.currency)).performTextInput("GBP")
        ui.onNodeWithText(context.getString(R.string.expected_amount)).performTextInput("75")
        ui.onNodeWithText(context.getString(R.string.save)).performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText(item).fetchSemanticsNodes().isNotEmpty() }
        fixtureId = runBlocking {
            withFixtureLedger { database ->
                database.query("SELECT id FROM cases WHERE item = ? ORDER BY id DESC LIMIT 1", arrayOf(item)).use {
                    if (it.moveToFirst()) it.getLong(0) else error("Saved purchase missing from Room")
                }
            }
        }
        ui.onNodeWithText(context.getString(R.string.record_refund)).performScrollTo().performClick()
        ui.onNodeWithText(context.getString(R.string.amount_received)).performTextInput("25")
        ui.onNodeWithText(context.getString(R.string.save)).performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText(context.getString(R.string.status_partial)).fetchSemanticsNodes().isNotEmpty() }
        ui.activityRule.scenario.recreate()
        ui.waitUntil(10_000) { ui.onAllNodesWithText(context.getString(R.string.status_partial)).fetchSemanticsNodes().isNotEmpty() }
        val day = LocalDate.now()
        runBlocking {
            withFixtureLedger {
                val saved = requireNotNull(it.ledger().get(requireNotNull(fixtureId))).snapshot()
                check(saved.outstanding.longValueExact() == 5000L && saved.receipts.single().day == day)
            }
        }
    }

    @Test fun showActionQueuesAndBalancesInDemo() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ui.onNodeWithText(context.getString(R.string.more)).performClick()
        ui.onNodeWithText(context.getString(R.string.preview_demo)).performClick()
        ui.onNodeWithText(context.getString(R.string.demo_label)).assertIsDisplayed()
        ui.onNodeWithText(context.getString(R.string.waiting_for)).assertIsDisplayed()
        ui.onAllNodesWithText(context.getString(R.string.deadlines)).onFirst().assertIsDisplayed()
        ui.onAllNodesWithText(context.getString(R.string.followups)).onFirst().assertIsDisplayed()
        ui.waitForIdle()
        val screenshot = ui.onRoot().captureToImage().asAndroidBitmap()
        File(context.filesDir, "dashboard-qa.png").outputStream().use {
            check(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun eastAsianResourcesFollowDeviceRegionAndExplicitScript() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val warrantiesByLocale = mapOf(
            "ko-KR" to "보증",
            "zh-CN" to "保修",
            "zh-SG" to "保修",
            "zh-Hans-CN" to "保修",
            "zh-TW" to "保固",
            "zh-HK" to "保固",
            "zh-Hant-TW" to "保固",
        )
        for ((tag, expected) in warrantiesByLocale) {
            val config = Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            }
            val localized = context.createConfigurationContext(config)
            assertEquals(tag, expected, localized.getString(R.string.warranties))
        }
    }

    @Test fun turkishAndIndonesianResourcesFollowDeviceLocale() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for ((tag, expected) in mapOf("tr-TR" to "Garantiler", "id-ID" to "Garansi", "in-ID" to "Garansi")) {
            val config = Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            }
            assertEquals(tag, expected, context.createConfigurationContext(config).getString(R.string.warranties))
        }
    }

    @Test fun regionalResourcesInheritTranslatedBaseStrings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cases = listOf(
            Triple("fr-CA", "Code de devise (p. ex. CAD)", "Remboursements en attente"),
            Triple("es-MX", "Código de moneda (p. ej., MXN)", "Reembolsos pendientes"),
            Triple("es-419", "Código de moneda (p. ej., MXN)", "Reembolsos pendientes"),
        )
        for ((tag, currency, pending) in cases) {
            val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
            val localized = context.createConfigurationContext(configuration)
            assertEquals(tag, currency, localized.getString(R.string.currency))
            assertEquals(tag, pending, localized.getString(R.string.waiting_for))
        }
    }

    @Test fun setupCurrencyPrefillsNextPurchase() {
        val activity = ui.activity
        val prefs = activity.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        val original = prefs.getString("preferred_currency", null)
        try {
            ui.onNodeWithText(activity.getString(R.string.more)).performClick()
            ui.onNodeWithText(activity.getString(R.string.setup_open)).performScrollTo().performClick()
            ui.onNodeWithText(activity.getString(R.string.setup_title)).assertIsDisplayed()
            ui.onNode(hasText(activity.getString(R.string.preferred_currency), substring = true) and hasAnyAncestor(isDialog()))
                .performScrollTo().performClick()
            val currency = ui.onNodeWithText(activity.getString(R.string.currency))
            currency.performTextClearance()
            currency.performTextInput("USD")
            ui.onNodeWithText(activity.getString(R.string.save)).performClick()
            ui.onNodeWithText(activity.getString(R.string.add_purchase)).performClick()
            ui.onNodeWithText("USD").assertIsDisplayed()
            ui.onNodeWithText(activity.getString(R.string.cancel)).performClick()
        } finally {
            prefs.edit().putString("preferred_currency", original).commit()
        }
    }

    @Test fun aTouchShortcutCanOpenDeadlinesWithoutSpeechRecognition() {
        val activity = ui.activity
        val speech = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        org.junit.Assume.assumeTrue(speech.resolveActivity(activity.packageManager) == null)
        ui.onNodeWithText(activity.getString(R.string.more)).performClick()
        ui.onNodeWithText(activity.getString(R.string.preview_demo)).performScrollTo().performClick()
        ui.onNodeWithContentDescription(activity.getString(R.string.voice_title)).performClick()
        ui.onNodeWithText(activity.getString(R.string.listen)).assertIsNotEnabled()
        ui.onNodeWithText(activity.getString(R.string.voice_unavailable)).assertIsDisplayed()
        ui.onNodeWithText(activity.getString(R.string.voice_deadlines)).performClick()
        ui.onNodeWithText(activity.getString(R.string.run_command)).performClick()
        ui.onNodeWithText(activity.getString(R.string.label_value, activity.getString(R.string.show),
            activity.getString(R.string.deadlines))).assertIsDisplayed()
    }

    @Test fun anotherOrderItemKeepsItsOwnRecordAndPrefillsSharedFields() {
        val activity = ui.activity
        val dao = LedgerDb.open(activity).ledger()
        val merchant = "Refundavio QA order shop"
        val firstItem = "Refundavio QA order first item"
        val secondItem = "Refundavio QA order second item"
        val order = "REFUNDAVIO-QA-ORDER-LINK"
        val firstId = runBlocking {
            dao.insert(CaseRow.from(RecoveryCase(item = firstItem, merchant = merchant, currency = "EUR", expected = 9800,
                stage = Stage.PROCESSING, orderReference = order)))
        }
        try {
            ui.onNodeWithText(activity.getString(R.string.purchases)).performClick()
            ui.waitUntil(10_000) { ui.onAllNodesWithText(firstItem).fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText(firstItem).performClick()
            ui.onNodeWithText(activity.getString(R.string.order_items)).performScrollTo().performClick()
            ui.onNodeWithText(activity.getString(R.string.add_order_item)).performClick()
            ui.onNodeWithText(activity.getString(R.string.item)).performTextInput(secondItem)
            ui.onNodeWithText(activity.getString(R.string.save)).performClick()
            ui.waitUntil(10_000) { runBlocking { dao.observe().first().any { it.row.item == secondItem } } }
            runBlocking {
                val entries = dao.observe().first().filter { it.row.orderReference == order && it.row.merchant == merchant }
                assertEquals(2, entries.size)
                val newItem = entries.single { it.row.item == secondItem }.row
                assertEquals("EUR", newItem.currency)
                assertEquals(0L, newItem.expected)
                assertEquals(9800L, dao.get(firstId)?.row?.expected)
            }
        } finally {
            runBlocking {
                dao.observe().first().filter { it.row.item in listOf(firstItem, secondItem) &&
                    it.row.merchant == merchant && it.row.orderReference == order }.forEach { dao.remove(it.row.id) }
            }
        }
    }

    @Test fun exportReportFormatsOpenAndIncludeSavedValues() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val day = LocalDate.of(2026, 11, 16)
        val sample = CaseSnapshot(RecoveryCase(1, "QA report item", "QA shop", "GBP", 7500, Stage.PROCESSING,
            note = (1..100).joinToString("\n") { "QA note line $it" }), listOf(Receipt(amount = 2500, day = day)))
        for (format in listOf(ReportFormat.CSV, ReportFormat.TXT)) {
            val output = java.io.ByteArrayOutputStream()
            exportReport(context, listOf(sample), day, format, output)
            val content = output.toString(Charsets.UTF_8.name())
            for (value in listOf("QA report item", "75.00", "25.00", "50.00", "QA note line 100")) {
                assertTrue(format.name, value in content)
            }
        }
        val pdf = File.createTempFile("refundavio-qa-", ".pdf", context.cacheDir)
        try {
            pdf.outputStream().use { exportReport(context, listOf(sample), day, ReportFormat.PDF, it) }
            PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                assertTrue(renderer.pageCount > 1)
                renderer.openPage(renderer.pageCount - 1).use { assertTrue(it.width > 0) }
            }
        } finally {
            pdf.delete()
        }
    }

    private suspend fun <T> withFixtureLedger(action: suspend (LedgerDb) -> T): T {
        val database = Room.databaseBuilder(ApplicationProvider.getApplicationContext<Context>(),
            LedgerDb::class.java, "refundavio.db").build()
        return try { action(database) } finally { database.close() }
    }
}
