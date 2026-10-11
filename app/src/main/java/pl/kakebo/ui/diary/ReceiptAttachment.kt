package pl.kakebo.ui.diary

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pl.kakebo.R
import pl.kakebo.data.receipts.ReceiptStore
import pl.kakebo.data.receipts.ReceiptTextReader
import pl.kakebo.domain.ReceiptParser
import pl.kakebo.domain.ReceiptSuggestion
import pl.kakebo.domain.formatMoney
import java.time.format.DateTimeFormatter

@Composable
internal fun rememberReceiptStore(): ReceiptStore {
    val context = LocalContext.current.applicationContext
    return remember(context) { ReceiptStore(context) }
}

@Composable
internal fun ReceiptAttachment(
    name: String?, enabled: Boolean, store: ReceiptStore,
    onChange: (String?) -> Unit, onBusyChange: (Boolean) -> Unit,
    onRecognized: (ReceiptSuggestion) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var pendingCamera by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf(false) }
    var viewing by rememberSaveable { mutableStateOf(false) }
    var recognizedText by rememberSaveable { mutableStateOf<String?>(null) }
    var recognitionFailed by rememberSaveable { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    var applied by rememberSaveable { mutableStateOf(false) }
    val reader = remember(store) { ReceiptTextReader(store) }
    val suggestion = remember(recognizedText) { recognizedText?.let(ReceiptParser::parse) }

    suspend fun readPhoto(photo: String) {
        recognizedText = null
        recognitionFailed = false
        applied = false
        reading = true
        try { recognizedText = reader.read(photo) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { recognitionFailed = true }
        finally { reading = false }
    }

    fun importPhoto(operation: suspend () -> String) {
        onBusyChange(true)
        error = false
        scope.launch {
            try {
                val photo = operation()
                onChange(photo)
                readPhoto(photo)
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { onBusyChange(false) }
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importPhoto { store.importImage(uri) }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val target = pendingCamera
        pendingCamera = null
        if (target != null) {
            if (success) importPhoto { store.finishCamera(target) }
            else store.discardDraft(target)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.receipt_title), style = MaterialTheme.typography.titleSmall)
        if (name != null) {
            OutlinedButton(onClick = { viewing = true }, enabled = enabled,
                modifier = Modifier.fillMaxWidth().testTag("view_receipt")) {
                Text(stringResource(R.string.view_receipt))
            }
            TextButton(enabled = enabled, onClick = {
                onChange(null); error = false; recognizedText = null; recognitionFailed = false; applied = false
            }, modifier = Modifier.testTag("remove_receipt")) {
                Text(stringResource(R.string.remove_receipt))
            }
            TextButton(enabled = enabled, onClick = {
                onBusyChange(true)
                scope.launch { try { readPhoto(name) } finally { onBusyChange(false) } }
            }, modifier = Modifier.testTag("read_receipt")) {
                Text(stringResource(R.string.read_receipt))
            }
            if (reading) Text(stringResource(R.string.receipt_reading))
            if (recognitionFailed) Text(stringResource(R.string.receipt_read_error), color = MaterialTheme.colorScheme.error)
            if (suggestion != null && !reading) {
                if (!suggestion.hasValues) Text(stringResource(R.string.receipt_no_data))
                else Card(Modifier.fillMaxWidth().testTag("receipt_suggestion")) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.receipt_check_data), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.receipt_detected_amount,
                            suggestion.amountGrosz?.let(::formatMoney) ?: stringResource(R.string.receipt_not_found)))
                        Text(stringResource(R.string.receipt_detected_date,
                            suggestion.date?.format(DateTimeFormatter.ofPattern("dd.MM.uuuu")) ?: stringResource(R.string.receipt_not_found)))
                        Text(stringResource(R.string.receipt_detected_merchant,
                            suggestion.merchant ?: stringResource(R.string.receipt_not_found)))
                        Text(stringResource(R.string.receipt_apply_hint), style = MaterialTheme.typography.bodySmall)
                        TextButton(enabled = enabled && !applied, onClick = {
                            onRecognized(suggestion)
                            applied = true
                        }, modifier = Modifier.testTag("apply_receipt")) {
                            Text(stringResource(if (applied) R.string.receipt_applied else R.string.receipt_apply))
                        }
                    }
                }
            }
        }
        OutlinedButton(enabled = enabled, onClick = {
            error = false
            try { filePicker.launch(arrayOf("image/*")) } catch (_: Exception) { error = true }
        }, modifier = Modifier.fillMaxWidth().testTag("attach_receipt_file")) {
            Icon(painterResource(R.drawable.ic_hero_paper_clip), contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.attach_receipt_file))
        }
        OutlinedButton(enabled = enabled, onClick = {
            error = false
            try {
                val target = store.createCameraTarget()
                pendingCamera = target
                camera.launch(store.cameraUri(target))
            } catch (_: Exception) {
                store.discardDraft(pendingCamera)
                pendingCamera = null
                error = true
            }
        }, modifier = Modifier.fillMaxWidth().testTag("attach_receipt_camera")) {
            Icon(painterResource(R.drawable.ic_hero_camera), contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.attach_receipt_camera))
        }
        Text(stringResource(R.string.receipt_hint), style = MaterialTheme.typography.bodySmall)
        if (error) Text(stringResource(R.string.receipt_import_error), color = MaterialTheme.colorScheme.error)
    }
    if (viewing && name != null) ReceiptPreview(name, store) { viewing = false }
}

@Composable
internal fun ReceiptPreview(name: String, store: ReceiptStore, onDismiss: () -> Unit) {
    var bitmap by remember(name) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(name) { mutableStateOf(false) }
    var scale by remember(name) { mutableFloatStateOf(1f) }
    var offset by remember(name) { mutableStateOf(Offset.Zero) }
    LaunchedEffect(name) {
        try { bitmap = store.preview(name) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().systemBarsPadding()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.receipt_preview_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                }
                Text(stringResource(R.string.receipt_zoom_hint), style = MaterialTheme.typography.bodySmall)
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().pointerInput(name) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        val limitX = size.width * (scale - 1) / 2
                        val limitY = size.height * (scale - 1) / 2
                        offset = Offset((offset.x + pan.x).coerceIn(-limitX, limitX), (offset.y + pan.y).coerceIn(-limitY, limitY))
                    }
                }, contentAlignment = Alignment.Center) {
                    when {
                        failed -> Text(stringResource(R.string.receipt_preview_error))
                        bitmap == null -> CircularProgressIndicator()
                        else -> Image(bitmap!!.asImageBitmap(), contentDescription = stringResource(R.string.receipt_preview_title),
                            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().testTag("receipt_image")
                                .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y })
                    }
                }
            }
        }
    }
}
