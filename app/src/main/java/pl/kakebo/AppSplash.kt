package pl.kakebo

import android.content.Context

enum class SplashStyle { INK, SUN }

private const val SPLASH_PREFERENCES = "splash_preferences"
private const val SPLASH_STYLE = "splash_style"

fun savedSplashStyle(context: Context): SplashStyle {
    val saved = context.getSharedPreferences(SPLASH_PREFERENCES, Context.MODE_PRIVATE)
        .getString(SPLASH_STYLE, null)
    return SplashStyle.entries.firstOrNull { it.name == saved } ?: SplashStyle.INK
}

fun saveSplashStyle(context: Context, style: SplashStyle) {
    context.getSharedPreferences(SPLASH_PREFERENCES, Context.MODE_PRIVATE)
        .edit().putString(SPLASH_STYLE, style.name).apply()
}
