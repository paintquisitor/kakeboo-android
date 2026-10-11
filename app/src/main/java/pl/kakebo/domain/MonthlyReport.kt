package pl.kakebo.domain

import java.time.YearMonth
import pl.kakebo.data.local.EntryEntity
import pl.kakebo.data.local.EntryType
import pl.kakebo.data.local.MonthlyPlanWithItems

enum class SavingsResult { NO_INCOME, NO_GOAL, REACHED, PARTIAL, NOT_REACHED }

data class MonthlyReport(
    val balance: BudgetBalance,
    val categoryTotals: Map<CategoryKey?, Long>,
    val hasIncome: Boolean,
) {
    val totalExpenses: Long get() = balance.fixedExpenses + balance.spent
    val leftFromIncome: Long get() = balance.income - totalExpenses
    val savingsResult: SavingsResult get() = when {
        !hasIncome -> SavingsResult.NO_INCOME
        balance.savings == 0L -> SavingsResult.NO_GOAL
        leftFromIncome >= balance.savings -> SavingsResult.REACHED
        leftFromIncome > 0 -> SavingsResult.PARTIAL
        else -> SavingsResult.NOT_REACHED
    }
}

fun monthlyReport(plan: MonthlyPlanWithItems, entries: List<EntryEntity>, catalog: ExpenseCatalog = ExpenseCatalog()): MonthlyReport {
    val month = YearMonth.parse(plan.plan.month)
    val diary = reconcileExpenses(plan, entries).variable
    return MonthlyReport(
        balance = budgetBalance(plan, entries),
        categoryTotals = catalog.totals(diary),
        hasIncome = plan.items.any { it.type == EntryType.INCOME && it.isDueIn(month) },
    )
}
