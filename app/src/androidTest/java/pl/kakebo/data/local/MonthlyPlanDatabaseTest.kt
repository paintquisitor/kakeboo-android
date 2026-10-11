package pl.kakebo.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pl.kakebo.data.repository.MonthlyPlanRepository
import pl.kakebo.domain.isDueIn

@RunWith(AndroidJUnit4::class)
class MonthlyPlanDatabaseTest {
    @get:Rule
    val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KakeboDatabase::class.java)

    @Test
    fun planIsCopiedOnlyWhenTheMonthStartsAndFutureCopiesAreRemoved() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KakeboDatabase::class.java).build()
        try {
            val dao = db.monthlyPlanDao()
            dao.saveItem(PlannedItemEntity(month = "2026-09", type = EntryType.INCOME, title = "Pensja",
                amountGrosz = 400_000, dateEpochDay = LocalDate.of(2026, 9, 10).toEpochDay()))
            dao.saveItem(PlannedItemEntity(month = "2026-11", type = EntryType.INCOME, title = "Stara kopia",
                amountGrosz = 500_000, dateEpochDay = LocalDate.of(2026, 11, 10).toEpochDay()))
            val repository = MonthlyPlanRepository(dao) { YearMonth.of(2026, 10) }

            val future = repository.observeMonth(YearMonth.of(2026, 11)).first()
            assertTrue(future.items.isEmpty())
            assertEquals(null, dao.observeMonth("2026-11").first())

            val current = repository.observeMonth(YearMonth.of(2026, 10)).first()
            assertEquals("Pensja", current.items.single().title)
            assertEquals("2026-10", current.plan.month)
        } finally { db.close() }
    }

    @Test
    fun carryForwardCopiesLatestPlanOnceWithNewIdsAndAdjustedDates() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KakeboDatabase::class.java).build()
        try {
            val dao = db.monthlyPlanDao()
            val repository = MonthlyPlanRepository(dao) { YearMonth.of(2030, 1) }
            dao.savePlan(MonthlyPlanEntity("2028-01", 25_000, "Gotować w domu"))
            dao.saveItem(PlannedItemEntity(month = "2028-01", type = EntryType.INCOME, title = "Pensja",
                amountGrosz = 400_000, dateEpochDay = LocalDate.of(2028, 1, 10).toEpochDay()))
            dao.saveItem(PlannedItemEntity(month = "2028-01", type = EntryType.EXPENSE, title = "Czynsz",
                amountGrosz = 100_000, dateEpochDay = LocalDate.of(2028, 1, 31).toEpochDay()))
            val january = dao.observeMonth("2028-01").first()!!
            val copies = (1..3).map { async { repository.observeMonth(YearMonth.of(2028, 2)).first() } }.awaitAll()
            val february = copies.first()
            assertTrue(copies.all { it == february })
            assertEquals(january.plan.copy(month = "2028-02"), february.plan)
            assertEquals(2, february.items.size)
            assertTrue(february.items.none { copy -> january.items.any { it.id == copy.id } })
            assertEquals(LocalDate.of(2028, 2, 29).toEpochDay(), february.items.single { it.title == "Czynsz" }.dateEpochDay)
            assertEquals(LocalDate.of(2028, 2, 10).toEpochDay(), february.items.single { it.title == "Pensja" }.dateEpochDay)
            dao.saveItem(february.items.single { it.title == "Czynsz" }.copy(amountGrosz = 110_000))
            dao.savePlan(february.plan.copy(savingsGrosz = 30_000))
            val april = repository.observeMonth(YearMonth.of(2028, 4)).first()
            assertEquals(30_000L, april.plan.savingsGrosz)
            assertEquals(110_000L, april.items.single { it.title == "Czynsz" }.amountGrosz)
            assertEquals(january, dao.observeMonth("2028-01").first())
            val repeated = repository.observeMonth(YearMonth.of(2028, 2)).first()
            assertEquals(2, repeated.items.size)
            assertEquals(110_000L, repeated.items.single { it.title == "Czynsz" }.amountGrosz)
            val nextYear = repository.observeMonth(YearMonth.of(2029, 2)).first()
            assertEquals(LocalDate.of(2029, 2, 28).toEpochDay(), nextYear.items.single { it.title == "Czynsz" }.dateEpochDay)
            assertEquals(0, db.entryDao().observeCount().first())
        } finally { db.close() }
    }

    @Test
    fun carryForwardKeepsTheRecurrenceAnchor() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KakeboDatabase::class.java).build()
        try {
            val repository = MonthlyPlanRepository(db.monthlyPlanDao()) { YearMonth.of(2030, 1) }
            db.monthlyPlanDao().saveItem(PlannedItemEntity(
                month = "2028-01", type = EntryType.EXPENSE, title = "Ubezpieczenie", amountGrosz = 120_000,
                dateEpochDay = LocalDate.of(2028, 1, 15).toEpochDay(), recurrence = PlanRecurrence.QUARTERLY,
                recurrenceAnchorMonth = "2028-01",
            ))

            val februaryItem = repository.observeMonth(YearMonth.of(2028, 2)).first().items.single()
            assertEquals("2028-01", februaryItem.recurrenceAnchorMonth)
            assertTrue(!februaryItem.isDueIn(YearMonth.of(2028, 2)))
            val aprilItem = repository.observeMonth(YearMonth.of(2028, 4)).first().items.single()
            assertTrue(aprilItem.isDueIn(YearMonth.of(2028, 4)))
        } finally { db.close() }
    }

    @Test
    fun carryForwardDoesNotOverwriteExistingOrIntentionallyEmptiedPlansOrCopyBackwards() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KakeboDatabase::class.java).build()
        try {
            val dao = db.monthlyPlanDao()
            val repository = MonthlyPlanRepository(dao) { YearMonth.of(2030, 1) }
            dao.savePlan(MonthlyPlanEntity("2027-01", 50_000, "Własny plan"))
            dao.saveItem(PlannedItemEntity(month = "2026-12", type = EntryType.EXPENSE, title = "Czynsz",
                amountGrosz = 100_000, dateEpochDay = LocalDate.of(2026, 12, 31).toEpochDay()))
            val january = repository.observeMonth(YearMonth.of(2027, 1)).first()
            assertEquals(50_000L, january.plan.savingsGrosz)
            assertTrue(january.items.isEmpty())
            val november = repository.observeMonth(YearMonth.of(2026, 11)).first()
            assertTrue(november.items.isEmpty())
            val february = repository.observeMonth(YearMonth.of(2027, 2)).first()
            assertTrue(february.items.isEmpty())
            dao.saveItem(PlannedItemEntity(month = "2027-02", type = EntryType.EXPENSE, title = "Usunięty",
                amountGrosz = 100, dateEpochDay = LocalDate.of(2027, 2, 1).toEpochDay()))
            dao.deleteItem(dao.observeMonth("2027-02").first()!!.items.single().id, "2027-02")
            assertTrue(repository.observeMonth(YearMonth.of(2027, 2)).first().items.isEmpty())
        } finally { db.close() }
    }

    @Test
    fun migrationPreservesExistingLedger() {
        val name = "migration-test.db"
        migration.createDatabase(name, 1).apply {
            execSQL("INSERT INTO entries VALUES (1, 12345, 'EXPENSE', 20724, 'Zakupy')")
            close()
        }
        migration.runMigrationsAndValidate(name, 2, true, KakeboDatabase.MIGRATION_1_2).use { db ->
            db.query("SELECT amountGrosz, note FROM entries WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(12345L, cursor.getLong(0))
                assertEquals("Zakupy", cursor.getString(1))
            }
        }
    }

    @Test
    fun plansPersistStaySeparateAndDoNotCreateActualTransactions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "monthly-plan-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, KakeboDatabase::class.java, name).build()
        var database = open()
        try {
            val dao = database.monthlyPlanDao()
            dao.savePlan(MonthlyPlanEntity("2026-09", 20_000, "Lista zakupów"))
            dao.saveItem(PlannedItemEntity(month = "2026-09", type = EntryType.INCOME, title = "Pensja",
                amountGrosz = 300_000, dateEpochDay = LocalDate.of(2026, 9, 10).toEpochDay()))
            dao.saveItem(PlannedItemEntity(month = "2026-10", type = EntryType.EXPENSE, title = "Czynsz",
                amountGrosz = 100_000, dateEpochDay = LocalDate.of(2026, 10, 1).toEpochDay()))
            val september = dao.observeMonth("2026-09").first()!!
            assertEquals(20_000L, september.plan.savingsGrosz)
            dao.saveItem(september.items.single().copy(amountGrosz = 400_000))
            dao.savePlan(september.plan.copy(savingsGrosz = 30_000))
            database.close()
            database = open()
            val restored = database.monthlyPlanDao().observeMonth("2026-09").first()!!
            assertEquals(400_000L, restored.items.single().amountGrosz)
            assertEquals(30_000L, restored.plan.savingsGrosz)
            assertEquals("Lista zakupów", restored.plan.resolutions)
            assertEquals(1, database.monthlyPlanDao().observeMonth("2026-10").first()!!.items.size)
            assertEquals(0, database.entryDao().observeCount().first())
            database.monthlyPlanDao().deleteItem(restored.items.single().id, "2026-09")
            assertTrue(database.monthlyPlanDao().observeMonth("2026-09").first()!!.items.isEmpty())
            assertEquals(1, database.monthlyPlanDao().observeMonth("2026-10").first()!!.items.size)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
