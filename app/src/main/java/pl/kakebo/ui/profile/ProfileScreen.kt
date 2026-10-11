package pl.kakebo.ui.profile

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pl.kakebo.AppThemeMode
import pl.kakebo.R
import pl.kakebo.SplashStyle
import pl.kakebo.setAppLanguage
import pl.kakebo.ui.components.ScreenHeader
import pl.kakebo.ui.diary.CategoryManager
import pl.kakebo.ui.diary.DiaryViewModel
import java.time.LocalDate
import java.util.Currency
import java.util.Locale

@Composable
fun ProfileScreen(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    splashStyle: SplashStyle,
    onSplashStyleChange: (SplashStyle) -> Unit,
    onPreviewSplash: () -> Unit,
    currencyCode: String,
    onCurrencyChange: (String) -> Unit,
    diaryViewModel: DiaryViewModel? = null,
    dataManager: ProfileDataManager? = null,
    appLockEnabled: Boolean = false,
    startTab: Int = 0,
    onStartTabChange: (Int) -> Unit = {},
    onAppLockChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val selectedLanguage = LocalConfiguration.current.locales[0].language
    var reminder by remember { mutableStateOf(savedReminderSettings(context)) }
    var bookName by remember { mutableStateOf(savedBookName(context)) }
    var showBookName by rememberSaveable { mutableStateOf(false) }
    var showStartScreen by rememberSaveable { mutableStateOf(false) }
    var showLanguage by rememberSaveable { mutableStateOf(false) }
    var showCurrency by rememberSaveable { mutableStateOf(false) }
    var showSplash by rememberSaveable { mutableStateOf(false) }
    var showReminder by rememberSaveable { mutableStateOf(false) }
    var showCategories by rememberSaveable { mutableStateOf(false) }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var confirmClearData by rememberSaveable { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun message(text: String) { scope.launch { snackbar.showSnackbar(text) } }
    fun runData(success: String, afterSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            runCatching { block() }.onSuccess { afterSuccess(); snackbar.showSnackbar(success) }
                .onFailure { snackbar.showSnackbar(context.getString(R.string.profile_file_error)) }
            busy = false
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null && dataManager != null) runData(context.getString(R.string.profile_backup_done)) { dataManager.createBackup(uri) }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmRestore = uri
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null && dataManager != null) runData(context.getString(R.string.profile_csv_done)) { dataManager.exportCsv(uri, currencyCode) }
    }
    var pendingReminder by remember { mutableStateOf<ReminderSettings?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingReminder?.let { settings ->
            val saved = settings.copy(enabled = granted)
            saveReminderSettings(context, saved); reminder = saved
            if (!granted) message(context.getString(R.string.profile_notification_permission))
        }
        pendingReminder = null
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            ScreenHeader(title = stringResource(R.string.profile_tab), onOpenProfile = {})
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(stringResource(R.string.profile_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp)); ProfileIdentityCard(
                        bookName.ifBlank { stringResource(R.string.profile_my_book) },
                        onClick = { showBookName = true },
                    )
                }
                item { SectionLabel(stringResource(R.string.profile_preferences)) }
                item {
                    ProfileGroup {
                        Column(Modifier.padding(14.dp)) {
                            Text(stringResource(R.string.appearance), fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp)); ThemeSelector(themeMode, onThemeModeChange)
                        }
                        ProfileDivider()
                        ProfileRow(R.drawable.ic_hero_globe_alt, stringResource(R.string.choose_language),
                            if (selectedLanguage == "pl") stringResource(R.string.language_polish) else stringResource(R.string.language_english)) { showLanguage = true }
                        ProfileDivider()
                        ProfileRow(R.drawable.ic_hero_banknotes, stringResource(R.string.choose_currency),
                            "$currencyCode · ${currencyDisplayName(currencyCode, LocalConfiguration.current.locales[0])}") { showCurrency = true }
                        ProfileDivider()
                        ProfileRow(R.drawable.ic_hero_sparkles, stringResource(R.string.profile_start_animation),
                            if (splashStyle == SplashStyle.INK) stringResource(R.string.splash_ink) else stringResource(R.string.splash_sun)) { showSplash = true }
                        ProfileDivider()
                        ProfileRow(R.drawable.ic_hero_home, stringResource(R.string.profile_start_screen), startScreenName(startTab)) {
                            showStartScreen = true
                        }
                    }
                }
                item { SectionLabel(stringResource(R.string.profile_organization)) }
                item {
                    ProfileGroup {
                        ProfileRow(R.drawable.ic_hero_squares_2x2, stringResource(R.string.profile_categories), stringResource(R.string.profile_categories_hint)) {
                            if (diaryViewModel != null) showCategories = true
                        }
                        ProfileDivider()
                        ProfileRow(R.drawable.ic_hero_calendar_days, stringResource(R.string.profile_reminder),
                            if (reminder.enabled) String.format(Locale.getDefault(), "%02d:%02d", reminder.hour, reminder.minute) else stringResource(R.string.profile_off)) {
                            showReminder = true
                        }
                    }
                }
                item { SectionLabel(stringResource(R.string.profile_data_privacy)) }
                item {
                    ProfileGroup {
                        ProfileRow(R.drawable.ic_hero_document_arrow_up, stringResource(R.string.profile_backup), stringResource(R.string.profile_backup_hint), !busy && dataManager != null) {
                            backupLauncher.launch("kakebo-${LocalDate.now()}.kakebo")
                        }
                        ProfileDivider()
                        ProfileRow(R.drawable.ic_hero_arrow_down_circle, stringResource(R.string.profile_restore), stringResource(R.string.profile_restore_hint), !busy && dataManager != null) {
                            restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                        }
                        ProfileDivider()
                        ProfileRow(R.drawable.ic_hero_chart_bar, stringResource(R.string.profile_csv), stringResource(R.string.profile_csv_hint), !busy && dataManager != null) {
                            csvLauncher.launch("kakebo-wydatki-${LocalDate.now()}.csv")
                        }
                        ProfileDivider()
                        ProfileSwitchRow(R.drawable.ic_hero_lock_closed, stringResource(R.string.profile_lock), stringResource(R.string.profile_lock_hint), appLockEnabled, onAppLockChange)
                        ProfileDivider()
                        ProfileRow(R.drawable.ic_hero_trash, stringResource(R.string.profile_clear_data), stringResource(R.string.profile_clear_data_hint), !busy && dataManager != null) {
                            confirmClearData = true
                        }
                    }
                }
                item { SectionLabel(stringResource(R.string.profile_help)) }
                item { ProfileGroup {
                    ProfileRow(R.drawable.ic_hero_lifebuoy, stringResource(R.string.profile_guide), stringResource(R.string.profile_guide_hint)) { showGuide = true }
                    ProfileDivider()
                    ProfileRow(R.drawable.ic_hero_information_circle, stringResource(R.string.profile_about), stringResource(R.string.profile_about_hint)) { showAbout = true }
                } }
                if (busy) item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp)); Text(stringResource(R.string.profile_processing), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    if (showLanguage) ChoiceDialog(stringResource(R.string.choose_language), listOf(
        "pl" to stringResource(R.string.language_polish), "en" to stringResource(R.string.language_english)
    ), selectedLanguage, { setAppLanguage(context, it); showLanguage = false }, { showLanguage = false })
    if (showBookName) BookNameDialog(bookName, onDismiss = { showBookName = false }) { name ->
        saveBookName(context, name); bookName = name; showBookName = false
    }
    if (showCurrency) CurrencyPickerDialog(currencyCode, { onCurrencyChange(it); showCurrency = false }, { showCurrency = false })
    if (showSplash) ChoiceDialog(stringResource(R.string.profile_start_animation), listOf(
        SplashStyle.INK.name to stringResource(R.string.splash_ink), SplashStyle.SUN.name to stringResource(R.string.splash_sun)
    ), splashStyle.name, { onSplashStyleChange(SplashStyle.valueOf(it)); showSplash = false }, { showSplash = false },
        extraAction = { TextButton(onClick = onPreviewSplash) { Text(stringResource(R.string.preview_splash)) } })
    if (showStartScreen) ChoiceDialog(stringResource(R.string.profile_start_screen), listOf(
        "0" to stringResource(R.string.plan_tab),
        "1" to stringResource(R.string.diary),
        "2" to stringResource(R.string.weeks_tab),
        "3" to stringResource(R.string.monthly_tab),
    ), startTab.toString(), { selected -> onStartTabChange(selected.toInt()); showStartScreen = false }, { showStartScreen = false })
    if (showReminder) ReminderDialog(reminder, onDismiss = { showReminder = false }) { settings ->
        showReminder = false
        if (settings.enabled && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingReminder = settings; notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else { saveReminderSettings(context, settings); reminder = settings }
    }
    if (showCategories && diaryViewModel != null) {
        val state by diaryViewModel.state.collectAsState()
        val saving by diaryViewModel.categorySaving.collectAsState()
        val error by diaryViewModel.categoryError.collectAsState()
        CategoryManager(state.catalog, saving, error, diaryViewModel::clearCategoryError,
            diaryViewModel::addCategory, diaryViewModel::addSubcategory, diaryViewModel::updateCategoryIcon,
            diaryViewModel::renameCategory, diaryViewModel::renameSubcategory,
            diaryViewModel::deleteCategory, diaryViewModel::deleteSubcategory) { showCategories = false }
    }
    confirmRestore?.let { uri ->
        AlertDialog(onDismissRequest = { confirmRestore = null }, title = { Text(stringResource(R.string.profile_restore_confirm_title)) },
            text = { Text(stringResource(R.string.profile_restore_confirm_text)) }, confirmButton = { Button(onClick = {
                confirmRestore = null
                if (dataManager != null) runData(context.getString(R.string.profile_restore_done), afterSuccess = {
                    bookName = savedBookName(context)
                    reminder = savedReminderSettings(context)
                    onStartTabChange(savedStartTab(context))
                }) { dataManager.restoreBackup(uri) }
            }) { Text(stringResource(R.string.profile_restore)) } }, dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text(stringResource(R.string.cancel)) } })
    }
    if (confirmClearData) AlertDialog(
        onDismissRequest = { confirmClearData = false },
        icon = { Icon(painterResource(R.drawable.ic_hero_trash), null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.profile_clear_confirm_title)) },
        text = { Text(stringResource(R.string.profile_clear_confirm_text)) },
        confirmButton = { Button(
            onClick = {
                confirmClearData = false
                if (dataManager != null) runData(context.getString(R.string.profile_clear_done)) { dataManager.clearFinancialData() }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) { Text(stringResource(R.string.profile_clear_action)) } },
        dismissButton = { TextButton(onClick = { confirmClearData = false }) { Text(stringResource(R.string.cancel)) } },
    )
    if (showGuide) GuideDialog { showGuide = false }
    if (showAbout) AboutDialog { showAbout = false }
}

