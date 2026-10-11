package pl.kakebo.ui.diary

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import pl.kakebo.ui.components.*
import pl.kakebo.MoneyCurrency
import pl.kakebo.R
import pl.kakebo.data.local.EntryEntity
import pl.kakebo.domain.CategoryIcon
import pl.kakebo.domain.ExpenseCatalog
import pl.kakebo.domain.formatMoney

@Composable
fun DiaryScreen(viewModel: DiaryViewModel, onOpenProfile: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val saveError by viewModel.saveError.collectAsStateWithLifecycle()
    val categorySaving by viewModel.categorySaving.collectAsStateWithLifecycle()
    val categoryError by viewModel.categoryError.collectAsStateWithLifecycle()
    val statementImport by viewModel.statementImport.collectAsStateWithLifecycle()
    val statementPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.readStatement(it, MoneyCurrency.code) }
    }
    var managingCategories by rememberSaveable { mutableStateOf(false) }
    val openCategories = { viewModel.clearCategoryError(); managingCategories = true }
    var editorOpen by rememberSaveable(state.month.toString()) { mutableStateOf(false) }
    var receiptFirst by rememberSaveable(state.month.toString()) { mutableStateOf(false) }
    var editedId by rememberSaveable(state.month.toString()) { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable(state.month.toString()) { mutableStateOf<Long?>(null) }
    var showCategories by rememberSaveable { mutableStateOf(false) }
    val receiptStore = rememberReceiptStore()
    var viewedReceipt by rememberSaveable { mutableStateOf<String?>(null) }
    val closeEditor = { editorOpen = false; editedId = null; receiptFirst = false; viewModel.clearSaveError() }
    val locale = LocalConfiguration.current.locales[0]
    val dayEntries = state.entries.filter { it.dateEpochDay == state.selectedDate.toEpochDay() }

    Scaffold { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            ScreenHeader(stringResource(R.string.diary), state.month,
                onPrevious = { viewModel.changeMonth(-1) }, onNext = { viewModel.changeMonth(1) }, enabled = !saving,
                monthTag = "diary_month", onOpenProfile = onOpenProfile)
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                state.loadError -> Column(Modifier.padding(24.dp)) {
                    Text(stringResource(R.string.load_error))
                    TextButton(onClick = viewModel::retry) { Text(stringResource(R.string.retry)) }
                }
                else -> LazyColumn(
                    Modifier.weight(1f).testTag("diary_list"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        DiaryDaySummary(
                            selectedDate = state.selectedDate,
                            dayTotal = dayEntries.sumOf { it.amountGrosz },
                            transactionCount = dayEntries.size,
                            locale = locale,
                            enabled = !saving,
                            showCategories = showCategories,
                            onToggleCategories = { showCategories = !showCategories },
                            onPreviousDay = { viewModel.changeDay(-1) },
                            onNextDay = { viewModel.changeDay(1) },
                        ) {
                            val totals = state.catalog.totals(dayEntries)
                            state.catalog.keys.forEach { category ->
                                Box(Modifier.semantics(mergeDescendants = true) {}) {
                                    CategoryAmountRow(category, totals[category] ?: 0, catalog = state.catalog)
                                }
                            }
                            if (totals.containsKey(null)) Box(Modifier.semantics(mergeDescendants = true) {}) {
                                CategoryAmountRow(null, totals[null] ?: 0, catalog = state.catalog)
                            }
                        }
                    }
                    item {
                        Button(
                            onClick = { editedId = null; receiptFirst = false; editorOpen = true; viewModel.clearSaveError() },
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Icon(painterResource(R.drawable.ic_hero_plus_circle), contentDescription = null, Modifier.size(21.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.add_daily_expense))
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { editedId = null; receiptFirst = true; editorOpen = true; viewModel.clearSaveError() },
                                enabled = !saving,
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("add_from_receipt"),
                                shape = RoundedCornerShape(18.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                            ) {
                                Icon(painterResource(R.drawable.ic_hero_camera), contentDescription = null, Modifier.size(20.dp))
                                Spacer(Modifier.width(7.dp))
                                Text(stringResource(R.string.diary_from_receipt), maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { statementPicker.launch(arrayOf("application/pdf")) },
                                enabled = !saving && !statementImport.reading && !statementImport.saving,
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("import_statement_pdf"),
                                shape = RoundedCornerShape(18.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                            ) {
                                Icon(painterResource(R.drawable.ic_hero_document_arrow_up), contentDescription = null, Modifier.size(20.dp))
                                Spacer(Modifier.width(7.dp))
                                Text(stringResource(R.string.diary_import_pdf), maxLines = 1)
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.diary_transactions), style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f))
                            TextButton(onClick = openCategories, modifier = Modifier.testTag("manage_categories")) {
                                Icon(painterResource(R.drawable.ic_hero_squares_2x2), contentDescription = null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.custom_categories))
                            }
                        }
                    }
                    if (dayEntries.isEmpty()) {
                        item {
                            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface) {
                                Text(stringResource(R.string.no_daily_expenses), Modifier.padding(14.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center)
                            }
                        }
                    } else {
                        item {
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column {
                                    dayEntries.forEachIndexed { index, entry ->
                                        DiaryTransactionRow(
                                            entry = entry,
                                            catalog = state.catalog,
                                            saving = saving,
                                            onEdit = { editedId = entry.id; receiptFirst = false; editorOpen = true; viewModel.clearSaveError() },
                                            onViewReceipt = { viewedReceipt = it },
                                            onDelete = { deletingId = entry.id; viewModel.clearSaveError() },
                                        )
                                        if (index != dayEntries.lastIndex) HorizontalDivider(
                                            Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                        }
                    }
                    item { Text(stringResource(R.string.local_storage), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                }
            }
        }
    }
    deletingId?.let { id -> state.entries.find { it.id == id }?.let { entry ->
        val dismissDelete = { deletingId = null; viewModel.clearSaveError() }
        AlertDialog(onDismissRequest = { if (!saving) dismissDelete() },
            title = { Text(stringResource(R.string.delete_expense)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entry.note.isNotBlank()) Text(entry.note, fontWeight = FontWeight.Bold)
                Text(formatMoney(entry.amountGrosz), style = MaterialTheme.typography.titleMedium)
                Text(LocalDate.ofEpochDay(entry.dateEpochDay).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))
                Text(stringResource(R.string.delete_expense_confirmation))
                if (saveError) Text(stringResource(R.string.save_error), color = MaterialTheme.colorScheme.error)
            } },
            confirmButton = { TextButton(enabled = !saving, modifier = Modifier.testTag("confirm_expense_delete"),
                onClick = { viewModel.deleteEntry(entry, dismissDelete) }) {
                Text(stringResource(if (saving) R.string.saving else R.string.delete), color = MaterialTheme.colorScheme.error)
            } },
            dismissButton = { TextButton(enabled = !saving, modifier = Modifier.testTag("cancel_expense_delete"), onClick = dismissDelete) {
                Text(stringResource(R.string.cancel))
            } })
    } }
    viewedReceipt?.let { name -> ReceiptPreview(name, receiptStore) { viewedReceipt = null } }
    if (editorOpen && !state.loading && !state.loadError) {
        val entry = state.entries.find { it.id == editedId }
        if (editedId == null || entry != null) ExpenseEditor(
            month = state.month, entry = entry, saving = saving, saveError = saveError, onDismiss = closeEditor,
            onSave = { viewModel.saveEntry(it, closeEditor) }, onDelete = { viewModel.deleteEntry(it, closeEditor) },
            receiptFirst = receiptFirst, catalog = state.catalog, onManageCategories = openCategories,
            initialDate = state.selectedDate,
        )
    }
    if (managingCategories) CategoryManager(
        catalog = state.catalog, saving = categorySaving, error = categoryError,
        onClearError = viewModel::clearCategoryError,
        onAddCategory = viewModel::addCategory, onAddSubcategory = viewModel::addSubcategory,
        onUpdateIcon = viewModel::updateCategoryIcon,
        onRenameCategory = viewModel::renameCategory, onRenameSubcategory = viewModel::renameSubcategory,
        onDeleteCategory = viewModel::deleteCategory, onDeleteSubcategory = viewModel::deleteSubcategory,
        onDismiss = { managingCategories = false; viewModel.clearCategoryError() },
    )

    BankStatementImportDialog(
        state = statementImport,
        onToggle = viewModel::toggleStatementExpense,
        onSelectAll = viewModel::selectAllStatementExpenses,
        onImport = viewModel::saveStatementExpenses,
        onDismiss = viewModel::dismissStatementImport,
    )

}

