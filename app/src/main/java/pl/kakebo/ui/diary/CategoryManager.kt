package pl.kakebo.ui.diary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import pl.kakebo.domain.CategoryIcon
import pl.kakebo.domain.CategoryKey
import pl.kakebo.ui.components.CategoryBadge
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.kakebo.R
import pl.kakebo.domain.ExpenseCatalog

@Composable
internal fun CategoryManager(
    catalog: ExpenseCatalog, saving: Boolean, error: Int?, onClearError: () -> Unit,
    onAddCategory: (String, CategoryIcon, (Long) -> Unit) -> Unit,
    onAddSubcategory: (Long, String, (Long) -> Unit) -> Unit,
    onUpdateIcon: (Long, CategoryIcon, (Long) -> Unit) -> Unit,
    onRenameCategory: (Long, String, (Long) -> Unit) -> Unit,
    onRenameSubcategory: (Long, String, (Long) -> Unit) -> Unit,
    onDeleteCategory: (Long, (Long) -> Unit) -> Unit,
    onDeleteSubcategory: (Long, (Long) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var naming by rememberSaveable { mutableStateOf(false) }
    var parentId by rememberSaveable { mutableStateOf<Long?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var selectedIcon by rememberSaveable { mutableStateOf(CategoryIcon.WALLET) }
    var editingIconId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingNameId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingCategory by rememberSaveable { mutableStateOf(false) }
    var deletingName by rememberSaveable { mutableStateOf("") }
    val beginNaming: (Long?) -> Unit = { parent ->
        editingNameId = null; parentId = parent; name = ""; selectedIcon = CategoryIcon.WALLET; onClearError(); naming = true
    }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.custom_categories)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("custom_categories_list")) {
                item { Text(stringResource(R.string.custom_categories_hint)) }
                if (catalog.custom.isEmpty()) item { Text(stringResource(R.string.custom_categories_empty)) }
                items(catalog.custom, key = { it.category.id }) { group ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CategoryBadge(CategoryKey(customId = group.category.id), catalog = catalog)
                            Text(group.category.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        }
                        TextButton(enabled = !saving, modifier = Modifier.testTag("change_category_icon_${group.category.id}"), onClick = {
                            selectedIcon = CategoryIcon.fromId(group.category.icon)
                            editingIconId = group.category.id
                            onClearError()
                        }) { Text(stringResource(R.string.change_category_icon)) }
                        Row {
                            TextButton(enabled = !saving, modifier = Modifier.testTag("edit_category_${group.category.id}"), onClick = {
                                parentId = null; editingNameId = group.category.id; name = group.category.name
                                onClearError(); naming = true
                            }) { Text(stringResource(R.string.category_edit_action)) }
                            TextButton(enabled = !saving, modifier = Modifier.testTag("delete_category_${group.category.id}"), onClick = {
                                deletingId = group.category.id; deletingCategory = true; deletingName = group.category.name; onClearError()
                            }) { Text(stringResource(R.string.category_delete_action), color = MaterialTheme.colorScheme.error) }
                        }
                        if (group.subcategories.isEmpty()) Text(stringResource(R.string.custom_subcategories_empty), style = MaterialTheme.typography.bodySmall)
                        group.subcategories.sortedBy { it.id }.forEach { subcategory ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(subcategory.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                TextButton(enabled = !saving, modifier = Modifier.testTag("edit_subcategory_${subcategory.id}"), onClick = {
                                    parentId = group.category.id; editingNameId = subcategory.id; name = subcategory.name
                                    onClearError(); naming = true
                                }) { Text(stringResource(R.string.category_edit_action)) }
                                TextButton(enabled = !saving, modifier = Modifier.testTag("delete_subcategory_${subcategory.id}"), onClick = {
                                    deletingId = subcategory.id; deletingCategory = false; deletingName = subcategory.name; onClearError()
                                }) { Text(stringResource(R.string.category_delete_action), color = MaterialTheme.colorScheme.error) }
                            }
                        }
                        TextButton(onClick = { beginNaming(group.category.id) }, enabled = !saving,
                            modifier = Modifier.testTag("add_subcategory_${group.category.id}")) {
                            Text(stringResource(R.string.add_subcategory))
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { beginNaming(null) }, enabled = !saving, modifier = Modifier.testTag("add_category")) {
            Text(stringResource(R.string.add_category))
        } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.testTag("close_categories")) {
            Text(stringResource(R.string.close))
        } })
    if (naming) {
        val duplicateBuiltIn = parentId == null && pl.kakebo.domain.ExpenseCategory.entries.any {
            stringResource(it.label).equals(name.trim(), ignoreCase = true)
        }
        AlertDialog(onDismissRequest = { if (!saving) { naming = false; onClearError() } },
            title = { Text(stringResource(when {
                editingNameId != null && parentId == null -> R.string.edit_category
                editingNameId != null -> R.string.edit_subcategory
                parentId == null -> R.string.add_category
                else -> R.string.add_subcategory
            })) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    parentId?.let { id -> catalog.custom.find { it.category.id == id }?.let { Text(it.category.name) } }
                    OutlinedTextField(value = name, onValueChange = { name = it.take(60); onClearError() },
                        label = { Text(stringResource(R.string.category_name)) }, singleLine = true, enabled = !saving,
                        isError = error != null || duplicateBuiltIn, modifier = Modifier.fillMaxWidth().testTag("category_name"))
                    if (editingNameId != null) Text(stringResource(R.string.rename_category_hint))
                    if (parentId == null && editingNameId == null) CategoryIconPicker(selectedIcon, !saving) { selectedIcon = it }
                    if (duplicateBuiltIn) Text(stringResource(R.string.category_duplicate), color = MaterialTheme.colorScheme.error)
                    else error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(enabled = !saving && name.isNotBlank() && !duplicateBuiltIn,
                modifier = Modifier.testTag("save_category"), onClick = {
                    val done: (Long) -> Unit = { naming = false; name = "" }
                    val editing = editingNameId
                    when {
                        editing != null && parentId == null -> onRenameCategory(editing, name, done)
                        editing != null -> onRenameSubcategory(editing, name, done)
                        parentId != null -> onAddSubcategory(requireNotNull(parentId), name, done)
                        else -> onAddCategory(name, selectedIcon, done)
                    }
                }) { Text(stringResource(if (saving) R.string.saving else R.string.save)) } },
            dismissButton = { TextButton(enabled = !saving, modifier = Modifier.testTag("cancel_category_name"), onClick = { naming = false; onClearError() }) {
                Text(stringResource(R.string.cancel))
            } })
    }
    deletingId?.let { id ->
        AlertDialog(onDismissRequest = { if (!saving) { deletingId = null; onClearError() } },
            title = { Text(stringResource(if (deletingCategory) R.string.delete_category_title else R.string.delete_subcategory_title, deletingName)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(if (deletingCategory) R.string.delete_category_message else R.string.delete_subcategory_message))
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(enabled = !saving, modifier = Modifier.testTag("confirm_category_delete"), onClick = {
                val done: (Long) -> Unit = { deletingId = null }
                if (deletingCategory) onDeleteCategory(id, done) else onDeleteSubcategory(id, done)
            }) { Text(stringResource(if (saving) R.string.saving else R.string.category_delete_action), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(enabled = !saving, modifier = Modifier.testTag("cancel_category_delete"), onClick = {
                deletingId = null; onClearError()
            }) { Text(stringResource(R.string.cancel)) } })
    }
    editingIconId?.let { id ->
        AlertDialog(onDismissRequest = { if (!saving) { editingIconId = null; onClearError() } },
            title = { Text(stringResource(R.string.change_category_icon)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    catalog.custom.find { it.category.id == id }?.let { Text(it.category.name) }
                    CategoryIconPicker(selectedIcon, !saving) { selectedIcon = it }
                    error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(enabled = !saving, modifier = Modifier.testTag("save_category_icon"), onClick = {
                onUpdateIcon(id, selectedIcon) { editingIconId = null }
            }) { Text(stringResource(if (saving) R.string.saving else R.string.save)) } },
            dismissButton = { TextButton(enabled = !saving, modifier = Modifier.testTag("cancel_category_icon"), onClick = {
                editingIconId = null; onClearError()
            }) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable
private fun CategoryIconPicker(selected: CategoryIcon, enabled: Boolean, onSelect: (CategoryIcon) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    Text(stringResource(R.string.choose_category_icon), style = MaterialTheme.typography.labelLarge)
    Text(stringResource(R.string.selected_category_icon, stringResource(selected.label)), style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, enabled = enabled,
        label = { Text(stringResource(R.string.search_category_icons)) },
        modifier = Modifier.fillMaxWidth().testTag("category_icon_search"))
    val matchingIcons = CategoryIcon.entries.filter { stringResource(it.label).contains(query.trim(), ignoreCase = true) }
    if (matchingIcons.isEmpty()) Text(stringResource(R.string.no_category_icons))
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        matchingIcons.chunked(3).forEach { icons ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                icons.forEach { icon ->
                    val chosen = selected == icon
                    Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                        color = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(if (chosen) 2.dp else 1.dp,
                            if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                        Column(modifier = Modifier.testTag("category_icon_${icon.name}")
                            .selectable(selected = chosen, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(icon) })
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(painterResource(icon.drawable), contentDescription = null, modifier = Modifier.size(24.dp))
                            Text(stringResource(icon.label), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                        }
                    }
                }
                repeat(3 - icons.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
