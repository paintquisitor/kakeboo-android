package pl.kakebo.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import pl.kakebo.ui.components.*
import pl.kakebo.R
import pl.kakebo.data.local.EntryType
import pl.kakebo.data.local.PlanRecurrence
import pl.kakebo.data.local.PlannedItemEntity
import pl.kakebo.domain.FixedExpensePreset
import pl.kakebo.domain.CategoryIcon
import pl.kakebo.domain.formatMoney
import pl.kakebo.domain.isDueIn
import pl.kakebo.domain.nextDueMonth
import pl.kakebo.domain.totals

@Composable
fun HomeScreen(viewModel: HomeViewModel, onOpenProfile: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val saveError by viewModel.saveError.collectAsStateWithLifecycle()
    // Dialog i jego pola przetrwają obrót ekranu oraz odtworzenie procesu.
    var editorType by rememberSaveable(state.month.toString()) { mutableStateOf<String?>(null) }
    var editedId by rememberSaveable(state.month.toString()) { mutableStateOf<Long?>(null) }
    var editGoals by rememberSaveable(state.month.toString()) { mutableStateOf(false) }
    val closeEditor = { editorType = null; editedId = null; viewModel.clearSaveError() }
    val data = state.data

    Scaffold { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            ScreenHeader(
                title = stringResource(R.string.plan_month),
                month = state.month,
                onPrevious = { viewModel.changeMonth(-1) },
                onNext = { viewModel.changeMonth(1) },
                onOpenProfile = onOpenProfile,
                enabled = !saving,
                monthTag = "selected_month",
            )
            when {
                state.loading -> CircularProgressIndicator(Modifier.padding(24.dp))
                state.loadError -> Column(Modifier.padding(24.dp)) {
                    Text(stringResource(R.string.load_error))
                    Button(onClick = viewModel::retry) { Text(stringResource(R.string.retry)) }
                }
                data != null -> {
                    val totals = data.totals()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag("plan_list"),
                        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            BudgetOverview(
                                available = totals.available,
                                savings = totals.savings,
                                isFuture = state.month > YearMonth.now(),
                            )
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PlanMetric(
                                    stringResource(R.string.income), totals.income,
                                    MaterialTheme.colorScheme.primary, R.drawable.ic_hero_arrow_down_circle,
                                    Modifier.weight(1f),
                                )
                                PlanMetric(
                                    stringResource(R.string.fixed_expenses), totals.fixedExpenses,
                                    MaterialTheme.colorScheme.error, R.drawable.ic_hero_arrow_up_circle,
                                    Modifier.weight(1f),
                                )
                            }
                        }
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().testTag("edit_goals").clickable {
                                    viewModel.clearSaveError()
                                    editGoals = true
                                },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(20.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    IconTile(R.drawable.ic_hero_target, MaterialTheme.colorScheme.tertiary)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(stringResource(R.string.goal_this_month), style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            data.plan.resolutions.ifBlank { stringResource(R.string.goals_empty) },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Text(
                                        stringResource(R.string.edit),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                        listOf(EntryType.EXPENSE, EntryType.INCOME).forEach { type ->
                            val sectionItems = data.items.filter { it.type == type }
                                .sortedWith(compareBy({ it.dateEpochDay }, { it.id }))
                            item(key = type.name) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        stringResource(if (type == EntryType.INCOME) R.string.income else R.string.fixed_expenses),
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.weight(1f),
                                    )
                                    val addLabel = stringResource(if (type == EntryType.INCOME) R.string.add_income else R.string.add_expense)
                                    IconButton(onClick = { viewModel.clearSaveError(); editedId = null; editorType = type.name }) {
                                        Icon(
                                            painterResource(R.drawable.ic_hero_plus_circle),
                                            addLabel,
                                            Modifier.size(22.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                            if (sectionItems.isEmpty()) item {
                                Text(
                                    stringResource(R.string.no_planned_items),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            items(sectionItems, key = { it.id }) { item ->
                                PlannedItemRow(item, state.month) {
                                    viewModel.clearSaveError()
                                    editedId = item.id
                                    editorType = item.type.name
                                }
                            }
                            item(key = "add_${type.name}") {
                                Button(
                                    onClick = { viewModel.clearSaveError(); editedId = null; editorType = type.name },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Icon(painterResource(R.drawable.ic_hero_plus), null, Modifier.size(19.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(if (type == EntryType.INCOME) R.string.add_income else R.string.add_expense))
                                }
                            }
                        }
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Text(
                                    stringResource(R.string.plan_explanation),
                                    Modifier.padding(14.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        item {
                            Text(
                                stringResource(R.string.local_storage),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (data != null && editorType != null) {
        ItemEditor(
            month = state.month,
            type = EntryType.valueOf(editorType!!),
            item = data.items.find { it.id == editedId },
            saving = saving, saveError = saveError,
            onDismiss = closeEditor,
            onSave = { viewModel.saveItem(it, closeEditor) },
            onDelete = { viewModel.deleteItem(it, closeEditor) },
        )
    }
    if (data != null && editGoals) {
        GoalsEditor(
            plan = data.plan, saving = saving, saveError = saveError,
            onDismiss = { editGoals = false; viewModel.clearSaveError() },
            onSave = { viewModel.savePlan(it) { editGoals = false } },
        )
    }
}

@Composable
private fun recurrenceLabel(recurrence: PlanRecurrence): String = stringResource(when (recurrence) {
    PlanRecurrence.MONTHLY -> R.string.recurrence_monthly
    PlanRecurrence.QUARTERLY -> R.string.recurrence_quarterly
    PlanRecurrence.YEARLY -> R.string.recurrence_yearly
})

@Composable
private fun SummaryRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium, color = valueColor)
    }
}

@Composable
private fun BudgetOverview(available: Long, savings: Long, isFuture: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        if (isFuture) R.string.available_budget_future_short
                        else R.string.available_budget_short,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    painterResource(R.drawable.ic_hero_wallet),
                    null,
                    Modifier.size(30.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                formatMoney(available),
                modifier = Modifier.testTag("available_budget"),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = if (available < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                stringResource(R.string.budget_formula_short),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
            )
            if (available < 0) {
                Text(stringResource(R.string.budget_negative), color = MaterialTheme.colorScheme.error)
            }
            HorizontalDivider(
                Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    painterResource(R.drawable.ic_hero_target),
                    null,
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                SummaryRow(
                    stringResource(R.string.savings_goal_plan),
                    formatMoney(savings),
                    valueColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun PlanMetric(label: String, amount: Long, color: Color, icon: Int, modifier: Modifier) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(painterResource(icon), null, Modifier.size(34.dp), tint = color)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatMoney(amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            }
        }
    }
}

@Composable
private fun IconTile(icon: Int, color: Color, size: Int = 44) {
    Box(
        Modifier.size(size.dp).background(color.copy(alpha = 0.12f), RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), null, Modifier.size((size * 0.52f).dp), tint = color)
    }
}

@Composable
private fun PlannedItemRow(item: PlannedItemEntity, month: java.time.YearMonth, onClick: () -> Unit) {
    val due = item.isDueIn(month)
    val amountColor = if (item.type == EntryType.INCOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val locale = LocalConfiguration.current.locales[0]
    val date = LocalDate.ofEpochDay(item.dateEpochDay).format(DateTimeFormatter.ofPattern("d MMM", locale))
    val detail = if (!due) {
        val next = item.nextDueMonth(month).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        "${stringResource(R.string.not_due_this_month)} · ${stringResource(R.string.next_payment, next)}"
    } else {
        "$date · ${recurrenceLabel(item.recurrence)}"
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconTile(plannedItemIcon(item), MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                formatMoney(item.amountGrosz),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (due) amountColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun plannedItemIcon(item: PlannedItemEntity): Int {
    if (item.type == EntryType.INCOME) return R.drawable.ic_hero_banknotes
    if (item.icon.isNotBlank()) return CategoryIcon.fromId(item.icon).drawable
    val locale = LocalConfiguration.current.locales[0]
    val title = item.title.lowercase(locale)
    FixedExpensePreset.entries.forEach { preset ->
        val presetTitle = stringResource(preset.label).lowercase(locale)
        if (!preset.custom && (presetTitle == title || (title.length >= 4 && presetTitle.contains(title)))) {
            return preset.icon.drawable
        }
    }
    if ("czynsz" in title || "rent" in title || "mieszkan" in title) return R.drawable.ic_hero_home
    if ("telefon" in title || "internet" in title || "phone" in title) return R.drawable.ic_hero_wifi
    if ("sport" in title || "gym" in title || "siłown" in title) return R.drawable.ic_hero_trophy
    return R.drawable.ic_hero_receipt_percent
}
