package pl.kakebo.ui.diary

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.kakebo.data.local.EntryEntity
import pl.kakebo.data.local.EntryType
import pl.kakebo.data.repository.CategoryRepository
import pl.kakebo.data.statements.BankStatementPdfReader
import pl.kakebo.domain.BankStatementExpense
import pl.kakebo.domain.ExpenseCatalog
import kotlinx.coroutines.flow.flowOf
import pl.kakebo.data.repository.EntryRepository

data class DiaryUiState(
    val month: YearMonth = YearMonth.now(),
    val entries: List<EntryEntity> = emptyList(),
    val loading: Boolean = true,
    val loadError: Boolean = false,
    val catalog: ExpenseCatalog = ExpenseCatalog(),
    val selectedDate: LocalDate = month.atDay(1),
)

enum class StatementImportError { READ_FAILED, NO_EXPENSES, CURRENCY_MISMATCH, SAVE_FAILED }

data class StatementExpenseItem(
    val id: Int,
    val date: LocalDate,
    val amountMinor: Long,
    val note: String,
)

data class StatementImportUiState(
    val reading: Boolean = false,
    val saving: Boolean = false,
    val candidates: List<StatementExpenseItem> = emptyList(),
    val selectedIds: Set<Int> = emptySet(),
    val ignoredSavings: Int = 0,
    val ignoredCredits: Int = 0,
    val duplicates: Int = 0,
    val unrecognized: Int = 0,
    val sourceCurrency: String? = null,
    val expectedCurrency: String? = null,
    val error: StatementImportError? = null,
    val importedCount: Int? = null,
)

