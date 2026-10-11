package pl.kakebo.ui.profile

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.database.Cursor
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.math.BigDecimal
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import pl.kakebo.data.local.KakeboDatabase

/** Portable, local-only export used by the Profile screen. */
class ProfileDataManager(
    private val context: Context,
    private val database: KakeboDatabase,
) {
    private val tables = listOf(
        "monthly_plans",
        "custom_categories",
        "planned_items",
        "custom_subcategories",
        "entries",
        "monthly_reflections",
    )

    suspend fun createBackup(uri: Uri) = withContext(Dispatchers.IO) {
        val root = JSONObject().put("format", 1).put("createdAt", System.currentTimeMillis())
        val data = JSONObject()
        val db = database.openHelper.readableDatabase
        tables.forEach { table -> data.put(table, readTable(db.query("SELECT * FROM $table"))) }
        root.put("tables", data)
        root.put("profileSettings", profileSettingsBackup(context))

        requireNotNull(context.contentResolver.openOutputStream(uri, "w")).use { output ->
            ZipOutputStream(output.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("kakebo.json"))
                zip.write(root.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                val receipts = File(context.filesDir, "receipts")
                receipts.listFiles()?.filter { it.isFile }?.forEach { file ->
                    zip.putNextEntry(ZipEntry("receipts/${file.name}"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    suspend fun restoreBackup(uri: Uri) = withContext(Dispatchers.IO) {
        val staged = File(context.cacheDir, "profile-restore-${System.nanoTime()}").apply { mkdirs() }
        try {
            var json: JSONObject? = null
            var totalBytes = 0L
            requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        require(!entry.isDirectory)
                        when {
                            entry.name == "kakebo.json" -> {
                                val bytes = zip.readBytesLimited(20L * 1024 * 1024)
                                json = JSONObject(bytes.toString(Charsets.UTF_8))
                            }
                            entry.name.startsWith("receipts/") -> {
                                val name = entry.name.substringAfterLast('/')
                                require(name.matches(Regex("[a-f0-9-]{36}\\.img")))
                                val target = File(staged, name)
                                target.outputStream().use { output ->
                                    val copied = zip.copyToLimited(output, 25L * 1024 * 1024)
                                    totalBytes += copied
                                    require(totalBytes <= 250L * 1024 * 1024)
                                }
                            }
                            else -> error("Nieznany element kopii")
                        }
                        zip.closeEntry()
                    }
                }
            }
            val root = requireNotNull(json)
            require(root.getInt("format") == 1)
            val allData = root.getJSONObject("tables")
            tables.forEach { allData.getJSONArray(it) }
            val restoredProfile = root.optJSONObject("profileSettings")

            val db = database.openHelper.writableDatabase
            db.beginTransaction()
            try {
                listOf("entries", "custom_subcategories", "planned_items", "monthly_reflections", "custom_categories", "monthly_plans")
                    .forEach { db.delete(it, null, null) }
                tables.forEach { table ->
                    val rows = allData.getJSONArray(table)
                    repeat(rows.length()) { index ->
                        val row = rows.getJSONObject(index)
                        val values = ContentValues(row.length())
                        row.keys().forEach { key -> values.putJson(key, row.get(key)) }
                        check(db.insert(table, 0, values) != -1L)
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            val receipts = File(context.filesDir, "receipts").apply { mkdirs() }
            receipts.listFiles()?.forEach { it.delete() }
            staged.listFiles()?.forEach { it.copyTo(File(receipts, it.name), overwrite = true) }
            restoreProfileSettings(context, restoredProfile)
            database.invalidationTracker.refreshAsync()
        } finally {
            staged.deleteRecursively()
        }
    }

    suspend fun clearFinancialData() = withContext(Dispatchers.IO) {
        val db = database.openHelper.writableDatabase
        db.beginTransaction()
        try {
            listOf("entries", "planned_items", "monthly_reflections", "monthly_plans")
                .forEach { db.delete(it, null, null) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        File(context.filesDir, "receipts").apply {
            deleteRecursively()
            mkdirs()
        }
        database.invalidationTracker.refreshAsync()
    }

    suspend fun exportCsv(uri: Uri, currencyCode: String) = withContext(Dispatchers.IO) {
        val sql = """
            SELECT e.dateEpochDay, e.note,
              COALESCE(s.name, e.subcategory, 'Wydatki z karty') AS category,
              e.amountGrosz, e.receiptName
            FROM entries e LEFT JOIN custom_subcategories s ON s.id = e.customSubcategoryId
            WHERE e.type = 'EXPENSE' ORDER BY e.dateEpochDay, e.id
        """.trimIndent()
        requireNotNull(context.contentResolver.openOutputStream(uri, "w")).use { output ->
            BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8)).use { writer ->
                writer.write("\uFEFFData;Opis;Kategoria;Kwota;Waluta;Paragon\n")
                database.openHelper.readableDatabase.query(sql).use { cursor ->
                    while (cursor.moveToNext()) {
                        val fields = listOf(
                            LocalDate.ofEpochDay(cursor.getLong(0)).toString(),
                            cursor.getString(1).orEmpty(),
                            cursor.getString(2).orEmpty(),
                            BigDecimal.valueOf(cursor.getLong(3), 2).toPlainString(),
                            currencyCode,
                            if (cursor.isNull(4)) "" else "tak",
                        )
                        writer.write(fields.joinToString(";") { it.csv() })
                        writer.newLine()
                    }
                }
            }
        }
    }

    private fun readTable(cursor: Cursor): JSONArray = cursor.use {
        JSONArray().also { rows ->
            while (it.moveToNext()) {
                val row = JSONObject()
                for (index in 0 until it.columnCount) {
                    val value: Any = when (it.getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                        Cursor.FIELD_TYPE_INTEGER -> it.getLong(index)
                        Cursor.FIELD_TYPE_FLOAT -> it.getDouble(index)
                        Cursor.FIELD_TYPE_BLOB -> android.util.Base64.encodeToString(it.getBlob(index), android.util.Base64.NO_WRAP)
                        else -> it.getString(index)
                    }
                    row.put(it.getColumnName(index), value)
                }
                rows.put(row)
            }
        }
    }
}

private fun ContentValues.putJson(key: String, value: Any) {
    when (value) {
        JSONObject.NULL -> putNull(key)
        is Int -> put(key, value)
        is Long -> put(key, value)
        is Double -> put(key, value)
        is Boolean -> put(key, if (value) 1 else 0)
        else -> put(key, value.toString())
    }
}

private fun ZipInputStream.readBytesLimited(limit: Long): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    copyToLimited(output, limit)
    return output.toByteArray()
}

private fun ZipInputStream.copyToLimited(output: java.io.OutputStream, limit: Long): Long {
    val buffer = ByteArray(8192)
    var total = 0L
    while (true) {
        val count = read(buffer)
        if (count == -1) break
        total += count
        require(total <= limit)
        output.write(buffer, 0, count)
    }
    return total
}

private fun String.csv(): String = if (contains(';') || contains('"') || contains('\n'))
    "\"${replace("\"", "\"\"")}\"" else this