@Composable private fun ProfileIdentityCard(name: String, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_hero_book_open), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.profile_local_data), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = .75f), shape = RoundedCornerShape(50)) {
                Text(stringResource(R.string.profile_local), Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(6.dp))
            Icon(painterResource(R.drawable.ic_hero_chevron_right), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun SectionLabel(text: String) = Text(text.uppercase(), style = MaterialTheme.typography.labelMedium,
    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
@Composable private fun ProfileGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) { Column(content = content) }
}
@Composable private fun ProfileDivider() = HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))

@Composable private fun ProfileRow(icon: Int, title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .10f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = LocalContentColor.current.copy(alpha = if (enabled) 1f else .45f))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(painterResource(R.drawable.ic_hero_chevron_right), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun ProfileSwitchRow(icon: Int, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .10f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }; Switch(checked, onChange)
    }
}

@Composable private fun ThemeSelector(selected: AppThemeMode, onSelect: (AppThemeMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf(AppThemeMode.LIGHT to R.string.theme_light, AppThemeMode.DARK to R.string.theme_dark, AppThemeMode.SYSTEM to R.string.profile_theme_system_short).forEachIndexed { index, item ->
            SegmentedButton(selected == item.first, { onSelect(item.first) }, SegmentedButtonDefaults.itemShape(index, 3)) {
                Text(stringResource(item.second), maxLines = 1, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable private fun startScreenName(tab: Int): String = when (tab) {
    1 -> stringResource(R.string.diary)
    2 -> stringResource(R.string.weeks_tab)
    3 -> stringResource(R.string.monthly_tab)
    else -> stringResource(R.string.plan_tab)
}

@Composable private fun BookNameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable(current) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_book_name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.profile_book_name_label)) },
                    supportingText = { Text(stringResource(R.string.profile_book_name_hint)) },
                )
            }
        },
        confirmButton = { Button(onClick = { onSave(name) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable private fun GuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_hero_lifebuoy), null) },
        title = { Text(stringResource(R.string.profile_guide)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                GuideStep(R.drawable.ic_hero_calendar_days, stringResource(R.string.profile_guide_plan_title), stringResource(R.string.profile_guide_plan_text))
                GuideStep(R.drawable.ic_hero_list_bullet, stringResource(R.string.profile_guide_diary_title), stringResource(R.string.profile_guide_diary_text))
                GuideStep(R.drawable.ic_hero_chart_bar, stringResource(R.string.profile_guide_weeks_title), stringResource(R.string.profile_guide_weeks_text))
                GuideStep(R.drawable.ic_hero_chart_pie, stringResource(R.string.profile_guide_balance_title), stringResource(R.string.profile_guide_balance_text))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable private fun GuideStep(icon: Int, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .10f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(painterResource(icon), null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun ChoiceDialog(title: String, choices: List<Pair<String,String>>, selected: String, onSelect: (String)->Unit, onDismiss: ()->Unit,
    extraAction: @Composable (() -> Unit)? = null) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Column {
        choices.forEach { choice -> Row(Modifier.fillMaxWidth().clickable { onSelect(choice.first) }.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected == choice.first, { onSelect(choice.first) }); Spacer(Modifier.width(8.dp)); Text(choice.second)
        } }
    } }, confirmButton = { if (extraAction != null) extraAction() }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } })
}

