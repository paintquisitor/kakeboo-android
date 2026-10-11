package pl.kakebo.domain

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.kakebo.data.local.EntryType
import pl.kakebo.data.local.MonthlyPlanEntity
import pl.kakebo.data.local.MonthlyPlanWithItems
import pl.kakebo.data.local.PlanRecurrence
import pl.kakebo.data.local.PlannedItemEntity

class MonthlyPlanTest {
    @Test
    fun moneyAcceptsPolishAndDecimalInputWithoutRounding() {
        assertEquals(123456L, parseGrosz("1 234,56"))
        assertEquals(123456L, parseGrosz("1\u00A0234.56"))
        assertEquals(10L, parseGrosz("0,1"))
        assertEquals(0L, parseGrosz("0"))
        assertEquals(99_999_999_999L, parseGrosz("999999999,99"))
        assertEquals("1234,56", amountInput(123456, Locale.forLanguageTag("pl-PL")))
        assertEquals("1234.56", amountInput(123456, Locale.ENGLISH))
    }

    @Test
    fun moneyRejectsAmbiguousOrOutOfRangeValues() {
        listOf("", "-10", "1,234", "1.234,56", "NaN", "1e2", "1000000000", "99999999999999999999999")
            .forEach { assertNull(it, parseGrosz(it)) }
    }

    @Test
    fun budgetSubtractsFixedExpensesAndSavingsOnceAndAllowsDeficit() {
        val date = LocalDate.of(2026, 9, 1).toEpochDay()
        val data = MonthlyPlanWithItems(
            MonthlyPlanEntity("2026-09", savingsGrosz = 20_000),
            listOf(
                PlannedItemEntity(month = "2026-09", type = EntryType.INCOME, title = "Pensja", amountGrosz = 395_000, dateEpochDay = date),
                PlannedItemEntity(month = "2026-09", type = EntryType.EXPENSE, title = "Rachunki", amountGrosz = 124_800, dateEpochDay = date),
            ),
        )
        assertEquals(250_200L, data.totals().available)
        assertEquals(-29_800L, data.copy(plan = data.plan.copy(savingsGrosz = 300_000)).totals().available)
    }

    @Test
    fun quarterlyAndYearlyExpensesAreCountedOnlyInTheirDueMonths() {
        fun plan(month: String) = MonthlyPlanWithItems(
            MonthlyPlanEntity(month),
            listOf(
                PlannedItemEntity(month = month, type = EntryType.INCOME, title = "Pensja", amountGrosz = 300_000,
                    dateEpochDay = LocalDate.parse("$month-01").toEpochDay()),
                PlannedItemEntity(month = month, type = EntryType.EXPENSE, title = "Ubezpieczenie", amountGrosz = 120_000,
                    dateEpochDay = LocalDate.parse("$month-01").toEpochDay(), recurrence = PlanRecurrence.QUARTERLY,
                    recurrenceAnchorMonth = "2026-01"),
                PlannedItemEntity(month = month, type = EntryType.EXPENSE, title = "Podatek", amountGrosz = 60_000,
                    dateEpochDay = LocalDate.parse("$month-01").toEpochDay(), recurrence = PlanRecurrence.YEARLY,
                    recurrenceAnchorMonth = "2026-01"),
            ),
        )

        assertEquals(120_000L, plan("2026-01").totals().available)
        assertEquals(300_000L, plan("2026-02").totals().available)
        assertEquals(180_000L, plan("2026-04").totals().available)
        assertEquals(120_000L, plan("2027-01").totals().available)
    }

    @Test(expected = IllegalArgumentException::class)
    fun itemCannotBelongToAnotherMonthThanItsDate() {
        PlannedItemEntity(month = "2026-02", type = EntryType.INCOME, title = "Pensja", amountGrosz = 100,
            dateEpochDay = LocalDate.of(2026, 3, 1).toEpochDay())
    }
}
