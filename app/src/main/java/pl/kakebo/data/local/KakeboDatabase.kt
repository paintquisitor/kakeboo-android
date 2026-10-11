package pl.kakebo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [EntryEntity::class, MonthlyPlanEntity::class, PlannedItemEntity::class, MonthlyReflectionEntity::class, CustomCategoryEntity::class, CustomSubcategoryEntity::class],
    version = 11,
    exportSchema = true,
)
abstract class KakeboDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun entryDao(): EntryDao
    abstract fun monthlyPlanDao(): MonthlyPlanDao
    abstract fun monthlyReflectionDao(): MonthlyReflectionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS monthly_plans (
                        month TEXT NOT NULL PRIMARY KEY,
                        savingsGrosz INTEGER NOT NULL,
                        resolutions TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS planned_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        month TEXT NOT NULL,
                        type TEXT NOT NULL,
                        title TEXT NOT NULL,
                        amountGrosz INTEGER NOT NULL,
                        dateEpochDay INTEGER NOT NULL,
                        FOREIGN KEY(month) REFERENCES monthly_plans(month) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_planned_items_month ON planned_items(month)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN subcategory TEXT")
                db.execSQL("CREATE INDEX index_entries_type_dateEpochDay ON entries(type, dateEpochDay)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQLite wymaga przebudowania tabeli, aby dodać klucz obcy.
                db.execSQL("""
                    CREATE TABLE entries_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        amountGrosz INTEGER NOT NULL, type TEXT NOT NULL,
                        dateEpochDay INTEGER NOT NULL, note TEXT NOT NULL,
                        subcategory TEXT, plannedItemId INTEGER,
                        FOREIGN KEY(plannedItemId) REFERENCES planned_items(id) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                """.trimIndent())
                db.execSQL("INSERT INTO entries_new (id, amountGrosz, type, dateEpochDay, note, subcategory) SELECT id, amountGrosz, type, dateEpochDay, note, subcategory FROM entries")
                db.execSQL("DROP TABLE entries")
                db.execSQL("ALTER TABLE entries_new RENAME TO entries")
                db.execSQL("CREATE INDEX index_entries_type_dateEpochDay ON entries(type, dateEpochDay)")
                db.execSQL("CREATE INDEX index_entries_plannedItemId ON entries(plannedItemId)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS monthly_reflections (
                        month TEXT NOT NULL PRIMARY KEY,
                        successes TEXT NOT NULL,
                        improvements TEXT NOT NULL,
                        nextMonth TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN receiptName TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE custom_categories (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, nameKey TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX index_custom_categories_nameKey ON custom_categories(nameKey)")
                db.execSQL("""CREATE TABLE custom_subcategories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, categoryId INTEGER NOT NULL,
                    name TEXT NOT NULL, nameKey TEXT NOT NULL,
                    FOREIGN KEY(categoryId) REFERENCES custom_categories(id) ON UPDATE NO ACTION ON DELETE RESTRICT)
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX index_custom_subcategories_categoryId_nameKey ON custom_subcategories(categoryId, nameKey)")
                db.execSQL("ALTER TABLE entries ADD COLUMN customSubcategoryId INTEGER REFERENCES custom_subcategories(id) ON UPDATE NO ACTION ON DELETE RESTRICT")
                db.execSQL("CREATE INDEX index_entries_customSubcategoryId ON entries(customSubcategoryId)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE custom_categories ADD COLUMN icon TEXT NOT NULL DEFAULT 'WALLET'")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Consolidate clear equivalents. Unmatched historical entries become uncategorized;
                // amounts, receipts, plan links and custom categories stay intact.
                db.execSQL("""
                    UPDATE entries SET subcategory = CASE subcategory
                        WHEN 'SUPERMARKET' THEN 'FOOD'
                        WHEN 'FOOD_SHOPS' THEN 'FOOD'
                        WHEN 'PHARMACY' THEN 'HEALTH_MEDICINE'
                        WHEN 'HEALTH' THEN 'HEALTH_MEDICINE'
                        WHEN 'CHILDCARE' THEN 'CHILDREN'
                        WHEN 'TAXIS' THEN 'TRANSPORT'
                        WHEN 'NEWSPAPERS' THEN 'BOOKS'
                        WHEN 'MUSIC' THEN 'MEDIA'
                        WHEN 'SHOWS' THEN 'PERFORMANCES'
                        WHEN 'HOBBY_OTHER' THEN 'HOBBY_ACTIVITIES'
                        WHEN 'RESTAURANTS' THEN 'DINING'
                        WHEN 'TAKEAWAY' THEN 'DINING'
                        WHEN 'PUBS' THEN 'DINING'
                        WHEN 'CAFES' THEN 'DINING'
                        WHEN 'PHONE_TOPUP' THEN NULL
                        WHEN 'NEEDS_OTHER' THEN NULL
                        WHEN 'GIFTS' THEN NULL
                        WHEN 'OPTIONAL_OTHER' THEN NULL
                        WHEN 'FILMS' THEN NULL
                        WHEN 'PARTIES' THEN NULL
                        WHEN 'OUTINGS_OTHER' THEN NULL
                        WHEN 'ELECTRONICS' THEN NULL
                        WHEN 'HOME_EQUIPMENT' THEN NULL
                        ELSE subcategory END
                    WHERE subcategory IS NOT NULL
                """.trimIndent())
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE planned_items ADD COLUMN recurrence TEXT NOT NULL DEFAULT 'MONTHLY'")
                db.execSQL("ALTER TABLE planned_items ADD COLUMN recurrenceAnchorMonth TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE planned_items SET recurrenceAnchorMonth = month")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE planned_items ADD COLUMN icon TEXT NOT NULL DEFAULT ''")
            }
        }

        fun open(context: Context): KakeboDatabase =
            Room.databaseBuilder(context.applicationContext, KakeboDatabase::class.java, "kakebo.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                .build()
    }
}