@Composable private fun CurrencyPickerDialog(selected: String, onSelect: (String)->Unit, onDismiss: ()->Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val locale = LocalConfiguration.current.locales[0]
    val currencies = remember(locale, query) { Currency.getAvailableCurrencies().map { it.currencyCode }.sorted().filter {
        query.isBlank() || it.contains(query, true) || currencyDisplayName(it, locale).contains(query, true)
    } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.choose_currency)) }, text = { Column(Modifier.heightIn(max = 520.dp)) {
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.search_currency)) })
        Spacer(Modifier.height(8.dp)); LazyColumn { items(currencies) { code ->
            Row(Modifier.fillMaxWidth().clickable { onSelect(code) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(code == selected, { onSelect(code) }); Spacer(Modifier.width(8.dp)); Column { Text(code, fontWeight = FontWeight.SemiBold); Text(currencyDisplayName(code, locale), style = MaterialTheme.typography.bodySmall) }
            }
        } }
    } }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } })
}

@Composable private fun ReminderDialog(current: ReminderSettings, onDismiss: ()->Unit, onSave:(ReminderSettings)->Unit) {
    var enabled by remember { mutableStateOf(current.enabled) }; var hour by remember { mutableIntStateOf(current.hour) }; var minute by remember { mutableIntStateOf(current.minute) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.profile_reminder)) }, text = { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.profile_reminder_enable), Modifier.weight(1f)); Switch(enabled, { enabled = it }) }
        Text(stringResource(R.string.profile_reminder_time), style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            NumberPicker(hour, 0..23, { hour = it }); Text(":", style = MaterialTheme.typography.headlineMedium); NumberPicker(minute, 0..55 step 5, { minute = it })
        }
        Text(stringResource(R.string.profile_reminder_explain), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }, confirmButton = { Button(onClick = { onSave(ReminderSettings(enabled, hour, minute)) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun NumberPicker(value: Int, range: IntProgression, onValue:(Int)->Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = { val i = range.indexOf(value); onValue(range.elementAt(if (i <= 0) range.count()-1 else i-1)) }) { Icon(painterResource(R.drawable.ic_hero_chevron_up), null) }
        Text(String.format(Locale.ROOT, "%02d", value), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        IconButton(onClick = { val i = range.indexOf(value); onValue(range.elementAt(if (i >= range.count()-1) 0 else i+1)) }) { Icon(painterResource(R.drawable.ic_hero_chevron_down), null) }
    }
}

@Composable private fun AboutDialog(onDismiss:()->Unit) {
    val context = LocalContext.current
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(painterResource(R.drawable.ic_hero_book_open), null) }, title = { Text(stringResource(R.string.brand_name)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.profile_about_text)); Text(stringResource(R.string.profile_privacy_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.profile_version, version), style = MaterialTheme.typography.labelMedium)
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } })
}

private fun currencyDisplayName(code: String, locale: Locale): String = runCatching { Currency.getInstance(code).getDisplayName(locale) }
    .getOrDefault(code).replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
