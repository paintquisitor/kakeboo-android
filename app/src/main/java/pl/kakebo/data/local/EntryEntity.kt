package pl.kakebo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.ForeignKey
import pl.kakebo.domain.ExpenseSubcategory

enum class EntryType { INCOME, EXPENSE }

@Entity(
    tableName = "entries",
    indices = [Index(value = ["type", "dateEpochDay"]), Index("plannedItemId"), Index("customSubcategoryId")],
    foreignKeys = [ForeignKey(entity = PlannedItemEntity::class, parentColumns = ["id"],
        childColumns = ["plannedItemId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = CustomSubcategoryEntity::class, parentColumns = ["id"],
            childColumns = ["customSubcategoryId"], onDelete = ForeignKey.RESTRICT)],
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // Kwota dodatnia w groszach (PLN); kierunek określa type.
    val amountGrosz: Long,
    val type: EntryType,
    // LocalDate.toEpochDay(): data rachunku niezależna od strefy czasowej.
    val dateEpochDay: Long,
    val note: String = "",
    val subcategory: ExpenseSubcategory? = null,
    val plannedItemId: Long? = null,
    val receiptName: String? = null,
    val customSubcategoryId: Long? = null,
) {
    init {
        require(amountGrosz > 0) { "Kwota musi być większa od zera." }
        require(subcategory == null || customSubcategoryId == null)
        require(type == EntryType.EXPENSE || (subcategory == null && plannedItemId == null && receiptName == null && customSubcategoryId == null))
    }
}
