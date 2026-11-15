package app.refundavio

import android.app.DatePickerDialog
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate

internal val Stage.label get() = when (this) {
    Stage.NOT_REQUESTED -> R.string.status_not_requested
    Stage.REQUESTED -> R.string.status_requested
    Stage.APPROVED -> R.string.status_approved
    Stage.AWAITING_DISPATCH -> R.string.awaiting_dispatch
    Stage.DISPATCHED -> R.string.status_dispatched
    Stage.MERCHANT_RECEIVED -> R.string.status_merchant_received
    Stage.REFUND_APPROVED -> R.string.status_refund_approved
    Stage.PROCESSING -> R.string.status_processing
    Stage.PARTIAL -> R.string.status_partial
    Stage.REFUNDED -> R.string.status_refunded
    Stage.REJECTED -> R.string.status_rejected
    Stage.CANCELLED -> R.string.status_cancelled
    Stage.DISPUTED -> R.string.status_disputed
    Stage.CLOSED -> R.string.status_closed
}

@Composable
internal fun RecordsPage(title: Int, hint: Int, cases: List<CaseSnapshot>, date: LocalDate, detail: (Long) -> Unit,
                         add: () -> Unit, filter: Int = 0, change: ((Int) -> Unit)? = null, warranty: Boolean = false) {
    val filterLabels = listOf(R.string.all_cases, R.string.deadlines, R.string.followups, R.string.pending,
        R.string.awaiting_dispatch, R.string.awaiting_response, R.string.recent)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            PageTitle(stringResource(title), stringResource(hint))
            if (change != null) SelectOption(stringResource(R.string.show), filter, filterLabels.map { stringResource(it) }, change)
        }
        if (cases.isEmpty()) item {
            Text(stringResource(R.string.no_cases), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (change == null && !warranty) TextButton(onClick = add) { Text(stringResource(R.string.add_purchase)) }
        }
        items(cases, key = { it.record.id }) { item ->
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Box(Modifier.padding(horizontal = 14.dp)) { RecordLine(item, date, if (warranty) 7 else filter, detail) }
            }
        }
    }
}

