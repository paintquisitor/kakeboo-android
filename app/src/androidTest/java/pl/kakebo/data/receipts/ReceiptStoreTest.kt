package pl.kakebo.data.receipts

import android.content.Context
import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pl.kakebo.data.local.*
import pl.kakebo.data.repository.EntryRepository
import pl.kakebo.domain.ExpenseSubcategory

@RunWith(AndroidJUnit4::class)
class ReceiptStoreTest {
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KakeboDatabase::class.java)
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = ReceiptStore(context)
    private fun expense(receipt: String?) = EntryEntity(amountGrosz = 1234, type = EntryType.EXPENSE,
        dateEpochDay = LocalDate.of(2026, 9, 28).toEpochDay(), subcategory = ExpenseSubcategory.FOOD, receiptName = receipt)
    private fun image(): File = File.createTempFile("receipt-test-", ".jpg", context.cacheDir).apply {
        val bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
        outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
    }

    @Test fun migrationPreservesExpensesPlansReflectionsAndMakesPhotoOptional() {
        val name = "receipt-migration-test.db"
        migration.createDatabase(name, 5).apply {
            execSQL("INSERT INTO monthly_plans VALUES ('2026-09', 50000, 'Cel')")
            execSQL("INSERT INTO planned_items (id, month, type, title, amountGrosz, dateEpochDay) VALUES (10, '2026-09', 'EXPENSE', 'Czynsz', 100000, 20724)")
            execSQL("INSERT INTO entries VALUES (42, 12345, 'EXPENSE', 20724, 'Zakupy', 'NEEDS_OTHER', 10)")
            execSQL("INSERT INTO monthly_reflections VALUES ('2026-09', 'Udało się', 'Poprawa', 'Kolejny cel')")
            close()
        }
        migration.runMigrationsAndValidate(name, 6, true, KakeboDatabase.MIGRATION_5_6).use { db ->
            db.query("SELECT amountGrosz, note, subcategory, plannedItemId, receiptName FROM entries WHERE id = 42").use {
                assertTrue(it.moveToFirst()); assertEquals(12345L, it.getLong(0)); assertEquals("Zakupy", it.getString(1))
                assertEquals("NEEDS_OTHER", it.getString(2)); assertEquals(10L, it.getLong(3)); assertTrue(it.isNull(4))
            }
            db.query("SELECT successes, nextMonth FROM monthly_reflections").use {
                assertTrue(it.moveToFirst()); assertEquals("Udało się", it.getString(0)); assertEquals("Kolejny cel", it.getString(1))
            }
            db.query("SELECT savingsGrosz FROM monthly_plans").use { assertTrue(it.moveToFirst()); assertEquals(50000L, it.getLong(0)) }
        }
    }

    @Test fun importPersistsAfterSourceRemovalAndRestartThenReplacementRemovalAndDeletionCleanFiles() = runBlocking {
        val name = "receipt-persistence-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, KakeboDatabase::class.java, name).build()
        var db = open()
        val files = mutableSetOf<String>()
        val source = image()
        try {
            var repository = EntryRepository(db.entryDao(), store)
            val draft = store.importImage(Uri.fromFile(source)).also(files::add)
            val id = repository.add(expense(draft))
            var entry = db.entryDao().findById(id)!!
            val original = entry.receiptName!!.also(files::add)
            assertFalse(store.isDraft(original)); assertFalse(store.file(draft).exists())
            source.delete()
            db.close(); db = open(); repository = EntryRepository(db.entryDao(), store)
            entry = db.entryDao().findById(id)!!
            assertEquals(original, entry.receiptName)
            store.preview(original).also { assertEquals(80, it.width); it.recycle() }
            val replacement = store.importImage(Uri.fromFile(store.file(original))).also(files::add)
            repository.save(entry.copy(receiptName = replacement))
            val newer = db.entryDao().findById(id)!!.also { files.add(it.receiptName!!) }
            assertFalse(store.file(original).exists()); assertTrue(store.file(newer.receiptName!!).exists())
            repository.save(newer.copy(receiptName = null))
            assertFalse(store.file(newer.receiptName!!).exists()); assertNull(db.entryDao().findById(id)!!.receiptName)
            val newSource = image()
            try {
                val draft2 = store.importImage(Uri.fromFile(newSource)).also(files::add)
                repository.save(newer.copy(receiptName = draft2))
                val last = db.entryDao().findById(id)!!.receiptName!!.also(files::add)
                repository.delete(id)
                assertFalse(store.file(last).exists()); assertNull(db.entryDao().findById(id))
            } finally { newSource.delete() }
        } finally {
            db.close(); context.deleteDatabase(name); source.delete()
            files.forEach { store.file(it).delete() }
        }
    }

    @Test fun invalidImageAndOversizeInputAreRejectedAndFailedSaveKeepsOriginalAndDraft() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, KakeboDatabase::class.java).build()
        val source = image()
        val names = mutableSetOf<String>()
        val invalid = File.createTempFile("receipt-invalid-", ".txt", context.cacheDir)
        try {
            invalid.writeText("not an image")
            try { store.importImage(Uri.fromFile(invalid)); fail("Invalid file accepted") } catch (_: IllegalArgumentException) { }
            java.io.RandomAccessFile(invalid, "rw").use { it.setLength(25L * 1024 * 1024 + 1) }
            try { store.importImage(Uri.fromFile(invalid)); fail("Oversize file accepted") } catch (_: IllegalArgumentException) { }
            try { store.file("../kakebo.db"); fail("Traversal accepted") } catch (_: IllegalArgumentException) { }
            val repository = EntryRepository(db.entryDao(), store)
            val draft = store.importImage(Uri.fromFile(source)).also(names::add)
            val id = repository.add(expense(draft))
            val entry = db.entryDao().findById(id)!!
            names.add(entry.receiptName!!)
            val replacement = store.importImage(Uri.fromFile(source)).also(names::add)
            try { repository.save(entry.copy(receiptName = replacement, plannedItemId = 999)); fail("Invalid plan accepted") }
            catch (_: IllegalArgumentException) { }
            assertEquals(entry.receiptName, db.entryDao().findById(id)!!.receiptName)
            assertTrue(store.file(entry.receiptName!!).exists()); assertTrue(store.file(replacement).exists())
            store.discardDraft(replacement)
            assertFalse(store.file(replacement).exists()); assertTrue(store.file(entry.receiptName!!).exists())
        } finally { db.close(); source.delete(); invalid.delete(); names.forEach { store.file(it).delete() } }
    }

    @Test fun cameraTargetUsesContentUriAndPreviewHonorsExifRotation(): Unit = runBlocking {
        val source = image()
        val target = store.createCameraTarget()
        var draft: String? = null
        try {
            ExifInterface(source.absolutePath).apply { setAttribute(ExifInterface.TAG_ORIENTATION, "6"); saveAttributes() }
            val uri = store.cameraUri(target)
            assertEquals("content", uri.scheme)
            context.contentResolver.openOutputStream(uri)!!.use { output -> source.inputStream().use { it.copyTo(output) } }
            draft = store.finishCamera(target)
            assertFalse(store.file(target).exists())
            store.preview(draft).also { assertEquals(40, it.width); assertEquals(80, it.height); it.recycle() }
        } finally { source.delete(); store.discardDraft(target); store.discardDraft(draft) }
    }
}
