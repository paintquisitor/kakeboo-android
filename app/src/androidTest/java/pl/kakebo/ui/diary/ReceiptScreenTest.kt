package pl.kakebo.ui.diary

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import pl.kakebo.MoneyCurrency
import pl.kakebo.data.local.*
import pl.kakebo.data.receipts.ReceiptStore
import pl.kakebo.data.repository.EntryRepository
import pl.kakebo.domain.ExpenseSubcategory
import pl.kakebo.ui.theme.KakeboTheme

@RunWith(AndroidJUnit4::class)
class ReceiptScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun setCurrency() {
        MoneyCurrency.select("PLN")
    }

    @Test fun recognizesReceiptLocallyAndAppliesOnlyAfterReview() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission(android.Manifest.permission.INTERNET))
        val receipts = ReceiptStore(context)
        val source = File.createTempFile("ocr-test-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = 44f; typeface = Typeface.MONOSPACE
        }
        listOf("SKLEP TESTOWY", "NIP 1234567890", "2026-09-28 12:35", "PARAGON FISKALNY",
            "Chleb 1 x 4,50", "SUMA PTU 3,50", "SUMA PLN 37,49", "GOTOWKA 100,00", "RESZTA 62,51")
            .forEachIndexed { index, text -> canvas.drawText(text, 40f, 80f + index * 90f, paint) }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                dispatchResult(requestCode, Uri.fromFile(source))
            }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        var saved: EntryEntity? = null
        try {
            compose.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                KakeboTheme { ExpenseEditor(YearMonth.of(2026, 9), null, false, false,
                    onDismiss = {}, onSave = { saved = it }, onDelete = {}) }
            } }
            compose.onNodeWithText("Kwota (PLN)").performTextReplacement("9,99")
            compose.onNodeWithText("Opis (opcjonalny)").performScrollTo().performTextReplacement("Mój opis")
            compose.onNodeWithTag("attach_receipt_file").performScrollTo().performClick()
            compose.waitUntil(20000) { compose.onAllNodes(hasTestTag("apply_receipt") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            // OCR suggestions must not overwrite the user's data or create an expense on their own.
            compose.onNodeWithText("Kwota (PLN)").assertTextContains("9,99")
            compose.onNodeWithText("Opis (opcjonalny)").assertTextContains("Mój opis")
            assertNull(saved)
            compose.onNodeWithTag("apply_receipt").performScrollTo().performClick()
            compose.onNodeWithText("Kwota (PLN)").assertTextContains("37,49")
            compose.onNodeWithText("Data (dd.MM.rrrr)").assertTextContains("28.09.2026")
            compose.onNodeWithText("Opis (opcjonalny)").assertTextContains("SKLEP TESTOWY")
            compose.onNodeWithTag("category_choice_NEEDS").performScrollTo().performClick()
            compose.onNodeWithTag("subcategory_choice_FOOD").performScrollTo().performClick()
            compose.onNodeWithTag("selected_subcategory_icon", useUnmergedTree = true).assertExists()
            compose.onNodeWithText("Kwota (PLN)").performScrollTo().performTextReplacement("38,00")
            compose.onNodeWithText("Zapisz").performClick()
            compose.runOnIdle {
                assertEquals(3800L, saved!!.amountGrosz)
                assertEquals(LocalDate.of(2026, 9, 28).toEpochDay(), saved!!.dateEpochDay)
                assertEquals(ExpenseSubcategory.FOOD, saved!!.subcategory)
                assertTrue(receipts.file(saved!!.receiptName!!).exists())
            }
        } finally {
            saved?.receiptName?.let(receipts::discardDraft)
            // Cancelling also removes the draft if an assertion above failed before saving.
            runCatching { compose.onNodeWithText("Anuluj").performClick() }
            source.delete()
        }
    }

    @Test fun filePreviewCancelRemovalAndCameraReplacementAfterBackgrounding() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val receipts = ReceiptStore(context)
        val db = Room.inMemoryDatabaseBuilder(context, KakeboDatabase::class.java).build()
        val models = ViewModelStore()
        val created = mutableSetOf<String>()
        val source = File.createTempFile("receipt-ui-test-", ".jpg", context.cacheDir)
        Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888).also { bitmap ->
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
        }
        var acceptCamera = false
        var cameraUri: Uri? = null
        var cameraRequest: Int? = null
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                when (contract) {
                    is ActivityResultContracts.OpenDocument -> dispatchResult(requestCode, Uri.fromFile(source))
                    is ActivityResultContracts.TakePicture -> {
                        val uri = input as Uri
                        cameraUri = uri
                        if (acceptCamera) context.contentResolver.openOutputStream(uri)!!.use { output ->
                            source.inputStream().use { it.copyTo(output) }
                        }
                        if (acceptCamera) cameraRequest = requestCode else dispatchResult(requestCode, false)
                    }
                    else -> error("Unexpected activity contract")
                }
            }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        lateinit var model: DiaryViewModel
        try {
            runBlocking { db.entryDao().insert(EntryEntity(amountGrosz = 100, type = EntryType.EXPENSE,
                dateEpochDay = LocalDate.of(2026, 9, 28).toEpochDay(), note = "Test paragonu",
                subcategory = ExpenseSubcategory.FOOD)) }
            compose.runOnUiThread {
                model = DiaryViewModel(EntryRepository(db.entryDao(), receipts), SavedStateHandle(mapOf(
                    "month" to "2026-09", "selectedDate" to "2026-09-28")))
                models.put("diary", model)
            }
            compose.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                KakeboTheme { DiaryScreen(model) }
            } }
            compose.waitUntil(5000) { model.state.value.entries.size == 1 }
            compose.onNodeWithContentDescription("Pokaż paragon").assertDoesNotExist()
            fun edit() {
                compose.onNodeWithTag("diary_list").performScrollToNode(hasText("Test paragonu"))
                compose.onNodeWithText("Test paragonu").performClick()
            }
            fun photo() = model.state.value.entries.single().receiptName
            edit()
            compose.onNodeWithTag("attach_receipt_file").performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodes(hasTestTag("view_receipt") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("view_receipt").performScrollTo().performClick()
            try {
                compose.waitUntil(5000) { compose.onAllNodesWithTag("receipt_image").fetchSemanticsNodes().isNotEmpty() }
            } catch (error: Throwable) {
                compose.onAllNodes(isRoot()).fetchSemanticsNodes().forEach { println(it) }
                compose.onRoot().printToLog("ReceiptTest")
                throw error
            }
            compose.onNodeWithText("Zamknij").performClick()
            compose.onNodeWithText("Zapisz").performClick()
            compose.waitUntil(5000) { !model.saving.value && photo() != null }
            val original = photo()!!.also(created::add)
            assertTrue(receipts.file(original).exists())
            compose.onNodeWithTag("diary_list").performScrollToNode(hasContentDescription("Pokaż paragon"))
            compose.onNodeWithContentDescription("Pokaż paragon").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("receipt_image").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Zapisz").assertDoesNotExist()
            compose.onNodeWithText("Zamknij").performClick()

            edit()
            compose.onNodeWithTag("remove_receipt").performScrollTo().performClick()
            compose.onNodeWithText("Anuluj").performClick()
            assertEquals(original, photo())
            assertTrue(receipts.file(original).exists())

            edit()
            compose.onNodeWithTag("attach_receipt_camera").performScrollTo().performClick()
            compose.onNodeWithText("Zapisz").performClick()
            compose.waitUntil(5000) { !model.saving.value && compose.onAllNodesWithText("Zapisz").fetchSemanticsNodes().isEmpty() }
            assertEquals(original, photo())
            assertEquals("content", cameraUri!!.scheme)

            acceptCamera = true
            edit()
            compose.onNodeWithTag("attach_receipt_camera").performScrollTo().performClick()
            // The real camera pauses the app, often for longer than the flow's stop timeout.
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            Thread.sleep(5500)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitForIdle()
            compose.runOnUiThread { registry.dispatchResult(cameraRequest!!, true) }
            compose.waitUntil(5000) { compose.onAllNodesWithText("Zapisz").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Zapisz").performClick()
            compose.waitUntil(5000) { !model.saving.value && photo() != original }
            created.add(photo()!!)
            assertTrue(receipts.file(photo()!!).exists())
            assertFalse(receipts.file(original).exists())
        } finally {
            compose.runOnUiThread { models.clear() }
            runBlocking { db.entryDao().findById(1)?.receiptName?.let(created::add) }
            cameraUri?.lastPathSegment?.let(receipts::discardDraft)
            db.close(); source.delete(); created.forEach { receipts.file(it).delete() }
        }
    }
}
