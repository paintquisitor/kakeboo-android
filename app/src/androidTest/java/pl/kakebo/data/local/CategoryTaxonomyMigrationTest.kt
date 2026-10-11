package pl.kakebo.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pl.kakebo.domain.ExpenseSubcategory

@RunWith(AndroidJUnit4::class)
class CategoryTaxonomyMigrationTest {
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KakeboDatabase::class.java)
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun upgradePreservesFinancialDataAndCustomCategoriesAndAllEntriesRemainReadable() = runBlocking {
        val name = "category-taxonomy-migration-test.db"
        val expected = linkedMapOf(
            "SUPERMARKET" to "FOOD", "FOOD_SHOPS" to "FOOD", "PHARMACY" to "HEALTH_MEDICINE",
            "CHILDREN" to "CHILDREN", "TRANSPORT" to "TRANSPORT", "PETS" to "PETS",
            "PHONE_TOPUP" to null, "NEEDS_OTHER" to null, "CLOTHING" to "CLOTHING",
            "COSMETICS" to "COSMETICS", "TAXIS" to "TRANSPORT", "GIFTS" to null, "OPTIONAL_OTHER" to null,
            "GAMES" to "GAMES", "FILMS" to null, "NEWSPAPERS" to "BOOKS", "BOOKS" to "BOOKS",
            "MUSIC" to "MEDIA", "SHOWS" to "PERFORMANCES", "HOBBY_OTHER" to "HOBBY_ACTIVITIES",
            "RESTAURANTS" to "DINING", "TAKEAWAY" to "DINING", "PUBS" to "DINING",
            "PARTIES" to null, "CAFES" to "DINING", "OUTINGS_OTHER" to null,
            "HEALTH" to "HEALTH_MEDICINE", "CHILDCARE" to "CHILDREN", "REPAIRS" to "REPAIRS",
            "ELECTRONICS" to null, "HOME_EQUIPMENT" to null, "TRAVEL" to "TRAVEL", "EXTRA_OTHER" to "EXTRA_OTHER",
        )
        val date = LocalDate.of(2026, 9, 28).toEpochDay()
        migration.createDatabase(name, 8).use { db ->
            db.execSQL("INSERT INTO monthly_plans VALUES ('2026-09', 50000, 'Cel')")
            db.execSQL("INSERT INTO planned_items (id, month, type, title, amountGrosz, dateEpochDay) VALUES (10, '2026-09', 'EXPENSE', 'Plan', 10000, ?)", arrayOf(date))
            expected.keys.forEachIndexed { index, old ->
                db.execSQL("INSERT INTO entries (id, amountGrosz, type, dateEpochDay, note, subcategory, plannedItemId, receiptName) VALUES (?, ?, 'EXPENSE', ?, ?, ?, 10, ?)",
                    arrayOf(index + 1, 1000L + index, date, "Wpis $index", old, "receipt-$index.img"))
            }
            db.execSQL("INSERT INTO custom_categories VALUES (1, 'Własna', 'własna', 'BOOK')")
            db.execSQL("INSERT INTO custom_subcategories VALUES (2, 1, 'Własna podkategoria', 'własna podkategoria')")
            db.execSQL("INSERT INTO entries (id, amountGrosz, type, dateEpochDay, note, customSubcategoryId, receiptName) VALUES (100, 4500, 'EXPENSE', ?, 'Własny wpis', 2, 'custom.img')", arrayOf(date))
            db.execSQL("INSERT INTO entries (id, amountGrosz, type, dateEpochDay, note) VALUES (101, 5000, 'INCOME', ?, 'Wpływ')", arrayOf(date))
        }
        migration.runMigrationsAndValidate(name, 11, true, KakeboDatabase.MIGRATION_8_9,
            KakeboDatabase.MIGRATION_9_10, KakeboDatabase.MIGRATION_10_11).use { db ->
            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
        val db = Room.databaseBuilder(context, KakeboDatabase::class.java, name).build()
        try {
            val entries = db.entryDao().observeAll().first().associateBy { it.id }
            assertEquals(expected.size + 2, entries.size)
            expected.values.forEachIndexed { index, target ->
                assertEquals(EntryEntity(id = index + 1L, amountGrosz = 1000L + index, type = EntryType.EXPENSE,
                    dateEpochDay = date, note = "Wpis $index", subcategory = target?.let(ExpenseSubcategory::valueOf),
                    plannedItemId = 10, receiptName = "receipt-$index.img"), entries[index + 1L])
            }
            assertEquals(EntryEntity(id = 100, amountGrosz = 4500, type = EntryType.EXPENSE, dateEpochDay = date,
                note = "Własny wpis", customSubcategoryId = 2, receiptName = "custom.img"), entries[100])
            assertEquals(EntryType.INCOME, entries[101]?.type)
            val custom = db.categoryDao().observeAll().first().single()
            assertEquals("Własna", custom.category.name)
            assertEquals("BOOK", custom.category.icon)
            assertEquals("Własna podkategoria", custom.subcategories.single().name)
            // Every newly introduced choice must round-trip through Room as well.
            ExpenseSubcategory.entries.forEach { subcategory ->
                db.entryDao().insert(EntryEntity(amountGrosz = 100, type = EntryType.EXPENSE,
                    dateEpochDay = date, note = "Nowy wpis", subcategory = subcategory))
            }
            assertEquals(ExpenseSubcategory.entries.toSet(), db.entryDao().observeAll().first()
                .filter { it.note == "Nowy wpis" }.map { it.subcategory }.toSet())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
