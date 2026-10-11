package pl.kakebo.domain

import java.time.YearMonth
import pl.kakebo.data.local.EntryType
import pl.kakebo.data.local.MonthlyPlanWithItems

data class PlanTotals(val income: Long, val fixedExpenses: Long, val savings: Long) {
    val available: Long get() = income - fixedExpenses - savings
}

fun MonthlyPlanWithItems.totals(): PlanTotals {
    val month = YearMonth.parse(plan.month)
    return PlanTotals(
        income = items.filter { it.type == EntryType.INCOME && it.isDueIn(month) }.sumOf { it.amountGrosz },
        fixedExpenses = items.filter { it.type == EntryType.EXPENSE && it.isDueIn(month) }.sumOf { it.amountGrosz },
        savings = plan.savingsGrosz,
    )
}
