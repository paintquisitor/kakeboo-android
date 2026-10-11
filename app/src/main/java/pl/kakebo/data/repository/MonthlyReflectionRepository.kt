package pl.kakebo.data.repository

import java.time.YearMonth
import kotlinx.coroutines.flow.map
import pl.kakebo.data.local.MonthlyReflectionDao
import pl.kakebo.data.local.MonthlyReflectionEntity

class MonthlyReflectionRepository(private val dao: MonthlyReflectionDao) {
    fun observeMonth(month: YearMonth) = dao.observeMonth(month.toString()).map {
        it ?: MonthlyReflectionEntity(month.toString())
    }

    suspend fun save(reflection: MonthlyReflectionEntity) = dao.save(reflection)
}
