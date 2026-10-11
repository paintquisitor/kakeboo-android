package pl.kakebo.ui.monthly

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.YearMonth
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import pl.kakebo.R
import pl.kakebo.data.local.MonthlyReflectionEntity
import pl.kakebo.domain.ExpenseCatalog
import pl.kakebo.domain.CategoryIcon
import pl.kakebo.domain.SavingsResult
import pl.kakebo.domain.formatMoney
import pl.kakebo.ui.components.*

private enum class BalancePeriod { MONTH, YEAR }
private enum class ExpenseDetails { FIXED, DIARY }

@Composable
fun MonthlyScreen(viewModel: MonthlyViewModel, onOpenProfile: () -> Unit = {}) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val saveError by viewModel.saveError.collectAsStateWithLifecycle()
    var editing by rememberSaveable(state.month.toString()) { mutableStateOf(false) }
    var period by rememberSaveable { mutableStateOf(BalancePeriod.MONTH) }
    var details by rememberSaveable(state.month.toString()) { mutableStateOf<ExpenseDetails?>(null) }
    BackHandler(enabled = details != null) { details = null }

    Scaffold { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            ScreenHeader(
                title = stringResource(if (period == BalancePeriod.MONTH) R.string.monthly_title else R.string.annual_title),
                month = state.month,
                onPrevious = { viewModel.changeMonth(if (period == BalancePeriod.MONTH) -1 else -12) },
                onNext = { viewModel.changeMonth(if (period == BalancePeriod.MONTH) 1 else 12) },
                enabled = !saving,
                monthTag = if (period == BalancePeriod.MONTH) "month" else "year",
                periodLabel = if (period == BalancePeriod.YEAR) state.month.year.toString() else null,
                previousDescription = if (period == BalancePeriod.MONTH) R.string.previous_month else R.string.previous_year,
                nextDescription = if (period == BalancePeriod.MONTH) R.string.next_month else R.string.next_year,
                onOpenProfile = onOpenProfile,
            )
            if (details == null) BalancePeriodPicker(period) { period = it }
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                state.loadError -> Column(Modifier.padding(24.dp)) {
                    Text(stringResource(R.string.load_error))
                    TextButton(onClick = viewModel::retry) { Text(stringResource(R.string.retry)) }
                }
                details != null -> MonthlyExpenseDetails(state, requireNotNull(details)) { details = null }
                period == BalancePeriod.MONTH -> MonthlyContent(state,
                    onFixedExpenses = { details = ExpenseDetails.FIXED },
                    onDiaryExpenses = { details = ExpenseDetails.DIARY }) {
                    viewModel.clearSaveError()
                    editing = true
                }
                else -> AnnualContent(requireNotNull(state.yearlyReport), state.catalog) { month ->
                    viewModel.selectMonth(month)
                    period = BalancePeriod.MONTH
                }
            }
        }
    }
    if (editing && state.reflection != null) {
        ReflectionEditor(
            state.reflection!!,
            saving,
            saveError,
            onDismiss = { editing = false; viewModel.clearSaveError() },
            onSave = { viewModel.saveReflection(it) { editing = false } },
        )
    }
}

@Composable
private fun BalancePeriodPicker(period: BalancePeriod, onChange: (BalancePeriod) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BalancePeriod.entries.forEach { option ->
            if (period == option) {
                Button(onClick = { onChange(option) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(if (option == BalancePeriod.MONTH) R.string.balance_period_month else R.string.balance_period_year))
                }
            } else {
                OutlinedButton(onClick = { onChange(option) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(if (option == BalancePeriod.MONTH) R.string.balance_period_month else R.string.balance_period_year))
                }
            }
        }
    }
}

