package pl.kakebo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import pl.kakebo.R
import pl.kakebo.domain.ExpenseCategory
import pl.kakebo.domain.CategoryKey
import pl.kakebo.domain.ExpenseCatalog
import pl.kakebo.domain.formatMoney

@Composable
fun ScreenHeader(title: String, month: YearMonth? = null, onPrevious: () -> Unit = {}, onNext: () -> Unit = {},
    enabled: Boolean = true, monthTag: String = "month", periodLabel: String? = null,
    previousDescription: Int = R.string.previous_month, nextDescription: Int = R.string.next_month,
    onOpenProfile: () -> Unit = {}) {
    val locale = LocalConfiguration.current.locales[0]
    Column(
        Modifier.padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.brand_name),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                letterSpacing = (-0.5).sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                stringResource(R.string.app_japanese_name),
                Modifier.padding(start = 9.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenProfile, modifier = Modifier.size(36.dp).testTag("open_profile")) {
                Icon(painterResource(R.drawable.ic_hero_cog_6_tooth),
                    stringResource(R.string.profile_tab), Modifier.size(21.dp))
            }
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (month != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious, enabled = enabled, modifier = Modifier.size(36.dp)) {
                    Icon(painterResource(R.drawable.ic_hero_chevron_left), stringResource(previousDescription), Modifier.size(18.dp))
                }
                Text(periodLabel ?: month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() },
                    Modifier.weight(1f).testTag(monthTag), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                IconButton(onClick = onNext, enabled = enabled, modifier = Modifier.size(36.dp)) {
                    Icon(painterResource(R.drawable.ic_hero_chevron_right), stringResource(nextDescription), Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun categoryIcon(category: ExpenseCategory?): Int = when (category) {
    ExpenseCategory.NEEDS -> R.drawable.ic_hero_shopping_cart
    ExpenseCategory.OPTIONAL -> R.drawable.ic_hero_shopping_bag
    ExpenseCategory.HOBBY -> R.drawable.ic_hero_musical_note
    ExpenseCategory.EXTRA -> R.drawable.ic_hero_sparkles
    null -> R.drawable.ic_hero_wallet
}

@Composable
fun categoryColor(category: CategoryKey?): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val palette = category?.builtIn ?: category?.customId?.let { ExpenseCategory.entries[(it % ExpenseCategory.entries.size).toInt()] }
    return when (palette) {
        ExpenseCategory.NEEDS -> if (dark) Color(0xFF98D8BE) else Color(0xFF247D68)
        ExpenseCategory.OPTIONAL -> if (dark) Color(0xFFD3B27C) else Color(0xFF9A713B)
        ExpenseCategory.HOBBY -> if (dark) Color(0xFFD5B9E7) else Color(0xFF7C5792)
        ExpenseCategory.EXTRA -> if (dark) Color(0xFFA9CAE8) else Color(0xFF4779A4)
        null -> if (dark) Color(0xFF72C7F4) else Color(0xFF287FB8)
    }
}

@Composable
fun CategoryBadge(category: CategoryKey?, size: Dp = 38.dp, catalog: ExpenseCatalog = ExpenseCatalog()) {
    val color = categoryColor(category)
    Box(Modifier.size(size).background(color.copy(alpha = 0.12f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        val icon = if (category?.customId != null) pl.kakebo.domain.CategoryIcon.fromId(
            catalog.custom.find { it.category.id == category.customId }?.category?.icon
        ).drawable else categoryIcon(category?.builtIn)
        Icon(painterResource(icon), null, Modifier.size(size * 0.52f), tint = color)
    }
}

@Composable
fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun CategoryAmountRow(category: CategoryKey?, amount: Long, total: Long = 0, catalog: ExpenseCatalog = ExpenseCatalog(),
    showPercentage: Boolean = false) {
    val color = categoryColor(category)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CategoryBadge(category, 32.dp, catalog)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(categoryLabel(category, catalog), style = MaterialTheme.typography.bodyMedium)
            if (total > 0) LinearProgressIndicator(progress = { (amount.toFloat() / total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp).clearAndSetSemantics {}, color = color,
                trackColor = color.copy(alpha = 0.1f), drawStopIndicator = {})
        }
        Text(if (showPercentage && total > 0) stringResource(R.string.amount_with_share, formatMoney(amount), amount * 100 / total)
            else formatMoney(amount), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
fun CategoryRing(totals: Map<CategoryKey?, Long>) {
    val entries = totals.entries.sortedBy { it.key?.builtIn?.ordinal?.toLong() ?: it.key?.customId?.plus(ExpenseCategory.entries.size) ?: Long.MAX_VALUE }.map { it.key to it.value }
    val total = entries.sumOf { it.second }
    if (total <= 0) return
    val colors = entries.map { categoryColor(it.first) }
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(160.dp)) {
            val stroke = 19.dp.toPx()
            var start = -90f
            entries.forEachIndexed { index, (_, amount) ->
                val sweep = (amount.toDouble() / total * 360).toFloat()
                if (amount > 0) drawArc(colors[index].copy(alpha = 0.75f), start, sweep, false,
                    topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        Icon(painterResource(R.drawable.ic_hero_chart_pie), null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun DailyBars(days: List<Pair<LocalDate, Long>>) {
    val locale = LocalConfiguration.current.locales[0]
    val max = days.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1L
    val color = MaterialTheme.colorScheme.primary
    // The exact values are presented in the accessible day-by-day list below the chart.
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp).clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        days.forEach { (day, amount) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.height(100.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.width(24.dp).height((if (amount > 0) maxOf(5f, amount.toFloat() / max * 100f) else 3f).dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(color.copy(alpha = if (amount > 0) 0.65f else 0.12f)))
                }
                Text(day.format(DateTimeFormatter.ofPattern("EEEEE", locale)),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun categoryLabel(category: CategoryKey?, catalog: ExpenseCatalog): String =
    category?.builtIn?.let { stringResource(it.label) }
        ?: catalog.custom.find { it.category.id == category?.customId }?.category?.name
        ?: stringResource(R.string.uncategorized)
