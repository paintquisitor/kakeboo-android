package pl.kakebo.ui.monthly

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import pl.kakebo.data.local.*
import pl.kakebo.data.repository.*
import pl.kakebo.domain.ExpenseSubcategory
import pl.kakebo.domain.formatMoney
import pl.kakebo.ui.theme.KakeboTheme

@RunWith(AndroidJUnit4::class)
class MonthlyScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: KakeboDatabase
    private lateinit var viewModel: MonthlyViewModel
    private val store = ViewModelStore()
    private val month = YearMonth.of(2026, 9)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), KakeboDatabase::class.java).build()
        runBlocking {
            db.monthlyPlanDao().savePlan(MonthlyPlanEntity(month.toString(), 50000, "Lista zakupów"))
            db.monthlyPlanDao().saveItem(PlannedItemEntity(1, month.toString(), EntryType.INCOME, "Pensja", 300000, month.atDay(1).toEpochDay()))
            db.monthlyPlanDao().saveItem(PlannedItemEntity(2, month.toString(), EntryType.EXPENSE, "Czynsz", 100000, month.atDay(1).toEpochDay()))
            db.entryDao().insert(EntryEntity(amountGrosz = 2500, type = EntryType.EXPENSE, dateEpochDay = month.atDay(8).toEpochDay(), subcategory = ExpenseSubcategory.BOOKS))
        }
        compose.runOnUiThread {
            viewModel = MonthlyViewModel(EntryRepository(db.entryDao()), MonthlyPlanRepository(db.monthlyPlanDao()),
                MonthlyReflectionRepository(db.monthlyReflectionDao()), SavedStateHandle(mapOf("month" to month.toString())))
            store.put("monthly", viewModel)
        }
        compose.setContent { KakeboTheme { MonthlyScreen(viewModel) } }
        compose.waitUntil(5000) { !viewModel.state.value.loading }
    }
    @After fun tearDown() { compose.runOnUiThread { store.clear() }; db.close() }

    @Test fun reportUpdatesAndReflectionsCanBeSavedEditedAndCancelledPerMonth() {
        compose.onNodeWithTag("monthly_left").assertTextEquals(formatMoney(197500))
        scrollTo("savings_result")
        compose.onNodeWithTag("savings_result").assertTextEquals("Według wpisów: cel osiągnięty.")
        runBlocking {
            db.entryDao().insert(EntryEntity(amountGrosz = 180000, type = EntryType.EXPENSE,
                dateEpochDay = month.atDay(9).toEpochDay(), subcategory = ExpenseSubcategory.FOOD))
        }
        compose.waitUntil(5000) { viewModel.state.value.report?.leftFromIncome == 17500L }
        compose.onNodeWithTag("savings_result").assertTextEquals("Według wpisów: cel osiągnięty częściowo.")
        edit()
        compose.onNodeWithTag("reflection_successes").performTextInput("Lista zakupów pomaga")
        compose.onNodeWithTag("reflection_improvements").performScrollTo().performTextInput("Mniej wyjść")
        compose.onNodeWithTag("reflection_next_month").performScrollTo().performTextInput("Gotować w domu")
        compose.onNodeWithTag("save_reflection").performClick()
        compose.waitUntil(5000) { viewModel.state.value.reflection?.successes == "Lista zakupów pomaga" && !viewModel.saving.value }
        Assert.assertEquals("Gotować w domu", runBlocking { db.monthlyReflectionDao().observeMonth(month.toString()).first()!!.nextMonth })
        changeMonth(1)
        edit()
        compose.onNodeWithTag("reflection_successes").assertTextEquals("Co się udało?", "")
        compose.onNodeWithText("Anuluj").performClick()
        changeMonth(-1)
        edit()
        compose.onNodeWithTag("reflection_successes").performTextReplacement("Niezapisane")
        compose.onNodeWithText("Anuluj").performClick()
        Assert.assertEquals("Lista zakupów pomaga", viewModel.state.value.reflection!!.successes)
        edit()
        compose.onNodeWithTag("reflection_successes").performTextReplacement("Poprawione")
        compose.onNodeWithTag("save_reflection").performClick()
        compose.waitUntil(5000) { viewModel.state.value.reflection?.successes == "Poprawione" && !viewModel.saving.value }
    }

    @Test fun monthlyTransactionsExpandCollapseAndResetWhenMonthChanges() {
        runBlocking {
            db.entryDao().insert(EntryEntity(amountGrosz = 1000, type = EntryType.EXPENSE,
                dateEpochDay = month.atDay(10).toEpochDay(), subcategory = ExpenseSubcategory.FOOD,
                note = "Zakupy testowe bilansu"))
        }
        compose.waitUntil(5000) { viewModel.state.value.transactions.size == 2 }
        scrollTo("monthly_transactions_toggle")
        compose.onNodeWithText("Zakupy testowe bilansu").assertDoesNotExist()
        compose.onNodeWithTag("monthly_transactions_toggle").performClick()
        compose.onNodeWithTag("monthly_list").performScrollToNode(hasText("Zakupy testowe bilansu"))
        compose.onNodeWithText("Zakupy testowe bilansu").assertIsDisplayed()
        scrollTo("monthly_transactions_toggle")
        compose.onNodeWithTag("monthly_transactions_toggle").performClick()
        compose.onNodeWithText("Zakupy testowe bilansu").assertDoesNotExist()
        compose.onNodeWithTag("monthly_transactions_toggle").performClick()
        changeMonth(1)
        changeMonth(-1)
        scrollTo("monthly_transactions_toggle")
        compose.onNodeWithText("Zakupy testowe bilansu").assertDoesNotExist()
    }

    @Test fun breakdownOpensMonthlyListsWithReconciledTotals() {
        runBlocking {
            db.entryDao().insert(EntryEntity(amountGrosz = 110000, type = EntryType.EXPENSE,
                dateEpochDay = month.atDay(2).toEpochDay(), note = "Przelew Czynsz"))
            db.entryDao().insert(EntryEntity(amountGrosz = 7700, type = EntryType.EXPENSE,
                dateEpochDay = month.plusMonths(1).atDay(3).toEpochDay(), note = "Zakup w październiku",
                subcategory = ExpenseSubcategory.FOOD))
        }
        compose.waitUntil(5000) { viewModel.state.value.report?.balance?.fixedExpenses == 110000L }
        scrollTo("monthly_open_fixed")
        compose.onNodeWithTag("monthly_open_fixed").performClick()
        compose.onNodeWithText("Czynsz").assertIsDisplayed()
        compose.onNodeWithText("Pensja").assertDoesNotExist()
        compose.onNodeWithTag("monthly_details_total").assertTextEquals("Razem: ${formatMoney(110000)}")
        compose.onNodeWithTag("monthly_details_back").performClick()
        scrollTo("monthly_open_diary")
        compose.onNodeWithTag("monthly_open_diary").performClick()
        compose.onNodeWithTag("monthly_details_total").assertTextEquals("Razem: ${formatMoney(2500)}")
        val books = ApplicationProvider.getApplicationContext<android.content.Context>().getString(ExpenseSubcategory.BOOKS.label)
        compose.onNodeWithText(books).assertIsDisplayed()
        compose.onNodeWithText("Przelew Czynsz").assertDoesNotExist()
        compose.onNodeWithText("Zakup w październiku").assertDoesNotExist()
        changeMonth(1)
        compose.onNodeWithTag("monthly_expense_details").assertDoesNotExist()
        scrollTo("monthly_open_diary")
        compose.onNodeWithTag("monthly_open_diary").performClick()
        compose.onNodeWithText("Zakup w październiku").assertIsDisplayed()
        compose.onNodeWithTag("monthly_details_total").assertTextEquals("Razem: ${formatMoney(7700)}")
        compose.onNodeWithTag("monthly_details_back").performClick()
        changeMonth(-1)
        changeMonth(-1)
        scrollTo("monthly_open_diary")
        compose.onNodeWithTag("monthly_open_diary").performClick()
        compose.onNodeWithText("Brak zapisanych transakcji w tym miesiącu.").assertIsDisplayed()
        compose.onNodeWithTag("monthly_details_total").assertTextEquals("Razem: ${formatMoney(0)}")
        compose.onNodeWithTag("monthly_details_back").performClick()
        scrollTo("monthly_open_fixed")
        compose.onNodeWithTag("monthly_open_fixed").performClick()
        compose.onNodeWithText("Brak stałych wydatków w tym miesiącu.").assertIsDisplayed()
    }

    @Test fun weeklySummaryExpandsAndCollapses() {
        scrollTo("monthly_weeks_toggle")
        compose.onNodeWithTag("monthly_weeks_details").assertDoesNotExist()
        compose.onNodeWithTag("monthly_weeks_toggle").performClick()
        compose.onNodeWithTag("monthly_list").performScrollToNode(hasText("2. tydzień"))
        compose.onNodeWithText("2. tydzień").assertIsDisplayed()
        compose.onAllNodes(hasText(formatMoney(2500)) and hasAnyAncestor(hasTestTag("monthly_weeks_details")))
            .assertCountEquals(2)
        scrollTo("monthly_weeks_toggle")
        compose.onNodeWithTag("monthly_weeks_toggle").performClick()
        compose.onNodeWithTag("monthly_weeks_details").assertDoesNotExist()
    }

    @Test fun annualMonthsExpandAndOpenMonthlyBalance() {
        compose.onNodeWithText("Rok", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("annual_left").assertTextEquals(formatMoney(197500))
        compose.onNodeWithTag("annual_list").performScrollToNode(hasTestTag("annual_months_toggle"))
        compose.onNodeWithTag("annual_months_toggle").performClick()
        val january = YearMonth.of(month.year, 1)
        compose.onNodeWithTag("annual_list").performScrollToNode(hasTestTag("annual_month_$january"))
        compose.onNodeWithTag("annual_month_$january").assertIsDisplayed()
        compose.onNodeWithTag("annual_list").performScrollToNode(hasTestTag("annual_month_$month"))
        compose.onNodeWithTag("annual_month_$month").performClick()
        compose.onNodeWithTag("monthly_left").assertTextEquals(formatMoney(197500))
        scrollTo("monthly_transactions_toggle")
        compose.onNodeWithTag("monthly_transactions_toggle").performClick()
        val title = ApplicationProvider.getApplicationContext<android.content.Context>().getString(ExpenseSubcategory.BOOKS.label)
        compose.onNodeWithTag("monthly_list").performScrollToNode(hasText(title))
        compose.onNodeWithText(title).assertIsDisplayed()
    }

    @Test fun annualCategoriesExpandAndFutureYearRemainsZero() {
        compose.onNodeWithText("Rok", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("annual_list").performScrollToNode(hasTestTag("annual_categories"))
        compose.onNodeWithTag("annual_categories").performClick()
        compose.onNodeWithTag("annual_list").performScrollToNode(hasText("Wydatki według kategorii"))
        compose.onNodeWithText("Wydatki według kategorii").assertIsDisplayed()
        // A saved plan must not create a fictitious balance in a future year.
        val future = YearMonth.of(YearMonth.now().year + 1, month.monthValue)
        runBlocking {
            db.monthlyPlanDao().savePlan(MonthlyPlanEntity(future.toString(), 50000, ""))
            db.monthlyPlanDao().saveItem(PlannedItemEntity(3, future.toString(), EntryType.INCOME,
                "Przyszła pensja", 900000, future.atDay(1).toEpochDay()))
        }
        compose.runOnUiThread { viewModel.selectMonth(future) }
        compose.waitUntil(5000) { viewModel.state.value.month == future && !viewModel.state.value.loading }
        compose.onNodeWithTag("annual_list").performScrollToIndex(0)
        compose.onNodeWithTag("annual_left").assertTextEquals(formatMoney(0))
        compose.onNodeWithTag("annual_list").performScrollToNode(hasTestTag("annual_bar_$future"))
        val name = future.format(java.time.format.DateTimeFormatter.ofPattern("LLLL", java.util.Locale.forLanguageTag("pl")))
        compose.onNodeWithTag("annual_bar_$future").assertContentDescriptionEquals("$name: ${formatMoney(0)}")
    }

    private fun scrollTo(tag: String) { compose.onNodeWithTag("monthly_list").performScrollToNode(hasTestTag(tag)) }
    private fun edit() { scrollTo("edit_reflection"); compose.onNodeWithTag("edit_reflection").performClick() }
    private fun changeMonth(offset: Long) {
        val expected = viewModel.state.value.month.plusMonths(offset)
        compose.onNodeWithContentDescription(if (offset > 0) "Następny miesiąc" else "Poprzedni miesiąc").performClick()
        compose.waitUntil(5000) { viewModel.state.value.month == expected && !viewModel.state.value.loading }
    }
}