@Composable
private fun MonthlyContent(state: MonthlyUiState, onFixedExpenses: () -> Unit, onDiaryExpenses: () -> Unit, onEditReflection: () -> Unit) {
    var transactionsExpanded by rememberSaveable(state.month.toString()) { mutableStateOf(false) }
    var weeksExpanded by rememberSaveable(state.month.toString()) { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().testTag("monthly_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { MonthlySummaryCard(state, onFixedExpenses, onDiaryExpenses) }
        item { MonthlySavingsCard(requireNotNull(state.report)) }
        item { MonthlyCategoriesCard(requireNotNull(state.report), state.catalog) }
        item {
            MonthlyWeeksCard(state.weeks, weeksExpanded) { weeksExpanded = !weeksExpanded }
        }
        item {
            MonthlyTransactionsHeader(state.transactions.size, transactionsExpanded) {
                transactionsExpanded = !transactionsExpanded
            }
        }
        if (transactionsExpanded) {
            if (state.transactions.isEmpty()) item {
                SectionCard { Text(stringResource(R.string.monthly_transactions_empty)) }
            }
            items(state.transactions.sortedByDescending { it.dateEpochDay }, key = { "transaction_${it.id}" }) { entry ->
                MonthlyTransactionRow(entry, state.catalog)
            }
        }
        if (state.resolutions.isNotBlank()) item {
            SectionCard {
                Text(stringResource(R.string.monthly_original_goals), style = MaterialTheme.typography.titleLarge)
                Text(state.resolutions)
            }
        }
        item {
            val reflection = requireNotNull(state.reflection)
            SectionCard {
                Text(stringResource(R.string.monthly_reflection), style = MaterialTheme.typography.titleLarge)
                ReflectionText(R.string.reflection_successes, reflection.successes)
                ReflectionText(R.string.reflection_improvements, reflection.improvements)
                ReflectionText(R.string.reflection_next_month, reflection.nextMonth)
                if (reflection.successes.isBlank() && reflection.improvements.isBlank() && reflection.nextMonth.isBlank()) {
                    Text(stringResource(R.string.reflection_empty))
                }
                OutlinedButton(onClick = onEditReflection, modifier = Modifier.testTag("edit_reflection")) {
                    Text(stringResource(R.string.edit_reflection))
                }
            }
        }
    }
}

@Composable
private fun MonthlyExpenseDetails(state: MonthlyUiState, details: ExpenseDetails, onBack: () -> Unit) {
    val fixed = details == ExpenseDetails.FIXED
    val amount = if (fixed) state.report!!.balance.fixedExpenses else state.report!!.balance.spent
    val locale = LocalConfiguration.current.locales[0]
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack, modifier = Modifier.padding(horizontal = 12.dp).testTag("monthly_details_back")) {
            Icon(painterResource(R.drawable.ic_hero_chevron_left), null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.balance_back_to_summary))
        }
        LazyColumn(Modifier.weight(1f).testTag("monthly_expense_details"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                SectionCard {
                    Text(stringResource(if (fixed) R.string.fixed_expenses else R.string.balance_from_diary),
                        style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.daily_total, formatMoney(amount)),
                        style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("monthly_details_total"))
                }
            }
            if (fixed) {
                if (state.fixedExpenseItems.isEmpty()) item {
                    SectionCard { Text(stringResource(R.string.balance_fixed_empty)) }
                }
                items(state.fixedExpenseItems.sortedBy { it.dateEpochDay }, key = { it.id }) { item ->
                    SectionCard {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(painterResource(if (item.icon.isBlank()) R.drawable.ic_hero_home else CategoryIcon.fromId(item.icon).drawable),
                                null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(item.title, fontWeight = FontWeight.Medium)
                                Text(LocalDate.ofEpochDay(item.dateEpochDay).format(DateTimeFormatter.ofPattern("d MMM", locale)),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(formatMoney(item.amountGrosz), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                if (state.diaryTransactions.isEmpty()) item {
                    SectionCard { Text(stringResource(R.string.monthly_transactions_empty)) }
                }
                items(state.diaryTransactions.sortedByDescending { it.dateEpochDay }, key = { it.id }) { entry ->
                    MonthlyTransactionRow(entry, state.catalog)
                }
            }
        }
    }
}

@Composable
private fun MonthlyTransactionRow(entry: pl.kakebo.data.local.EntryEntity, catalog: ExpenseCatalog) {
    val locale = LocalConfiguration.current.locales[0]
    val subcategory = entry.subcategory?.let { stringResource(it.label) }
        ?: catalog.subcategories[entry.customSubcategoryId]?.name
        ?: stringResource(R.string.uncategorized)
    val category = catalog.categoryOf(entry)
    val description = if (category == null) subcategory else "${categoryLabel(category, catalog)} · $subcategory"
    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.note.ifBlank { subcategory }, fontWeight = FontWeight.Medium)
                Text(
                    "${LocalDate.ofEpochDay(entry.dateEpochDay).format(DateTimeFormatter.ofPattern("d MMM", locale))} · $description",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatMoney(entry.amountGrosz), fontWeight = FontWeight.SemiBold)
        }
    }
}

internal fun savingsResultLabel(result: SavingsResult): Int = when (result) {
    SavingsResult.NO_INCOME -> R.string.savings_no_income
    SavingsResult.NO_GOAL -> R.string.savings_no_goal
    SavingsResult.REACHED -> R.string.savings_reached
    SavingsResult.PARTIAL -> R.string.savings_partial
    SavingsResult.NOT_REACHED -> R.string.savings_not_reached
}

@Composable
private fun ReflectionText(label: Int, text: String) {
    if (text.isNotBlank()) {
        Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
        Text(text)
    }
}

@Composable
private fun ReflectionEditor(
    reflection: MonthlyReflectionEntity,
    saving: Boolean,
    error: Boolean,
    onDismiss: () -> Unit,
    onSave: (MonthlyReflectionEntity) -> Unit,
) {
    var successes by rememberSaveable(reflection.month) { mutableStateOf(reflection.successes) }
    var improvements by rememberSaveable(reflection.month) { mutableStateOf(reflection.improvements) }
    var nextMonth by rememberSaveable(reflection.month) { mutableStateOf(reflection.nextMonth) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.monthly_reflection)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(successes, { successes = it }, enabled = !saving, minLines = 2,
                    label = { Text(stringResource(R.string.reflection_successes)) }, modifier = Modifier.fillMaxWidth().testTag("reflection_successes"))
                OutlinedTextField(improvements, { improvements = it }, enabled = !saving, minLines = 2,
                    label = { Text(stringResource(R.string.reflection_improvements)) }, modifier = Modifier.fillMaxWidth().testTag("reflection_improvements"))
                OutlinedTextField(nextMonth, { nextMonth = it }, enabled = !saving, minLines = 2,
                    label = { Text(stringResource(R.string.reflection_next_month)) }, modifier = Modifier.fillMaxWidth().testTag("reflection_next_month"))
                if (error) Text(stringResource(R.string.save_error), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                onSave(reflection.copy(successes = successes.trim(), improvements = improvements.trim(), nextMonth = nextMonth.trim()))
            }, modifier = Modifier.testTag("save_reflection")) {
                Text(stringResource(if (saving) R.string.saving else R.string.save))
            }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
