package pl.kakebo.ui.diary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import pl.kakebo.R
import pl.kakebo.MoneyCurrency
import pl.kakebo.data.local.EntryEntity
import pl.kakebo.data.local.EntryType
import pl.kakebo.domain.*
import pl.kakebo.ui.components.CategoryBadge
import pl.kakebo.ui.components.categoryColor
import pl.kakebo.ui.components.categoryLabel

private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT)

@Composable
internal fun ExpenseEditor(
    month: YearMonth,
    entry: EntryEntity?,
    saving: Boolean,
    saveError: Boolean,
    onDismiss: () -> Unit,
    onSave: (EntryEntity) -> Unit,
    onDelete: (EntryEntity) -> Unit,
    receiptFirst: Boolean = false,
    catalog: ExpenseCatalog = ExpenseCatalog(),
    onManageCategories: (() -> Unit)? = null,
    initialDate: LocalDate = if (month == YearMonth.now()) LocalDate.now() else month.atDay(1),
) {
    var note by rememberSaveable { mutableStateOf(entry?.note.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf(entry?.let { amountInput(it.amountGrosz) }.orEmpty()) }
    var date by rememberSaveable {
        mutableStateOf((entry?.let { LocalDate.ofEpochDay(it.dateEpochDay) }
            ?: initialDate).format(dateFormat))
    }
    var category by rememberSaveable { mutableStateOf(entry?.subcategory?.category) }
    var subcategory by rememberSaveable { mutableStateOf(entry?.subcategory) }
    var customCategoryId by rememberSaveable { mutableStateOf(entry?.customSubcategoryId?.let { catalog.subcategories[it]?.categoryId }) }
    var customSubcategoryId by rememberSaveable { mutableStateOf(entry?.customSubcategoryId) }
    val selectedCategory = category?.key ?: customCategoryId?.let { CategoryKey(customId = it) }
    val subcategoryChoices = category?.subcategories.orEmpty().map { SubcategoryChoice(it, null, stringResource(it.label)) } +
        catalog.subcategories.values.filter { it.categoryId == customCategoryId }.sortedBy { it.id }.map { SubcategoryChoice(null, it.id, it.name) }
    val selectedSubcategory = subcategoryChoices.find { (it.builtIn != null && it.builtIn == subcategory) || (it.customId != null && it.customId == customSubcategoryId) }
    var choosingCategory by rememberSaveable(entry?.id) { mutableStateOf(selectedSubcategory == null) }
    var pendingBuiltInCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCustomCategoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    val pendingCategory = pendingBuiltInCategory?.let { name -> ExpenseCategory.entries.find { it.name == name }?.key }
        ?: pendingCustomCategoryId?.let { CategoryKey(customId = it) }
    val pendingSubcategoryChoices = pendingCategory?.builtIn?.subcategories.orEmpty()
        .map { SubcategoryChoice(it, null, stringResource(it.label)) } +
        catalog.subcategories.values.filter { it.categoryId == pendingCategory?.customId }.sortedBy { it.id }
            .map { SubcategoryChoice(null, it.id, it.name) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val receiptStore = rememberReceiptStore()
    var receiptName by rememberSaveable { mutableStateOf(entry?.receiptName) }
    var receiptBusy by remember { mutableStateOf(false) }
    val busy = saving || receiptBusy
    val dismiss = {
        receiptStore.discardDraft(receiptName)
        onDismiss()
    }
    val parsedAmount = parseGrosz(amount)?.takeIf { it > 0 }
    val parsedDate = runCatching { LocalDate.parse(date, dateFormat) }.getOrNull()
        ?.takeIf { it.year in 1..9999 }
    val receiptSection: @Composable () -> Unit = {
        ReceiptAttachment(receiptName, !busy, receiptStore,
            onChange = { replacement ->
                if (receiptName != replacement) receiptStore.discardDraft(receiptName)
                receiptName = replacement
            }, onBusyChange = { receiptBusy = it }, onRecognized = { recognized ->
                recognized.amountGrosz?.let { amount = amountInput(it) }
                recognized.date?.let { date = it.format(dateFormat) }
                recognized.merchant?.let { note = it }
            })
        if (receiptBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }

    AlertDialog(
        onDismissRequest = { if (!busy) dismiss() },
        title = { Text(stringResource(when {
            entry != null -> R.string.edit_daily_expense
            receiptFirst -> R.string.add_from_receipt
            else -> R.string.add_daily_expense
        })) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (receiptFirst) {
                    Text(stringResource(R.string.add_from_receipt_hint), style = MaterialTheme.typography.bodySmall)
                    receiptSection()
                }
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it.take(32) },
                    label = { Text(stringResource(R.string.amount_label, MoneyCurrency.code)) },
                    textStyle = MaterialTheme.typography.headlineMedium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = submitted && parsedAmount == null,
                    supportingText = { if (submitted && parsedAmount == null) Text(stringResource(R.string.amount_positive_error, MoneyCurrency.code)) },
                    singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = date, onValueChange = { date = it.take(10) },
                    label = { Text(stringResource(R.string.expense_date)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    isError = submitted && parsedDate == null,
                    supportingText = { if (submitted && parsedDate == null) Text(stringResource(R.string.expense_date_error)) },
                    singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                )
                if (!choosingCategory && selectedCategory != null && selectedSubcategory != null) {
                    SelectedSubcategoryCard(
                        category = selectedCategory,
                        subcategory = selectedSubcategory,
                        catalog = catalog,
                        enabled = !busy,
                        onChange = { choosingCategory = true },
                    )
                } else {
                    CategoryGrid(
                        selected = selectedCategory, choices = catalog.keys, catalog = catalog, enabled = !busy,
                        onSelect = {
                            pendingBuiltInCategory = it.builtIn?.name
                            pendingCustomCategoryId = it.customId
                        },
                    )
                }
                onManageCategories?.let { manage ->
                    TextButton(onClick = manage, enabled = !busy, modifier = Modifier.testTag("manage_categories_editor")) {
                        Text(stringResource(R.string.manage_categories))
                    }
                }
                if (submitted && selectedSubcategory == null) Text(stringResource(R.string.category_required), color = MaterialTheme.colorScheme.error)
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(500) },
                    label = { Text(stringResource(R.string.expense_note)) },
                    minLines = 1, maxLines = 3, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                )
                if (!receiptFirst) receiptSection()
                if (saveError) Text(stringResource(R.string.save_error), color = MaterialTheme.colorScheme.error)
                if (entry != null) TextButton(onClick = { confirmDelete = true }, enabled = !busy) {
                    Text(stringResource(R.string.delete_item), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                submitted = true
                if (parsedAmount != null && parsedDate != null && selectedSubcategory != null) {
                    onSave(EntryEntity(id = entry?.id ?: 0, amountGrosz = parsedAmount, type = EntryType.EXPENSE,
                        dateEpochDay = parsedDate.toEpochDay(), note = note.trim(), subcategory = subcategory, // Zachowanie starych wpisów bez podwójnego naliczania kosztu.
                        plannedItemId = entry?.plannedItemId?.takeIf { YearMonth.from(parsedDate) == month },
                        receiptName = receiptName, customSubcategoryId = customSubcategoryId))
                }
            }) { Text(stringResource(if (busy) R.string.saving else R.string.save)) }
        },
        dismissButton = { TextButton(onClick = dismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
    )
    if (pendingCategory != null) SubcategoryPickerDialog(
        category = pendingCategory,
        choices = pendingSubcategoryChoices,
        selected = selectedSubcategory,
        catalog = catalog,
        enabled = !busy,
        onDismiss = {
            pendingBuiltInCategory = null
            pendingCustomCategoryId = null
        },
        onSelect = { choice ->
            category = pendingCategory.builtIn
            customCategoryId = pendingCategory.customId
            subcategory = choice.builtIn
            customSubcategoryId = choice.customId
            pendingBuiltInCategory = null
            pendingCustomCategoryId = null
            choosingCategory = false
        },
    )
    if (confirmDelete && entry != null) AlertDialog(
        onDismissRequest = { if (!busy) confirmDelete = false },
        title = { Text(stringResource(R.string.delete_item)) },
        text = { Column {
            Text(stringResource(R.string.delete_expense_confirmation))
            if (saveError) Text(stringResource(R.string.save_error), color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(modifier = Modifier.testTag("confirm_editor_expense_delete"), onClick = { receiptStore.discardDraft(receiptName); receiptName = entry.receiptName; onDelete(entry) }, enabled = !busy) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun CategoryGrid(
    selected: CategoryKey?,
    choices: List<CategoryKey>,
    catalog: ExpenseCatalog,
    enabled: Boolean,
    onSelect: (CategoryKey) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.expense_category), style = MaterialTheme.typography.labelMedium)
        Column(
            modifier = Modifier.fillMaxWidth().selectableGroup().testTag("category_picker"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            choices.chunked(2).forEach { rowChoices ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowChoices.forEach { choice ->
                        val chosen = selected == choice
                        val tag = choice.builtIn?.name ?: "custom_${choice.customId}"
                        Surface(
                            modifier = Modifier.weight(1f).height(92.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                if (chosen) 2.dp else 1.dp,
                                if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            ),
                        ) {
                            Column(
                                modifier = Modifier
                                    .testTag("category_choice_$tag")
                                    .selectable(
                                        selected = chosen,
                                        enabled = enabled,
                                        role = Role.RadioButton,
                                        onClick = { onSelect(choice) },
                                    )
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                CategoryBadge(choice, size = 40.dp, catalog = catalog)
                                Text(
                                    categoryLabel(choice, catalog),
                                    style = MaterialTheme.typography.labelMedium,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    repeat(2 - rowChoices.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun SelectedSubcategoryCard(
    category: CategoryKey,
    subcategory: SubcategoryChoice,
    catalog: ExpenseCatalog,
    enabled: Boolean,
    onChange: () -> Unit,
) {
    val color = categoryColor(category)
    Surface(
        onClick = onChange,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag("category_picker"),
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(40.dp).testTag("selected_subcategory_icon"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(subcategoryIcon(subcategory, catalog).drawable),
                    contentDescription = subcategory.name,
                    modifier = Modifier.size(28.dp),
                    tint = color,
                )
            }
            Column(Modifier.weight(1f).testTag("subcategory_picker")) {
                Text(categoryLabel(category, catalog), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(subcategory.name, style = MaterialTheme.typography.titleSmall)
            }
            Text(stringResource(R.string.change_category_selection), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SubcategoryPickerDialog(
    category: CategoryKey,
    choices: List<SubcategoryChoice>,
    selected: SubcategoryChoice?,
    catalog: ExpenseCatalog,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSelect: (SubcategoryChoice) -> Unit,
) {
    val color = categoryColor(category)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CategoryBadge(category, 40.dp, catalog)
                Column {
                    Text(stringResource(R.string.choose_subcategory), style = MaterialTheme.typography.titleLarge)
                    Text(categoryLabel(category, catalog), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        text = {
            if (choices.isEmpty()) {
                Text(stringResource(R.string.custom_subcategories_empty))
            } else {
                Column(
                    Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()).testTag("subcategory_picker"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    choices.chunked(2).forEach { rowChoices ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowChoices.forEach { choice ->
                                val chosen = selected == choice
                                val tag = choice.builtIn?.name ?: "custom_${choice.customId}"
                                Surface(
                                    modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 88.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (chosen) color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(if (chosen) 2.dp else 1.dp, if (chosen) color else MaterialTheme.colorScheme.outlineVariant),
                                ) {
                                    Column(
                                        Modifier.testTag("subcategory_choice_$tag").selectable(
                                            selected = chosen,
                                            enabled = enabled,
                                            role = Role.RadioButton,
                                            onClick = { onSelect(choice) },
                                        ).padding(horizontal = 8.dp, vertical = 9.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        Icon(
                                            painterResource(subcategoryIcon(choice, catalog).drawable),
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp),
                                            tint = color,
                                        )
                                        Text(choice.name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                            repeat(2 - rowChoices.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, enabled = enabled) { Text(stringResource(R.string.cancel)) } },
    )
}

private data class SubcategoryChoice(val builtIn: ExpenseSubcategory?, val customId: Long?, val name: String)

private fun subcategoryIcon(choice: SubcategoryChoice, catalog: ExpenseCatalog): CategoryIcon =
    choice.builtIn?.icon ?: CategoryIcon.fromId(
        choice.customId?.let { catalog.subcategories[it]?.categoryId }?.let { categoryId ->
            catalog.custom.find { it.category.id == categoryId }?.category?.icon
        },
    )
