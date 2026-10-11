package pl.kakebo.data.repository

import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pl.kakebo.data.local.EntryDao
import pl.kakebo.data.local.EntryEntity
import pl.kakebo.data.receipts.ReceiptStore

class EntryRepository(private val dao: EntryDao, private val receipts: ReceiptStore? = null) {
    private val mutations = Mutex()
    val entries = dao.observeAll()
    val count = dao.observeCount()
    fun observeMonth(month: YearMonth) = dao.observeExpenses(
        month.atDay(1).toEpochDay(), month.plusMonths(1).atDay(1).toEpochDay(),
    )

    suspend fun save(entry: EntryEntity) { persist(entry) }
    suspend fun add(entry: EntryEntity): Long {
        require(entry.id == 0L)
        return persist(entry)
    }

    suspend fun addAll(entries: List<EntryEntity>): List<Long> = withContext(NonCancellable + Dispatchers.IO) {
        require(entries.all { it.id == 0L && it.receiptName == null })
        mutations.withLock { dao.insertAll(entries) }
    }

    // Finish the file + database operation even when the screen is closed during saving.
    private suspend fun persist(entry: EntryEntity): Long = withContext(NonCancellable + Dispatchers.IO) {
        mutations.withLock {
            val previous = if (entry.id == 0L) null else requireNotNull(dao.findById(entry.id))
            val savedName = entry.receiptName?.let { requireNotNull(receipts).commit(it) }
            val saved = entry.copy(receiptName = savedName)
            val id = try {
                if (saved.id == 0L) dao.insert(saved) else { dao.update(saved); saved.id }
            } catch (error: Throwable) {
                if (savedName != null && savedName != entry.receiptName) receipts?.deleteSaved(savedName)
                throw error
            }
            // Cleanup comes after the DB commit. A cleanup failure must not report a failed save.
            runCatching {
                receipts?.discardDraft(entry.receiptName)
                if (previous?.receiptName != savedName) removeUnused(previous?.receiptName)
            }
            id
        }
    }

    suspend fun delete(id: Long) = withContext(NonCancellable + Dispatchers.IO) {
        mutations.withLock {
            val previous = dao.findById(id)
            dao.deleteById(id)
            runCatching { removeUnused(previous?.receiptName) }
            Unit
        }
    }

    private suspend fun removeUnused(name: String?) {
        if (name != null && dao.receiptReferences(name) == 0) receipts?.deleteSaved(name)
    }
}
