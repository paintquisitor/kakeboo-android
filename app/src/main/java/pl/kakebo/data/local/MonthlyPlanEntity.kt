package pl.kakebo.data.local

import androidx.room.Embedded
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.LocalDate
import java.time.YearMonth

@Entity(tableName = "monthly_plans")
data class MonthlyPlanEntity(
    @PrimaryKey val month: String,
    val savingsGrosz: Long = 0,
    val resolutions: String = "",
) {
    init {
        YearMonth.parse(month)
        require(savingsGrosz >= 0)
    }
}

// Plan nie jest zapisem faktycznego przychodu ani poniesionego wydatku.
@Entity(
    tableName = "planned_items",
    foreignKeys = [ForeignKey(
        entity = MonthlyPlanEntity::class,
        parentColumns = ["month"], childColumns = ["month"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("month")],
)
data class PlannedItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val month: String,
    val type: EntryType,
    val title: String,
    val amountGrosz: Long,
    val dateEpochDay: Long,
    @ColumnInfo(defaultValue = "'MONTHLY'") val recurrence: PlanRecurrence = PlanRecurrence.MONTHLY,
    @ColumnInfo(defaultValue = "''") val recurrenceAnchorMonth: String = month,
    @ColumnInfo(defaultValue = "''") val icon: String = "",
) {
    init {
        require(title.isNotBlank())
        require(amountGrosz > 0)
        require(YearMonth.from(LocalDate.ofEpochDay(dateEpochDay)).toString() == month)
        YearMonth.parse(recurrenceAnchorMonth)
    }
}

enum class PlanRecurrence { MONTHLY, QUARTERLY, YEARLY }

data class MonthlyPlanWithItems(
    @Embedded val plan: MonthlyPlanEntity,
    @Relation(parentColumn = "month", entityColumn = "month")
    val items: List<PlannedItemEntity>,
)
