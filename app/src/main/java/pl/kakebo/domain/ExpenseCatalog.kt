package pl.kakebo.domain

import pl.kakebo.data.local.CustomCategoryWithSubcategories
import pl.kakebo.data.local.EntryEntity

// Null is reserved for legacy, uncategorized expenses.
data class CategoryKey(val builtIn: ExpenseCategory? = null, val customId: Long? = null) {
    init { require((builtIn != null) != (customId != null)) }
}
val ExpenseCategory.key: CategoryKey get() = CategoryKey(builtIn = this)

data class ExpenseCatalog(val custom: List<CustomCategoryWithSubcategories> = emptyList()) {
    val subcategories = custom.flatMap { it.subcategories }.associateBy { it.id }
    val keys: List<CategoryKey> get() = ExpenseCategory.entries.map { it.key } + custom.map { CategoryKey(customId = it.category.id) }

    fun categoryOf(entry: EntryEntity): CategoryKey? = entry.subcategory?.category?.key
        ?: subcategories[entry.customSubcategoryId]?.let { CategoryKey(customId = it.categoryId) }

    fun totals(entries: List<EntryEntity>): Map<CategoryKey?, Long> =
        entries.groupBy(::categoryOf).mapValues { (_, rows) -> rows.sumOf { it.amountGrosz } }
}