@Composable
internal fun CaseEditor(initial: CaseSnapshot?, cancel: () -> Unit, save: (RecoveryCase) -> Unit) {
    val c = initial?.record
    val locale = appLocale()
    val context = LocalContext.current
    var item by rememberSaveable { mutableStateOf(c?.item.orEmpty()) }
    var merchant by rememberSaveable { mutableStateOf(c?.merchant.orEmpty()) }
    var code by rememberSaveable { mutableStateOf(c?.currency ?: defaultCurrency(locale)) }
    var expected by rememberSaveable { mutableStateOf(c?.expected?.takeIf { it > 0 }?.let { Money.input(it, code, locale) }.orEmpty()) }
    var boughtFor by rememberSaveable { mutableStateOf(c?.purchaseAmount?.let { Money.input(it, code, locale) }.orEmpty()) }
    val stages = Stage.entries.filter { it !in setOf(Stage.PARTIAL, Stage.REFUNDED) }
    var stage by rememberSaveable { mutableStateOf(stages.indexOf(c?.stage).coerceAtLeast(0)) }
    var deadline by rememberSaveable { mutableStateOf(c?.deadline) }
    var follow by rememberSaveable { mutableStateOf(c?.followUp) }
    var warranty by rememberSaveable { mutableStateOf(c?.warranty) }
    var bought by rememberSaveable { mutableStateOf(c?.purchaseDate) }
    var reference by rememberSaveable { mutableStateOf(c?.orderReference.orEmpty()) }
    var carrier by rememberSaveable { mutableStateOf(c?.carrier.orEmpty()) }
    var tracking by rememberSaveable { mutableStateOf(c?.tracking.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(c?.note.orEmpty()) }
    var attachment by rememberSaveable { mutableStateOf(c?.attachment) }
    var attachmentError by rememberSaveable { mutableStateOf(false) }
    var showError by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && uri.scheme == "content") {
            attachmentError = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                attachment = uri.toString()
            }.isFailure
        }
    }
    AlertDialog(onDismissRequest = cancel,
        title = { Text(stringResource(if (c == null) R.string.add_purchase else R.string.edit_purchase)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(item, { item = it }, R.string.item, 140)
                Field(merchant, { merchant = it }, R.string.merchant, 100)
                SelectOption(stringResource(R.string.return_status), stage, stages.map { stringResource(it.label) }, { stage = it })
                Field(code, { code = it.uppercase(java.util.Locale.ROOT) }, R.string.currency, 3, enabled = initial?.receipts.isNullOrEmpty())
                Field(expected, { expected = it }, R.string.expected_amount, 28, keyboard = KeyboardType.Decimal)
                DateField(R.string.return_deadline, deadline) { deadline = it }
                DateField(R.string.followup_date, follow) { follow = it }
                TextButton(onClick = { expanded = !expanded }) { Text(stringResource(if (expanded) R.string.fewer_details else R.string.more_details)) }
                if (expanded) {
                    Field(boughtFor, { boughtFor = it }, R.string.purchase_amount, 28, keyboard = KeyboardType.Decimal)
                    DateField(R.string.purchase_date, bought) { bought = it }
                    DateField(R.string.warranty_expiry, warranty) { warranty = it }
                    Field(reference, { reference = it }, R.string.order_ref, 100)
                    Field(carrier, { carrier = it }, R.string.carrier, 100)
                    Field(tracking, { tracking = it }, R.string.tracking, 160)
                    Field(note, { note = it }, R.string.notes, 2000, singleLine = false)
                    TextButton(onClick = { pick.launch(arrayOf("application/pdf", "image/*")) }) { Text(stringResource(
                        if (attachment == null) R.string.attach else R.string.replace_attachment)) }
                    if (attachment != null) TextButton(onClick = { attachment = null }) { Text(stringResource(R.string.remove_attachment)) }
                    if (attachmentError) Text(stringResource(R.string.attachment_error), color = MaterialTheme.colorScheme.error)
                }
                if (showError) Text(stringResource(R.string.check_fields), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { Button(onClick = {
            val amount = if (expected.isBlank()) 0L else Money.parse(expected, code, locale)
            val purchase = if (boughtFor.isBlank()) null else Money.parse(boughtFor, code, locale)
            if (item.isBlank() || merchant.isBlank() || !Money.valid(code) || amount == null || (boughtFor.isNotBlank() && purchase == null)) {
                showError = true
            } else save(RecoveryCase(c?.id ?: 0L, item.trim(), merchant.trim(), code, amount, stages[stage], deadline, follow,
                note.trim(), warranty, purchase, bought, reference.trim(), carrier.trim(), tracking.trim(), attachment))
        }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = cancel) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun Field(value: String, change: (String) -> Unit, label: Int, maxLength: Int, enabled: Boolean = true,
                  singleLine: Boolean = true, keyboard: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(value, { if (it.length <= maxLength) change(it) }, modifier = Modifier.fillMaxWidth(), enabled = enabled,
        label = { Text(stringResource(label)) }, singleLine = singleLine, maxLines = if (singleLine) 1 else 4,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard))
}

@Composable
internal fun SelectOption(label: String, current: Int, options: List<String>, change: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.label_value, label, options[current]), Modifier.weight(1f))
            Icon(Icons.Default.KeyboardArrowDown, null)
        }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEachIndexed { i, text -> DropdownMenuItem(text = { Text(text) }, onClick = { change(i); expanded = false }) }
        }
    }
}

