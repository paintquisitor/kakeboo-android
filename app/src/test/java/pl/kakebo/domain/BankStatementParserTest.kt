package pl.kakebo.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BankStatementParserTest {
    @Test
    fun importsDebitsAndIgnoresSavingsAndCredits() {
        val result = BankStatementParser.parse(
            """
            Data operacji
            2026-09-26
            Data księgowania
            2026-09-27
            TRANSAKCJA KARTĄ
            Tytuł: DOP. MC 123456******0000 PŁATNOŚĆ KARTĄ 121.00 PLN Sklep Testowy
             -121,00 PLN
            Data operacji
            2026-09-25
            Data księgowania
            2026-09-25
            OSZCZĘDZANIE
            Tytuł: Wpłata końcówek na cel- transakcja BLIK na kwotę 17.32 PLN
             -6,80 PLN
            Data operacji
            2026-09-25
            Data księgowania
            2026-09-25
            UZNANIE
            Tytuł: Przelew własny
             300,00 PLN
            Data operacji
            2026-09-24
            Data księgowania
            2026-09-24
            OBCIĄŻENIE
            Tytuł: Zakup BLIK Sklep Internetowy ref:00000000000
             -17,32 PLN
            Data operacji
            2026-09-23
            Data księgowania
            2026-09-23
            ZLECENIE STAŁE NA RACHUNEK W NASZYM BANKU
            Tytuł: Automatyczne oszczędzanie - stała kwota
             -300,00 PLN
            """.trimIndent(),
        )

        assertEquals(2, result.expenses.size)
        assertEquals(2, result.ignoredSavings)
        assertEquals(1, result.ignoredCredits)
        assertEquals(0, result.unrecognized)
        assertEquals(12_100, result.expenses[0].amountMinor)
        assertEquals("Sklep Testowy", result.expenses[0].note)
        assertEquals("Sklep Internetowy", result.expenses[1].note)
    }

    @Test
    fun importsPositionSortedPdfText() {
        val result = BankStatementParser.parse(
            """
            Data operacji TRANSAKCJA KARTĄ -121,00 PLN
            2026-09-26
            Z rachunku: 00 0000 0000 0000 0000 0000 0000
            Data księgowania Jan Kowalski
            2026-09-27
            Tytuł: DOP. MC 123456******0000 PŁATNOŚĆ KARTĄ 121.00 PLN Sklep
            Testowy
            Data operacji OSZCZĘDZANIE -6,80 PLN
            2026-09-25
            Tytuł: Wpłata końcówek na cel
            Data operacji UZNANIE 300,00 PLN
            2026-09-25
            Tytuł: Przelew własny
            """.trimIndent(),
        )

        assertEquals(1, result.expenses.size)
        assertEquals(1, result.ignoredSavings)
        assertEquals(1, result.ignoredCredits)
        assertEquals(0, result.unrecognized)
        assertEquals(12_100, result.expenses.single().amountMinor)
        assertEquals("Sklep Testowy", result.expenses.single().note)
    }
}
