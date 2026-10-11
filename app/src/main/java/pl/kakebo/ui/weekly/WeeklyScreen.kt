package pl.kakebo.ui.weekly

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import pl.kakebo.R
import pl.kakebo.data.local.EntryEntity
import pl.kakebo.domain.CategoryKey
import pl.kakebo.domain.ExpenseCatalog
import pl.kakebo.domain.ExpenseCategory
import pl.kakebo.domain.WeekReport
import pl.kakebo.domain.formatMoney
import pl.kakebo.ui.components.ScreenHeader
import pl.kakebo.ui.components.categoryLabel

@Composable
fun WeeklyScreen(viewModel: WeeklyViewModel, onOpenProfile: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedStart by rememberSaveable(state.month.toString()) { mutableStateOf<Long?>(null) }
    val selectedIndex = state.weeks.indexOfFirst { it.start.toEpochDay() == selectedStart }.takeIf { it >= 0 }
        ?: state.weeks.indexOfFirst { LocalDate.now() in it.start..it.end }.coerceAtLeast(0)
    val week = state.weeks.getOrNull(selectedIndex)
    val locale = LocalConfiguration.current.locales[0]

    Scaffold { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            ScreenHeader(
                stringResource(R.string.weekly_title),
                state.month,
                onPrevious = { viewModel.changeMonth(-1) },
                onNext = { viewModel.changeMonth(1) },
                monthTag = "month",
                onOpenProfile = onOpenProfile,
            )
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                state.loadError -> Column(Modifier.padding(24.dp)) {
                    Text(stringResource(R.string.load_error))
                    TextButton(onClick = viewModel::retry) { Text(stringResource(R.string.retry)) }
                }
                else -> LazyColumn(
                    Modifier.weight(1f).testTag("weekly_list"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        WeekSelector(state.weeks, selectedIndex) { index ->
                            selectedStart = state.weeks[index].start.toEpochDay()
                        }
                    }
                    if (week != null) {
                        item {
                            WeeklySummaryCard(
                                week = week,
                                remaining = week.closingBalance,
                                variableBudget = state.balance!!.remaining + state.balance!!.spent,
                                hasIncomePlan = state.hasIncomePlan,
                                locale = locale,
                            )
                        }
                        item {
                            DailyCategoryChart(
                                week = week,
                                previousWeek = state.weeks.getOrNull(selectedIndex - 1),
                                catalog = state.catalog,
                                locale = locale,
                            )
                        }
                        item { WeeklyCategories(week, state.catalog) }
                        item { WeeklyBudgetDetails(week, state) }
                        item { DailyAmounts(week, locale) }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekSelector(weeks: List<WeekReport>, selectedIndex: Int, onSelected: (Int) -> Unit) {
    val scrollState = rememberScrollState()
    LaunchedEffect(selectedIndex, scrollState.maxValue) {
        if (scrollState.maxValue > 0 && weeks.lastIndex > 0) {
            scrollState.animateScrollTo(scrollState.maxValue * selectedIndex / weeks.lastIndex)
        }
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        weeks.forEachIndexed { index, week ->
            FilterChip(
                selected = index == selectedIndex,
                onClick = { onSelected(index) },
                modifier = Modifier.testTag("week_$index"),
                label = { Text("${week.start.dayOfMonth}–${week.end.dayOfMonth}") },
                shape = RoundedCornerShape(13.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = index == selectedIndex,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = Color.Transparent,
                ),
            )
        }
    }
}

@Composable
private fun WeeklySummaryCard(
    week: WeekReport,
    remaining: Long,
    variableBudget: Long,
    hasIncomePlan: Boolean,
    locale: java.util.Locale,
) {
    val remainingShare = if (hasIncomePlan && variableBudget > 0L) {
        (remaining.toFloat() / variableBudget).coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedRemainingShare by animateFloatAsState(
        targetValue = remainingShare,
        label = "remainingMonthProgress",
    )
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        stringResource(R.string.week_spent),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
                    )
                    Text(
                        formatMoney(week.spent),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.testTag("week_total"),
                    )
                    Text(
                        formatWeekRange(week, locale),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                    )
                    if (week.partial) {
                        Text(
                            stringResource(R.string.partial_week),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.66f),
                        )
                    }
                }
                Box(
                    Modifier.size(54.dp).background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_hero_chart_bar),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            HorizontalDivider(
                Modifier.padding(top = 14.dp, bottom = 12.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.23f),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(30.dp)
                        .background(
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f),
                            RoundedCornerShape(50),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    val progressColor = MaterialTheme.colorScheme.onPrimaryContainer
                    Canvas(Modifier.size(21.dp)) {
                        drawCircle(progressColor.copy(alpha = 0.18f))
                        if (animatedRemainingShare > 0f) {
                            drawArc(
                                color = progressColor,
                                startAngle = -90f,
                                sweepAngle = animatedRemainingShare * 360f,
                                useCenter = true,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    stringResource(R.string.remaining_month),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatMoney(remaining),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.testTag("remaining_month"),
                )
            }
            if (remaining < 0 || !hasIncomePlan) {
                Text(
                    stringResource(if (remaining < 0) R.string.budget_negative else R.string.missing_income_plan),
                    Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun DailyCategoryChart(
    week: WeekReport,
    previousWeek: WeekReport?,
    catalog: ExpenseCatalog,
    locale: java.util.Locale,
) {
    val days = generateSequence(week.start) { it.plusDays(1) }.takeWhile { it <= week.end }.toList()
    val entriesByDay = week.entries.groupBy { it.dateEpochDay }
    val activeCategories = week.categoryTotals.filterValues { it > 0 }.keys.sortedWith(categoryOrder)

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.week_days),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                previousWeek?.let {
                    val difference = week.spent - it.spent
                    val color = when {
                        difference < 0 -> MaterialTheme.colorScheme.primary
                        difference > 0 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
                        Text(
                            stringResource(
                                R.string.weekly_comparison_short,
                                (if (difference > 0) "+" else "") + formatMoney(difference),
                            ),
                            Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            maxLines = 1,
                        )
                    }
                }
            }
            StackedDailyBars(days, entriesByDay, catalog, locale)
            if (activeCategories.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    activeCategories.forEach { category ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(9.dp).background(
                                    weeklyCategoryColor(category), RoundedCornerShape(50)),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                weeklyLegendLabel(category, catalog),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (week.entries.isEmpty()) {
                Text(
                    stringResource(R.string.no_week_expenses),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StackedDailyBars(
    days: List<LocalDate>,
    entriesByDay: Map<Long, List<EntryEntity>>,
    catalog: ExpenseCatalog,
    locale: java.util.Locale,
) {
    val breakdowns = days.associateWith { day ->
        catalog.totals(entriesByDay[day.toEpochDay()].orEmpty()).filterValues { it > 0 }
    }
    val maxTotal = breakdowns.values.maxOfOrNull { values -> values.values.sum() }?.coerceAtLeast(1L) ?: 1L

    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { day ->
            val breakdown = breakdowns.getValue(day)
            val total = breakdown.values.sum()
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(Modifier.height(96.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    if (total == 0L) {
                        Box(
                            Modifier.width(26.dp).height(3.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)),
                        )
                    } else {
                        val height = maxOf(9f, total.toFloat() / maxTotal * 96f).dp
                        Column(
                            Modifier.width(26.dp).height(height).clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)),
                        ) {
                            breakdown.entries.sortedWith(compareByDescending { categoryRank(it.key) }).forEach { (category, amount) ->
                                Box(
                                    Modifier.fillMaxWidth().weight(amount.toFloat())
                                        .background(weeklyCategoryColor(category)),
                                )
                            }
                        }
                    }
                }
                Text(
                    day.format(DateTimeFormatter.ofPattern("EEE", locale)).removeSuffix(".")
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun WeeklyCategories(week: WeekReport, catalog: ExpenseCatalog) {
    val categories = week.categoryTotals.filterValues { it > 0 }.entries.sortedWith(compareBy { categoryRank(it.key) })
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.week_categories), style = MaterialTheme.typography.titleLarge)
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (categories.isEmpty()) {
                    Text(
                        stringResource(R.string.no_week_expenses),
                        Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    categories.forEachIndexed { index, (category, amount) ->
                        WeeklyCategoryRow(category, amount, week.spent, catalog)
                        if (index < categories.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    week.categoryTotals.maxByOrNull { it.value }?.let { top ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            stringResource(
                                R.string.top_category,
                                categoryLabel(top.key, catalog),
                                formatMoney(top.value),
                            ),
                            Modifier.padding(top = 11.dp, bottom = 7.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyCategoryRow(category: CategoryKey?, amount: Long, total: Long, catalog: ExpenseCatalog) {
    val color = weeklyCategoryColor(category)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(36.dp).background(color.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(weeklyCategoryIcon(category)),
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = color,
            )
        }
        Text(
            categoryLabel(category, catalog),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(formatMoney(amount), style = MaterialTheme.typography.titleSmall, maxLines = 1)
        Text(
            "${if (total > 0) amount * 100 / total else 0}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WeeklyBudgetDetails(week: WeekReport, state: WeeklyUiState) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.weekly_budget_details), style = MaterialTheme.typography.titleMedium)
            AmountRow(stringResource(R.string.opening_balance), week.openingBalance)
            AmountRow(stringResource(R.string.closing_balance), week.closingBalance, emphasized = true)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            AmountRow(stringResource(R.string.planned_income_label), state.balance!!.income)
            AmountRow(stringResource(R.string.savings_goal), state.balance.savings)
            AmountRow(stringResource(R.string.fixed_expenses), state.balance.fixedExpenses)
            AmountRow(stringResource(R.string.other_expenses), state.balance.spent)
            Text(
                stringResource(R.string.weekly_balance_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DailyAmounts(week: WeekReport, locale: java.util.Locale) {
    val entriesByDay = week.entries.groupBy { it.dateEpochDay }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.weekly_day_details), style = MaterialTheme.typography.titleMedium)
            generateSequence(week.start) { it.plusDays(1) }.takeWhile { it <= week.end }.forEach { day ->
                AmountRow(
                    day.format(DateTimeFormatter.ofPattern("EEE, dd.MM", locale)),
                    entriesByDay[day.toEpochDay()].orEmpty().sumOf { it.amountGrosz },
                )
            }
        }
    }
}

@Composable
private fun AmountRow(label: String, amount: Long, emphasized: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            label,
            style = if (emphasized) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            color = if (emphasized) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            formatMoney(amount),
            style = if (emphasized) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

private fun formatWeekRange(week: WeekReport, locale: java.util.Locale): String =
    if (week.start.month == week.end.month) {
        "${week.start.dayOfMonth}–${week.end.format(DateTimeFormatter.ofPattern("d MMMM", locale))}"
    } else {
        "${week.start.format(DateTimeFormatter.ofPattern("d MMM", locale))} – ${week.end.format(DateTimeFormatter.ofPattern("d MMM", locale))}"
    }

private val categoryOrder = Comparator<CategoryKey?> { first, second -> categoryRank(first) - categoryRank(second) }

private fun categoryRank(category: CategoryKey?): Int = when (category?.builtIn) {
    ExpenseCategory.NEEDS -> 0
    ExpenseCategory.OPTIONAL -> 1
    ExpenseCategory.HOBBY -> 2
    ExpenseCategory.EXTRA -> 3
    null -> category?.customId?.let { 4 + (it % 1000).toInt() } ?: Int.MAX_VALUE
}

@Composable
private fun weeklyCategoryColor(category: CategoryKey?): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (category?.builtIn) {
        ExpenseCategory.NEEDS -> if (dark) Color(0xFFFF9D92) else Color(0xFFD65F59)
        ExpenseCategory.OPTIONAL -> if (dark) Color(0xFFF0C46F) else Color(0xFFC28B32)
        ExpenseCategory.HOBBY -> if (dark) Color(0xFFBBA0FF) else Color(0xFF7658C7)
        ExpenseCategory.EXTRA -> if (dark) Color(0xFF76B7FF) else Color(0xFF3F83D5)
        null -> category?.customId?.let {
            listOf(Color(0xFFCF7EAD), Color(0xFF4E9B91), Color(0xFFB16B4D))[(it % 3).toInt()]
        } ?: if (dark) Color(0xFF72C7F4) else Color(0xFF287FB8)
    }
}

@Composable
private fun weeklyLegendLabel(category: CategoryKey?, catalog: ExpenseCatalog): String = when (category?.builtIn) {
    ExpenseCategory.NEEDS -> stringResource(R.string.week_legend_needs)
    ExpenseCategory.OPTIONAL -> stringResource(R.string.week_legend_optional)
    ExpenseCategory.HOBBY -> stringResource(R.string.week_legend_hobby)
    ExpenseCategory.EXTRA -> stringResource(R.string.week_legend_extra)
    null -> categoryLabel(category, catalog)
}

private fun weeklyCategoryIcon(category: CategoryKey?): Int = when (category?.builtIn) {
    ExpenseCategory.NEEDS -> R.drawable.ic_hero_shopping_cart
    ExpenseCategory.OPTIONAL -> R.drawable.ic_hero_shopping_bag
    ExpenseCategory.HOBBY -> R.drawable.ic_hero_book_open
    ExpenseCategory.EXTRA -> R.drawable.ic_hero_sparkles
    null -> if (category?.customId != null) R.drawable.ic_hero_squares_2x2 else R.drawable.ic_hero_wallet
}
