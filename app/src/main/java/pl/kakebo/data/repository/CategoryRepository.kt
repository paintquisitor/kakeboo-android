package pl.kakebo.data.repository

import java.util.Locale
import kotlinx.coroutines.flow.map
import pl.kakebo.data.local.*
import pl.kakebo.domain.ExpenseCatalog
import pl.kakebo.domain.CategoryIcon

class DuplicateCategoryNameException : IllegalArgumentException()

class CategoryRepository(private val dao: CategoryDao) {
    val catalog = dao.observeAll().map { ExpenseCatalog(it) }

    suspend fun addCategory(name: String, icon: CategoryIcon = CategoryIcon.WALLET): Long {
        val clean = cleanName(name)
        val id = dao.insertCategory(CustomCategoryEntity(name = clean, nameKey = clean.lowercase(Locale.ROOT), icon = icon.name))
        if (id == -1L) throw DuplicateCategoryNameException()
        return id
    }

    suspend fun updateIcon(categoryId: Long, icon: CategoryIcon) {
        check(dao.updateIcon(categoryId, icon.name) == 1) { "Category no longer exists" }
    }

    suspend fun addSubcategory(categoryId: Long, name: String): Long {
        val clean = cleanName(name)
        val id = dao.insertSubcategory(CustomSubcategoryEntity(categoryId = categoryId, name = clean, nameKey = clean.lowercase(Locale.ROOT)))
        if (id == -1L) throw DuplicateCategoryNameException()
        return id
    }

    suspend fun renameCategory(id: Long, name: String) {
        val clean = cleanName(name)
        if (dao.renameCategory(id, clean, clean.lowercase(Locale.ROOT)) == 0) throw DuplicateCategoryNameException()
    }

    suspend fun renameSubcategory(id: Long, name: String) {
        val clean = cleanName(name)
        if (dao.renameSubcategory(id, clean, clean.lowercase(Locale.ROOT)) == 0) throw DuplicateCategoryNameException()
    }

    suspend fun deleteCategory(id: Long) = dao.deleteCategory(id)
    suspend fun deleteSubcategory(id: Long) = dao.deleteSubcategory(id)

    private fun cleanName(name: String): String = name.trim().replace(Regex("\\s+"), " ").also {
        require(it.isNotEmpty() && it.length <= 60)
    }
}
