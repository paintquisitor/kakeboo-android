package pl.kakebo.ui.monthly

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import pl.kakebo.R
import pl.kakebo.domain.CategoryKey
import pl.kakebo.domain.ExpenseCatalog
import pl.kakebo.domain.ExpenseCategory
import pl.kakebo.domain.MonthlyReport
import pl.kakebo.domain.WeekReport
import pl.kakebo.domain.formatMoney
import pl.kakebo.ui.components.SectionCard
import pl.kakebo.ui.components.categoryLabel

@Composable
internal fun MonthlySummaryCard(state: MonthlyUiState, onFixedExpenses: () -> Unit, onDiaryExpenses: () -> Unit) {
    val report = requireNotNull(state.report)
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val foreground = if (dark) Color(0xFFEAF7F1) else Color(0xFF173E33)
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(contentColor = foreground),
    ) {
        Column(
            Modifier.fillMaxWidth().background(Brush.linearGradient(
                if (dark) listOf(Color(0xFF24473C), Color(0xFF203A32))
                else listOf(Color(0xFFDFF3E9), Color(0xFFEBF8F1)),
            )).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.left_from_income), style = MaterialTheme.typography.bodyMedium)
                    Text(formatMoney(report.leftFromIncome), style = MaterialTheme.typography.headlineLarge,
                        color = if (report.leftFromIncome < 0) colors.error else foreground,
                        modifier = Modifier.testTag("monthly_left"))
                }
                Box(Modifier.size(44.dp).background(foreground.copy(alpha = 0.07f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center) { BalanceIcon(R.drawable.ic_hero_wallet) }
            }
            if (state.month >= YearMonth.now()) {
                Surface(color = colors.primary.copy(alpha = 0.12f), shape = CircleShape,
                    modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(if (state.month == YearMonth.now()) R.string.balance_month_in_progress else R.string.balance_future_month),
                        Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall, color = foreground)
                }
            }
            HorizontalDivider(color = foreground.copy(alpha = 0.12f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BalanceMetric(stringResource(R.string.income), report.balance.income,
                    R.drawable.ic_hero_arrow_down_circle, if (dark) Color(0xFF84E0BC) else Color(0xFF247D68), Modifier.weight(1f))
                BalanceMetric(stringResource(R.string.balance_expenses), report.totalExpenses,
                    R.drawable.ic_hero_arrow_up_circle, if (dark) Color(0xFFFFAA9E) else Color(0xFFBA514C), Modifier.weight(1f))
            }
            HorizontalDivider(color = foreground.copy(alpha = 0.12f))
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                BalanceBreakdown(R.drawable.ic_hero_home, stringResource(R.string.fixed_expenses), report.balance.fixedExpenses,
                    Modifier.testTag("monthly_open_fixed"), onFixedExpenses)
                HorizontalDivider(Modifier.padding(start = 36.dp), color = foreground.copy(alpha = 0.12f))
                BalanceBreakdown(R.drawable.ic_hero_receipt_percent, stringResource(R.string.balance_from_diary), report.balance.spent,
                    Modifier.testTag("monthly_open_diary"), onDiaryExpenses)
            }
            if (report.leftFromIncome < 0) Text(stringResource(R.string.monthly_deficit), style = MaterialTheme.typography.bodySmall)
            if (!report.hasIncome) Text(stringResource(R.string.missing_income_plan), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun BalanceMetric(label: String, amount: Long, icon: Int, tint: Color, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BalanceIcon(icon, tint)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(formatMoney(amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun BalanceBreakdown(icon: Int, label: String, amount: Long, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick).heightIn(min = 48.dp) else Modifier), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BalanceIcon(icon)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(formatMoney(amount), style = MaterialTheme.typography.bodyMedium)
        if (onClick != null) Icon(painterResource(R.drawable.ic_hero_chevron_right), null, Modifier.size(16.dp))
    }
}

@Composable
internal fun MonthlySavingsCard(report: MonthlyReport) {
    val goal = report.balance.savings
    val progress = if (goal > 0 && report.hasIncome) (report.leftFromIncome.toDouble() / goal).coerceIn(0.0, 1.0).toFloat() else 0f
    val percent = NumberFormat.getPercentInstance(LocalConfiguration.current.locales[0]).format(progress)
    SectionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BalanceIcon(R.drawable.ic_hero_target, MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.balance_savings_goal), style = MaterialTheme.typography.titleMedium)
                if (goal > 0 && report.hasIncome) Text(
                    stringResource(R.string.balance_goal_amounts, formatMoney(report.leftFromIncome), formatMoney(goal)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (goal > 0 && report.hasIncome) Text(percent, style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary)
        }
        if (goal > 0 && report.hasIncome) {
            LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(8.dp).testTag("monthly_savings_progress"),
                color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant,
                drawStopIndicator = {})
            Text(stringResource(if (report.balance.remaining < 0) R.string.balance_goal_shortfall else R.string.balance_goal_surplus,
                formatMoney(kotlin.math.abs(report.balance.remaining))), style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(savingsResultLabel(report.savingsResult)), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("savings_result"))
    }
}

