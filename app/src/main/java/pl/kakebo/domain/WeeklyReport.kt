package pl.kakebo.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.text.Normalizer
import java.util.Locale
import pl.kakebo.data.local.EntryEntity
import pl.kakebo.data.local.EntryType
import pl.kakebo.data.local.MonthlyPlanWithItems

data class BudgetBalance(val income: Long, val savings: Long, val spent: Long, val fixedExpenses: Long) {
    val remaining: Long get() = income - savings - fixedExpenses - spent
}

data class WeekReport(
    val start: LocalDate,
    val end: LocalDate,
    val entries: List<EntryEntity>,
    val openingBalance: Long,
    val closingBalance: Long,
    val catalog: ExpenseCatalog = ExpenseCatalog(),
) {
    val spent: Long get() = entries.sumOf { it.amountGrosz }
    val categoryTotals: Map<CategoryKey?, Long>
        get() = catalog.totals(entries)
    val partial: Boolean get() = ChronoUnit.DAYS.between(start, end) + 1 < 7
}

private fun expensesInMonth(plan: MonthlyPlanWithItems, entries: List<EntryEntity>): List<EntryEntity> {
    val month = YearMonth.parse(plan.plan.month)
    return entries.filter { it.type == EntryType.EXPENSE &&
        it.dateEpochDay >= month.atDay(1).toEpochDay() && it.dateEpochDay <= month.atEndOfMonth().toEpochDay() }
}

internal data class ReconciledExpenses(
    val all: List<EntryEntity>,
    val variable: List<EntryEntity>,
    val fixedActualById: Map<Long, Long>,
)

/**
 * Bank imports have no category or attachment. When such a transaction has the exact amount of an
 * unpaid fixed item in the same month, it is its actual payment rather than another daily expense.
 * Explicit plannedItemId links from older versions still take precedence.
 */
internal fun reconcileExpenses(
    plan: MonthlyPlanWithItems,
    entries: List<EntryEntity>,
): ReconciledExpenses {
    val expenses = expensesInMonth(plan, entries)
    val month = YearMonth.parse(plan.plan.month)
    val fixedItems = plan.items.filter { it.type == EntryType.EXPENSE && it.isDueIn(month) }
    val fixedIds = fixedItems.mapTo(mutableSetOf()) { it.id }
    val actualById = expenses.filter { it.plannedItemId in fixedIds }
        .groupBy { requireNotNull(it.plannedItemId) }
        .mapValuesTo(mutableMapOf()) { (_, rows) -> rows.sumOf { it.amountGrosz } }

    val importCandidates = expenses.withIndex().filter { (_, entry) ->
        entry.plannedItemId == null && entry.subcategory == null &&
            entry.customSubcategoryId == null && entry.receiptName == null
    }.toMutableList()
    val inferredFixedIndexes = mutableSetOf<Int>()
    fixedItems.forEach { item ->
        if (actualById[item.id] == null) {
            val candidateIndex = importCandidates.indexOfFirst { (_, entry) ->
                entry.note.matchesFixedTitle(item.title)
            }
            if (candidateIndex >= 0) {
                val match = importCandidates.removeAt(candidateIndex)
                inferredFixedIndexes += match.index
                actualById[item.id] = match.value.amountGrosz
            }
        }
    }

    return ReconciledExpenses(
        all = expenses,
        variable = expenses.filterIndexed { index, entry ->
            entry.plannedItemId !in fixedIds && index !in inferredFixedIndexes
        },
        fixedActualById = actualById,
    )
}

private fun String.matchesFixedTitle(title: String): Boolean {
    val noteWords = normalizedWords()
    val titleWords = title.normalizedWords().filter { it.length >= 3 }
    return titleWords.isNotEmpty() && titleWords.all(noteWords::contains)
}

private fun String.normalizedWords(): Set<String> = Normalizer.normalize(
    lowercase(Locale.ROOT).replace('ł', 'l'),
    Normalizer.Form.NFD,
).replace(Regex("\\p{M}+"), "")
    .split(Regex("[^a-z0-9]+"))
    .filterTo(mutableSetOf()) { it.isNotBlank() }

// Koszty stałe z Planu są odejmowane od razu. Dziennik zawiera pozostałe wydatki.
fun budgetBalance(plan: MonthlyPlanWithItems, entries: List<EntryEntity>): BudgetBalance {
    val month = YearMonth.parse(plan.plan.month)
    val fixedItems = plan.items.filter { it.type == EntryType.EXPENSE && it.isDueIn(month) }
    val reconciled = reconcileExpenses(plan, entries)
    return BudgetBalance(
        income = plan.items.filter { it.type == EntryType.INCOME && it.isDueIn(month) }.sumOf { it.amountGrosz },
        savings = plan.plan.savingsGrosz,
        spent = reconciled.variable.sumOf { it.amountGrosz },
        fixedExpenses = fixedItems.sumOf { maxOf(it.amountGrosz, reconciled.fixedActualById[it.id] ?: 0) },
    )
}

fun weeklyReports(plan: MonthlyPlanWithItems, entries: List<EntryEntity>, catalog: ExpenseCatalog = ExpenseCatalog()): List<WeekReport> {
    val month = YearMonth.parse(plan.plan.month)
    val expenses = expensesInMonth(plan, entries)
    val weeks = mutableListOf<WeekReport>()
    var start = month.atDay(1)
    while (start <= month.atEndOfMonth()) {
        val end = minOf(start.plusDays(6), month.atEndOfMonth())
        val before = expenses.filter { it.dateEpochDay < start.toEpochDay() }
        val through = expenses.filter { it.dateEpochDay <= end.toEpochDay() }
        weeks += WeekReport(start, end,
            through.filter { it.dateEpochDay >= start.toEpochDay() },
            budgetBalance(plan, before).remaining, budgetBalance(plan, through).remaining, catalog)
        start = end.plusDays(1)
    }
    return weeks
}
