package pl.kakebo.domain

import java.time.LocalDate

data class BankStatementExpense(
    val date: LocalDate,
    val amountMinor: Long,
    val currencyCode: String,
    val note: String,
)

data class BankStatementParseResult(
    val expenses: List<BankStatementExpense>,
    val ignoredSavings: Int,
    val ignoredCredits: Int,
    val unrecognized: Int,
)

object BankStatementParser {
    private val operationStart = Regex(
        "Data\\s+operacji\\b",
        RegexOption.IGNORE_CASE,
    )
    private val operationDate = Regex("\\d{4}-\\d{2}-\\d{2}")
    private val amount = Regex(
        "([+\\-−]?\\s*\\d(?:[\\d\\u00A0 ]*\\d)?[.,]\\d{2})\\s*([A-Z]{3})",
    )
    private val headerAmount = Regex(
        "Data[\\t ]+operacji[^\\r\\n]*?" +
            "([+\\-−]?[\\t \\u00A0]*\\d(?:[\\d\\u00A0 ]*\\d)?[.,]\\d{2})[\\t \\u00A0]*([A-Z]{3})",
        RegexOption.IGNORE_CASE,
    )
    private val savingsOperationType = Regex(
        "(?im)^[\\t \\u00A0]*OSZCZĘDZANIE[\\t \\u00A0]*$",
    )
    private val savingsHeaderType = Regex(
        "(?i)Data[\\t ]+operacji[\\t ]+OSZCZĘDZANIE\\b",
    )
    private val creditOperationType = Regex(
        "(?im)^[\\t \\u00A0]*UZNANIE[\\t \\u00A0]*$",
    )
    private val creditHeaderType = Regex(
        "(?i)Data[\\t ]+operacji[\\t ]+UZNANIE\\b",
    )
    private val automaticSavingsTitle = Regex(
        "Tytuł\\s*:\\s*Automatyczne\\s+oszczędzanie\\b",
        RegexOption.IGNORE_CASE,
    )

    fun parse(text: String): BankStatementParseResult {
        val starts = operationStart.findAll(text).toList()
        val expenses = mutableListOf<BankStatementExpense>()
        var ignoredSavings = 0
        var ignoredCredits = 0
        var unrecognized = 0

        starts.forEachIndexed { index, match ->
            val end = starts.getOrNull(index + 1)?.range?.first ?: text.length
            val block = text.substring(match.range.first, end)
            val date = operationDate.find(block)?.value
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val operationAmount = headerAmount.find(block) ?: amount.findAll(block).lastOrNull()
            if (date == null || operationAmount == null) {
                unrecognized++
                return@forEachIndexed
            }
            if (
                savingsOperationType.containsMatchIn(block) ||
                savingsHeaderType.containsMatchIn(block) ||
                automaticSavingsTitle.containsMatchIn(block)
            ) {
                ignoredSavings++
                return@forEachIndexed
            }
            val signedAmount = operationAmount.groupValues[1]
                .replace("\u00A0", "")
                .replace(Regex("\\s+"), "")
                .replace('−', '-')
                .replace(',', '.')
                .toBigDecimalOrNull()
            if (signedAmount == null) {
                unrecognized++
                return@forEachIndexed
            }
            if (
                creditOperationType.containsMatchIn(block) ||
                creditHeaderType.containsMatchIn(block) ||
                signedAmount.signum() >= 0
            ) {
                ignoredCredits++
                return@forEachIndexed
            }
            val minor = signedAmount.abs().movePointRight(2).longValueExact()
            expenses += BankStatementExpense(
                date = date,
                amountMinor = minor,
                currencyCode = operationAmount.groupValues[2],
                note = extractNote(block),
            )
        }
        return BankStatementParseResult(expenses, ignoredSavings, ignoredCredits, unrecognized)
    }

    private fun extractNote(block: String): String {
        val titleMarker = Regex("Tytuł\\s*:", RegexOption.IGNORE_CASE).find(block)
        val titleTail = titleMarker?.let { block.substring(it.range.last + 1) }.orEmpty()
        val titleEnd = listOfNotNull(
            Regex(
                "(?m)^[\\t \\u00A0]*[+\\-−]?[\\t \\u00A0]*\\d(?:[\\d\\u00A0 ]*\\d)?" +
                    "[.,]\\d{2}[\\t \\u00A0]*[A-Z]{3}[\\t \\u00A0]*$",
            ).find(titleTail)?.range?.first,
            Regex("(?im)^Dokument jest wydrukiem").find(titleTail)?.range?.first,
        ).minOrNull() ?: titleTail.length
        val rawTitle = titleTail.take(titleEnd)
        val cleaned = rawTitle
            .replace(Regex("DOP\\.\\s*MC\\s+\\S+\\s*", RegexOption.IGNORE_CASE), "")
            .replace(
                Regex(
                    "(?:PŁATNOŚĆ|PRZELEW|WYPŁATA\\s+Z\\s+BANKOMATU)\\s+KARTĄ\\s*" +
                        "\\d[\\d.,]*\\s*[A-Z]{3}\\s*",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            )
            .replace(Regex("^\\s*Zakup\\s+BLIK\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+ref\\s*:\\s*\\d+\\s*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isNotBlank()) return cleaned.take(180)

        return Regex(
            "Data\\s+księgowania\\s*\\d{4}-\\d{2}-\\d{2}\\s*(.*?)\\s*(?=Z\\s+rachunku:|Na\\s+rachunek:|Tytuł\\s*:)",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(block)?.groupValues?.get(1)?.replace(Regex("\\s+"), " ")?.trim()
            ?.takeIf(String::isNotBlank)
            ?.take(180)
            ?: "Wydatek z wyciągu bankowego"
    }
}
