package pl.kakebo.domain

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.kakebo.data.local.EntryEntity
import pl.kakebo.data.local.EntryType
import pl.kakebo.data.local.MonthlyPlanEntity
import pl.kakebo.data.local.MonthlyPlanWithItems
import pl.kakebo.data.local.PlannedItemEntity

class YearlyReportTest {
    private fun plan(month: YearMonth, income: Long, fixed: Long, savings: Long) = MonthlyPlanWithItems(
        MonthlyPlanEntity(month.toString(), savings),
        listOf(
            PlannedItemEntity(month = month.toString(), type = EntryType.INCOME, title = "Pensja",
                amountGrosz = income, dateEpochDay = month.atDay(1).toEpochDay()),
            PlannedItemEntity(month = month.toString(), type = EntryType.EXPENSE, title = "Czynsz",
                amountGrosz = fixed, dateEpochDay = month.atDay(2).toEpochDay()),
        ),
    )

    @Test
    fun annualTotalsCategoriesAndExtremesComeFromTwelveMonthlyBalances() {
        val january = YearMonth.of(2026, 1)
        val february = YearMonth.of(2026, 2)
        val entries = listOf(
            EntryEntity(amountGrosz = 20_000, type = EntryType.EXPENSE,
                dateEpochDay = january.atDay(10).toEpochDay(), subcategory = ExpenseSubcategory.FOOD),
            EntryEntity(amountGrosz = 40_000, type = EntryType.EXPENSE,
                dateEpochDay = february.atDay(10).toEpochDay(), subcategory = ExpenseSubcategory.DINING),
        )

        val report = yearlyReport(2026, listOf(
            plan(january, income = 300_000, fixed = 100_000, savings = 50_000),
            plan(february, income = 200_000, fixed = 50_000, savings = 20_000),
        ), entries)

        assertEquals(12, report.months.size)
        assertEquals(500_000L, report.income)
        assertEquals(150_000L, report.fixedExpenseTotal)
        assertEquals(60_000L, report.diaryExpenses)
        assertEquals(210_000L, report.totalExpenses)
        assertEquals(290_000L, report.savings)
        assertEquals(70_000L, report.savingsGoal)
        assertEquals(SavingsResult.REACHED, report.savingsResult)
        assertEquals(20_000L, report.categoryTotals[ExpenseCategory.NEEDS.key])
        assertEquals(40_000L, report.categoryTotals[ExpenseCategory.OPTIONAL.key])
        assertEquals(january, report.mostExpensiveMonth?.month)
        assertEquals(february, report.leastExpensiveMonth?.month)
        assertEquals(AnnualFixedExpense("Czynsz", 150_000, 2), report.fixedExpenses.single())
    }
}
