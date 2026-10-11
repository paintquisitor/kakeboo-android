package pl.kakebo.data.receipts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Only generated relative names are accepted; external file paths never enter the database. */
class ReceiptStore(context: Context) {
    private val context = context.applicationContext
    private val drafts = File(context.cacheDir, "receipt-drafts")
    private val receipts = File(context.filesDir, "receipts")
    private val validName = Regex("(?:draft_|camera_)?[a-f0-9-]{36}\\.(?:img|jpg)")

    fun file(name: String): File {
        require(validName.matches(name))
        return File(if (isDraft(name)) drafts else receipts, name)
    }

    fun isDraft(name: String) = name.startsWith("draft_") || name.startsWith("camera_")

    fun createCameraTarget(): String {
        check(drafts.mkdirs() || drafts.isDirectory)
        val name = "camera_${UUID.randomUUID()}.jpg"
        check(file(name).createNewFile())
        return name
    }

    fun cameraUri(name: String): Uri {
        require(name.startsWith("camera_"))
        return FileProvider.getUriForFile(context, "${context.packageName}.receipts", file(name))
    }

    suspend fun importImage(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input)
            copyDraft(input)
        }
    }

    suspend fun finishCamera(name: String): String = withContext(Dispatchers.IO) {
        try { file(name).inputStream().use(::copyDraft) }
        finally { discardDraft(name) }
    }

    private fun copyDraft(input: InputStream): String {
        check(drafts.mkdirs() || drafts.isDirectory)
        val name = "draft_${UUID.randomUUID()}.img"
        val target = file(name)
        try {
            target.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    total += count
                    require(total <= MAX_BYTES) { "Image exceeds 25 MB" }
                    output.write(buffer, 0, count)
                }
            }
            validate(target)
            return name
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
    }

    private fun validate(file: File) {
        require(file.length() in 1..MAX_BYTES)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        require(options.outWidth > 0 && options.outHeight > 0) { "Unsupported image" }
    }

    // Called on IO before saving the database reference. Draft remains available for a retry.
    fun commit(name: String): String {
        val source = file(name)
        validate(source)
        if (!isDraft(name)) return name
        check(receipts.mkdirs() || receipts.isDirectory)
        val savedName = "${UUID.randomUUID()}.img"
        try {
            source.copyTo(file(savedName))
            return savedName
        } catch (error: Throwable) {
            file(savedName).delete()
            throw error
        }
    }

    fun discardDraft(name: String?) {
        if (name != null && isDraft(name)) file(name).delete()
    }

    fun deleteSaved(name: String) {
        require(!isDraft(name))
        file(name).delete()
    }

    suspend fun preview(name: String): Bitmap = withContext(Dispatchers.IO) {
        val source = file(name)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 3072) sample *= 2
        val bitmap = requireNotNull(BitmapFactory.decodeFile(source.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }))
        val orientation = runCatching { ExifInterface(source.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
        val matrix = Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }
                6 -> setRotate(90f)
                7 -> { setRotate(270f); postScale(-1f, 1f) }
                8 -> setRotate(270f)
            }
        }
        if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }

    private companion object { const val MAX_BYTES = 25L * 1024 * 1024 }
}