@Composable
internal fun DateField(label: Int, value: LocalDate?, clearable: Boolean = true, change: (LocalDate?) -> Unit) {
    val context = LocalContext.current
    val locale = appLocale()
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = {
            val start = value ?: LocalDate.now()
            DatePickerDialog(context, { _, year, month, day -> change(LocalDate.of(year, month + 1, day)) },
                start.year, start.monthValue - 1, start.dayOfMonth).show()
        }, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.label_value, stringResource(label), value?.let { dateLabel(it, locale) } ?: stringResource(R.string.unset)),
                Modifier.weight(1f))
            Icon(Icons.Default.DateRange, null)
        }
        if (clearable && value != null) IconButton(onClick = { change(null) }) {
            Icon(Icons.Default.Close, stringResource(R.string.clear_field, stringResource(label)))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CaseDetails(item: CaseSnapshot, today: LocalDate, demo: Boolean, model: LedgerModel,
                         close: () -> Unit, edit: () -> Unit) {
    val c = item.record
    val locale = appLocale()
    val context = LocalContext.current
    var action by rememberSaveable { mutableStateOf("") }
    var attachmentError by rememberSaveable { mutableStateOf(false) }
    var removeId by rememberSaveable { mutableStateOf<Long?>(null) }
    ModalBottomSheet(onDismissRequest = close, sheetMaxWidth = 640.dp) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                PageTitle(c.item, c.merchant)
                Text(stringResource(item.stage.label), color = MaterialTheme.colorScheme.primary)
            }
            item {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(stringResource(R.string.waiting_for), style = MaterialTheme.typography.labelMedium)
                        Text(Money.format(item.outstanding, c.currency, locale), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        val expected = Money.format(c.expected.toBigDecimal(), c.currency, locale)
                        val received = Money.format(item.received, c.currency, locale)
                        Text(stringResource(R.string.label_value, stringResource(R.string.expected_amount), expected))
                        Text(stringResource(R.string.label_value, stringResource(R.string.received), received))
                    }
                }
            }
            item {
                c.deadline?.let { Text(stringResource(R.string.label_value, stringResource(R.string.return_deadline), dateLabel(it, locale))) }
                c.followUp?.let { Text(stringResource(R.string.label_value, stringResource(R.string.followup_date), dateLabel(it, locale))) }
                c.warranty?.let { Text(stringResource(R.string.label_value, stringResource(R.string.warranty_expiry), dateLabel(it, locale))) }
                c.purchaseDate?.let { Text(stringResource(R.string.label_value, stringResource(R.string.purchase_date), dateLabel(it, locale))) }
                c.purchaseAmount?.let { Text(stringResource(R.string.label_value, stringResource(R.string.purchase_amount), Money.format(it.toBigDecimal(), c.currency, locale))) }
                listOf(R.string.order_ref to c.orderReference, R.string.carrier to c.carrier, R.string.tracking to c.tracking, R.string.notes to c.note)
                    .filter { it.second.isNotBlank() }.forEach { (label, text) -> SelectionContainer { Text(stringResource(R.string.label_value, stringResource(label), text)) } }
                if (c.attachment != null) {
                    TextButton(onClick = {
                        attachmentError = runCatching {
                            val uri = android.net.Uri.parse(c.attachment)
                            require(uri.scheme == "content")
                            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, context.contentResolver.getType(uri))
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                        }.isFailure
                    }) { Text(stringResource(R.string.open_attachment)) }
                    if (attachmentError) Text(stringResource(R.string.attachment_error), color = MaterialTheme.colorScheme.error)
                }
            }
            if (!demo) item {
                Button(onClick = { action = "receive" }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.record_refund)) }
                OutlinedButton(onClick = { action = "contact" }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.log_contact)) }
                if (c.followUp != null && !item.stage.terminal) OutlinedButton(onClick = { action = "reschedule" }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.reschedule))
                }
                TextButton(onClick = { action = "draft" }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.draft_followup)) }
            }
            if (item.receipts.isNotEmpty()) item { Text(stringResource(R.string.refund_history), style = MaterialTheme.typography.titleMedium) }
            items(item.receipts, key = { "receipt-${it.id}" }) { receipt ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${dateLabel(receipt.day, locale)} · ${Money.format(receipt.amount.toBigDecimal(), c.currency, locale)}", Modifier.weight(1f))
                    if (!demo) IconButton(onClick = { removeId = receipt.id }) { Icon(Icons.Default.Delete, stringResource(R.string.remove_refund)) }
                }
            }
            if (item.contacts.isNotEmpty()) item { Text(stringResource(R.string.contact_history), style = MaterialTheme.typography.titleMedium) }
            items(item.contacts, key = { "contact-${it.id}" }) { entry ->
                Text(stringResource(R.string.label_value, dateLabel(entry.day, locale), entry.note))
            }
            if (!demo) item {
                HorizontalDivider()
                TextButton(onClick = edit) { Text(stringResource(R.string.edit_purchase)) }
                TextButton(onClick = { action = "delete" }) { Text(stringResource(R.string.delete_purchase), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    val dismiss = { action = "" }
    when (action) {
        "receive" -> PaymentDialog(c.currency, today, dismiss) { amount, day -> model.receive(c.id, c.currency, amount, day); dismiss() }
        "contact" -> ContactDialog(today, c.followUp, dismiss) { note, day, next -> model.contact(c.id, note, day, next); dismiss() }
        "reschedule" -> {
            var next by rememberSaveable { mutableStateOf(c.followUp) }
            AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.reschedule)) },
                text = { DateField(R.string.followup_date, next) { next = it } },
                confirmButton = { Button(onClick = { model.snooze(c.id, next); dismiss() }) { Text(stringResource(R.string.save)) } },
                dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } })
        }
        "draft" -> {
            var draft by rememberSaveable { mutableStateOf("") }
            val template = stringResource(R.string.draft_template, c.merchant, c.item)
            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
            val clipLabel = stringResource(R.string.draft_followup)
            AlertDialog(onDismissRequest = dismiss, title = { Text(clipLabel) },
                text = { OutlinedTextField(draft.ifEmpty { template }, { draft = it }, maxLines = 9, modifier = Modifier.fillMaxWidth()) },
                confirmButton = { Button(onClick = {
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText(clipLabel, draft.ifEmpty { template }).apply {
                        if (android.os.Build.VERSION.SDK_INT >= 24) description.extras = android.os.PersistableBundle().apply {
                            putBoolean("android.content.extra.IS_SENSITIVE", true)
                        }
                    })
                    dismiss()
                }) { Text(stringResource(R.string.copy_draft)) } },
                dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.close)) } })
        }
        "delete" -> Confirm(R.string.delete_purchase, R.string.delete_purchase_warning, dismiss) { model.delete(c.id); dismiss(); close() }
    }
    removeId?.let { id -> Confirm(R.string.remove_refund, R.string.remove_refund_warning, { removeId = null }) {
        model.removeReceipt(c.id, id); removeId = null
    } }
}

