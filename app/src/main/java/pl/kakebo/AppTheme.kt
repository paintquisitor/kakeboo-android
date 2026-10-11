package pl.kakebo

import android.content.Context

enum class AppThemeMode { SYSTEM, LIGHT, DARK }

private const val APPEARANCE_PREFERENCES = "appearance_preferences"
private const val THEME_MODE = "theme_mode"

fun savedThemeMode(context: Context): AppThemeMode {
    val saved = context.getSharedPreferences(APPEARANCE_PREFERENCES, Context.MODE_PRIVATE)
        .getString(THEME_MODE, null)
    return AppThemeMode.entries.firstOrNull { it.name == saved } ?: AppThemeMode.SYSTEM
}

fun saveThemeMode(context: Context, mode: AppThemeMode) {
    context.getSharedPreferences(APPEARANCE_PREFERENCES, Context.MODE_PRIVATE)
        .edit()
        .putString(THEME_MODE, mode.name)
        .apply()
}
