package app.refundavio

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

@Entity(tableName = "cases")
data class CaseRow(
    @PrimaryKey(autoGenerate = true) val id: Long, val item: String, val merchant: String,
    val currency: String, val expected: Long, val stage: String, val deadline: Long?,
    val followUp: Long?, val note: String, val warranty: Long?, val purchaseAmount: Long?,
    val purchaseDate: Long?, val orderReference: String, val carrier: String, val tracking: String,
    val attachment: String?
) {
    fun domain() = RecoveryCase(id, item, merchant, currency, expected, Stage.valueOf(stage), deadline?.let(LocalDate::ofEpochDay),
        followUp?.let(LocalDate::ofEpochDay), note, warranty?.let(LocalDate::ofEpochDay), purchaseAmount,
        purchaseDate?.let(LocalDate::ofEpochDay), orderReference, carrier, tracking, attachment)
    companion object {
        fun from(c: RecoveryCase) = CaseRow(c.id, c.item, c.merchant, c.currency, c.expected, c.stage.name,
            c.deadline?.toEpochDay(), c.followUp?.toEpochDay(), c.note, c.warranty?.toEpochDay(), c.purchaseAmount,
            c.purchaseDate?.toEpochDay(), c.orderReference, c.carrier, c.tracking, c.attachment)
    }
}

@Entity(tableName = "receipts", foreignKeys = [ForeignKey(entity = CaseRow::class, parentColumns = ["id"],
    childColumns = ["caseId"], onDelete = ForeignKey.CASCADE)], indices = [Index("caseId")])
data class ReceiptRow(@PrimaryKey(autoGenerate = true) val id: Long = 0, val caseId: Long, val amount: Long, val day: Long)

@Entity(tableName = "contacts", foreignKeys = [ForeignKey(entity = CaseRow::class, parentColumns = ["id"],
    childColumns = ["caseId"], onDelete = ForeignKey.CASCADE)], indices = [Index("caseId")])
data class ContactRow(@PrimaryKey(autoGenerate = true) val id: Long = 0, val caseId: Long, val day: Long, val note: String)

data class Joined(
    @Embedded val row: CaseRow,
    @Relation(parentColumn = "id", entityColumn = "caseId") val receipts: List<ReceiptRow>,
    @Relation(parentColumn = "id", entityColumn = "caseId") val contacts: List<ContactRow>
) {
    fun snapshot() = CaseSnapshot(row.domain(), receipts.map { Receipt(it.id, it.amount, LocalDate.ofEpochDay(it.day)) }
        .sortedWith(compareByDescending<Receipt> { it.day }.thenByDescending { it.id }),
        contacts.map { Contact(it.id, LocalDate.ofEpochDay(it.day), it.note) }
            .sortedWith(compareByDescending<Contact> { it.day }.thenByDescending { it.id }))
}

@Dao
interface LedgerDao {
    @Transaction @Query("SELECT * FROM cases ORDER BY id DESC") fun observe(): Flow<List<Joined>>
    @Transaction @Query("SELECT * FROM cases WHERE id = :id") suspend fun get(id: Long): Joined?
    @Insert suspend fun insert(row: CaseRow): Long
    @Update suspend fun update(row: CaseRow)
    @Insert suspend fun insertReceipt(row: ReceiptRow)
    @Insert suspend fun insertContact(row: ContactRow)
    @Query("DELETE FROM receipts WHERE id = :id AND caseId = :caseId") suspend fun removeReceipt(caseId: Long, id: Long)
    @Query("DELETE FROM cases WHERE id = :id") suspend fun remove(id: Long)
    @Query("DELETE FROM cases") suspend fun erase()
    @Query("UPDATE cases SET followUp = :date WHERE id = :id") suspend fun followUp(id: Long, date: Long?)
}

@Database(entities = [CaseRow::class, ReceiptRow::class, ContactRow::class], version = 1, exportSchema = true)
abstract class LedgerDb : RoomDatabase() { abstract fun ledger(): LedgerDao }

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(application, LedgerDb::class.java, "refundavio.db").build()
    private val dao = db.ledger()
    private val refresh = MutableStateFlow(0L)
    val cases = refresh.flatMapLatest {
        dao.observe().map<List<Joined>, LedgerState> { LedgerState.Ready(it.map(Joined::snapshot)) }
            .onStart { emit(LedgerState.Loading) }
            .catch { emit(LedgerState.Unavailable) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LedgerState.Loading)
    fun reload() { refresh.update { it + 1 } }
    private val _error = MutableStateFlow(false)
    val error = _error.asStateFlow()
    fun clearError() { _error.value = false }
    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { db.withTransaction { block() } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { _error.value = true }
        }
    }
    fun save(c: RecoveryCase, done: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                val id = db.withTransaction {
                    require(c.item.isNotBlank() && c.merchant.isNotBlank() && c.expected >= 0 && Money.valid(c.currency))
                    if (c.id == 0L) dao.insert(CaseRow.from(c)) else {
                        val old = requireNotNull(dao.get(c.id))
                        require(old.receipts.isEmpty() || old.row.currency == c.currency)
                        dao.update(CaseRow.from(c))
                        c.id
                    }
                }
                done(id)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _error.value = true }
        }
    }
    fun receive(caseId: Long, currency: String, amount: Long, day: LocalDate) = write {
        val data = requireNotNull(dao.get(caseId)).snapshot()
        require(data.record.currency == currency && amount > 0 && !day.isAfter(LocalDate.now()))
        dao.insertReceipt(ReceiptRow(caseId = caseId, amount = amount, day = day.toEpochDay()))
    }
    fun removeReceipt(caseId: Long, id: Long) = write { dao.removeReceipt(caseId, id) }
    fun delete(id: Long) = write { dao.remove(id) }
    fun erase() = write { dao.erase() }
    fun snooze(id: Long, date: LocalDate?) = write { dao.followUp(id, date?.toEpochDay()) }
    fun contact(id: Long, note: String, day: LocalDate, next: LocalDate?) = write {
        require(note.isNotBlank() && !day.isAfter(LocalDate.now()))
        dao.insertContact(ContactRow(caseId = id, day = day.toEpochDay(), note = note))
        dao.followUp(id, next?.toEpochDay())
    }
}

sealed interface LedgerState {
    data object Loading : LedgerState
    data object Unavailable : LedgerState
    data class Ready(val cases: List<CaseSnapshot>) : LedgerState
}