@Composable
private fun PaymentDialog(code: String, today: LocalDate, close: () -> Unit, save: (Long, LocalDate) -> Unit) {
    val locale = appLocale()
    var amount by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableStateOf(today) }
    val parsed = Money.parse(amount, code, locale)?.takeIf { it > 0 }
    AlertDialog(onDismissRequest = close, title = { Text(stringResource(R.string.record_refund)) },
        text = { Column {
            Text(stringResource(R.string.record_refund_hint, code))
            Field(amount, { amount = it }, R.string.amount_received, 28, keyboard = KeyboardType.Decimal)
            DateField(R.string.received_date, day, false) { if (it != null) day = it }
            if (day.isAfter(today)) Text(stringResource(R.string.future_date), color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { Button(onClick = { parsed?.let { save(it, day) } }, enabled = parsed != null && !day.isAfter(today)) {
            Text(stringResource(R.string.save))
        } }, dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun ContactDialog(today: LocalDate, current: LocalDate?, close: () -> Unit, save: (String, LocalDate, LocalDate?) -> Unit) {
    var note by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableStateOf(today) }
    var next by rememberSaveable { mutableStateOf(current?.takeIf { it.isAfter(today) }) }
    AlertDialog(onDismissRequest = close, title = { Text(stringResource(R.string.log_contact)) },
        text = { Column {
            Text(stringResource(R.string.log_contact_hint))
            Field(note, { note = it }, R.string.notes, 2000, singleLine = false)
            DateField(R.string.contact_date, day, false) { if (it != null) day = it }
            DateField(R.string.followup_date, next) { next = it }
            if (day.isAfter(today)) Text(stringResource(R.string.future_date), color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { Button(onClick = { save(note.trim(), day, next) }, enabled = note.isNotBlank() && !day.isAfter(today)) {
            Text(stringResource(R.string.save))
        } }, dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun Confirm(title: Int, message: Int, close: () -> Unit, action: () -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text(stringResource(title)) }, text = { Text(stringResource(message)) },
        confirmButton = { Button(onClick = action) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.cancel)) } })
}

@Composable
internal fun SettingsPage(spoken: Boolean, changeSpoken: (Boolean) -> Unit, demo: Boolean, toggleDemo: () -> Unit,
                          erase: () -> Unit, voice: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    var reminders by rememberSaveable { mutableStateOf(false) }
    var privacy by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val languageOptions = listOf("", "en", "es", "fr", "ar")
    val languages = listOf(stringResource(R.string.device_language), "English", "Español", "Français", "العربية")
    val localPrefs = remember { context.getSharedPreferences("preferences", 0) }
    val index = languageOptions.indexOf(localPrefs.getString("locale", "")).coerceAtLeast(0)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageTitle(stringResource(R.string.more), stringResource(R.string.settings_hint))
        SelectOption(stringResource(R.string.language), index, languages) { indexSelected ->
            localPrefs.edit().putString("locale", languageOptions[indexSelected]).apply()
            (context as? MainActivity)?.recreate()
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.spoken_feedback), Modifier.weight(1f)); Switch(spoken, changeSpoken)
        }
        Text(stringResource(R.string.spoken_privacy), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = voice, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.voice_title)) }
        OutlinedButton(onClick = { reminders = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(painter = androidx.compose.ui.res.painterResource(R.drawable.ic_reminder), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.reminder_settings))
        }
        HorizontalDivider()
        Text(stringResource(R.string.demo_hint))
        OutlinedButton(onClick = toggleDemo, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (demo) R.string.exit_demo else R.string.preview_demo))
        }
        HorizontalDivider()
        Text(stringResource(R.string.privacy_hint))
        OutlinedButton(onClick = { privacy = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.privacy_policy_title))
        }
        TextButton(onClick = { confirm = true }, enabled = !demo) { Text(stringResource(R.string.erase_data), color = MaterialTheme.colorScheme.error) }
    }
    if (confirm) Confirm(R.string.erase_data, R.string.erase_warning, { confirm = false }) { confirm = false; erase() }
    if (reminders) ReminderSettingsDialog { reminders = false }
    if (privacy) AlertDialog(
        onDismissRequest = { privacy = false },
        title = { Text(stringResource(R.string.privacy_policy_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.privacy_policy_text))
            }
        },
        confirmButton = { TextButton(onClick = { privacy = false }) { Text(stringResource(R.string.close)) } }
    )
}
