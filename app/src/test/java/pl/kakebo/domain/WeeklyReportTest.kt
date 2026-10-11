package pl.kakebo.domain

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test
import pl.kakebo.data.local.*

class WeeklyReportTest {
    private val month = YearMonth.of(2026, 9)
    private fun plan(month: YearMonth = this.month) = MonthlyPlanWithItems(MonthlyPlanEntity(month.toString(), 20_000), listOf(
        PlannedItemEntity(1, month.toString(), EntryType.INCOME, "Pensja", 300_000, month.atDay(1).toEpochDay()),
        PlannedItemEntity(2, month.toString(), EntryType.EXPENSE, "Czynsz", 100_000, month.atDay(1).toEpochDay()),
    ))
    private fun expense(amount: Long, day: Int = 1, bill: Long? = null) = EntryEntity(amountGrosz = amount,
        type = EntryType.EXPENSE, dateEpochDay = month.atDay(day).toEpochDay(), plannedItemId = bill,
        subcategory = ExpenseSubcategory.FOOD)

    @Test fun legacyLinkedEntriesAreNotCountedTwiceAfterSimplification() {
        val partial = listOf(expense(40_000, bill = 2), expense(5_000))
        assertEquals(100_000L, budgetBalance(plan(), partial).fixedExpenses)
        assertEquals(5_000L, budgetBalance(plan(), partial).spent)
        assertEquals(175_000L, budgetBalance(plan(), partial).remaining)
        val paid = partial + expense(60_000, bill = 2)
        assertEquals(100_000L, budgetBalance(plan(), paid).fixedExpenses)
        assertEquals(175_000L, budgetBalance(plan(), paid).remaining)
        assertEquals(165_000L, budgetBalance(plan(), paid + expense(10_000, bill = 2)).remaining)
    }

    @Test fun fixedCostsAreDeductedImmediatelyAndDiaryExpensesNeedNoPaymentStatus() {
        val initial = budgetBalance(plan(), emptyList())
        assertEquals(100_000L, initial.fixedExpenses)
        assertEquals(180_000L, initial.remaining)
        val entries = listOf(expense(5_000, 3), expense(2_000, 8))
        val balance = budgetBalance(plan(), entries)
        assertEquals(7_000L, balance.spent)
        assertEquals(173_000L, balance.remaining)
        val weeks = weeklyReports(plan(), entries)
        assertEquals(175_000L, weeks[0].closingBalance)
        assertEquals(175_000L, weeks[1].openingBalance)
        assertEquals(173_000L, weeks[1].closingBalance)
        val changed = plan().let { it.copy(items = listOf(it.items[0], it.items[1].copy(amountGrosz = 120_000))) }
        assertEquals(153_000L, budgetBalance(changed, entries).remaining)
    }

    @Test fun matchingUncategorizedBankImportDoesNotDuplicateFixedExpense() {
        val importedRent = expense(100_000, 3).copy(subcategory = null, note = "PRZELEW CZYNSZ")
        val balance = budgetBalance(plan(), listOf(importedRent))

        assertEquals(100_000L, balance.fixedExpenses)
        assertEquals(0L, balance.spent)
        assertEquals(180_000L, balance.remaining)
    }

    @Test fun categorizedExpenseWithSameAmountIsStillASeparateExpense() {
        val balance = budgetBalance(plan(), listOf(expense(100_000, 3)))

        assertEquals(100_000L, balance.fixedExpenses)
        assertEquals(100_000L, balance.spent)
        assertEquals(80_000L, balance.remaining)
    }

    @Test fun unrelatedBankTransactionWithSameAmountIsStillASeparateExpense() {
        val importedPurchase = expense(100_000, 3).copy(
            subcategory = null,
            note = "ORLEN STACJA PALIW",
        )
        val balance = budgetBalance(plan(), listOf(importedPurchase))

        assertEquals(100_000L, balance.fixedExpenses)
        assertEquals(100_000L, balance.spent)
        assertEquals(80_000L, balance.remaining)
    }

    @Test fun importedBankPaymentCanMatchVariableFixedExpenseByTitle() {
        val electricityPlan = plan().let { current ->
            current.copy(items = current.items.map { item ->
                if (item.id == 2L) item.copy(title = "Prąd", amountGrosz = 40_000) else item
            })
        }
        val importedPayment = expense(43_500, 12).copy(
            subcategory = null,
            note = "OPŁATA ZA PRAD 09/2026",
        )
        val balance = budgetBalance(electricityPlan, listOf(importedPayment))

        assertEquals(43_500L, balance.fixedExpenses)
        assertEquals(0L, balance.spent)
        assertEquals(236_500L, balance.remaining)
    }

