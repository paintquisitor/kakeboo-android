package pl.kakebo.ui.weekly

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import pl.kakebo.data.repository.CategoryRepository
import pl.kakebo.domain.ExpenseCatalog
import kotlinx.coroutines.flow.flowOf
import pl.kakebo.data.repository.EntryRepository
import pl.kakebo.data.repository.MonthlyPlanRepository
import pl.kakebo.data.local.EntryType
import pl.kakebo.domain.*

data class WeeklyUiState(
    val month: YearMonth = YearMonth.now(),
    val weeks: List<WeekReport> = emptyList(),
    val balance: BudgetBalance? = null,
    val hasIncomePlan: Boolean = false,
    val loading: Boolean = true,
    val loadError: Boolean = false,
    val catalog: ExpenseCatalog = ExpenseCatalog(),
)

class WeeklyViewModel(
    entries: EntryRepository,
    plans: MonthlyPlanRepository,
    private val savedState: SavedStateHandle,
    private val categories: CategoryRepository? = null,
) : ViewModel() {
    private val month = savedState.getStateFlow("month", YearMonth.now().toString())
    private val reload = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state = combine(month, reload) { selected, _ -> YearMonth.parse(selected) }
        .flatMapLatest { selected ->
            combine(entries.observeMonth(selected), plans.observeMonth(selected), categories?.catalog ?: flowOf(ExpenseCatalog())) { expenses, plan, catalog ->
                WeeklyUiState(selected, weeklyReports(plan, expenses, catalog),
                    budgetBalance(plan, expenses), plan.items.any { it.type == EntryType.INCOME }, loading = false, catalog = catalog)
            }.onStart { emit(WeeklyUiState(selected)) }
                .catch { emit(WeeklyUiState(selected, loading = false, loadError = true)) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeeklyUiState(YearMonth.parse(month.value)))

    fun changeMonth(offset: Long) { savedState["month"] = YearMonth.parse(month.value).plusMonths(offset).toString() }
    fun retry() { reload.value++ }

    companion object {
        fun factory(entries: EntryRepository, plans: MonthlyPlanRepository, categories: CategoryRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { WeeklyViewModel(entries, plans, createSavedStateHandle(), categories) }
        }
    }
}
