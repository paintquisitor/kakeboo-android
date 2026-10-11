package pl.kakebo.ui.monthly

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import pl.kakebo.data.local.MonthlyReflectionEntity
import pl.kakebo.data.local.EntryEntity
import pl.kakebo.data.local.EntryType
import pl.kakebo.data.local.PlannedItemEntity
import pl.kakebo.domain.isDueIn
import pl.kakebo.domain.reconcileExpenses
import pl.kakebo.data.repository.CategoryRepository
import pl.kakebo.domain.ExpenseCatalog
import kotlinx.coroutines.flow.flowOf
import pl.kakebo.data.repository.EntryRepository
import pl.kakebo.data.repository.MonthlyPlanRepository
import pl.kakebo.data.repository.MonthlyReflectionRepository
import pl.kakebo.domain.MonthlyReport
import pl.kakebo.domain.WeekReport
import pl.kakebo.domain.YearlyReport
import pl.kakebo.domain.monthlyReport
import pl.kakebo.domain.weeklyReports
import pl.kakebo.domain.yearlyReport

data class MonthlyUiState(
    val month: YearMonth = YearMonth.now(),
    val report: MonthlyReport? = null,
    val weeks: List<WeekReport> = emptyList(),
    val yearlyReport: YearlyReport? = null,
    val resolutions: String = "",
    val reflection: MonthlyReflectionEntity? = null,
    val loading: Boolean = true,
    val loadError: Boolean = false,
    val catalog: ExpenseCatalog = ExpenseCatalog(),
    val transactions: List<EntryEntity> = emptyList(),
    val diaryTransactions: List<EntryEntity> = emptyList(),
    val fixedExpenseItems: List<PlannedItemEntity> = emptyList(),
)

class MonthlyViewModel(
    entries: EntryRepository,
    plans: MonthlyPlanRepository,
    private val reflections: MonthlyReflectionRepository,
    private val savedState: SavedStateHandle,
    private val categories: CategoryRepository? = null,
) : ViewModel() {
    private val month = savedState.getStateFlow("month", YearMonth.now().toString())
    private val reload = MutableStateFlow(0)
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val _saveError = MutableStateFlow(false)
    val saveError = _saveError.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state = combine(month, reload) { selected, _ -> YearMonth.parse(selected) }
        .flatMapLatest { selected ->
            val annualData = combine(entries.entries, plans.observeYear(selected.year)) { allEntries, yearlyPlans ->
                allEntries to yearlyPlans
            }
            combine(entries.observeMonth(selected), plans.observeMonth(selected), reflections.observeMonth(selected), categories?.catalog ?: flowOf(ExpenseCatalog()), annualData) { expenses, plan, reflection, catalog, annual ->
                val reconciled = reconcileExpenses(plan, expenses)
                MonthlyUiState(
                    month = selected,
                    report = monthlyReport(plan, expenses, catalog),
                    weeks = weeklyReports(plan, expenses, catalog),
                    transactions = expenses,
                    diaryTransactions = reconciled.variable,
                    fixedExpenseItems = plan.items.filter { it.type == EntryType.EXPENSE && it.isDueIn(selected) }
                        .map { it.copy(amountGrosz = maxOf(it.amountGrosz, reconciled.fixedActualById[it.id] ?: 0)) },
                    yearlyReport = yearlyReport(selected.year, annual.second, annual.first, catalog),
                    resolutions = plan.plan.resolutions,
                    reflection = reflection,
                    loading = false,
                    catalog = catalog,
                )
            }.onStart { emit(MonthlyUiState(selected)) }
                .catch { emit(MonthlyUiState(selected, loading = false, loadError = true)) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthlyUiState(YearMonth.parse(month.value)))

    fun changeMonth(offset: Long) {
        if (!_saving.value) savedState["month"] = YearMonth.parse(month.value).plusMonths(offset).toString()
    }
    fun selectMonth(month: YearMonth) {
        if (!_saving.value) savedState["month"] = month.toString()
    }
    fun retry() { reload.value++ }
    fun clearSaveError() { _saveError.value = false }

    fun saveReflection(reflection: MonthlyReflectionEntity, onSaved: () -> Unit) {
        if (_saving.value) return
        _saving.value = true
        _saveError.value = false
        viewModelScope.launch {
            try {
                reflections.save(reflection)
                onSaved()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _saveError.value = true
            } finally {
                _saving.value = false
            }
        }
    }

    companion object {
        fun factory(entries: EntryRepository, plans: MonthlyPlanRepository, reflections: MonthlyReflectionRepository, categories: CategoryRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { MonthlyViewModel(entries, plans, reflections, createSavedStateHandle(), categories) }
        }
    }
}
