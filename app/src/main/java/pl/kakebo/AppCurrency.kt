package pl.kakebo

import android.content.Context
import java.util.Currency

private const val CURRENCY_PREFERENCES = "currency_preferences"
private const val CURRENCY_KEY = "currency_code"
const val DEFAULT_CURRENCY_CODE = "PLN"

object MoneyCurrency {
    @Volatile
    var code: String = DEFAULT_CURRENCY_CODE
        private set

    fun select(currencyCode: String) {
        code = runCatching { Currency.getInstance(currencyCode).currencyCode }
            .getOrDefault(DEFAULT_CURRENCY_CODE)
    }
}

fun savedCurrencyCode(context: Context): String {
    val saved = context.getSharedPreferences(CURRENCY_PREFERENCES, Context.MODE_PRIVATE)
        .getString(CURRENCY_KEY, DEFAULT_CURRENCY_CODE)
        .orEmpty()
    return runCatching { Currency.getInstance(saved).currencyCode }
        .getOrDefault(DEFAULT_CURRENCY_CODE)
}

fun saveCurrencyCode(context: Context, currencyCode: String) {
    context.getSharedPreferences(CURRENCY_PREFERENCES, Context.MODE_PRIVATE)
        .edit().putString(CURRENCY_KEY, currencyCode).apply()
}
