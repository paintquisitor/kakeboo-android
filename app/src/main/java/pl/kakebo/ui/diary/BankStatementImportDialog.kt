package pl.kakebo.ui.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import pl.kakebo.R
import pl.kakebo.domain.formatMoney

@Composable
internal fun BankStatementImportDialog(
    state: StatementImportUiState,
    onToggle: (Int) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    when {
        state.reading -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.statement_reading)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.statement_reading_hint))
                }
            },
            confirmButton = {},
        )

        state.importedCount != null -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.statement_imported, state.importedCount)) },
            text = { Text(stringResource(R.string.statement_imported_hint)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
        )

        state.candidates.isNotEmpty() || state.duplicates > 0 -> StatementPreview(
            state = state,
            onToggle = onToggle,
            onSelectAll = onSelectAll,
            onImport = onImport,
            onDismiss = onDismiss,
        )

        state.error != null -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.statement_import_title)) },
            text = { Text(statementErrorText(state)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        )
    }
}

@Composable
private fun StatementPreview(
    state: StatementImportUiState,
    onToggle: (Int) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedCount = state.selectedIds.size
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy", locale)
    AlertDialog(
        onDismissRequest = { if (!state.saving) onDismiss() },
        title = { Text(stringResource(R.string.statement_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.statement_expenses_found, state.candidates.size), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.statement_ignored_savings, state.ignoredSavings), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.statement_ignored_credits, state.ignoredCredits), style = MaterialTheme.typography.bodySmall)
                if (state.duplicates > 0) {
                    Text(stringResource(R.string.statement_duplicates, state.duplicates), style = MaterialTheme.typography.bodySmall)
                }
                if (state.unrecognized > 0) {
                    Text(stringResource(R.string.statement_unrecognized, state.unrecognized), style = MaterialTheme.typography.bodySmall)
                }
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        stringResource(R.string.statement_fixed_expense_warning),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
                if (state.candidates.isEmpty()) {
                    Text(stringResource(R.string.statement_no_new_expenses))
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.statement_selected, selectedCount), modifier = Modifier.weight(1f))
                        TextButton(
                            enabled = !state.saving,
                            onClick = { onSelectAll(selectedCount != state.candidates.size) },
                        ) {
                            Text(stringResource(
                                if (selectedCount == state.candidates.size) R.string.statement_clear_selection
                                else R.string.statement_select_all,
                            ))
                        }
                    }
                    HorizontalDivider()
                    LazyColumn(Modifier.heightIn(max = 360.dp).testTag("statement_expense_list")) {
                        items(state.candidates, key = { it.id }) { expense ->
                            val selected = expense.id in state.selectedIds
                            Row(
                                Modifier.fillMaxWidth()
                                    .toggleable(
                                        value = selected,
                                        enabled = !state.saving,
                                        role = Role.Checkbox,
                                        onValueChange = { onToggle(expense.id) },
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = selected, onCheckedChange = null, enabled = !state.saving)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(expense.note, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(expense.date.format(dateFormat), style = MaterialTheme.typography.bodySmall)
                                }
                                Text(formatMoney(expense.amountMinor), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                if (state.error == StatementImportError.SAVE_FAILED) {
                    Text(stringResource(R.string.statement_save_error), color = MaterialTheme.colorScheme.error)
                }
                if (state.saving) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.saving))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedCount > 0 && !state.saving,
                modifier = Modifier.testTag("confirm_statement_import"),
                onClick = onImport,
            ) { Text(stringResource(R.string.statement_add_selected, selectedCount)) }
        },
        dismissButton = {
            TextButton(enabled = !state.saving, onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun statementErrorText(state: StatementImportUiState): String = when (state.error) {
    StatementImportError.NO_EXPENSES -> stringResource(R.string.statement_no_expenses)
    StatementImportError.CURRENCY_MISMATCH -> stringResource(
        R.string.statement_currency_mismatch,
        state.sourceCurrency.orEmpty(),
        state.expectedCurrency.orEmpty(),
    )
    StatementImportError.SAVE_FAILED -> stringResource(R.string.statement_save_error)
    else -> stringResource(R.string.statement_read_error)
}