@Composable
private fun DiaryDaySummary(
    selectedDate: LocalDate,
    dayTotal: Long,
    transactionCount: Int,
    locale: Locale,
    enabled: Boolean,
    showCategories: Boolean,
    onToggleCategories: () -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    categoryTotals: @Composable ColumnScope.() -> Unit,
) {
    val formattedDate = selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    val dayLabel = if (selectedDate == LocalDate.now()) {
        stringResource(R.string.diary_today, selectedDate.format(DateTimeFormatter.ofPattern("d MMMM", locale)))
    } else formattedDate
    val accessibleDayTotal = stringResource(R.string.daily_total, formatMoney(dayTotal))

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPreviousDay, enabled = enabled) {
                    Icon(painterResource(R.drawable.ic_hero_chevron_left),
                        stringResource(R.string.previous_day), Modifier.size(20.dp))
                }
                Text(
                    dayLabel,
                    modifier = Modifier.weight(1f).testTag("diary_day"),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = onNextDay, enabled = enabled) {
                    Icon(painterResource(R.drawable.ic_hero_chevron_right),
                        stringResource(R.string.next_day), Modifier.size(20.dp))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.diary_day_spending), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f))
                    Text(
                        formatMoney(dayTotal),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.clearAndSetSemantics {
                            text = AnnotatedString(accessibleDayTotal)
                            testTag = "diary_day_total"
                        },
                    )
                    Text(
                        pluralStringResource(R.plurals.diary_transaction_count, transactionCount, transactionCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
                Box(
                    Modifier.size(54.dp).background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.72f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_hero_receipt_percent), contentDescription = null,
                        modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.28f),
            )
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f).clickable(onClick = onToggleCategories).padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_hero_chart_bar), contentDescription = null,
                        modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.diary_this_day), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Text(formatMoney(dayTotal), style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.testTag("diary_total"))
                IconButton(onClick = onToggleCategories, modifier = Modifier.size(34.dp)) {
                    Icon(
                        painterResource(if (showCategories) R.drawable.ic_hero_chevron_up else R.drawable.ic_hero_chevron_down),
                        contentDescription = stringResource(if (showCategories) R.string.hide_categories else R.string.show_categories),
                        modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.68f),
                    )
                }
            }
            if (showCategories) {
                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("diary_categories"), content = categoryTotals)
            }
        }
    }
}

