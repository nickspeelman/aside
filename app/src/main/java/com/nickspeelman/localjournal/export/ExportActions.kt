package com.nickspeelman.localjournal.export

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nickspeelman.localjournal.data.MoodEntry
import com.nickspeelman.localjournal.data.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun ChartExportActions(model: ShareableChart?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bytesForSave by remember { mutableStateOf<ByteArray?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val bytes = bytesForSave
        bytesForSave = null
        if (uri != null && bytes != null) {
            scope.launch {
                val ok = runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Couldn't open destination") } }.isSuccess
                Toast.makeText(context, if (ok) "Image saved" else "Couldn't save image", Toast.LENGTH_SHORT).show()
            }
        }
    }
    fun makePng(block: (ByteArray) -> Unit) {
        val m = model ?: return
        scope.launch {
            val bytes = withContext(Dispatchers.Default) {
                val bitmap = ChartPngRenderer.render(m)
                try { ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() } }
                finally { bitmap.recycle() }
            }
            block(bytes)
        }
    }
    if (model == null || model.checkInCount <= 0) {
        Text("There isn't enough rated mood data in this view to export.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = {
            makePng { bytes ->
                scope.launch { withContext(Dispatchers.IO) { ExportFileManager.writeTemp(context, ExportFilename.chart(model.title, model.startDate, model.endDate), bytes) }.also { ExportFileManager.share(context, it, "image/png") } }
            }
        }) { Text("Share image") }
        OutlinedButton(onClick = {
            makePng { bytes -> bytesForSave = bytes; saveLauncher.launch(ExportFilename.chart(model.title, model.startDate, model.endDate)) }
        }) { Text("Save image") }
    }
}

@Composable
fun ReportExportActions(
    kind: ReportExportKind,
    title: String,
    entries: List<MoodEntry>,
    settings: UserSettings,
    startDate: LocalDate,
    endDate: LocalDate,
    onFreshAuthentication: (String, () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val zoneId = remember { ZoneId.systemDefault() }
    var showDialog by remember { mutableStateOf(false) }
    var includeHashtags by remember { mutableStateOf(true) }
    var includeNotes by remember { mutableStateOf(false) }
    var pendingSaveBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingLongNotesAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val noteCount = remember(entries, startDate, endDate, zoneId) { ReportExportBuilder.noteCount(entries, startDate, endDate, zoneId) }
    val hasRatedData = remember(entries, startDate, endDate, zoneId) {
        entries.any { entry ->
            if (entry.rating == null) false else {
                val date = java.time.Instant.ofEpochMilli(entry.timestamp).atZone(zoneId).toLocalDate()
                !date.isBefore(startDate) && !date.isAfter(endDate)
            }
        }
    }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val bytes = pendingSaveBytes; pendingSaveBytes = null
        if (uri != null && bytes != null) scope.launch {
            val ok = runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Couldn't open destination") } }.isSuccess
            Toast.makeText(context, if (ok) "PDF saved" else "Couldn't save PDF", Toast.LENGTH_SHORT).show()
        }
    }
    fun generate(share: Boolean) {
        val action: () -> Unit = {
            scope.launch {
                val bytes = withContext(Dispatchers.Default) {
                    val model = ReportExportBuilder.build(
                        kind = kind,
                        entries = entries,
                        settings = settings,
                        startDate = startDate,
                        endDate = endDate,
                        title = title,
                        includeHashtags = includeHashtags,
                        includeNotes = includeNotes,
                        zoneId = zoneId
                    )
                    model?.let(ReportPdfRenderer::render)
                }
                if (bytes == null) Toast.makeText(context, "There isn't enough rated mood data in this period to export.", Toast.LENGTH_LONG).show()
                else if (share) {
                    val uri = withContext(Dispatchers.IO) { ExportFileManager.writeTemp(context, ExportFilename.report(kind, startDate, endDate), bytes) }
                    ExportFileManager.share(context, uri, "application/pdf")
                } else { pendingSaveBytes = bytes; saveLauncher.launch(ExportFilename.report(kind, startDate, endDate)) }
                showDialog = false
            }
            Unit
        }
        val authenticated: () -> Unit = { if (includeNotes) onFreshAuthentication("Export journal notes", action) else action() }
        if (includeNotes && noteCount > 50) pendingLongNotesAction = authenticated else authenticated()
    }

    if (!hasRatedData) {
        Text(
            "There isn't enough rated mood data in this period to export.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Button(onClick = { includeHashtags = true; includeNotes = false; showDialog = true }) {
        Text("Export report")
    }

    if (showDialog) AlertDialog(
        onDismissRequest = { showDialog = false },
        title = { Text("Export $title") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Included in report", style = MaterialTheme.typography.titleSmall)
                Text("✓ Mood ratings and statistics")
                Text("✓ Charts")
                Row { Checkbox(checked = includeHashtags, onCheckedChange = { includeHashtags = it }); Text("Include hashtags", modifier = Modifier.padding(top = 12.dp)) }
                Row { Checkbox(checked = includeNotes, onCheckedChange = { includeNotes = it }); Text("Include journal notes", modifier = Modifier.padding(top = 12.dp)) }
                if (includeNotes) Text("Journal notes can contain private information. Notes are excluded again the next time you export.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("The PDF will leave Aside when you save or share it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { generate(true) }) { Text("Share PDF") } },
        dismissButton = { Row { TextButton(onClick = { generate(false) }) { Text("Save PDF") }; TextButton(onClick = { showDialog = false }) { Text("Cancel") } } }
    )
    pendingLongNotesAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingLongNotesAction = null },
            title = { Text("Include all $noteCount notes?") },
            text = { Text("This report contains $noteCount journal notes and may create a long PDF. Aside will include all notes rather than silently truncating them.") },
            confirmButton = { TextButton(onClick = { pendingLongNotesAction = null; action() }) { Text("Include all notes") } },
            dismissButton = { TextButton(onClick = { pendingLongNotesAction = null }) { Text("Cancel") } }
        )
    }
}
