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

@RunWith(AndroidJUnit4::class)
class MonthlyReflectionDatabaseTest {
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KakeboDatabase::class.java)

    @Test fun migrationPreservesPlanDiaryAndLegacyLinks() {
        val name = "reflection-migration-test.db"
        migration.createDatabase(name, 4).apply {
            execSQL("INSERT INTO monthly_plans VALUES ('2026-09', 50000, 'Lista zakupów')")
            execSQL("INSERT INTO planned_items (id, month, type, title, amountGrosz, dateEpochDay) VALUES (10, '2026-09', 'EXPENSE', 'Czynsz', 100000, 20724)")
            execSQL("INSERT INTO entries VALUES (42, 12345, 'EXPENSE', 20724, 'Rachunek', 'NEEDS_OTHER', 10)")
            close()
        }
        migration.runMigrationsAndValidate(name, 5, true, KakeboDatabase.MIGRATION_4_5).use { db ->
            db.query("SELECT amountGrosz, note, subcategory, plannedItemId FROM entries WHERE id = 42").use {
                assertTrue(it.moveToFirst()); assertEquals(12345L, it.getLong(0))
                assertEquals("Rachunek", it.getString(1)); assertEquals("NEEDS_OTHER", it.getString(2)); assertEquals(10L, it.getLong(3))
            }
            db.query("SELECT savingsGrosz, resolutions FROM monthly_plans WHERE month = '2026-09'").use {
                assertTrue(it.moveToFirst()); assertEquals(50000L, it.getLong(0)); assertEquals("Lista zakupów", it.getString(1))
            }
            db.query("SELECT title, amountGrosz FROM planned_items WHERE id = 10").use {
                assertTrue(it.moveToFirst()); assertEquals("Czynsz", it.getString(0)); assertEquals(100000L, it.getLong(1))
            }
            db.query("SELECT COUNT(*) FROM monthly_reflections").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test fun reflectionsPersistPerMonthAndPlanEditsDoNotOverwriteThem() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "reflection-persistence-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, KakeboDatabase::class.java, name).build()
        var db = open()
        try {
            val september = MonthlyReflectionEntity("2026-09", "Mniej zakupów", "Mniej taksówek", "Gotować w domu")
            val october = MonthlyReflectionEntity("2026-10", "Cel osiągnięty")
            db.monthlyReflectionDao().save(september)
            db.monthlyReflectionDao().save(october)
            db.monthlyPlanDao().savePlan(MonthlyPlanEntity("2026-09", 30000, "Nowy plan"))
            db.close()
            db = open()
            assertEquals(september, db.monthlyReflectionDao().observeMonth("2026-09").first())
            assertEquals(october, db.monthlyReflectionDao().observeMonth("2026-10").first())
            db.monthlyReflectionDao().save(september.copy(successes = "Poprawione"))
            assertEquals("Poprawione", db.monthlyReflectionDao().observeMonth("2026-09").first()!!.successes)
            assertEquals(october, db.monthlyReflectionDao().observeMonth("2026-10").first())
            assertEquals(30000L, db.monthlyPlanDao().observeMonth("2026-09").first()!!.plan.savingsGrosz)
            assertNull(db.monthlyReflectionDao().observeMonth("2026-11").first())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