@Composable
private fun DiaryTransactionRow(
    entry: EntryEntity,
    catalog: ExpenseCatalog,
    saving: Boolean,
    onEdit: () -> Unit,
    onViewReceipt: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val category = catalog.categoryOf(entry)
    val subcategory = entry.subcategory?.let { stringResource(it.label) }
        ?: catalog.subcategories[entry.customSubcategoryId]?.name
        ?: stringResource(R.string.uncategorized)
    val categoryPath = category?.let { "${categoryLabel(it, catalog)} · $subcategory" } ?: subcategory
    val color = categoryColor(category)
    val icon = entry.subcategory?.icon ?: category?.customId?.let { customId ->
        CategoryIcon.fromId(catalog.custom.find { it.category.id == customId }?.category?.icon)
    } ?: CategoryIcon.CARD

    Row(
        Modifier.fillMaxWidth().clickable(enabled = !saving, onClick = onEdit).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(40.dp).background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon.drawable), contentDescription = null, Modifier.size(21.dp), tint = color)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.note.ifBlank { subcategory },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                entry.receiptName?.let { name ->
                    IconButton(onClick = { onViewReceipt(name) }, modifier = Modifier.size(28.dp)) {
                        Icon(painterResource(R.drawable.ic_hero_paper_clip),
                            contentDescription = stringResource(R.string.view_receipt), Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Text(categoryPath, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(formatMoney(entry.amountGrosz), fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall, maxLines = 1)
            IconButton(
                onClick = onDelete,
                enabled = !saving,
                modifier = Modifier.size(28.dp).testTag("delete_expense_${entry.id}"),
            ) {
                Icon(painterResource(R.drawable.ic_hero_trash), contentDescription = stringResource(R.string.delete_expense),
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(17.dp))
            }
        }
    }
}
