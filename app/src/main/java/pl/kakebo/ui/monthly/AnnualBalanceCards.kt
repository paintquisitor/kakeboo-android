package pl.kakebo.ui.monthly

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import pl.kakebo.R
import pl.kakebo.domain.*
import pl.kakebo.ui.components.SectionCard
import pl.kakebo.ui.components.categoryLabel
import kotlin.math.abs

@Composable
internal fun AnnualContent(report: YearlyReport, catalog: ExpenseCatalog, onMonthSelected: (YearMonth) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val monthFormat = DateTimeFormatter.ofPattern("LLLL", locale)
    var categories by rememberSaveable(report.year) { mutableStateOf(false) }
    var fixed by rememberSaveable(report.year) { mutableStateOf(false) }
    var insights by rememberSaveable(report.year) { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().testTag("annual_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { AnnualSummaryCard(report) }
        item { AnnualSavingsCard(report) }
        item { AnnualMonthsCard(report, onMonthSelected) }
        item {
            AnnualDisclosure(stringResource(R.string.annual_categories_title), R.drawable.ic_hero_chart_pie,
                "annual_categories", categories, { categories = !categories })
        }
        if (categories) item { BalanceCategoriesCard(report.categoryTotals, catalog) }
        item {
            AnnualDisclosure(stringResource(R.string.annual_fixed_title), R.drawable.ic_hero_home,
                "annual_fixed", fixed, { fixed = !fixed })
        }
        if (fixed) item {
            SectionCard {
                Text(stringResource(R.string.annual_fixed_hint), style = MaterialTheme.typography.bodySmall)
                if (report.fixedExpenses.isEmpty()) Text(stringResource(R.string.annual_no_data))
                report.fixedExpenses.forEachIndexed { index, expense ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(expense.title, style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.annual_occurrences, expense.occurrences),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(formatMoney(expense.total), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
        item {
            AnnualDisclosure(stringResource(R.string.annual_year_insights), R.drawable.ic_hero_sparkles,
                "annual_insights", insights, { insights = !insights })
        }
        if (insights) item {
            SectionCard {
                if (report.monthsWithExpenses.isEmpty()) Text(stringResource(R.string.annual_no_data))
                report.mostExpensiveMonth?.let { Text(stringResource(R.string.annual_most_expensive, it.month.format(monthFormat))) }
                report.leastExpensiveMonth?.let { Text(stringResource(R.string.annual_least_expensive, it.month.format(monthFormat))) }
                report.categoryTotals.maxByOrNull { it.value }?.let {
                    Text(stringResource(R.string.annual_dominant_category, categoryLabel(it.key, catalog)))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(stringResource(R.string.annual_reflection_prompt), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun AnnualSummaryCard(report: YearlyReport) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val foreground = if (dark) Color(0xFFEAF7F1) else Color(0xFF173E33)
    val now = YearMonth.now()
    val format = DateTimeFormatter.ofPattern("LLLL", LocalConfiguration.current.locales[0])
    val lastMonth = if (report.year == now.year) now.monthValue else 12
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(contentColor = foreground)) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(
            if (dark) listOf(Color(0xFF24473C), Color(0xFF203A32)) else listOf(Color(0xFFDFF3E9), Color(0xFFEBF8F1))
        )).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.left_from_income), style = MaterialTheme.typography.bodyMedium)
                    Text(formatMoney(report.savings), Modifier.testTag("annual_left"),
                        style = MaterialTheme.typography.headlineLarge,
                        color = if (report.savings < 0) colors.error else foreground)
                }
                Box(Modifier.size(44.dp).background(foreground.copy(alpha = 0.07f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center) { BalanceIcon(R.drawable.ic_hero_wallet) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (report.year > now.year) report.year.toString() else
                    "${YearMonth.of(report.year, 1).format(format)} – ${YearMonth.of(report.year, lastMonth).format(format)}",
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                if (report.year >= now.year) Surface(color = colors.primary.copy(alpha = 0.12f), shape = CircleShape) {
                    Text(stringResource(if (report.year == now.year) R.string.annual_in_progress else R.string.annual_future),
                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = foreground)
                }
            }
            HorizontalDivider(color = foreground.copy(alpha = 0.12f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BalanceMetric(stringResource(R.string.income), report.income, R.drawable.ic_hero_arrow_down_circle,
                    if (dark) Color(0xFF84E0BC) else Color(0xFF247D68), Modifier.weight(1f))
                BalanceMetric(stringResource(R.string.balance_expenses), report.totalExpenses, R.drawable.ic_hero_arrow_up_circle,
                    if (dark) Color(0xFFFFAA9E) else Color(0xFFBA514C), Modifier.weight(1f))
            }
            HorizontalDivider(color = foreground.copy(alpha = 0.12f))
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                BalanceBreakdown(R.drawable.ic_hero_home, stringResource(R.string.fixed_expenses), report.fixedExpenseTotal)
                HorizontalDivider(Modifier.padding(start = 36.dp), color = foreground.copy(alpha = 0.12f))
                BalanceBreakdown(R.drawable.ic_hero_receipt_percent, stringResource(R.string.balance_from_diary), report.diaryExpenses)
            }
        }
    }
}

@Composable
private fun AnnualSavingsCard(report: YearlyReport) {
    val now = YearMonth.now()
    val locale = LocalConfiguration.current.locales[0]
    val progress = if (report.savingsGoal > 0 && report.income > 0)
        (report.savings.toDouble() / report.savingsGoal).coerceIn(0.0, 1.0).toFloat() else 0f
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BalanceIcon(R.drawable.ic_hero_target, MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (report.year == now.year) stringResource(R.string.annual_goal_through,
                    now.format(DateTimeFormatter.ofPattern("MMMM", locale))) else stringResource(R.string.annual_savings_goal),
                    style = MaterialTheme.typography.titleMedium)
                if (report.savingsGoal > 0) Text(stringResource(R.string.balance_goal_amounts,
                    formatMoney(report.savings), formatMoney(report.savingsGoal)), style = MaterialTheme.typography.bodySmall)
            }
            if (report.savingsGoal > 0) Text(NumberFormat.getPercentInstance(locale).format(progress),
                style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (report.savingsGoal > 0 && report.income > 0) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(12.dp),
                color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant, drawStopIndicator = {})
            val difference = report.savings - report.savingsGoal
            Text(stringResource(if (difference < 0) R.string.balance_goal_shortfall else R.string.balance_goal_surplus,
                formatMoney(abs(difference))), style = MaterialTheme.typography.bodySmall)
        } else Text(stringResource(savingsResultLabel(report.savingsResult)), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AnnualMonthsCard(report: YearlyReport, onMonthSelected: (YearMonth) -> Unit) {
    var expanded by rememberSaveable(report.year) { mutableStateOf(false) }
    val now = YearMonth.now()
    val locale = LocalConfiguration.current.locales[0]
    val format = DateTimeFormatter.ofPattern("LLLL", locale)
    val amounts = report.months.map { if (it.month > now) 0L else it.report.leftFromIncome }
    val positiveMax = amounts.maxOrNull()?.coerceAtLeast(0)?.toDouble() ?: 0.0
    val negativeMax = amounts.minOrNull()?.coerceAtMost(0)?.toDouble()?.let { -it } ?: 0.0
    val range = (positiveMax + negativeMax).coerceAtLeast(1.0)
    val baseline = if (positiveMax + negativeMax == 0.0) 1f else (positiveMax / range).toFloat()
    val positive = MaterialTheme.colorScheme.primary
    val negative = MaterialTheme.colorScheme.error
    val outline = MaterialTheme.colorScheme.outlineVariant
    val recent = report.months.filter { it.month <= now }.takeLast(3).reversed().ifEmpty { report.months.take(3) }
    SectionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BalanceIcon(R.drawable.ic_hero_chart_bar, MaterialTheme.colorScheme.primary)
            Column {
                Text(stringResource(R.string.annual_months), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.left_from_income), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            report.months.forEachIndexed { index, month ->
                val amount = amounts[index]
                val description = "${month.month.format(format)}: ${formatMoney(amount)}"
                Column(Modifier.weight(1f).testTag("annual_bar_${month.month}")
                    .semantics(mergeDescendants = true) { contentDescription = description }
                    .clickable { onMonthSelected(month.month) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.fillMaxWidth().height(96.dp).clearAndSetSemantics {}) {
                        val zero = size.height * baseline
                        drawLine(outline, Offset(0f, zero), Offset(size.width, zero), 1.dp.toPx())
                        if (amount != 0L) {
                            val height = (abs(amount.toDouble()) / range * size.height).toFloat()
                            drawRoundRect(if (amount > 0) positive else negative,
                                topLeft = Offset(size.width * 0.12f, if (amount > 0) zero - height else zero),
                                size = Size(size.width * 0.76f, height),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                        }
                    }
                    Text(month.month.monthValue.toString().padStart(2, '0'), Modifier.padding(top = 8.dp).clearAndSetSemantics {},
                        style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                        color = if (month.month > now) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Text(stringResource(R.string.annual_months_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        (if (expanded) report.months else recent).forEach { month ->
            HorizontalDivider(color = outline)
            Row(Modifier.fillMaxWidth().testTag("annual_month_${month.month}").clickable { onMonthSelected(month.month) }
                .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(month.month.format(format).replaceFirstChar { it.titlecase(locale) }, Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium)
                val amount = if (month.month > now) 0L else month.report.leftFromIncome
                Text(formatMoney(amount), style = MaterialTheme.typography.titleSmall,
                    color = if (amount < 0) negative else MaterialTheme.colorScheme.onSurface)
                BalanceIcon(R.drawable.ic_hero_chevron_right, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().testTag("annual_months_toggle")) {
            Text(stringResource(if (expanded) R.string.annual_fewer_months else R.string.annual_all_months))
        }
    }
}

@Composable
private fun AnnualDisclosure(title: String, icon: Int, tag: String, expanded: Boolean, onToggle: () -> Unit) {
    val label = stringResource(if (expanded) R.string.balance_expanded else R.string.balance_collapsed)
    Card(onClick = onToggle, modifier = Modifier.fillMaxWidth().testTag(tag).semantics { stateDescription = label },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BalanceIcon(icon, MaterialTheme.colorScheme.primary)
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            BalanceIcon(if (expanded) R.drawable.ic_hero_chevron_up else R.drawable.ic_hero_chevron_down)
        }
    }
}
