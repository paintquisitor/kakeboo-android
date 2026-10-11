package pl.kakebo.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "monthly_reflections")
data class MonthlyReflectionEntity(
    @PrimaryKey val month: String,
    val successes: String = "",
    val improvements: String = "",
    val nextMonth: String = "",
) {
    init { YearMonth.parse(month) }
}

@Dao
interface MonthlyReflectionDao {
    @Query("SELECT * FROM monthly_reflections WHERE month = :month")
    fun observeMonth(month: String): Flow<MonthlyReflectionEntity?>

    @Upsert
    suspend fun save(reflection: MonthlyReflectionEntity)
}
