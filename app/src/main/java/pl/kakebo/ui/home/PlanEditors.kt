package pl.kakebo.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import pl.kakebo.R
import pl.kakebo.MoneyCurrency
import pl.kakebo.data.local.EntryType
import pl.kakebo.data.local.MonthlyPlanEntity
import pl.kakebo.data.local.PlanRecurrence
import pl.kakebo.data.local.PlannedItemEntity
import pl.kakebo.domain.FixedExpenseGroup
import pl.kakebo.domain.FixedExpensePreset
import pl.kakebo.domain.CategoryIcon
import pl.kakebo.domain.amountInput
import pl.kakebo.domain.parseGrosz

@Composable
internal fun ItemEditor(
    month: YearMonth,
    type: EntryType,
    item: PlannedItemEntity?,
    saving: Boolean,
    saveError: Boolean,
    onDismiss: () -> Unit,
    onSave: (PlannedItemEntity) -> Unit,
    onDelete: (PlannedItemEntity) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(item?.title.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf(item?.let { amountInput(it.amountGrosz) }.orEmpty()) }
    var day by rememberSaveable {
        mutableStateOf((item?.let { LocalDate.ofEpochDay(it.dateEpochDay).dayOfMonth }
            ?: if (month == YearMonth.now()) LocalDate.now().dayOfMonth else 1).toString())
    }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var selectedPresetName by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingGroupName by rememberSaveable { mutableStateOf<String?>(null) }
    var recurrenceName by rememberSaveable { mutableStateOf((item?.recurrence ?: PlanRecurrence.MONTHLY).name) }
    var inferredIcon = CategoryIcon.DISCOUNTS
    if (item?.icon?.isNotBlank() == true) {
        inferredIcon = CategoryIcon.fromId(item.icon)
    } else if (item != null && type == EntryType.EXPENSE) {
        FixedExpensePreset.entries.forEach { preset ->
            if (!preset.custom && stringResource(preset.label).equals(item.title, ignoreCase = true)) {
                inferredIcon = preset.icon
            }
        }
    }
    var iconName by rememberSaveable(item?.id) { mutableStateOf(inferredIcon.name) }
    var showIconPicker by rememberSaveable { mutableStateOf(false) }
    val selectedPreset = selectedPresetName?.let { name -> FixedExpensePreset.entries.find { it.name == name } }
    val pendingGroup = pendingGroupName?.let { name -> FixedExpenseGroup.entries.find { it.name == name } }
    val recurrence = PlanRecurrence.valueOf(recurrenceName)
    val usesPresetPicker = item == null && type == EntryType.EXPENSE
    val titleValid = title.isNotBlank() && (!usesPresetPicker || selectedPreset != null)
    val parsedAmount = parseGrosz(amount)?.takeIf { it > 0 }
    val parsedDay = day.toIntOrNull()?.takeIf { it in 1..month.lengthOfMonth() }

    if (!confirmDelete) AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(when {
            item != null -> R.string.edit_item
            type == EntryType.INCOME -> R.string.add_income
            else -> R.string.add_expense
        })) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (usesPresetPicker && selectedPreset == null) {
                    FixedExpenseGroupGrid(!saving) { pendingGroupName = it.name }
                    if (submitted) Text(stringResource(R.string.fixed_expense_required), color = MaterialTheme.colorScheme.error)
                } else if (usesPresetPicker && selectedPreset != null) {
                    SelectedFixedExpenseCard(selectedPreset, !saving) {
                        selectedPresetName = null
                        title = ""
                    }
                }
                if (!usesPresetPicker || selectedPreset?.custom == true) {
                    OutlinedTextField(
                        value = title, onValueChange = { title = it.take(120) },
                        label = { Text(stringResource(R.string.item_title)) },
                        isError = submitted && title.isBlank(),
                        supportingText = { if (submitted && title.isBlank()) Text(stringResource(R.string.title_required)) },
                        singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it.take(32) },
                    label = { Text(stringResource(R.string.amount_label, MoneyCurrency.code)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = submitted && parsedAmount == null,
                    supportingText = { if (submitted && parsedAmount == null) Text(stringResource(R.string.amount_positive_error, MoneyCurrency.code)) },
                    singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = day, onValueChange = { day = it.take(2) },
                    label = { Text(stringResource(R.string.day_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = submitted && parsedDay == null,
                    supportingText = { Text(stringResource(R.string.day_hint, month.lengthOfMonth(), month.toString())) },
                    singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth(),
                )
                if (type == EntryType.EXPENSE) {
                    PlannedItemIconField(
                        selected = CategoryIcon.fromId(iconName),
                        enabled = !saving,
                        onClick = { showIconPicker = true },
                    )
                    RecurrencePicker(recurrence, !saving) { recurrenceName = it.name }
                }
                if (saveError) SaveError()
                if (item != null) TextButton(
                    onClick = { confirmDelete = true },
                    enabled = !saving,
                    modifier = Modifier.testTag("delete_planned_item"),
                ) {
                    Text(stringResource(R.string.delete_item), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                submitted = true
                if (titleValid && parsedAmount != null && parsedDay != null) {
                    val anchor = if (item != null && item.recurrence == recurrence) item.recurrenceAnchorMonth else month.toString()
                    onSave(PlannedItemEntity(
                        id = item?.id ?: 0, month = month.toString(), type = type,
                        title = title.trim(), amountGrosz = parsedAmount,
                        dateEpochDay = month.atDay(parsedDay).toEpochDay(),
                        recurrence = recurrence, recurrenceAnchorMonth = anchor,
                        icon = if (type == EntryType.EXPENSE) iconName else item?.icon.orEmpty(),
                    ))
                }
            }) { Text(stringResource(if (saving) R.string.saving else R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) } },
    )
    if (pendingGroup != null) FixedExpensePresetDialog(
        group = pendingGroup,
        selected = selectedPreset,
        enabled = !saving,
        onDismiss = { pendingGroupName = null },
        onSelect = { preset, label ->
            selectedPresetName = preset.name
            title = if (preset.custom) "" else label
            iconName = preset.icon.name
            pendingGroupName = null
        },
    )
    if (showIconPicker) PlannedItemIconDialog(
        selected = CategoryIcon.fromId(iconName),
        enabled = !saving,
        onDismiss = { showIconPicker = false },
        onSelect = {
            iconName = it.name
            showIconPicker = false
        },
    )
    if (confirmDelete && item != null) {
        AlertDialog(
            onDismissRequest = { if (!saving) confirmDelete = false },
            title = { Text(stringResource(R.string.delete_item)) },
            text = {
                Column {
                    Text(stringResource(R.string.delete_confirmation, item.title))
                    if (saveError) SaveError()
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onDelete(item) },
                    enabled = !saving,
                    modifier = Modifier.testTag("confirm_planned_item_delete"),
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }, enabled = !saving) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun PlannedItemIconField(selected: CategoryIcon, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag("planned_item_icon_field"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(painterResource(selected.drawable), null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.choose_category_icon), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(selected.label), style = MaterialTheme.typography.titleSmall)
            }
            Text(stringResource(R.string.change_category_icon), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PlannedItemIconDialog(
    selected: CategoryIcon,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSelect: (CategoryIcon) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val icons = CategoryIcon.entries.filter { stringResource(it.label).contains(query.trim(), ignoreCase = true) }
    AlertDialog(
        modifier = Modifier.testTag("planned_item_icon_picker"),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_category_icon)) },
        text = {
            Column(
                Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    enabled = enabled,
                    label = { Text(stringResource(R.string.search_category_icons)) },
                    modifier = Modifier.fillMaxWidth().testTag("planned_item_icon_search"),
                )
                if (icons.isEmpty()) Text(stringResource(R.string.no_category_icons))
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    icons.chunked(3).forEach { rowIcons ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowIcons.forEach { icon ->
                                val chosen = icon == selected
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        if (chosen) 2.dp else 1.dp,
                                        if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    ),
                                ) {
                                    Column(
                                        Modifier.testTag("planned_item_icon_${icon.name}").selectable(
                                            selected = chosen,
                                            enabled = enabled,
                                            role = Role.RadioButton,
                                            onClick = { onSelect(icon) },
                                        ).padding(horizontal = 4.dp, vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(painterResource(icon.drawable), null, Modifier.size(24.dp))
                                        Text(stringResource(icon.label), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                            repeat(3 - rowIcons.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, enabled = enabled) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun FixedExpenseGroupGrid(enabled: Boolean, onSelect: (FixedExpenseGroup) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.choose_fixed_expense), style = MaterialTheme.typography.labelMedium)
        Column(Modifier.fillMaxWidth().selectableGroup().testTag("fixed_group_picker"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FixedExpenseGroup.entries.chunked(2).forEach { rowGroups ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowGroups.forEach { group ->
                        Surface(
                            modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 92.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Column(
                                Modifier.testTag("fixed_group_${group.name}").selectable(
                                    selected = false, enabled = enabled, role = Role.RadioButton,
                                    onClick = { onSelect(group) },
                                ).padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(painterResource(group.icon.drawable), null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(stringResource(group.label), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    repeat(2 - rowGroups.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun FixedExpensePresetDialog(
    group: FixedExpenseGroup,
    selected: FixedExpensePreset?,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSelect: (FixedExpensePreset, String) -> Unit,
) {
    val choices = FixedExpensePreset.entries.filter { it.group == group }
    AlertDialog(
        modifier = Modifier.testTag("fixed_preset_picker"),
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(painterResource(group.icon.drawable), null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(stringResource(R.string.choose_fixed_expense_item), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(group.label), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.chunked(2).forEach { rowChoices ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowChoices.forEach { preset ->
                            val label = stringResource(preset.label)
                            val chosen = selected == preset
                            Surface(
                                modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 88.dp),
                                shape = RoundedCornerShape(14.dp),
                                color = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(if (chosen) 2.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Column(
                                    Modifier.testTag("fixed_preset_${preset.name}").selectable(
                                        selected = chosen, enabled = enabled, role = Role.RadioButton,
                                        onClick = { onSelect(preset, label) },
                                    ).padding(horizontal = 8.dp, vertical = 9.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Icon(painterResource(preset.icon.drawable), null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                                }
                            }
                        }
                        repeat(2 - rowChoices.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, enabled = enabled) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SelectedFixedExpenseCard(preset: FixedExpensePreset, enabled: Boolean, onChange: () -> Unit) {
    Surface(
        onClick = onChange,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag("fixed_expense_picker"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(preset.icon.drawable), null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(stringResource(preset.group.label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(preset.label), style = MaterialTheme.typography.titleSmall)
            }
            Text(stringResource(R.string.change_category_selection), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun RecurrencePicker(selected: PlanRecurrence, enabled: Boolean, onSelect: (PlanRecurrence) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.recurrence_label), style = MaterialTheme.typography.labelMedium)
        PlanRecurrence.entries.forEach { recurrence ->
            val label = when (recurrence) {
                PlanRecurrence.MONTHLY -> R.string.recurrence_monthly
                PlanRecurrence.QUARTERLY -> R.string.recurrence_quarterly
                PlanRecurrence.YEARLY -> R.string.recurrence_yearly
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = if (selected == recurrence) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(if (selected == recurrence) 2.dp else 1.dp, if (selected == recurrence) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    Modifier.testTag("recurrence_${recurrence.name}").selectable(
                        selected = selected == recurrence, enabled = enabled, role = Role.RadioButton,
                        onClick = { onSelect(recurrence) },
                    ).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RadioButton(selected == recurrence, onClick = null, enabled = enabled)
                    Text(stringResource(label))
                }
            }
        }
        Text(stringResource(R.string.recurrence_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun GoalsEditor(
    plan: MonthlyPlanEntity,
    saving: Boolean,
    saveError: Boolean,
    onDismiss: () -> Unit,
    onSave: (MonthlyPlanEntity) -> Unit,
) {
    var amount by rememberSaveable { mutableStateOf(amountInput(plan.savingsGrosz)) }
    var resolutions by rememberSaveable { mutableStateOf(plan.resolutions) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val parsedAmount = parseGrosz(amount)
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.goals_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it.take(32) },
                    label = { Text(stringResource(R.string.savings_amount, MoneyCurrency.code)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = submitted && parsedAmount == null,
                    supportingText = {
                        Text(
                            if (submitted && parsedAmount == null) {
                                stringResource(R.string.savings_error, MoneyCurrency.code)
                            } else {
                                stringResource(R.string.savings_hint)
                            },
                        )
                    },
                    singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = resolutions, onValueChange = { resolutions = it.take(2000) },
                    label = { Text(stringResource(R.string.resolutions_label)) },
                    placeholder = { Text(stringResource(R.string.resolutions_example)) },
                    minLines = 3, maxLines = 5, enabled = !saving, modifier = Modifier.fillMaxWidth(),
                )
                if (saveError) SaveError()
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                submitted = true
                if (parsedAmount != null) onSave(plan.copy(savingsGrosz = parsedAmount, resolutions = resolutions.trim()))
            }) { Text(stringResource(if (saving) R.string.saving else R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SaveError() {
    Text(stringResource(R.string.save_error), color = MaterialTheme.colorScheme.error)
}
