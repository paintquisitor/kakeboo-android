package pl.kakebo.ui.weekly

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import pl.kakebo.data.local.*
import pl.kakebo.data.repository.*
import pl.kakebo.domain.ExpenseSubcategory
import pl.kakebo.domain.formatMoney
import pl.kakebo.ui.theme.KakeboTheme

@RunWith(AndroidJUnit4::class)
class WeeklyScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: KakeboDatabase
    private lateinit var viewModel: WeeklyViewModel
    private val store = ViewModelStore()
    private val month = YearMonth.of(2026, 9)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), KakeboDatabase::class.java).build()
        runBlocking {
            db.monthlyPlanDao().saveItem(PlannedItemEntity(1, month.toString(), EntryType.INCOME, "Pensja", 300000, month.atDay(1).toEpochDay()))
            db.monthlyPlanDao().saveItem(PlannedItemEntity(2, month.toString(), EntryType.EXPENSE, "Czynsz", 100000, month.atDay(1).toEpochDay()))
            db.entryDao().insert(EntryEntity(amountGrosz = 40000, type = EntryType.EXPENSE, dateEpochDay = month.atDay(2).toEpochDay(),
                subcategory = ExpenseSubcategory.FOOD))
            db.entryDao().insert(EntryEntity(amountGrosz = 2500, type = EntryType.EXPENSE, dateEpochDay = month.atDay(8).toEpochDay(),
                subcategory = ExpenseSubcategory.BOOKS))
        }
        compose.runOnUiThread {
            viewModel = WeeklyViewModel(EntryRepository(db.entryDao()), MonthlyPlanRepository(db.monthlyPlanDao(), currentMonth = { month.plusMonths(1) }), SavedStateHandle(mapOf("month" to month.toString())))
            store.put("weekly", viewModel)
        }
        compose.setContent { KakeboTheme { WeeklyScreen(viewModel) } }
        compose.waitUntil(5000) { !viewModel.state.value.loading }
    }
    @After fun tearDown() { compose.runOnUiThread { store.clear() }; db.close() }

    @Test fun userCanCompareWeeksAndBalanceSubtractsFixedAndDiaryExpenses() {
        selectWeek(0)
        total(40000)
        compose.onNodeWithTag("weekly_list").performScrollToNode(hasTestTag("remaining_month"))
        compose.onNodeWithTag("remaining_month").assertTextEquals(formatMoney(160000))
        selectWeek(1)
        total(2500)
        compose.onNodeWithTag("weekly_list").performScrollToNode(hasTestTag("remaining_month"))
        compose.onNodeWithTag("remaining_month").assertTextEquals(formatMoney(157500))
        compose.onNodeWithTag("weekly_list").performScrollToNode(hasText("Najwięcej: Kultura i czas wolny (${formatMoney(2500)})"))
        compose.onNodeWithText("Najwięcej: Kultura i czas wolny (${formatMoney(2500)})").assertExists()
        runBlocking {
            db.entryDao().insert(EntryEntity(amountGrosz = 70000, type = EntryType.EXPENSE,
                dateEpochDay = month.atDay(9).toEpochDay(), subcategory = ExpenseSubcategory.FOOD))
        }
        compose.waitUntil(5000) { viewModel.state.value.balance?.remaining == 87500L }
        total(72500)
        compose.onNodeWithTag("weekly_list").performScrollToNode(hasTestTag("remaining_month"))
        compose.onNodeWithTag("remaining_month").assertTextEquals(formatMoney(87500))
        compose.onNodeWithContentDescription("Następny miesiąc").performClick()
        compose.waitUntil(5000) { viewModel.state.value.month == month.plusMonths(1) && !viewModel.state.value.loading }
        compose.onNodeWithTag("remaining_month").assertTextEquals(formatMoney(200000))
        total(0)
    }

    private fun selectWeek(index: Int) {
        compose.onNodeWithTag("weekly_list").performScrollToNode(hasTestTag("week_$index"))
        compose.onNodeWithTag("week_$index").performClick()
    }
    private fun total(value: Long) {
        compose.onNodeWithTag("weekly_list").performScrollToNode(hasTestTag("week_total"))
        compose.onNodeWithTag("week_total").assertTextEquals(formatMoney(value))
    }
}
