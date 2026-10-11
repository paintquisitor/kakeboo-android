package pl.kakebo.domain

import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

data class ReceiptSuggestion(val amountGrosz: Long?, val date: LocalDate?, val merchant: String?) {
    val hasValues: Boolean get() = amountGrosz != null || date != null || merchant != null
}

/** Conservative suggestions: never infer a total from the largest number or cash tendered. */
object ReceiptParser {
    private val money = Regex("(?<![\\d.,])\\d+(?:[ \\u00a0]\\d{3})*[.,]\\d{2}(?![\\d.,])")
    private val foreignCurrency = Regex("\\b(EUR|USD|GBP|CHF|CZK|SEK|NOK|DKK|UAH)\\b|[€£$]")
    private val isoDate = Regex("(?<!\\d)(20\\d{2})[-./](\\d{2})[-./](\\d{2})(?!\\d)")
    private val localDate = Regex("(?<!\\d)(\\d{2})[-./](\\d{2})[-./](20\\d{2})(?!\\d)")

    fun parse(text: String): ReceiptSuggestion {
        val lines = text.lines().map(String::trim).filter(String::isNotEmpty)
        val normalized = lines.map(::normalize)
        val totals = normalized.mapIndexedNotNull { index, line ->
            val priority = when {
                Regex("\\b(PTU|VAT|NETTO|RABAT|OSZCZEDNOSCI)\\b").containsMatchIn(line) -> 0
                Regex("\\b(DO ZAPLATY|NALEZNOSC)\\b").containsMatchIn(line) -> 3
                Regex("\\bSUMA\\s*(PLN|ZL)\\b").containsMatchIn(line) -> 2
                Regex("\\b(SUMA|RAZEM|TOTAL)\\b").containsMatchIn(line) -> 1
                else -> 0
            }
            if (priority == 0) return@mapIndexedNotNull null
            // Some receipts print the amount on a separate line immediately below its label.
            val valueLine = if (money.containsMatchIn(line)) line else normalized.getOrNull(index + 1)
                ?.takeIf { Regex("^[\\d\\s.,]+(?:PLN|ZL|EUR|USD|GBP|CHF|€|£|\\$)?$").matches(it) }
                ?: return@mapIndexedNotNull null
            if (foreignCurrency.containsMatchIn(line + " " + valueLine)) return@mapIndexedNotNull null
            if (Regex("[-−]\\s*\\d").containsMatchIn(valueLine)) return@mapIndexedNotNull null
            val values = money.findAll(valueLine).mapNotNull { parseGrosz(it.value)?.takeIf { value -> value > 0 } }.toList()
            values.singleOrNull()?.let { priority to it }
        }
        val best = totals.maxOfOrNull { it.first }
        val amount = totals.filter { it.first == best }.map { it.second }.distinct().singleOrNull()
        val dates = lines.flatMap { line ->
            isoDate.findAll(line).mapNotNull { validDate(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }.toList() +
                localDate.findAll(line).mapNotNull { validDate(it.groupValues[3], it.groupValues[2], it.groupValues[1]) }.toList()
        }.distinct()
        val header = lines.takeWhile { !normalize(it).contains("PARAGON") }.take(5)
        // Prefer the first non-empty header line, including short brands and names with digits.
        val merchant = header.firstOrNull { line ->
            val value = normalize(line)
            line.any(Char::isLetter) &&
                !isoDate.containsMatchIn(line) && !localDate.containsMatchIn(line) &&
                !money.containsMatchIn(line) && !Regex("^\\d{2}-\\d{3}\\b").containsMatchIn(line) &&
                !Regex("^(NIP|TEL|UL|ULICA|DATA|KASA|KASJER|WWW|HTTP|SUMA|RAZEM|TOTAL|DO ZAPLATY|GOTOWKA|RESZTA|SPRZEDAZ|PTU|VAT)\\b").containsMatchIn(value)
        }?.take(100)
        return ReceiptSuggestion(amount, dates.singleOrNull(), merchant)
    }

    private fun validDate(year: String, month: String, day: String): LocalDate? =
        runCatching { LocalDate.of(year.toInt(), month.toInt(), day.toInt()) }.getOrNull()

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").replace('ł', 'l').replace('Ł', 'L').uppercase(Locale.ROOT)
}
