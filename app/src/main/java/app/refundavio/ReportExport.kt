package app.refundavio

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.time.LocalDate

internal enum class ReportFormat(val extension: String, val mimeType: String) {
    PDF("pdf", "application/pdf"), CSV("csv", "text/csv"), TXT("txt", "text/plain"),
}

internal fun exportReport(context: Context, cases: List<CaseSnapshot>, today: LocalDate, format: ReportFormat, output: OutputStream) {
    val tables = reportTables(cases, today, context::getString)
    val title = "Refundavio · ${context.getString(R.string.reports)} · $today"
    when (format) {
        ReportFormat.CSV -> output.bufferedWriter(Charsets.UTF_8).use {
            it.write("\uFEFF")
            writeReportCsv(it, tables, context.getString(R.string.report_section))
        }
        ReportFormat.TXT -> output.bufferedWriter(Charsets.UTF_8).use { writer ->
            reportLines(title, tables, context.getString(R.string.no_cases)).forEach {
                writer.write(it)
                writer.newLine()
            }
        }
        ReportFormat.PDF -> writeReportPdf(output, reportLines(title, tables, context.getString(R.string.no_cases)),
            context.resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL)
    }
}

private fun writeReportPdf(output: OutputStream, lines: Sequence<String>, rtl: Boolean) {
    val width = 595
    val height = 842
    val margin = 44
    val paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f }
    val document = PdfDocument()
    try {
        var pageNumber = 1
        fun newPage() = document.startPage(PdfDocument.PageInfo.Builder(width, height, pageNumber).create())
        var page = newPage()
        var top = margin
        for (line in lines) {
            val layout = StaticLayout.Builder.obtain(line, 0, line.length, paint, width - 2 * margin)
                .setTextDirection(if (rtl) TextDirectionHeuristics.FIRSTSTRONG_RTL else TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            var first = 0
            while (first < layout.lineCount) {
                val start = layout.getLineTop(first)
                var end = first
                while (end < layout.lineCount && top + layout.getLineBottom(end) - start <= height - margin) end++
                if (end > first) {
                    val blockHeight = layout.getLineBottom(end - 1) - start
                    page.canvas.save()
                    page.canvas.clipRect(margin, top, width - margin, top + blockHeight)
                    page.canvas.translate(margin.toFloat(), (top - start).toFloat())
                    layout.draw(page.canvas)
                    page.canvas.restore()
                    top += blockHeight + 3
                    first = end
                }
                if (first < layout.lineCount) {
                    document.finishPage(page)
                    pageNumber++
                    page = newPage()
                    top = margin
                }
            }
        }
        document.finishPage(page)
        document.writeTo(output)
    } finally {
        document.close()
    }
}

@Composable
internal fun ReportsDialog(cases: List<CaseSnapshot>, today: LocalDate, close: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable { mutableStateOf<ReportFormat?>(null) }
    var busy by remember { mutableStateOf(false) }
    var result by rememberSaveable { mutableIntStateOf(0) }
    val format = selected
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { response ->
        val uri = response.data?.data?.takeIf { it.scheme == "content" }
        busy = false
        if (response.resultCode == android.app.Activity.RESULT_OK && uri != null && format != null) {
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        checkNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { output ->
                            exportReport(context, cases, today, format, output)
                        }
                    }
                    result = R.string.report_saved
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    result = R.string.report_error
                } finally {
                    busy = false
                    selected = null
                }
            }
        } else {
            if (response.resultCode == android.app.Activity.RESULT_OK) result = R.string.report_error
            selected = null
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) close() },
        title = { Text(stringResource(R.string.reports)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.report_privacy))
                if (result != 0) Text(stringResource(result))
                if (format == null) ReportFormat.entries.forEach { choice ->
                    OutlinedButton(onClick = { selected = choice; result = 0 }, modifier = Modifier.fillMaxWidth()) { Text(choice.name) }
                } else Text(format.name)
            }
        },
        confirmButton = {
            if (format != null) Button(enabled = !busy, onClick = {
                busy = true
                try {
                    picker.launch(android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(android.content.Intent.CATEGORY_OPENABLE)
                        type = format.mimeType
                        putExtra(android.content.Intent.EXTRA_TITLE, "Refundavio-$today.${format.extension}")
                    })
                } catch (_: RuntimeException) {
                    busy = false
                    selected = null
                    result = R.string.report_error
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = close) { Text(stringResource(R.string.close)) } },
    )
}
