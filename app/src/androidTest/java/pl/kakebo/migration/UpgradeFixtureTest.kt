package pl.kakebo.migration

import android.graphics.Bitmap
import android.app.LocaleManager
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.LocaleList
import android.os.Process
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pl.kakebo.data.local.KakeboDatabase
import java.io.File

/** Runs only in pl.kakebo.upgradetest, never in a private user install. */
@RunWith(AndroidJUnit4::class)
class UpgradeFixtureTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), KakeboDatabase::class.java,
    )

    @Test fun seedV11WithSettingsAndReceipt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "pl.kakebo.upgradetest")
        helper.createDatabase("kakebo.db", 11).use { db ->
            db.execSQL("INSERT INTO monthly_plans VALUES ('2026-09', 12345, 'Oszczędzać')")
            db.execSQL("INSERT INTO custom_categories VALUES (41, 'Dom', 'dom', 'HOME')")
            db.execSQL("INSERT INTO planned_items VALUES (12, '2026-09', 'EXPENSE', 'Czynsz', 125000, 20724, 'MONTHLY', '2026-09', 'HOME')")
            db.execSQL("INSERT INTO custom_subcategories VALUES (42, 41, 'Kuchnia', 'kuchnia')")
            db.execSQL("INSERT INTO entries VALUES (101, 2000, 'EXPENSE', 20724, 'Sklep', NULL, NULL, '00000000-0000-4000-8000-000000000001.img', 42)")
            db.execSQL("INSERT INTO entries VALUES (102, 125000, 'EXPENSE', 20725, 'Czynsz', NULL, 12, NULL, NULL)")
            db.execSQL("INSERT INTO monthly_reflections VALUES ('2026-09', 'Test', 'Lepiej planować', 'Dalej')")
        }
        val receipt = File(context.filesDir, "receipts/00000000-0000-4000-8000-000000000001.img")
        val extraReceipt = File(context.filesDir, "receipts/00000000-0000-4000-8000-000000000002.img")
        receipt.parentFile!!.mkdirs()
        receipt.outputStream().use { Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        extraReceipt.outputStream().use { Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        context.getSharedPreferences("profile_preferences", 0).edit()
            .putString("book_name", "Księga testowa")
            .putInt("start_tab", 2)
            .putBoolean("reminder_enabled", true)
            .putInt("reminder_hour", 7)
            .putInt("reminder_minute", 15)
            .putBoolean("app_lock", true).commit()
        context.getSharedPreferences("currency_preferences", 0).edit().putString("currency_code", "EUR").commit()
        context.getSharedPreferences("appearance_preferences", 0).edit().putString("theme_mode", "DARK").commit()
        context.getSharedPreferences("splash_preferences", 0).edit().putString("splash_style", "SUN").commit()
        context.getSharedPreferences("language_preferences", 0).edit().putString("language_tag", "en").commit()
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags("en")
        }
        assertTrue(receipt.isFile)
        assertTrue(extraReceipt.isFile)
    }

    @Test fun seedV5WithUncheckpointedWal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "pl.kakebo.upgradetest")
        helper.createDatabase("kakebo.db", 5).use { db ->
            db.execSQL("INSERT INTO monthly_plans VALUES ('2026-08', 5000, '')")
        }
        val path = context.getDatabasePath("kakebo.db")
        val db = SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READWRITE)
        db.enableWriteAheadLogging()
        db.execSQL("INSERT INTO entries VALUES (501, 2450, 'EXPENSE', 20666, 'WAL-only', NULL, NULL)")
        assertTrue(File("${path.path}-wal").length() > 0)
        // Simulate process termination before SQLite can checkpoint/close WAL.
        Process.killProcess(Process.myPid())
    }
}
