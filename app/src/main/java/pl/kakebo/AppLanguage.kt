package pl.kakebo

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

private const val LANGUAGE_PREFERENCES = "language_preferences"
private const val LANGUAGE_TAG = "language_tag"

fun Context.withSavedLanguage(): Context {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return this
    val tag = getSharedPreferences(LANGUAGE_PREFERENCES, Context.MODE_PRIVATE)
        .getString(LANGUAGE_TAG, null)
        .orEmpty()
    if (tag.isBlank()) return this

    val locale = Locale.forLanguageTag(tag)
    Locale.setDefault(locale)
    val configuration = Configuration(resources.configuration).apply { setLocale(locale) }
    return createConfigurationContext(configuration)
}

fun setAppLanguage(context: Context, tag: String) {
    require(tag == "pl" || tag == "en")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            LocaleList.forLanguageTags(tag)
    } else {
        context.getSharedPreferences(LANGUAGE_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(LANGUAGE_TAG, tag)
            .apply()
        Locale.setDefault(Locale.forLanguageTag(tag))
        context.findActivity()?.recreate()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