    @Test fun removingOrReducingPlanNeverRemovesActualSpending() {
        val paid = listOf(expense(90_000, bill = 2))
        val reduced = plan().let { it.copy(items = listOf(it.items[0], it.items[1].copy(amountGrosz = 80_000))) }
        assertEquals(190_000L, budgetBalance(reduced, paid).remaining)
        val removed = plan().let { it.copy(items = it.items.filter { row -> row.type == EntryType.INCOME }) }
        assertEquals(190_000L, budgetBalance(removed, paid.map { it.copy(plannedItemId = null) }).remaining)
        assertEquals(180_000L, budgetBalance(plan(), emptyList()).remaining)
    }

    @Test fun weeksCoverEveryDayExactlyOnceIncludingLeapAndYearBoundaries() {
        listOf(YearMonth.of(2026, 9), YearMonth.of(2028, 2), YearMonth.of(2026, 12), YearMonth.of(2027, 1), YearMonth.of(2026, 3)).forEach { month ->
            val weeks = weeklyReports(plan(month), emptyList())
            val days = weeks.flatMap { week -> (week.start.toEpochDay()..week.end.toEpochDay()).toList() }
            assertEquals((month.atDay(1).toEpochDay()..month.atEndOfMonth().toEpochDay()).toList(), days)
            weeks.dropLast(1).forEach { assertEquals(7L, it.end.toEpochDay() - it.start.toEpochDay() + 1) }
            weeks.zipWithNext().forEach { (current, next) -> assertEquals(current.end.plusDays(1), next.start) }
            assertEquals(month.atDay(1), weeks.first().start)
            assertEquals(month.atEndOfMonth(), weeks.last().end)
        }
    }

    @Test fun weeksAreSevenDayPeriodsCountedFromFirstDayOfMonth() {
        val weeks = weeklyReports(plan(YearMonth.of(2026, 8)), emptyList())

        assertEquals(
            listOf(1 to 7, 8 to 14, 15 to 21, 22 to 28, 29 to 31),
            weeks.map { it.start.dayOfMonth to it.end.dayOfMonth },
        )
        assertFalse(weeks.first().partial)
        assertTrue(weeks.last().partial)
    }

    @Test fun weeklyBalancesCarryForwardAndOverpaymentAffectsOnlyItsWeek() {
        val entries = listOf(expense(40_000, 1, 2), expense(5_000, 3), expense(70_000, 8, 2))
        val weeks = weeklyReports(plan(), entries)
        assertEquals(45_000L, weeks[0].spent)
        assertEquals(180_000L, weeks[0].openingBalance)
        assertEquals(175_000L, weeks[0].closingBalance)
        assertEquals(175_000L, weeks[1].openingBalance)
        assertEquals(165_000L, weeks[1].closingBalance)
        weeks.zipWithNext().forEach { (a, b) -> assertEquals(a.closingBalance, b.openingBalance) }
        assertEquals(budgetBalance(plan(), entries).remaining, weeks.last().closingBalance)
    }

    @Test fun reportsIgnoreIncomeAndAdjacentMonthsButIncludeUncategorizedExpenses() {
        val entries = listOf(expense(1000).copy(subcategory = null),
            expense(2000).copy(dateEpochDay = LocalDate.of(2026, 8, 31).toEpochDay()),
            expense(3000).copy(dateEpochDay = LocalDate.of(2026, 10, 1).toEpochDay()),
            EntryEntity(amountGrosz = 100_000, type = EntryType.INCOME, dateEpochDay = month.atDay(1).toEpochDay()))
        val weeks = weeklyReports(plan(), entries)
        assertEquals(1000L, weeks.sumOf { it.spent })
        assertEquals(1000L, weeks.first().categoryTotals[null])
        assertEquals(179_000L, budgetBalance(plan(), entries).remaining)
    }

    @Test fun emptyMonthCanShowDeficitWithoutInventingIncome() {
        val empty = MonthlyPlanWithItems(MonthlyPlanEntity(month.toString()), emptyList())
        assertEquals(-1000L, budgetBalance(empty, listOf(expense(1000))).remaining)
    }
}
