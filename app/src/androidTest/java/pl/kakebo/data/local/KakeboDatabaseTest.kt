package pl.kakebo.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KakeboDatabaseTest {
    @Test
    fun entriesSurviveClosingAndReopeningDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "persistence-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, KakeboDatabase::class.java, name).build()
        var database = open()
        try {
            val entry = EntryEntity(
                amountGrosz = 12345,
                type = EntryType.EXPENSE,
                dateEpochDay = LocalDate.of(2026, 9, 28).toEpochDay(),
                note = "Zakupy spożywcze",
            )
            val id = database.entryDao().insert(entry)
            database.close()
            database = open()
            assertEquals(listOf(entry.copy(id = id)), database.entryDao().observeAll().first())
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
