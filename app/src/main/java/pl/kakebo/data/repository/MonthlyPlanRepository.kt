package pl.kakebo.data.repository

import java.time.YearMonth
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import pl.kakebo.data.local.MonthlyPlanDao
import pl.kakebo.data.local.MonthlyPlanEntity
import pl.kakebo.data.local.MonthlyPlanWithItems
import pl.kakebo.data.local.PlannedItemEntity

class MonthlyPlanRepository(
    private val dao: MonthlyPlanDao,
    private val currentMonth: () -> YearMonth = { YearMonth.now() },
) {
    fun observeMonth(month: YearMonth) = dao.observeMonth(month.toString())
        .onStart {
            val current = currentMonth()
            dao.deletePlansAfter(current.toString())
            if (!month.isAfter(current)) dao.carryForward(month.toString())
        }.map {
            it ?: MonthlyPlanWithItems(MonthlyPlanEntity(month.toString()), emptyList())
        }

    fun observeYear(year: Int) = dao.observeRange(
        YearMonth.of(year, 1).toString(),
        YearMonth.of(year + 1, 1).toString(),
    ).onStart { dao.deletePlansAfter(currentMonth().toString()) }

    suspend fun savePlan(plan: MonthlyPlanEntity) = dao.savePlan(plan)
    suspend fun saveItem(item: PlannedItemEntity) = dao.saveItem(item)
    suspend fun deleteItem(item: PlannedItemEntity) = dao.deleteItem(item.id, item.month)
}
