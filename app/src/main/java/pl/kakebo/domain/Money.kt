package pl.kakebo.domain

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import pl.kakebo.MoneyCurrency

// Maksymalna kwota pojedynczej pozycji formularza: 999 999 999,99 zł.
private const val MAX_AMOUNT_GROSZ = 99_999_999_999L

fun parseGrosz(text: String): Long? {
    val normalized = text.trim().replace(" ", "").replace("\u00A0", "").replace(',', '.')
    if (!Regex("[0-9]+(\\.[0-9]{1,2})?").matches(normalized)) return null
    return runCatching { BigDecimal(normalized).movePointRight(2).longValueExact() }
        .getOrNull()?.takeIf { it in 0..MAX_AMOUNT_GROSZ }
}

fun amountInput(grosz: Long, locale: Locale = Locale.getDefault()): String {
    val value = BigDecimal.valueOf(grosz, 2).toPlainString()
    return if (locale.language == "pl") value.replace('.', ',') else value
}

fun formatMoney(grosz: Long, locale: Locale = Locale.getDefault()): String =
    NumberFormat.getCurrencyInstance(locale)
    .apply {
        currency = Currency.getInstance(MoneyCurrency.code)
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    .format(BigDecimal.valueOf(grosz, 2))
