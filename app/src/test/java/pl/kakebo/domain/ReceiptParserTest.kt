package pl.kakebo.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ReceiptParserTest {
    @Test fun readsPolishTotalRatherThanVatCashOrChange() {
        val result = ReceiptParser.parse("""
            Sklep Spożywczy
            ul. Polna 15
            NIP 1234567890
            2026-09-28 12:35
            PARAGON FISKALNY
            Chleb 1 x 4,50 4,50A
            SUMA PTU 3,50
            SUMA PLN 37,49
            GOTÓWKA 100,00
            RESZTA 62,51
        """.trimIndent())
        assertEquals(3749L, result.amountGrosz)
        assertEquals(LocalDate.of(2026, 9, 28), result.date)
        assertEquals("Sklep Spożywczy", result.merchant)
    }

    @Test fun recognizesSplitTotalThousandsAndLocalDate() {
        val result = ReceiptParser.parse("Księgarnia\n28.09.2026\nPARAGON FISKALNY\nDO ZAPŁATY\n1 234,56 PLN")
        assertEquals(123456L, result.amountGrosz)
        assertEquals(LocalDate.of(2026, 9, 28), result.date)
    }

    @Test fun prefersFinalAmountAndHandlesDecimalPoint() {
        assertEquals(1800L, ReceiptParser.parse("SUMA 20.00\nRabat 2.00\nDo zapłaty 18.00").amountGrosz)
    }

    @Test fun prefersFirstHeaderLineIncludingShortBrandsAndDigits() {
        listOf("4F", "H&M", "Sklep 24/7", "Biedronka 1234").forEach { name ->
            val result = ReceiptParser.parse("\n  $name  \nFirma Handlowa\nul. Polna 15\nPARAGON FISKALNY\nSUMA PLN 12,50")
            assertEquals(name, result.merchant)
        }
    }

    @Test fun skipsMetadataWhenTheFirstLineIsNotAMerchant() {
        assertEquals("Sklep 24", ReceiptParser.parse(
            "NIP 1234567890\nul. Polna\n00-001 Warszawa\n2026-09-28 12:35 KASA\nSklep 24\nPARAGON FISKALNY"
        ).merchant)
        assertNull(ReceiptParser.parse("NIP 1234567890\nul. Polna\n00-001 Warszawa\nPARAGON FISKALNY").merchant)
    }

    @Test fun uncertainAmountsAndInvalidDatesRemainEmpty() {
        assertNull(ReceiptParser.parse("SUMA PLN 12,00\nSUMA PLN 13,00").amountGrosz)
        assertNull(ReceiptParser.parse("SUMA VAT 3,00\nGOTÓWKA 100,00\nRESZTA 70,00").amountGrosz)
        assertNull(ReceiptParser.parse("TOTAL EUR 12.50").amountGrosz)
        assertNull(ReceiptParser.parse("SUMA PLN -12,50").amountGrosz)
        assertNull(ReceiptParser.parse("SUMA PLN 0,00").amountGrosz)
        assertNull(ReceiptParser.parse("31.02.2026").date)
        assertNull(ReceiptParser.parse("28.09.2026\n29.09.2026").date)
        assertFalse(ReceiptParser.parse("").hasValues)
    }
}
