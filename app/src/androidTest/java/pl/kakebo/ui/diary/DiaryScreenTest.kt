package pl.kakebo.ui.diary

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import pl.kakebo.data.local.PlannedItemEntity
import pl.kakebo.data.local.EntryType
import pl.kakebo.data.local.EntryEntity
import org.junit.*
import org.junit.runner.RunWith
import pl.kakebo.MoneyCurrency
import pl.kakebo.data.local.KakeboDatabase
import pl.kakebo.data.repository.CategoryRepository
import pl.kakebo.data.repository.EntryRepository
import pl.kakebo.domain.CategoryIcon
import pl.kakebo.domain.ExpenseSubcategory
import pl.kakebo.domain.formatMoney
import pl.kakebo.ui.theme.KakeboTheme

@RunWith(AndroidJUnit4::class)
class DiaryScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: KakeboDatabase
    private lateinit var viewModel: DiaryViewModel
    private val store = ViewModelStore()

    @Before fun setUp() {
        MoneyCurrency.select("PLN")
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), KakeboDatabase::class.java).build()
        compose.runOnUiThread {
            viewModel = DiaryViewModel(EntryRepository(database.entryDao()), SavedStateHandle(mapOf("month" to "2026-09", "selectedDate" to "2026-09-28")), CategoryRepository(database.categoryDao()))
            store.put("diary", viewModel)
        }
        compose.setContent { KakeboTheme { DiaryScreen(viewModel) } }
        compose.waitUntil(5000) { !viewModel.state.value.loading }
    }

    @After fun tearDown() {
        compose.runOnUiThread { store.clear() }
        database.close()
    }

    @Test fun addChangeCategoryMoveToAnotherMonthAndDelete() {
        compose.onNodeWithText("Dodaj wydatek").performClick()
        compose.onNodeWithText("Kwota (PLN)").performTextReplacement("39,99")
        compose.onNodeWithText("Data (dd.MM.rrrr)").performTextReplacement("28.09.2026")
        choose("category_picker", "Kultura i czas wolny")
        choose("subcategory_picker", "Gry")
        compose.onNodeWithText("Opis (opcjonalny)").performScrollTo().performTextReplacement("Nowa gra")
        compose.onNodeWithText("Zapisz").performClick()
        waitForTotal(3999)
        scrollTo("Nowa gra")
        compose.onNodeWithText("Nowa gra").performClick()
        choose("category_picker", "Opcjonalne")
        compose.onNodeWithText("Wybierz podkategorię").assertExists()
        choose("subcategory_picker", "Jedzenie na mieście i dostawy")
        compose.onNodeWithText("Kwota (PLN)").performScrollTo().performTextReplacement("50,10")
        compose.onNodeWithText("Data (dd.MM.rrrr)").performScrollTo().performTextReplacement("01.10.2026")
        compose.onNodeWithText("Zapisz").performClick()
        waitForTotal(0)
        compose.onNodeWithContentDescription("Następny miesiąc").performClick()
        compose.waitUntil(5000) { viewModel.state.value.month == YearMonth.of(2026, 10) && !viewModel.state.value.loading }
        waitForTotal(5010)
        selectDay("2026-10-01")
        compose.runOnIdle { Assert.assertEquals(ExpenseSubcategory.DINING, viewModel.state.value.entries.single().subcategory) }
        scrollTo("Nowa gra")
        compose.onNodeWithText("Nowa gra").performClick()
        compose.onNodeWithText("Usuń pozycję").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_editor_expense_delete").performClick()
        waitForTotal(0)
    }

    @Test fun invalidAmountDateAndMissingCategoryDoNotSave() {
        compose.onNodeWithText("Dodaj wydatek").performClick()
        compose.onNodeWithText("Kwota (PLN)").performTextReplacement("0,001")
        compose.onNodeWithText("Data (dd.MM.rrrr)").performTextReplacement("31.09.2026")
        compose.onNodeWithText("Zapisz").performClick()
        compose.onNodeWithText("Wpisz prawidłową datę, np. 28.09.2026.").assertExists()
        compose.onNodeWithText("Wybierz kategorię i podkategorię.").assertExists()
        compose.runOnIdle { Assert.assertTrue(viewModel.state.value.entries.isEmpty()) }
        compose.onNodeWithText("Anuluj").performClick()
        waitForTotal(0)
    }

    @Test fun receiptButtonShowsPhotoChoicesAndCancelReturnsToManualEntry() {
        compose.onNodeWithTag("add_from_receipt").assertIsDisplayed().performClick()
        compose.onNodeWithTag("attach_receipt_camera").assertIsDisplayed()
        compose.onNodeWithTag("attach_receipt_file").assertIsDisplayed()
        compose.onNodeWithText("Anuluj").performClick()
        compose.runOnIdle { Assert.assertTrue(viewModel.state.value.entries.isEmpty()) }
        compose.onNodeWithText("Dodaj wydatek").performClick()
        compose.onNodeWithText("Kwota (PLN)").assertIsDisplayed()
        compose.onNodeWithText("Anuluj").performClick()
    }

    @Test fun legacyExpenseCanBeEditedWithoutPaymentControlsOrLosingItsLink() {
        runBlocking {
            database.monthlyPlanDao().saveItem(PlannedItemEntity(7, "2026-09", EntryType.EXPENSE,
                "Czynsz", 100000, YearMonth.of(2026, 9).atDay(1).toEpochDay()))
            database.entryDao().insert(EntryEntity(amountGrosz = 40000, type = EntryType.EXPENSE,
                dateEpochDay = YearMonth.of(2026, 9).atDay(10).toEpochDay(), note = "Dawny wpis",
                subcategory = ExpenseSubcategory.FOOD, plannedItemId = 7))
        }
        waitForTotal(40000)
        selectDay("2026-09-10")
        scrollTo("Dawny wpis")
        compose.onNodeWithText("Dawny wpis").performClick()
        compose.onNodeWithTag("bill_picker").assertDoesNotExist()
        compose.onNodeWithText("Kwota (PLN)").performTextReplacement("450,00")
        compose.onNodeWithText("Zapisz").performClick()
        waitForTotal(45000)
        compose.runOnIdle { Assert.assertEquals(7L, viewModel.state.value.entries.single().plannedItemId) }
        scrollTo("Dawny wpis")
        compose.onNodeWithText("Dawny wpis").performClick()
        compose.onNodeWithText("Data (dd.MM.rrrr)").performScrollTo().performTextReplacement("01.10.2026")
        compose.onNodeWithText("Zapisz").performClick()
        waitForTotal(0)
        compose.onNodeWithContentDescription("Następny miesiąc").performClick()
        compose.waitUntil(5000) { viewModel.state.value.month == YearMonth.of(2026, 10) && !viewModel.state.value.loading }
        waitForTotal(45000)
        compose.runOnIdle { Assert.assertNull(viewModel.state.value.entries.single().plannedItemId) }
    }

    @Test fun createCategoryAndSubcategoryInsideExpenseAndUseThemAcrossMonths() {
        compose.onNodeWithText("Dodaj wydatek").performClick()
        compose.onNodeWithText("Kwota (PLN)").performTextReplacement("125,00")
        compose.onNodeWithTag("manage_categories_editor").performScrollTo().performClick()
        compose.onNodeWithTag("add_category").performClick()
        compose.onNodeWithTag("save_category").assertIsNotEnabled()
        compose.onNodeWithTag("category_name").performTextInput("Edukacja")
        compose.onNodeWithTag("category_icon_BOOK").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("save_category").performClick()
        compose.waitUntil(5000) { viewModel.state.value.catalog.custom.size == 1 && !viewModel.categorySaving.value }
        val categoryId = viewModel.state.value.catalog.custom.single().category.id
        Assert.assertEquals(CategoryIcon.BOOK.name, viewModel.state.value.catalog.custom.single().category.icon)
        compose.onNodeWithTag("change_category_icon_$categoryId").performClick()
        compose.onNodeWithTag("category_icon_BOOK").assertIsSelected()
        compose.onNodeWithTag("category_icon_CAMERA").performScrollTo().performClick()
        compose.onNodeWithTag("cancel_category_icon").performClick()
        Assert.assertEquals(CategoryIcon.BOOK.name, viewModel.state.value.catalog.custom.single().category.icon)
        compose.onNodeWithTag("change_category_icon_$categoryId").performClick()
        compose.onNodeWithTag("category_icon_BOOK").assertIsSelected()
        compose.onNodeWithTag("category_icon_ANIMALS").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("category_icon_search").performScrollTo().performTextInput("nieistniejąca")
        compose.onNodeWithText("Brak pasujących ikon. Zmień lub wyczyść wyszukiwanie.").assertExists()
        compose.onNodeWithText("Wybrana: Książki").assertExists()
        compose.onNodeWithTag("category_icon_search").performTextReplacement("TRANSPORT")
        compose.onNodeWithTag("category_icon_TRANSPORT").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("category_icon_BOOK").assertDoesNotExist()
        compose.onNodeWithTag("save_category_icon").performClick()
        compose.waitUntil(5000) { viewModel.state.value.catalog.custom.single().category.icon == CategoryIcon.TRANSPORT.name && !viewModel.categorySaving.value }
        compose.onNodeWithTag("add_subcategory_$categoryId").performClick()
        compose.onNodeWithTag("category_name").performTextInput("Kursy")
        compose.onNodeWithTag("save_category").performClick()
        compose.waitUntil(5000) { viewModel.state.value.catalog.subcategories.size == 1 && !viewModel.categorySaving.value }
        val subcategoryId = viewModel.state.value.catalog.subcategories.keys.single()
        compose.onNodeWithTag("add_subcategory_$categoryId").performClick()
        compose.onNodeWithTag("category_name").performTextInput(" kursy ")
        compose.onNodeWithTag("save_category").performClick()
        compose.waitUntil(5000) { viewModel.categoryError.value != null }
        compose.onNodeWithText("Ta nazwa już istnieje. Wybierz inną.").assertExists()
        compose.onNodeWithTag("cancel_category_name").performClick()
        compose.onNodeWithTag("close_categories").performClick()
        compose.onNodeWithText("Kwota (PLN)").performScrollTo().assertTextContains("125,00")
        choose("category_picker", "Edukacja")
        choose("subcategory_picker", "Kursy")
        compose.onNodeWithText("Opis (opcjonalny)").performScrollTo().performTextInput("Kurs językowy")
        compose.onNodeWithText("Zapisz").performClick()
        waitForTotal(12500)
        Assert.assertEquals(subcategoryId, viewModel.state.value.entries.single().customSubcategoryId)
        Assert.assertNull(viewModel.state.value.entries.single().subcategory)
        scrollTo("Kurs językowy")
        compose.onNodeWithText("Edukacja · Kursy").assertExists()
        compose.onNodeWithText("Kurs językowy").performClick()
        compose.onNodeWithTag("category_picker").performScrollTo().assertTextContains("Kursy")
        choose("category_picker", "Kultura i czas wolny")
        compose.onNodeWithText("Wybierz podkategorię").assertExists()
        choose("subcategory_picker", "Książki i prasa")
        compose.onNodeWithText("Zapisz").performClick()
        waitForTotal(12500)
        Assert.assertNull(viewModel.state.value.entries.single().customSubcategoryId)
        Assert.assertEquals(ExpenseSubcategory.BOOKS, viewModel.state.value.entries.single().subcategory)
        compose.onNodeWithContentDescription("Następny miesiąc").performClick()
        compose.waitUntil(5000) { viewModel.state.value.month == YearMonth.of(2026, 10) && !viewModel.state.value.loading }
        compose.onNodeWithText("Dodaj wydatek").performClick()
        choose("category_picker", "Edukacja")
        choose("subcategory_picker", "Kursy")
        compose.onNodeWithText("Anuluj").performClick()
    }

    @Test fun renameAndDeleteCategoriesWithoutDeletingExpenses() {
        val (category, subcategory) = runBlocking {
            val repo = CategoryRepository(database.categoryDao())
            val category = repo.addCategory("Transport", CategoryIcon.TRANSPORT)
            val subcategory = repo.addSubcategory(category, "Taxi")
            val train = repo.addSubcategory(category, "Pociąg")
            listOf(subcategory, train).forEach { id ->
                database.entryDao().insert(EntryEntity(amountGrosz = 5000, type = EntryType.EXPENSE,
                    dateEpochDay = YearMonth.of(2026, 9).atDay(2).toEpochDay(), customSubcategoryId = id))
            }
            category to subcategory
        }
        waitForTotal(10000)
        compose.onNodeWithTag("manage_categories").performScrollTo().performClick()
        fun action(tag: String) {
            compose.onNodeWithTag("custom_categories_list").performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).performClick()
        }
        action("edit_category_$category")
        compose.onNodeWithTag("category_name").assertTextContains("Transport").performTextReplacement("Komunikacja")
        compose.onNodeWithTag("save_category").performClick()
        compose.waitUntil(5000) { viewModel.state.value.catalog.custom.single().category.name == "Komunikacja" && !viewModel.categorySaving.value }
        action("edit_subcategory_$subcategory")
        compose.onNodeWithTag("category_name").performTextReplacement("Pociąg")
        compose.onNodeWithTag("save_category").performClick()
        compose.waitUntil(5000) { viewModel.categoryError.value != null }
        compose.onNodeWithText("Ta nazwa już istnieje. Wybierz inną.").assertExists()
        compose.onNodeWithTag("category_name").performTextReplacement("Przejazdy")
        compose.onNodeWithTag("save_category").performClick()
        compose.waitUntil(5000) { viewModel.state.value.catalog.subcategories[subcategory]?.name == "Przejazdy" && !viewModel.categorySaving.value }
        action("delete_subcategory_$subcategory")
        compose.onNodeWithTag("cancel_category_delete").performClick()
        Assert.assertTrue(viewModel.state.value.catalog.subcategories.containsKey(subcategory))
        action("delete_subcategory_$subcategory")
        compose.onNodeWithTag("confirm_category_delete").performClick()
        compose.waitUntil(5000) { !viewModel.state.value.catalog.subcategories.containsKey(subcategory) && !viewModel.categorySaving.value }
        action("delete_category_$category")
        compose.onNodeWithTag("confirm_category_delete").performClick()
        compose.waitUntil(5000) { viewModel.state.value.catalog.custom.isEmpty() && !viewModel.categorySaving.value }
        compose.onNodeWithTag("close_categories").performClick()
        waitForTotal(10000)
        Assert.assertEquals(2, viewModel.state.value.entries.size)
        Assert.assertTrue(viewModel.state.value.entries.all { it.customSubcategoryId == null })
    }

    @Test fun deleteExpenseDirectlyFromDiaryRequiresConfirmationAndKeepsOtherExpenses() {
        val targetId = runBlocking {
            val date = YearMonth.of(2026, 9).atDay(3).toEpochDay()
            database.entryDao().insert(EntryEntity(amountGrosz = 7000, type = EntryType.EXPENSE,
                dateEpochDay = date, note = "Zachowany wydatek", subcategory = ExpenseSubcategory.BOOKS))
            database.entryDao().insert(EntryEntity(amountGrosz = 3000, type = EntryType.EXPENSE,
                dateEpochDay = date, note = "Wydatek do usunięcia"))
        }
        waitForTotal(10000)
        selectDay("2026-09-03")
        compose.onNodeWithTag("diary_list").performScrollToNode(hasTestTag("delete_expense_$targetId"))
        compose.onNodeWithTag("delete_expense_$targetId").assertIsDisplayed().performClick()
        compose.onNodeWithText("Usuń wydatek").assertIsDisplayed()
        compose.onNodeWithText("03.09.2026").assertIsDisplayed()
        compose.onNodeWithTag("cancel_expense_delete").performClick()
        Assert.assertEquals(2, viewModel.state.value.entries.size)
        compose.onNodeWithTag("delete_expense_$targetId").performClick()
        compose.onNodeWithTag("confirm_expense_delete").performClick()
        compose.waitUntil(5000) { !viewModel.saving.value && viewModel.state.value.entries.none { it.id == targetId } }
        waitForTotal(7000)
        Assert.assertEquals("Zachowany wydatek", viewModel.state.value.entries.single().note)
        compose.onNodeWithTag("confirm_expense_delete").assertDoesNotExist()
    }

    @Test fun navigateDaysFiltersExpensesAndCrossesMonthAndYearBoundaries() {
        runBlocking {
            listOf("2026-09-28" to 1000L, "2026-09-29" to 2000L, "2026-10-01" to 3000L).forEach { (date, amount) ->
                database.entryDao().insert(EntryEntity(amountGrosz = amount, type = EntryType.EXPENSE,
                    dateEpochDay = LocalDate.parse(date).toEpochDay(), note = "Zakup $date", subcategory = ExpenseSubcategory.BOOKS))
            }
        }
        waitForTotal(3000)
        scrollTo("Zakup 2026-09-28")
        compose.onNodeWithText("Zakup 2026-09-29").assertDoesNotExist()
        compose.onNodeWithContentDescription("Następny dzień").performClick()
        waitForDay("2026-09-29")
        waitForTotal(3000)
        scrollTo("Zakup 2026-09-29")
        compose.onNodeWithText("Zakup 2026-09-28").assertDoesNotExist()
        assertDayTotal(2000)
        compose.onNodeWithText("Dodaj wydatek").performClick()
        compose.onNodeWithText("Data (dd.MM.rrrr)").assertTextContains("29.09.2026")
        compose.onNodeWithText("Anuluj").performClick()
        compose.onNodeWithContentDescription("Następny dzień").performClick()
        waitForDay("2026-09-30")
        scrollTo("Nie masz jeszcze wydatków w tym dniu.")
        assertDayTotal(0)
        compose.onNodeWithContentDescription("Następny dzień").performClick()
        waitForDay("2026-10-01")
        Assert.assertEquals(YearMonth.of(2026, 10), viewModel.state.value.month)
        scrollTo("Zakup 2026-10-01")
        compose.onNodeWithContentDescription("Poprzedni dzień").performClick()
        waitForDay("2026-09-30")
        selectDay("2026-12-31")
        compose.onNodeWithContentDescription("Następny dzień").performClick()
        waitForDay("2027-01-01")
        Assert.assertEquals(YearMonth.of(2027, 1), viewModel.state.value.month)
        compose.onNodeWithContentDescription("Poprzedni dzień").performClick()
        waitForDay("2026-12-31")
        selectDay("2026-01-31")
        compose.onNodeWithContentDescription("Następny miesiąc").performClick()
        waitForDay("2026-02-28")
    }

    @Test fun categorySummaryUsesOnlySelectedDayIncludingEmptyDays() {
        runBlocking {
            database.entryDao().insert(EntryEntity(amountGrosz = 1000, type = EntryType.EXPENSE,
                dateEpochDay = LocalDate.parse("2026-09-28").toEpochDay(), subcategory = ExpenseSubcategory.BOOKS))
            database.entryDao().insert(EntryEntity(amountGrosz = 2000, type = EntryType.EXPENSE,
                dateEpochDay = LocalDate.parse("2026-09-29").toEpochDay(), subcategory = ExpenseSubcategory.FOOD))
            database.entryDao().insert(EntryEntity(amountGrosz = 700, type = EntryType.EXPENSE,
                dateEpochDay = LocalDate.parse("2026-09-29").toEpochDay()))
        }
        waitForTotal(3700)
        compose.onNodeWithTag("diary_total").assertTextEquals(formatMoney(1000))
        compose.onNodeWithText("W tym dniu").performClick()
        assertCategoryAmount("Kultura i czas wolny", 1000)
        assertCategoryAmount("Pierwszej potrzeby", 0)
        selectDay("2026-09-29")
        compose.onNodeWithTag("diary_list").performScrollToNode(hasTestTag("diary_total"))
        compose.onNodeWithTag("diary_total").assertTextEquals(formatMoney(2700))
        assertCategoryAmount("Kultura i czas wolny", 0)
        assertCategoryAmount("Pierwszej potrzeby", 2000)
        assertCategoryAmount("Wydatki z karty", 700)
        selectDay("2026-09-30")
        compose.onNodeWithTag("diary_list").performScrollToNode(hasTestTag("diary_total"))
        compose.onNodeWithTag("diary_total").assertTextEquals(formatMoney(0))
        assertCategoryAmount("Kultura i czas wolny", 0)
        assertCategoryAmount("Pierwszej potrzeby", 0)
        compose.onNodeWithText("Wydatki z karty").assertDoesNotExist()
        selectDay("2026-09-28")
        assertCategoryAmount("Kultura i czas wolny", 1000)
    }

    private fun assertCategoryAmount(label: String, amount: Long) {
        scrollTo(label)
        compose.onNode(hasText(formatMoney(amount)) and hasText(label)
            and hasAnyAncestor(hasTestTag("diary_categories")))
            .assertExists()
    }

    private fun selectDay(date: String) {
        compose.runOnIdle { viewModel.changeDay(LocalDate.parse(date).toEpochDay() - viewModel.state.value.selectedDate.toEpochDay()) }
        waitForDay(date)
    }
    private fun waitForDay(date: String) {
        compose.waitUntil(5000) { !viewModel.state.value.loading && viewModel.state.value.selectedDate == LocalDate.parse(date) }
    }

    private fun choose(tag: String, text: String) {
        if (tag == "category_picker") {
            val builtIn = mapOf(
                "Pierwszej potrzeby" to "NEEDS",
                "Opcjonalne" to "OPTIONAL",
                "Kultura i czas wolny" to "HOBBY",
                "Ekstra i nieprzewidziane" to "EXTRA",
            )[text]
            val choiceTag = if (builtIn != null) "category_choice_$builtIn" else {
                val custom = viewModel.state.value.catalog.custom.single { it.category.name == text }.category.id
                "category_choice_custom_$custom"
            }
            if (compose.onAllNodesWithTag(choiceTag).fetchSemanticsNodes().isEmpty()) {
                compose.onNodeWithTag("category_picker").performScrollTo().performClick()
            }
            compose.onNodeWithTag(choiceTag).performScrollTo().performClick()
            compose.onNodeWithTag("subcategory_picker").assertExists()
            return
        }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val builtIn = ExpenseSubcategory.entries.singleOrNull { context.getString(it.label) == text }
        val choiceTag = builtIn?.let { "subcategory_choice_${it.name}" } ?: run {
            val custom = viewModel.state.value.catalog.subcategories.values.single { it.name == text }.id
            "subcategory_choice_custom_$custom"
        }
        compose.onNodeWithTag(choiceTag).performScrollTo().performClick()
        compose.onNodeWithTag("selected_subcategory_icon", useUnmergedTree = true).assertExists()
    }
    private fun scrollTo(text: String) { compose.onNodeWithTag("diary_list").performScrollToNode(hasText(text)) }
    private fun assertDayTotal(amount: Long) {
        compose.onNodeWithTag("diary_list").performScrollToNode(hasTestTag("diary_day_total"))
        compose.onNodeWithTag("diary_day_total").assertTextEquals("Razem: ${formatMoney(amount)}")
    }
    private fun waitForTotal(amount: Long) {
        compose.waitUntil(5000) { !viewModel.saving.value && !viewModel.state.value.loading && viewModel.state.value.entries.sumOf { it.amountGrosz } == amount }
        compose.onNodeWithTag("diary_list").performScrollToNode(hasTestTag("diary_total"))
        val selected = viewModel.state.value
        val dailyAmount = selected.entries.filter { it.dateEpochDay == selected.selectedDate.toEpochDay() }.sumOf { it.amountGrosz }
        compose.onNodeWithTag("diary_total").assertTextEquals(formatMoney(dailyAmount))
    }
}
