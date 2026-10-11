package pl.kakebo.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlanRecurrenceMigrationTest {
    @get:Rule
    val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KakeboDatabase::class.java)

    @Test
    fun existingItemsRemainMonthlyAndKeepTheirOriginalMonthAsAnchor() {
        val name = "plan-recurrence-migration-test.db"
        migration.createDatabase(name, 9).use { db ->
            db.execSQL("INSERT INTO monthly_plans VALUES ('2026-09', 0, '')")
            db.execSQL("INSERT INTO planned_items VALUES (1, '2026-09', 'EXPENSE', 'Czynsz', 100000, 20724)")
        }

        migration.runMigrationsAndValidate(name, 10, true, KakeboDatabase.MIGRATION_9_10).use { db ->
            db.query("SELECT recurrence, recurrenceAnchorMonth FROM planned_items WHERE id = 1").use { row ->
                row.moveToFirst()
                assertEquals("MONTHLY", row.getString(0))
                assertEquals("2026-09", row.getString(1))
            }
        }
    }
}
