package app.refundavio

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.io.File

@RunWith(AndroidJUnit4::class)
class DashboardJourneyTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private var fixtureId: Long? = null

    @After fun removeFixture() = runBlocking {
        fixtureId?.let { id ->
            fixtureLedger().use { it.ledger().remove(id) }
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
            fixtureLedger().use { database ->
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
            fixtureLedger().use {
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
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(requireNotNull(context.getExternalFilesDir(null)), "dashboard-qa.png").outputStream().use {
            check(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    private fun fixtureLedger() = Room.databaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LedgerDb::class.java,
        "refundavio.db").build()
}
