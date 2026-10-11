package pl.kakebo.ui.home

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.kakebo.data.local.MonthlyPlanEntity
import pl.kakebo.data.local.MonthlyPlanWithItems
import pl.kakebo.data.local.PlannedItemEntity
import pl.kakebo.data.repository.MonthlyPlanRepository

data class HomeUiState(
    val month: YearMonth = YearMonth.now(),
    val data: MonthlyPlanWithItems? = null,
    val loading: Boolean = true,
    val loadError: Boolean = false,
)

class HomeViewModel(
    private val repository: MonthlyPlanRepository,
    private val savedState: SavedStateHandle,
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
            repository.observeMonth(selected)
                .map { HomeUiState(selected, data = it, loading = false) }
                .onStart { emit(HomeUiState(selected)) }
                .catch { emit(HomeUiState(selected, loading = false, loadError = true)) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(YearMonth.parse(month.value)))

    fun changeMonth(offset: Long) {
        if (!_saving.value) {
            savedState["month"] = YearMonth.parse(month.value).plusMonths(offset).toString()
        }
    }

    fun retry() { reload.value++ }
    fun clearSaveError() { _saveError.value = false }
    fun saveItem(item: PlannedItemEntity, onSaved: () -> Unit) = save(onSaved) { repository.saveItem(item) }
    fun savePlan(plan: MonthlyPlanEntity, onSaved: () -> Unit) = save(onSaved) { repository.savePlan(plan) }
    fun deleteItem(item: PlannedItemEntity, onSaved: () -> Unit) = save(onSaved) { repository.deleteItem(item) }

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

    companion object {
        fun factory(repository: MonthlyPlanRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(repository, createSavedStateHandle()) }
        }
    }
}
