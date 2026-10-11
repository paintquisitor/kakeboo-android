package pl.kakebo.data.local

import androidx.room.*

@Entity(tableName = "custom_categories", indices = [Index(value = ["nameKey"], unique = true)])
data class CustomCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nameKey: String,
    @ColumnInfo(defaultValue = "'WALLET'") val icon: String = "WALLET",
)

@Entity(tableName = "custom_subcategories",
    indices = [Index(value = ["categoryId", "nameKey"], unique = true)],
    foreignKeys = [ForeignKey(entity = CustomCategoryEntity::class, parentColumns = ["id"],
        childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT)])
data class CustomSubcategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val name: String,
    val nameKey: String,
)

data class CustomCategoryWithSubcategories(
    @Embedded val category: CustomCategoryEntity,
    @Relation(parentColumn = "id", entityColumn = "categoryId") val subcategories: List<CustomSubcategoryEntity>,
)

@Dao
interface CategoryDao {
    @Transaction
    @Query("SELECT * FROM custom_categories ORDER BY id")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<CustomCategoryWithSubcategories>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: CustomCategoryEntity): Long

    @Query("UPDATE custom_categories SET icon = :icon WHERE id = :categoryId")
    suspend fun updateIcon(categoryId: Long, icon: String): Int

    @Query("UPDATE OR IGNORE custom_categories SET name = :name, nameKey = :nameKey WHERE id = :id")
    suspend fun renameCategory(id: Long, name: String, nameKey: String): Int

    @Query("UPDATE OR IGNORE custom_subcategories SET name = :name, nameKey = :nameKey WHERE id = :id")
    suspend fun renameSubcategory(id: Long, name: String, nameKey: String): Int

    @Query("UPDATE entries SET customSubcategoryId = NULL WHERE customSubcategoryId = :id")
    suspend fun detachSubcategoryEntries(id: Long)

    @Query("UPDATE entries SET customSubcategoryId = NULL WHERE customSubcategoryId IN (SELECT id FROM custom_subcategories WHERE categoryId = :id)")
    suspend fun detachCategoryEntries(id: Long)

    @Query("DELETE FROM custom_subcategories WHERE id = :id")
    suspend fun deleteSubcategoryRow(id: Long)

    @Query("DELETE FROM custom_subcategories WHERE categoryId = :id")
    suspend fun deleteCategoryChildren(id: Long)

    @Query("DELETE FROM custom_categories WHERE id = :id")
    suspend fun deleteCategoryRow(id: Long)

    @Transaction
    suspend fun deleteCategory(id: Long) {
        detachCategoryEntries(id)
        deleteCategoryChildren(id)
        deleteCategoryRow(id)
    }

    @Transaction
    suspend fun deleteSubcategory(id: Long) {
        detachSubcategoryEntries(id)
        deleteSubcategoryRow(id)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSubcategory(subcategory: CustomSubcategoryEntity): Long
}
