package pl.kakebo.data.statements

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.kakebo.domain.BankStatementParseResult
import pl.kakebo.domain.BankStatementParser

/** Reads text-based bank PDFs locally. Statement contents never leave the phone. */
class BankStatementPdfReader(private val context: Context) {
    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    suspend fun read(uri: Uri): BankStatementParseResult = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { document ->
                PDFTextStripper().apply { sortByPosition = true }.getText(document)
            }
        } ?: error("Nie można otworzyć pliku PDF.")
        BankStatementParser.parse(text)
    }
}
