package pl.kakebo.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pl.kakebo.data.repository.CategoryRepository
import pl.kakebo.data.repository.DuplicateCategoryNameException
import pl.kakebo.domain.*
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class CustomCategoryDatabaseTest {
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KakeboDatabase::class.java)
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun migrationPreservesExistingCategoriesReceiptAndPlan() {
        val name = "custom-category-migration-test.db"
        migration.createDatabase(name, 6).use { db ->
            db.execSQL("INSERT INTO monthly_plans VALUES ('2026-09', 50000, 'Cel')")
            db.execSQL("INSERT INTO planned_items (id, month, type, title, amountGrosz, dateEpochDay) VALUES (10, '2026-09', 'EXPENSE', 'Czynsz', 100000, 20724)")
            db.execSQL("INSERT INTO entries VALUES (42, 12345, 'EXPENSE', 20724, 'Zakupy', 'NEEDS_OTHER', 10, 'receipt.img')")
            db.execSQL("INSERT INTO monthly_reflections VALUES ('2026-09', 'Sukces', '', '')")
        }
        migration.runMigrationsAndValidate(name, 7, true, KakeboDatabase.MIGRATION_6_7).use { db ->
            db.query("SELECT amountGrosz, note, subcategory, plannedItemId, receiptName, customSubcategoryId FROM entries WHERE id = 42").use {
                assertTrue(it.moveToFirst()); assertEquals(12345L, it.getLong(0)); assertEquals("Zakupy", it.getString(1))
                assertEquals("NEEDS_OTHER", it.getString(2)); assertEquals(10L, it.getLong(3))
                assertEquals("receipt.img", it.getString(4)); assertTrue(it.isNull(5))
            }
            db.query("SELECT savingsGrosz FROM monthly_plans").use { assertTrue(it.moveToFirst()); assertEquals(50000L, it.getLong(0)) }
            db.query("SELECT successes FROM monthly_reflections").use { assertTrue(it.moveToFirst()); assertEquals("Sukces", it.getString(0)) }
            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }

    @Test fun iconMigrationPreservesCustomCategoryAndExpense() {
        val name = "category-icon-migration-test.db"
        migration.createDatabase(name, 7).use { db ->
            db.execSQL("INSERT INTO custom_categories VALUES (1, 'Edukacja', 'edukacja')")
            db.execSQL("INSERT INTO custom_subcategories VALUES (2, 1, 'Kursy', 'kursy')")
            db.execSQL("INSERT INTO entries (id, amountGrosz, type, dateEpochDay, note, customSubcategoryId, receiptName) VALUES (3, 12500, 'EXPENSE', 20724, 'Kurs', 2, 'receipt.img')")
        }
        migration.runMigrationsAndValidate(name, 8, true, KakeboDatabase.MIGRATION_7_8).use { db ->
            db.query("SELECT c.name, c.icon, s.name, e.amountGrosz, e.receiptName FROM entries e JOIN custom_subcategories s ON e.customSubcategoryId = s.id JOIN custom_categories c ON s.categoryId = c.id").use {
                assertTrue(it.moveToFirst())
                assertEquals("Edukacja", it.getString(0)); assertEquals("WALLET", it.getString(1))
                assertEquals("Kursy", it.getString(2)); assertEquals(12500L, it.getLong(3))
                assertEquals("receipt.img", it.getString(4))
            }
            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }

    @Test fun renameAndDeleteKeepExpensesReceiptsAndOtherCategoriesIntact() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, KakeboDatabase::class.java).build()
        try {
            val repo = CategoryRepository(db.categoryDao())
            val category = repo.addCategory("Transport", CategoryIcon.TRANSPORT)
            val other = repo.addCategory("Praca")
            val taxi = repo.addSubcategory(category, "Taxi")
            val train = repo.addSubcategory(category, "Pociąg")
            val bus = repo.addSubcategory(category, "Autobus")
            val otherSubcategory = repo.addSubcategory(other, "Taxi")
            val originals = listOf(taxi, train, bus, otherSubcategory).mapIndexed { i, subcategory ->
                val entry = EntryEntity(amountGrosz = 1200L + i, type = EntryType.EXPENSE,
                    dateEpochDay = YearMonth.of(2026, 9).atDay(1).plusMonths(i.toLong()).toEpochDay(),
                    note = "Przejazd $i", customSubcategoryId = subcategory, receiptName = "receipt-$i.img")
                entry.copy(id = db.entryDao().insert(entry))
            }
            repo.renameCategory(category, "  Komunikacja  ")
            repo.renameSubcategory(taxi, " Przejazdy ")
            repo.renameSubcategory(taxi, "Przejazdy") // Saving an unchanged name is valid.
            try { repo.renameCategory(category, " PRACA "); fail("Duplicate accepted") } catch (_: DuplicateCategoryNameException) { }
            try { repo.renameSubcategory(taxi, " POCIĄG "); fail("Duplicate accepted") } catch (_: DuplicateCategoryNameException) { }
            val renamed = repo.catalog.first()
            assertEquals("Komunikacja", renamed.custom.first().category.name)
            assertEquals(CategoryIcon.TRANSPORT.name, renamed.custom.first().category.icon)
            assertEquals("Przejazdy", renamed.subcategories[taxi]?.name)
            assertEquals(originals.sortedBy { it.id }, db.entryDao().observeAll().first().sortedBy { it.id })
            repo.deleteSubcategory(taxi)
            var saved = db.entryDao().observeAll().first().associateBy { it.id }
            originals.forEachIndexed { i, entry -> assertEquals(if (i == 0) entry.copy(customSubcategoryId = null) else entry, saved[entry.id]) }
            assertFalse(repo.catalog.first().subcategories.containsKey(taxi))
            repo.deleteCategory(category)
            saved = db.entryDao().observeAll().first().associateBy { it.id }
            originals.forEachIndexed { i, entry -> assertEquals(if (i < 3) entry.copy(customSubcategoryId = null) else entry, saved[entry.id]) }
            assertEquals(listOf(other), repo.catalog.first().custom.map { it.category.id })
            assertEquals(setOf(otherSubcategory), repo.catalog.first().subcategories.keys)
            db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { db.close() }
    }

    @Test fun customCategoriesPersistAndReportsKeepThemSeparateFromBuiltInsAndUncategorized() = runBlocking {
        val name = "custom-category-persistence-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, KakeboDatabase::class.java, name).build()
        var db = open()
        try {
            val repo = CategoryRepository(db.categoryDao())
            val category = repo.addCategory("  Edukacja  ", CategoryIcon.BOOK)
            val secondCategory = repo.addCategory("Praca")
            val subcategory = repo.addSubcategory(category, "Kursy")
            repo.addSubcategory(secondCategory, "Kursy") // Same label in a different parent is valid.
            try { repo.addCategory("edukacja"); fail("Duplicate accepted") } catch (_: DuplicateCategoryNameException) { }
            try { repo.addSubcategory(category, " KURSY "); fail("Duplicate accepted") } catch (_: DuplicateCategoryNameException) { }
            try { repo.addSubcategory(99999, "Sierota"); fail("Missing parent accepted") } catch (_: android.database.sqlite.SQLiteConstraintException) { }
            val month = YearMonth.of(2026, 9)
            val date = month.atDay(8).toEpochDay()
            db.entryDao().insert(EntryEntity(amountGrosz = 12500, type = EntryType.EXPENSE, dateEpochDay = date, customSubcategoryId = subcategory))
            db.entryDao().insert(EntryEntity(amountGrosz = 3000, type = EntryType.EXPENSE, dateEpochDay = date, subcategory = ExpenseSubcategory.BOOKS))
            db.entryDao().insert(EntryEntity(amountGrosz = 100, type = EntryType.EXPENSE, dateEpochDay = date))
            db.close(); db = open()
            val catalog = CategoryRepository(db.categoryDao()).catalog.first()
            assertEquals(CategoryIcon.BOOK.name, catalog.custom.first().category.icon)
            CategoryRepository(db.categoryDao()).updateIcon(category, CategoryIcon.MUSIC)
            db.close(); db = open()
            val updated = CategoryRepository(db.categoryDao()).catalog.first()
            assertEquals(CategoryIcon.MUSIC.name, updated.custom.first().category.icon)
            assertEquals(CategoryIcon.WALLET.name, updated.custom.last().category.icon)
            assertEquals(catalog.subcategories, updated.subcategories)
            val entries = db.entryDao().observeAll().first()
            assertEquals("Edukacja", catalog.custom.first().category.name)
            assertEquals("Kursy", catalog.subcategories[subcategory]?.name)
            val plan = MonthlyPlanWithItems(MonthlyPlanEntity(month.toString()), emptyList())
            val monthly = monthlyReport(plan, entries, catalog)
            assertEquals(12500L, monthly.categoryTotals[CategoryKey(customId = category)])
            assertEquals(3000L, monthly.categoryTotals[ExpenseCategory.HOBBY.key])
            assertEquals(100L, monthly.categoryTotals[null])
            assertEquals(15600L, monthly.totalExpenses)
            val week = weeklyReports(plan, entries, catalog).single { it.entries.isNotEmpty() }
            assertEquals(monthly.categoryTotals, week.categoryTotals)
            assertEquals(15600L, week.spent)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
