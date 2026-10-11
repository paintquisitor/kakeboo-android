package pl.kakebo.domain

import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test
import pl.kakebo.data.local.*

class MonthlyReportTest {
    private val month = YearMonth.of(2026, 9)
    private val plan = MonthlyPlanWithItems(MonthlyPlanEntity(month.toString(), 50_000), listOf(
        PlannedItemEntity(1, month.toString(), EntryType.INCOME, "Pensja", 300_000, month.atDay(1).toEpochDay()),
        PlannedItemEntity(2, month.toString(), EntryType.EXPENSE, "Czynsz", 100_000, month.atDay(1).toEpochDay()),
    ))
    private fun expense(amount: Long) = EntryEntity(amountGrosz = amount, type = EntryType.EXPENSE,
        dateEpochDay = month.atDay(10).toEpochDay(), subcategory = ExpenseSubcategory.BOOKS)

    @Test fun categoriesReconcileWithSpendingWithoutCountingLegacyBillsTwice() {
        val entries = listOf(expense(10_000), expense(2_000).copy(subcategory = null),
            expense(110_000).copy(plannedItemId = 2),
            expense(9_999).copy(dateEpochDay = month.plusMonths(1).atDay(1).toEpochDay()),
            expense(8_888).copy(type = EntryType.INCOME, subcategory = null))
        val report = monthlyReport(plan, entries)
        assertEquals(110_000L, report.balance.fixedExpenses)
        assertEquals(12_000L, report.categoryTotals.values.sum())
        assertEquals(report.balance.spent, report.categoryTotals.values.sum())
        assertEquals(10_000L, report.categoryTotals[ExpenseCategory.HOBBY.key])
        assertEquals(2_000L, report.categoryTotals[null])
        assertEquals(122_000L, report.totalExpenses)
        assertEquals(178_000L, report.leftFromIncome)
        assertEquals(128_000L, report.balance.remaining)
    }

    @Test fun matchingBankImportIsExcludedFromVariableCategoryTotals() {
        val importedRent = expense(100_000).copy(subcategory = null, note = "PRZELEW CZYNSZ")
        val report = monthlyReport(plan, listOf(importedRent))

        assertEquals(100_000L, report.balance.fixedExpenses)
        assertEquals(0L, report.balance.spent)
        assertTrue(report.categoryTotals.isEmpty())
        assertEquals(200_000L, report.leftFromIncome)
    }

    @Test fun savingsGoalIsComparedWithMoneyLeftRatherThanDeductedTwice() {
        listOf(149_999L to SavingsResult.REACHED, 150_000L to SavingsResult.REACHED,
            150_001L to SavingsResult.PARTIAL, 199_999L to SavingsResult.PARTIAL,
            200_000L to SavingsResult.NOT_REACHED, 210_000L to SavingsResult.NOT_REACHED).forEach { (spent, expected) ->
            val report = monthlyReport(plan, listOf(expense(spent)))
            assertEquals(expected, report.savingsResult)
            assertEquals(200_000L - spent, report.leftFromIncome)
        }
    }

    @Test fun missingIncomeAndMissingGoalDoNotClaimSuccess() {
        val empty = MonthlyPlanWithItems(MonthlyPlanEntity(month.toString()), emptyList())
        val report = monthlyReport(empty, listOf(expense(1000)))
        assertEquals(SavingsResult.NO_INCOME, report.savingsResult)
        assertEquals(-1000L, report.leftFromIncome)
        assertEquals(SavingsResult.NO_GOAL, monthlyReport(plan.copy(plan = plan.plan.copy(savingsGrosz = 0)), emptyList()).savingsResult)
    }
}
