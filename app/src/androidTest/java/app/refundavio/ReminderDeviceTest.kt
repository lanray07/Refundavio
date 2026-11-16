package app.refundavio

import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class ReminderDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun seedAlarmForClosedAppTest() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("seedReminders") == "true")
        check(context.packageName.endsWith(".qa") || arguments.getString("disposableEmulator") == "true")
        val dao = LedgerDb.open(context).ledger()
        val delay = arguments.getString("delaySeconds")?.toLongOrNull()?.coerceIn(45, 300) ?: 65
        val alarm = Instant.now().plusSeconds(delay).truncatedTo(ChronoUnit.SECONDS)
        val local = alarm.atZone(ZoneId.systemDefault())
        val date = local.toLocalDate()
        val warranty = arguments.getString("includeWarranty") == "true"
        val case = RecoveryCase(item = "Private QA item", merchant = "Private QA merchant", currency = "GBP",
            expected = 2000, stage = Stage.AWAITING_DISPATCH, deadline = date.plusDays(1), followUp = date,
            warranty = date.plusDays(7).takeIf { warranty })
        dao.insert(CaseRow.from(case))
        ReminderPreferences.save(context, ReminderSettings(deadlines = true, followUps = true, warranties = warranty,
            leadDays = setOf(1), time = local.toLocalTime()))
        ReminderScheduler.reconcile(context)
        // The host can kill the process as soon as instrumentation returns.
        val persistedSettings = File(context.applicationInfo.dataDir, "shared_prefs/reminders.xml")
        val savedTime = """name="second_of_day" value="${local.toLocalTime().toSecondOfDay()}""""
        withTimeout(5_000) {
            while (!persistedSettings.isFile || !persistedSettings.readText().contains(savedTime)) {
                delay(50)
            }
        }
        val posted = context.getSystemService(NotificationManager::class.java).activeNotifications
        val ids = if (warranty) listOf(1001, 1002, 1003) else listOf(1001, 1002)
        assertTrue("No reminder may post before the preferred time", posted.none { it.id in ids })
        File(context.filesDir, "reminder-test-trigger.txt").writeText(alarm.toString())
    }

    @Test fun versionOneLedgerKeepsItsCasesAfterMigrations() = runBlocking {
        val name = "migration-reminder-test.db"
        context.deleteDatabase(name)
        try {
            val schema = InstrumentationRegistry.getInstrumentation().context.assets
                .open("app.refundavio.LedgerDb/1.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
            val file = context.getDatabasePath(name)
            requireNotNull(file.parentFile).mkdirs()
            SQLiteDatabase.openOrCreateDatabase(file, null).use { legacy ->
                val tables = schema.getJSONArray("entities")
                for (i in 0 until tables.length()) {
                    val table = tables.getJSONObject(i)
                    val tableName = table.getString("tableName")
                    legacy.execSQL(table.getString("createSql").replace("\${TABLE_NAME}", tableName))
                    val indices = table.getJSONArray("indices")
                    for (j in 0 until indices.length()) legacy.execSQL(
                        indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", tableName)
                    )
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) legacy.execSQL(setup.getString(i))
                legacy.version = 1
                legacy.insertOrThrow("cases", null, ContentValues().apply {
                    put("id", 42)
                    put("item", "V1 item")
                    put("merchant", "V1 merchant")
                    put("currency", "GBP")
                    put("expected", 7500)
                    put("stage", "PROCESSING")
                    put("deadline", LocalDate.of(2027, 1, 1).toEpochDay())
                    put("note", "")
                    put("orderReference", "")
                    put("carrier", "")
                    put("tracking", "")
                })
            }
            val upgraded = Room.databaseBuilder(context, LedgerDb::class.java, name)
                .addMigrations(LedgerDb.remindersMigration, LedgerDb.archiveMigration, LedgerDb.merchantMigration).build()
            try {
                assertEquals("V1 item", upgraded.ledger().get(42)?.row?.item)
                assertEquals(false, upgraded.ledger().get(42)?.row?.archived)
                upgraded.ledger().setArchived(42, true)
                assertEquals(true, upgraded.ledger().get(42)?.row?.archived)
                assertTrue(upgraded.ledger().reminderCases().isEmpty())
                upgraded.ledger().setArchived(42, false)
                assertEquals(42L, upgraded.ledger().reminderCases().single().row.id)
                val merchant = MerchantRow(merchantKey("V1 merchant"), "V1 merchant", "https://example.test", "",
                    "service@example.test", "User-entered return notes")
                upgraded.ledger().saveMerchant(merchant)
                assertEquals(listOf(merchant), upgraded.ledger().observeMerchants().first())
                val history = ReminderHistory(42, ReminderKind.DEADLINE.name, 20_000, 19_999)
                upgraded.ledger().markReminders(listOf(history))
                assertEquals(listOf(history), upgraded.ledger().reminderHistory())
                upgraded.ledger().remove(42)
                assertTrue(upgraded.ledger().reminderHistory().isEmpty())
                assertEquals(listOf(merchant), upgraded.ledger().observeMerchants().first())
            } finally {
                upgraded.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
        Unit
    }

    @Test fun warrantyOnlyRecordPostsPrivateNotification() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("testWarranty") == "true")
        check(context.packageName.endsWith(".qa"))
        val manager = context.getSystemService(NotificationManager::class.java)
        assumeTrue(manager.areNotificationsEnabled())
        val original = ReminderPreferences.read(context)
        val dao = LedgerDb.open(context).ledger()
        val today = LocalDate.now()
        val id = dao.insert(CaseRow.from(RecoveryCase(item = "Warranty QA item", merchant = "Warranty QA shop",
            currency = "GBP", expected = 0, stage = Stage.NOT_REQUESTED, warranty = today)))
        try {
            ReminderPreferences.save(context, ReminderSettings(warranties = true, time = java.time.LocalTime.now().minusSeconds(5)))
            ReminderScheduler.reconcile(context)
            val posted = manager.activeNotifications.single { it.id == ReminderKind.WARRANTY.notificationId }.notification
            val message = posted.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString()
            assertEquals(localizedAppContext(context).getString(R.string.reminder_warranty_notice), message)
            assertEquals(android.app.Notification.VISIBILITY_PRIVATE, posted.visibility)
            assertNotNull(posted.contentIntent)
            assertTrue(dao.reminderHistory().any { it.caseId == id && it.kind == "WARRANTY" })
        } finally {
            dao.remove(id)
            ReminderPreferences.save(context, original)
            ReminderScheduler.reconcile(context)
        }
    }
}
