package pl.kakebo.domain

import java.time.YearMonth
import java.time.temporal.ChronoUnit
import pl.kakebo.data.local.PlanRecurrence
import pl.kakebo.data.local.PlannedItemEntity

fun PlannedItemEntity.isDueIn(target: YearMonth = YearMonth.parse(month)): Boolean {
    val elapsed = ChronoUnit.MONTHS.between(YearMonth.parse(recurrenceAnchorMonth), target)
    if (elapsed < 0) return false
    val interval = when (recurrence) {
        PlanRecurrence.MONTHLY -> 1L
        PlanRecurrence.QUARTERLY -> 3L
        PlanRecurrence.YEARLY -> 12L
    }
    return elapsed % interval == 0L
}

fun PlannedItemEntity.nextDueMonth(from: YearMonth = YearMonth.parse(month)): YearMonth {
    var candidate = from
    while (!isDueIn(candidate)) candidate = candidate.plusMonths(1)
    return candidate
}
