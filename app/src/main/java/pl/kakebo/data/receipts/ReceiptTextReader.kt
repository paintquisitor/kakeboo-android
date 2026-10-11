package pl.kakebo.data.receipts

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Bundled Latin model: recognition works without a download or network permission. */
class ReceiptTextReader(private val store: ReceiptStore) {
    suspend fun read(name: String): String {
        val bitmap = store.preview(name)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return suspendCancellableCoroutine { continuation ->
            try {
                recognizer.process(InputImage.fromBitmap(bitmap, 0)).addOnCompleteListener { task ->
                    try {
                        if (continuation.isActive) {
                            if (task.isSuccessful) {
                                // Rejoin columns by row so a total and its right-aligned price stay together.
                                val lines = task.result.textBlocks.flatMap { it.lines }
                                    .sortedBy { it.boundingBox?.centerY() ?: 0 }
                                val rows = mutableListOf<MutableList<com.google.mlkit.vision.text.Text.Line>>()
                                for (line in lines) {
                                    val previous = rows.lastOrNull()?.firstOrNull()?.boundingBox
                                    val box = line.boundingBox
                                    if (previous != null && box != null &&
                                        kotlin.math.abs(previous.centerY() - box.centerY()) < minOf(previous.height(), box.height()) / 2) {
                                        rows.last().add(line)
                                    } else rows.add(mutableListOf(line))
                                }
                                continuation.resume(rows.joinToString("\n") { row ->
                                    row.sortedBy { it.boundingBox?.left ?: 0 }.joinToString(" ") { it.text }
                                })
                            } else continuation.resumeWithException(task.exception ?: IllegalStateException("Recognition failed"))
                        }
                    } finally {
                        recognizer.close()
                        bitmap.recycle()
                    }
                }
            } catch (error: Exception) {
                recognizer.close()
                bitmap.recycle()
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    }
}
