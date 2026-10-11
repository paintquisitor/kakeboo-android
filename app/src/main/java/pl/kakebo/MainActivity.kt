package pl.kakebo

import android.content.Context
import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.widget.Toast
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.view.WindowCompat
import pl.kakebo.ui.diary.DiaryScreen
import pl.kakebo.ui.diary.DiaryViewModel
import pl.kakebo.ui.home.HomeScreen
import pl.kakebo.ui.home.HomeViewModel
import pl.kakebo.ui.theme.KakeboTheme
import pl.kakebo.ui.weekly.WeeklyScreen
import pl.kakebo.ui.weekly.WeeklyViewModel
import pl.kakebo.ui.monthly.MonthlyScreen
import pl.kakebo.ui.monthly.MonthlyViewModel
import pl.kakebo.ui.profile.ProfileScreen
import pl.kakebo.ui.profile.savedAppLock
import pl.kakebo.ui.profile.savedStartTab
import pl.kakebo.ui.profile.saveAppLock
import pl.kakebo.ui.profile.saveStartTab
import pl.kakebo.ui.splash.KakeboSplash

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withSavedLanguage())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { it.remove() }
        }
        Locale.setDefault(resources.configuration.locales[0])
        enableEdgeToEdge()
        val app = application as KakeboApplication
        setContent {
            var themeMode by remember { mutableStateOf(savedThemeMode(this@MainActivity)) }
            var splashStyle by remember { mutableStateOf(savedSplashStyle(this@MainActivity)) }
            var currencyCode by remember {
                mutableStateOf(savedCurrencyCode(this@MainActivity).also(MoneyCurrency::select))
            }
            var appLockEnabled by remember { mutableStateOf(savedAppLock(this@MainActivity)) }
            var appUnlocked by rememberSaveable { mutableStateOf(!appLockEnabled) }
            var credentialPurpose by remember { mutableIntStateOf(0) }
            val credentialLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    if (credentialPurpose == 2) {
                        saveAppLock(this@MainActivity, true)
                        appLockEnabled = true
                    }
                    appUnlocked = true
                }
                credentialPurpose = 0
            }
            fun requestCredential(purpose: Int) {
                val keyguard = getSystemService(KeyguardManager::class.java)
                val intent: Intent? = keyguard.createConfirmDeviceCredentialIntent(
                    getString(R.string.profile_unlock_title), getString(R.string.profile_unlock_text),
                )
                if (intent == null) Toast.makeText(this@MainActivity, R.string.profile_lock_unavailable, Toast.LENGTH_LONG).show()
                else { credentialPurpose = purpose; credentialLauncher.launch(intent) }
            }
            KakeboTheme(themeMode) {
                val darkAppearance = MaterialTheme.colorScheme.background.luminance() < 0.5f
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !darkAppearance
                        isAppearanceLightNavigationBars = !darkAppearance
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        window.isNavigationBarContrastEnforced = false
                    }
                }
                val plan: HomeViewModel = viewModel(factory = HomeViewModel.factory(app.monthlyPlanRepository))
                val diary: DiaryViewModel = viewModel(factory = DiaryViewModel.factory(
                    app.entryRepository, app.categoryRepository, app.bankStatementPdfReader,
                ))
                val weekly: WeeklyViewModel = viewModel(factory = WeeklyViewModel.factory(app.entryRepository, app.monthlyPlanRepository, app.categoryRepository))
                val monthly: MonthlyViewModel = viewModel(factory = MonthlyViewModel.factory(app.entryRepository, app.monthlyPlanRepository, app.monthlyReflectionRepository, app.categoryRepository))
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner, appLockEnabled) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_STOP && appLockEnabled) appUnlocked = false
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                if (appLockEnabled && !appUnlocked) {
                    LaunchedEffect(Unit) { requestCredential(1) }
                    AppLockScreen { requestCredential(1) }
                    return@KakeboTheme
                }
                var startTab by remember { mutableIntStateOf(savedStartTab(this@MainActivity)) }
                var tab by rememberSaveable { mutableIntStateOf(startTab) }
                val holder = rememberSaveableStateHolder()
                var showSplash by rememberSaveable { mutableStateOf(savedInstanceState == null) }
                if (showSplash) {
                    KakeboSplash(style = splashStyle, onFinished = { showSplash = false })
                    return@KakeboTheme
                }
                Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                    ) {
                        NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                            icon = { Icon(painterResource(R.drawable.ic_hero_calendar_days), contentDescription = null) }, label = { Text(stringResource(R.string.plan_tab)) })
                        NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                            icon = { Icon(painterResource(R.drawable.ic_hero_list_bullet), contentDescription = null) }, label = { Text(stringResource(R.string.diary)) })
                        NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                            icon = { Icon(painterResource(R.drawable.ic_hero_chart_bar), contentDescription = null) }, label = { Text(stringResource(R.string.weeks_tab)) })
                        NavigationBarItem(selected = tab == 3, onClick = { tab = 3 },
                            icon = { Icon(painterResource(R.drawable.ic_hero_chart_pie), contentDescription = null) }, label = { Text(stringResource(R.string.monthly_tab)) })
                        NavigationBarItem(selected = tab == 4, onClick = { tab = 4 }, modifier = Modifier.testTag("profile_tab"),
                            icon = { Icon(painterResource(R.drawable.ic_hero_user), contentDescription = null) }, label = { Text(stringResource(R.string.profile_tab)) })
                    }
                }) { padding ->
                    Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
                        holder.SaveableStateProvider(tab) {
                            when (tab) {
                                0 -> HomeScreen(plan, onOpenProfile = { tab = 4 })
                                1 -> DiaryScreen(diary, onOpenProfile = { tab = 4 })
                                2 -> WeeklyScreen(weekly, onOpenProfile = { tab = 4 })
                                3 -> MonthlyScreen(monthly, onOpenProfile = { tab = 4 })
                                else -> ProfileScreen(
                                    themeMode = themeMode,
                                    onThemeModeChange = { mode ->
                                        saveThemeMode(this@MainActivity, mode)
                                        themeMode = mode
                                    },
                                    splashStyle = splashStyle,
                                    onSplashStyleChange = { style ->
                                        saveSplashStyle(this@MainActivity, style)
                                        splashStyle = style
                                    },
                                    onPreviewSplash = { showSplash = true },
                                    currencyCode = currencyCode,
                                    onCurrencyChange = { code ->
                                        MoneyCurrency.select(code)
                                        saveCurrencyCode(this@MainActivity, code)
                                        currencyCode = code
                                    },
                                    diaryViewModel = diary,
                                    dataManager = app.profileDataManager,
                                    appLockEnabled = appLockEnabled,
                                    startTab = startTab,
                                    onStartTabChange = { selected ->
                                        saveStartTab(this@MainActivity, selected)
                                        startTab = selected
                                    },
                                    onAppLockChange = { enabled ->
                                        if (enabled) requestCredential(2)
                                        else {
                                            saveAppLock(this@MainActivity, false)
                                            appLockEnabled = false
                                            appUnlocked = true
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppLockScreen(onUnlock: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(painterResource(R.drawable.ic_hero_lock_closed), null, Modifier.padding(22.dp).size(42.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(22.dp))
            Text(stringResource(R.string.profile_unlock_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.profile_unlock_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onUnlock) { Text(stringResource(R.string.profile_unlock_button)) }
        }
    }
}
