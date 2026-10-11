package pl.kakebo.ui.home

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.YearMonth
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pl.kakebo.MoneyCurrency
import pl.kakebo.data.local.KakeboDatabase
import pl.kakebo.data.repository.MonthlyPlanRepository
import pl.kakebo.domain.formatMoney
import pl.kakebo.domain.totals
import pl.kakebo.ui.theme.KakeboTheme

@RunWith(AndroidJUnit4::class)
class MonthlyPlanScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: KakeboDatabase
    private lateinit var viewModel: HomeViewModel
    private val store = ViewModelStore()

    @Before
    fun setUp() {
        MoneyCurrency.select("PLN")
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), KakeboDatabase::class.java).build()
        compose.runOnUiThread {
            viewModel = HomeViewModel(MonthlyPlanRepository(database.monthlyPlanDao()), SavedStateHandle(mapOf("month" to "2026-09")))
            store.put("plan", viewModel)
        }
        compose.setContent { KakeboTheme { HomeScreen(viewModel) } }
        compose.waitUntil(5_000) { viewModel.state.value.data != null }
    }

    @After
    fun tearDown() {
        compose.runOnUiThread { store.clear() }
        database.close()
    }

    @Test
    fun userCanPlanEditDeleteAndSwitchMonths() {
        addItem("Dodaj wpływ", "Pensja", "2000,00")
        waitForBudget(200_000)
        addFixedItem("500,00")
        waitForBudget(150_000)
        compose.onNodeWithTag("plan_list").performScrollToNode(hasTestTag("edit_goals"))
        compose.onNodeWithTag("edit_goals").performClick()
        compose.onNodeWithText("Chcę odłożyć (PLN)").performTextReplacement("300,00")
        compose.onNodeWithText("Postanowienia i sposób realizacji").performTextReplacement("Zakupy z listą")
        compose.onNodeWithText("Zapisz").performClick()
        waitForBudget(120_000)
        compose.onNodeWithContentDescription("Następny miesiąc").performClick()
        compose.waitUntil(5_000) { viewModel.state.value.month == YearMonth.of(2026, 10) && viewModel.state.value.data != null }
        waitForBudget(120_000)
        compose.onNodeWithContentDescription("Poprzedni miesiąc").performClick()
        compose.waitUntil(5_000) { viewModel.state.value.month == YearMonth.of(2026, 9) && viewModel.state.value.data != null }
        waitForBudget(120_000)
        scrollTo("Czynsz administracyjny")
        compose.onNodeWithText("Czynsz administracyjny").performClick()
        compose.onNodeWithText("Kwota (PLN)").performTextReplacement("750,00")
        compose.onNodeWithText("Zapisz").performClick()
        waitForBudget(95_000)
        scrollTo("Czynsz administracyjny")
        compose.onNodeWithText("Czynsz administracyjny").performClick()
        compose.onNodeWithTag("delete_planned_item").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_planned_item_delete").performClick()
        waitForBudget(170_000)
        compose.onNodeWithContentDescription("Następny miesiąc").performClick()
        compose.waitUntil(5_000) { viewModel.state.value.month == YearMonth.of(2026, 10) && viewModel.state.value.data != null }
        waitForBudget(120_000)
    }

    @Test
    fun invalidFormStaysOpenWithoutSaving() {
        scrollTo("Dodaj wpływ")
        compose.onNodeWithText("Dodaj wpływ").performClick()
        compose.onNodeWithText("Kwota (PLN)").performTextReplacement("0,001")
        compose.onNodeWithText("Dzień miesiąca").performTextReplacement("31")
        compose.onNodeWithText("Zapisz").performClick()
        compose.onNodeWithText("Wpisz opis pozycji.").assertExists()
        compose.runOnIdle { check(viewModel.state.value.data!!.items.isEmpty()) }
        compose.onNodeWithText("Anuluj").performClick()
        waitForBudget(0)
    }

    private fun addItem(button: String, title: String, amount: String) {
        scrollTo(button)
        compose.onNodeWithText(button).performClick()
        compose.onNodeWithText("Opis").performTextReplacement(title)
        compose.onNodeWithText("Kwota (PLN)").performTextReplacement(amount)
        compose.onNodeWithText("Dzień miesiąca").performTextReplacement("10")
        compose.onNodeWithText("Zapisz").performClick()
    }

    private fun addFixedItem(amount: String) {
        scrollTo("Dodaj stały wydatek")
        compose.onNodeWithText("Dodaj stały wydatek").performClick()
        compose.onNodeWithTag("fixed_group_HOUSING").performClick()
        compose.onNodeWithTag("fixed_preset_ADMIN_RENT").performClick()
        compose.onNodeWithText("Kwota (PLN)").performTextReplacement(amount)
        compose.onNodeWithText("Dzień miesiąca").performTextReplacement("10")
        compose.onNodeWithText("Zapisz").performClick()
    }

    private fun scrollTo(text: String) {
        compose.onNodeWithTag("plan_list").performScrollToNode(hasText(text))
    }

    private fun waitForBudget(expected: Long) {
        compose.waitUntil(5_000) { !viewModel.saving.value && viewModel.state.value.data?.totals()?.available == expected }
        compose.onNodeWithTag("plan_list").performScrollToNode(hasTestTag("available_budget"))
        compose.onNodeWithTag("available_budget").assertTextEquals(formatMoney(expected))
    }
}