class DiaryViewModel(
    private val repository: EntryRepository,
    private val savedState: SavedStateHandle,
    private val categories: CategoryRepository? = null,
    private val statementReader: BankStatementPdfReader? = null,
) : ViewModel() {
    private val initialMonth = YearMonth.parse(savedState.get<String>("month") ?: YearMonth.now().toString())
    private val selectedDate = savedState.getStateFlow("selectedDate",
        (if (initialMonth == YearMonth.now()) LocalDate.now() else initialMonth.atDay(1)).toString())
    private val month = selectedDate.map { YearMonth.from(LocalDate.parse(it)) }.distinctUntilChanged()
    private val reload = MutableStateFlow(0)
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val _saveError = MutableStateFlow(false)
    val saveError = _saveError.asStateFlow()
    private val _statementImport = MutableStateFlow(StatementImportUiState())
    val statementImport = _statementImport.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val monthlyState = combine(month, reload) { selected, _ -> selected }
        .flatMapLatest { selected ->
            combine(repository.observeMonth(selected), categories?.catalog ?: flowOf(ExpenseCatalog())) { entries, catalog ->
                    DiaryUiState(selected, entries = entries, loading = false, catalog = catalog)
                }
                .onStart { emit(DiaryUiState(selected)) }
                .catch { emit(DiaryUiState(selected, loading = false, loadError = true)) }
        }

    // Day changes keep the monthly query and camera activity-result registrations stable.
    val state = combine(selectedDate, monthlyState) { date, monthly ->
        val selected = LocalDate.parse(date)
        val selectedMonth = YearMonth.from(selected)
        if (monthly.month == selectedMonth) monthly.copy(selectedDate = selected)
        else DiaryUiState(month = selectedMonth, selectedDate = selected)
    }.stateIn(viewModelScope, SharingStarted.Lazily,
        DiaryUiState(month = YearMonth.from(LocalDate.parse(selectedDate.value)), selectedDate = LocalDate.parse(selectedDate.value)))

    fun changeMonth(offset: Long) = selectDate(LocalDate.parse(selectedDate.value).plusMonths(offset))
    fun changeDay(offset: Long) = selectDate(LocalDate.parse(selectedDate.value).plusDays(offset))

    private fun selectDate(date: LocalDate) {
        if (!_saving.value) {
            savedState["selectedDate"] = date.toString()
            savedState["month"] = YearMonth.from(date).toString()
        }
    }

    fun retry() { reload.value++ }
    fun clearSaveError() { _saveError.value = false }
    fun saveEntry(entry: EntryEntity, onSaved: () -> Unit) = save(onSaved) { repository.save(entry) }
    fun deleteEntry(entry: EntryEntity, onSaved: () -> Unit) = save(onSaved) { repository.delete(entry.id) }

    fun readStatement(uri: Uri, currencyCode: String) {
        if (_statementImport.value.reading || _statementImport.value.saving) return
        _statementImport.value = StatementImportUiState(reading = true)
        viewModelScope.launch {
            try {
                val parsed = requireNotNull(statementReader).read(uri)
                if (parsed.expenses.isEmpty()) {
                    _statementImport.value = StatementImportUiState(
                        ignoredSavings = parsed.ignoredSavings,
                        ignoredCredits = parsed.ignoredCredits,
                        unrecognized = parsed.unrecognized,
                        error = StatementImportError.NO_EXPENSES,
                    )
                    return@launch
                }
                val currencies = parsed.expenses.map { it.currencyCode }.distinct()
                if (currencies.size != 1 || currencies.single() != currencyCode) {
                    _statementImport.value = StatementImportUiState(
                        ignoredSavings = parsed.ignoredSavings,
                        ignoredCredits = parsed.ignoredCredits,
                        unrecognized = parsed.unrecognized,
                        sourceCurrency = currencies.joinToString(", "),
                        expectedCurrency = currencyCode,
                        error = StatementImportError.CURRENCY_MISMATCH,
                    )
                    return@launch
                }
                val existing = repository.entries.first()
                    .filter { it.type == EntryType.EXPENSE }
                    .groupingBy { it.statementKey() }
                    .eachCount()
                    .toMutableMap()
                var duplicateCount = 0
                val candidates = parsed.expenses.mapIndexedNotNull { index, expense ->
                    val key = expense.statementKey()
                    val duplicateSlots = existing[key] ?: 0
                    if (duplicateSlots > 0) {
                        existing[key] = duplicateSlots - 1
                        duplicateCount++
                        null
                    } else StatementExpenseItem(index, expense.date, expense.amountMinor, expense.note)
                }
                _statementImport.value = StatementImportUiState(
                    candidates = candidates,
                    selectedIds = candidates.mapTo(mutableSetOf()) { it.id },
                    ignoredSavings = parsed.ignoredSavings,
                    ignoredCredits = parsed.ignoredCredits,
                    duplicates = duplicateCount,
                    unrecognized = parsed.unrecognized,
                    sourceCurrency = currencies.single(),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _statementImport.value = StatementImportUiState(error = StatementImportError.READ_FAILED)
            }
        }
    }

    fun toggleStatementExpense(id: Int) {
        if (_statementImport.value.saving) return
        _statementImport.update { state ->
            state.copy(selectedIds = state.selectedIds.toMutableSet().apply {
                if (!add(id)) remove(id)
            })
        }
    }

    fun selectAllStatementExpenses(selected: Boolean) {
        if (_statementImport.value.saving) return
        _statementImport.update { state ->
            state.copy(selectedIds = if (selected) state.candidates.mapTo(mutableSetOf()) { it.id } else emptySet())
        }
    }

    fun saveStatementExpenses() {
        val state = _statementImport.value
        if (state.reading || state.saving) return
        val selected = state.candidates.filter { it.id in state.selectedIds }
        if (selected.isEmpty()) return
        _statementImport.value = state.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                repository.addAll(selected.map {
                    EntryEntity(
                        amountGrosz = it.amountMinor,
                        type = EntryType.EXPENSE,
                        dateEpochDay = it.date.toEpochDay(),
                        note = it.note,
                    )
                })
                _statementImport.value = StatementImportUiState(importedCount = selected.size)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _statementImport.update { it.copy(saving = false, error = StatementImportError.SAVE_FAILED) }
            }
        }
    }

    fun dismissStatementImport() {
        if (!_statementImport.value.reading && !_statementImport.value.saving) {
            _statementImport.value = StatementImportUiState()
        }
    }

    private fun save(onSaved: () -> Unit, operation: suspend () -> Unit) {
        if (_saving.value) return
        _saving.value = true
        _saveError.value = false
        viewModelScope.launch {
            try {
                operation()
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

    private val _categorySaving = MutableStateFlow(false)
    val categorySaving = _categorySaving.asStateFlow()
    private val _categoryError = MutableStateFlow<Int?>(null)
    val categoryError = _categoryError.asStateFlow()
    fun clearCategoryError() { _categoryError.value = null }

    fun addCategory(name: String, icon: pl.kakebo.domain.CategoryIcon, onCreated: (Long) -> Unit) = saveCategory(onCreated) {
        requireNotNull(categories).addCategory(name, icon)
    }
    fun updateCategoryIcon(categoryId: Long, icon: pl.kakebo.domain.CategoryIcon, onSaved: (Long) -> Unit) = saveCategory(onSaved) {
        requireNotNull(categories).updateIcon(categoryId, icon)
        categoryId
    }
    fun addSubcategory(categoryId: Long, name: String, onCreated: (Long) -> Unit) = saveCategory(onCreated) {
        requireNotNull(categories).addSubcategory(categoryId, name)
    }
    fun renameCategory(id: Long, name: String, done: (Long) -> Unit) = saveCategory(done) {
        requireNotNull(categories).renameCategory(id, name)
        id
    }
    fun renameSubcategory(id: Long, name: String, done: (Long) -> Unit) = saveCategory(done) {
        requireNotNull(categories).renameSubcategory(id, name)
        id
    }
    fun deleteCategory(id: Long, done: (Long) -> Unit) = saveCategory(done) {
        requireNotNull(categories).deleteCategory(id)
        id
    }
    fun deleteSubcategory(id: Long, done: (Long) -> Unit) = saveCategory(done) {
        requireNotNull(categories).deleteSubcategory(id)
        id
    }
    private fun saveCategory(onCreated: (Long) -> Unit, operation: suspend () -> Long) {
        if (_categorySaving.value) return
        _categorySaving.value = true
        _categoryError.value = null
        viewModelScope.launch {
            try {
                onCreated(operation())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: pl.kakebo.data.repository.DuplicateCategoryNameException) {
                _categoryError.value = pl.kakebo.R.string.category_duplicate
            } catch (_: Exception) {
                _categoryError.value = pl.kakebo.R.string.save_error
            } finally {
                _categorySaving.value = false
            }
        }
    }

    companion object {
        fun factory(
            repository: EntryRepository,
            categories: CategoryRepository,
            statementReader: BankStatementPdfReader,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { DiaryViewModel(repository, createSavedStateHandle(), categories, statementReader) }
        }
    }
}

private data class StatementKey(val dateEpochDay: Long, val amountMinor: Long, val note: String)

private fun EntryEntity.statementKey() = StatementKey(
    dateEpochDay,
    amountGrosz,
    note.replace(Regex("\\s+"), " ").trim().lowercase(Locale.ROOT),
)

private fun BankStatementExpense.statementKey() = StatementKey(
    date.toEpochDay(),
    amountMinor,
    note.replace(Regex("\\s+"), " ").trim().lowercase(Locale.ROOT),
)
