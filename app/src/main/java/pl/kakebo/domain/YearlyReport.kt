package pl.kakebo.domain

import java.time.YearMonth
import pl.kakebo.data.local.EntryEntity
import pl.kakebo.data.local.EntryType
import pl.kakebo.data.local.MonthlyPlanEntity
import pl.kakebo.data.local.MonthlyPlanWithItems

data class YearMonthBalance(
    val month: YearMonth,
    val report: MonthlyReport,
)

data class AnnualFixedExpense(
    val title: String,
    val total: Long,
    val occurrences: Int,
)

data class YearlyReport(
    val year: Int,
    val months: List<YearMonthBalance>,
    val fixedExpenses: List<AnnualFixedExpense>,
) {
    private val monthsThroughNow: List<YearMonthBalance>
        get() = when {
            year < YearMonth.now().year -> months
            year > YearMonth.now().year -> emptyList()
            else -> months.filter { it.month <= YearMonth.now() }
        }

    val income: Long get() = monthsThroughNow.sumOf { it.report.balance.income }
    val diaryExpenses: Long get() = monthsThroughNow.sumOf { it.report.balance.spent }
    val fixedExpenseTotal: Long get() = monthsThroughNow.sumOf { it.report.balance.fixedExpenses }
    val totalExpenses: Long get() = monthsThroughNow.sumOf { it.report.totalExpenses }
    val savingsGoal: Long get() = monthsThroughNow.sumOf { it.report.balance.savings }
    val savings: Long get() = income - totalExpenses
    val savingsResult: SavingsResult get() = when {
        income == 0L -> SavingsResult.NO_INCOME
        savingsGoal == 0L -> SavingsResult.NO_GOAL
        savings >= savingsGoal -> SavingsResult.REACHED
        savings > 0L -> SavingsResult.PARTIAL
        else -> SavingsResult.NOT_REACHED
    }
    val categoryTotals: Map<CategoryKey?, Long>
        get() = monthsThroughNow.flatMap { it.report.categoryTotals.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, amounts) -> amounts.sum() }
    val monthsWithExpenses: List<YearMonthBalance> get() = monthsThroughNow.filter { it.report.totalExpenses > 0 }
    val mostExpensiveMonth: YearMonthBalance? get() = monthsWithExpenses.maxByOrNull { it.report.totalExpenses }
    val leastExpensiveMonth: YearMonthBalance? get() = monthsWithExpenses.minByOrNull { it.report.totalExpenses }
}

fun yearlyReport(
    year: Int,
    plans: List<MonthlyPlanWithItems>,
    entries: List<EntryEntity>,
    catalog: ExpenseCatalog = ExpenseCatalog(),
): YearlyReport {
    val plansByMonth = plans.associateBy { it.plan.month }
    val months = (1..12).map { monthNumber ->
        val month = YearMonth.of(year, monthNumber)
        val plan = plansByMonth[month.toString()]
            ?: MonthlyPlanWithItems(MonthlyPlanEntity(month.toString()), emptyList())
        YearMonthBalance(month, monthlyReport(plan, entries, catalog))
    }
    val now = YearMonth.now()
    val includedMonths = when {
        year < now.year -> months.map { it.month }.toSet()
        year > now.year -> emptySet()
        else -> months.filter { it.month <= now }.map { it.month }.toSet()
    }
    val annualFixed = plans.filter { YearMonth.parse(it.plan.month) in includedMonths }.flatMap { plan ->
        val month = YearMonth.parse(plan.plan.month)
        plan.items.filter { it.type == EntryType.EXPENSE && it.isDueIn(month) }
    }.groupBy { it.title.trim() }
        .map { (title, items) -> AnnualFixedExpense(title, items.sumOf { it.amountGrosz }, items.size) }
        .sortedByDescending { it.total }
    return YearlyReport(year, months, annualFixed)
}