@Composable
internal fun MonthlyCategoriesCard(report: MonthlyReport, catalog: ExpenseCatalog) {
    BalanceCategoriesCard(report.categoryTotals, catalog)
}

@Composable
internal fun BalanceCategoriesCard(totals: Map<CategoryKey?, Long>, catalog: ExpenseCatalog) {
    val entries = totals.entries.filter { it.value > 0 }.sortedByDescending { it.value }
    val total = entries.sumOf { it.value }
    val colors = entries.map { balanceCategoryColor(it.key) }
    val percent = NumberFormat.getPercentInstance(LocalConfiguration.current.locales[0]).apply { maximumFractionDigits = 1 }
    SectionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BalanceIcon(R.drawable.ic_hero_chart_pie, MaterialTheme.colorScheme.primary)
            Column {
                Text(stringResource(R.string.monthly_categories), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.annual_diary_expenses), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (total == 0L) {
            Text(stringResource(R.string.balance_no_category_expenses), style = MaterialTheme.typography.bodyMedium)
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stacked = maxWidth < 280.dp || LocalConfiguration.current.fontScale > 1.15f
                val ring: @Composable () -> Unit = {
                    Box(Modifier.size(124.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val stroke = 16.dp.toPx()
                            var start = -90f
                            entries.forEachIndexed { index, entry ->
                                val sweep = (entry.value.toDouble() / total * 360).toFloat()
                                drawArc(colors[index], start, sweep, false, Offset(stroke / 2, stroke / 2),
                                    Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
                                start += sweep
                            }
                        }
                        Text(formatMoney(total), Modifier.width(86.dp), style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center)
                    }
                }
                val legend: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        entries.forEachIndexed { index, entry ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                                Box(Modifier.padding(top = 5.dp).size(9.dp).background(colors[index], CircleShape))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(categoryLabel(entry.key, catalog), style = MaterialTheme.typography.bodySmall)
                                    Text(formatMoney(entry.value), style = MaterialTheme.typography.titleSmall)
                                    Text(percent.format(entry.value.toDouble() / total), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                if (stacked) Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ring()
                    legend()
                } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    ring()
                    Box(Modifier.weight(1f)) { legend() }
                }
            }
        }
    }
}

@Composable
internal fun MonthlyWeeksCard(weeks: List<WeekReport>, expanded: Boolean, onToggle: () -> Unit) {
    val stateLabel = stringResource(if (expanded) R.string.balance_expanded else R.string.balance_collapsed)
    val dateFormat = DateTimeFormatter.ofPattern("d MMM", LocalConfiguration.current.locales[0])
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().testTag("monthly_weeks_toggle")
            .semantics { stateDescription = stateLabel }.clickable(onClick = onToggle).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BalanceIcon(R.drawable.ic_hero_chart_bar, MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.weekly_balance), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            BalanceIcon(if (expanded) R.drawable.ic_hero_chevron_up else R.drawable.ic_hero_chevron_down)
        }
        if (expanded) Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
            .testTag("monthly_weeks_details"), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(stringResource(R.string.monthly_weekly_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            weeks.forEachIndexed { index, week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.week_number, index + 1), style = MaterialTheme.typography.titleSmall)
                        Text("${week.start.format(dateFormat)} – ${week.end.format(dateFormat)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(formatMoney(week.spent), style = MaterialTheme.typography.titleSmall)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            BalanceBreakdown(R.drawable.ic_hero_receipt_percent, stringResource(R.string.balance_from_diary), weeks.sumOf { it.spent })
        }
    }
}

@Composable
internal fun MonthlyTransactionsHeader(count: Int, expanded: Boolean, onClick: () -> Unit) {
    val stateLabel = stringResource(if (expanded) R.string.balance_expanded else R.string.balance_collapsed)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("monthly_transactions_toggle")
        .semantics { stateDescription = stateLabel },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BalanceIcon(R.drawable.ic_hero_receipt_percent, MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.balance_transactions), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                Text(count.toString(), Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            BalanceIcon(if (expanded) R.drawable.ic_hero_chevron_up else R.drawable.ic_hero_chevron_down)
        }
    }
}

@Composable
internal fun BalanceIcon(icon: Int, tint: Color = LocalContentColor.current) {
    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp), tint = tint)
}

@Composable
private fun balanceCategoryColor(category: CategoryKey?): Color {
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
