package app.refundavio

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReminderSettingsDialog(close: () -> Unit) {
    val context = LocalContext.current
    val locale = appLocale()
    var choices by remember { mutableStateOf(ReminderPreferences.read(context)) }
    var blocked by remember { mutableStateOf(false) }
    var customDialog by rememberSaveable { mutableStateOf(false) }
    var quietTimeError by rememberSaveable { mutableStateOf(false) }
    val checkAccess = {
        blocked = ReminderKind.entries.any { choices.enables(it) && !ReminderScheduler.canPost(context, it) }
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        checkAccess()
        ReminderScheduler.request(context)
    }
    fun save(next: ReminderSettings, permissionOnEnable: Boolean = false) {
        choices = next
        ReminderPreferences.save(context, next)
        if (next.enabled) ReminderScheduler.createChannels(context)
        checkAccess()
        ReminderScheduler.request(context)
        if (permissionOnEnable && Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LifecycleResumeEffect(Unit) {
        checkAccess()
        onPauseOrDispose {}
    }

    val formatTime = remember(locale, context) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale,
            if (DateFormat.is24HourFormat(context)) "Hm" else "hm"), locale).withDecimalStyle(DecimalStyle.of(locale))
    }
    fun selectTime(current: LocalTime, changed: (LocalTime) -> Unit) {
        TimePickerDialog(context, { _, hour, minute -> changed(LocalTime.of(hour, minute)) },
            current.hour, current.minute, DateFormat.is24HourFormat(context)).show()
    }
    AlertDialog(onDismissRequest = close,
        title = { Text(stringResource(R.string.reminder_settings)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.reminder_intro))
                ReminderToggle(stringResource(R.string.deadlines), choices.deadlines) { value ->
                    save(choices.copy(deadlines = value), permissionOnEnable = value)
                }
                if (choices.deadlines) {
                    Text(stringResource(R.string.reminder_lead), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ReminderPreferences.presetDays.forEach { days ->
                            val selected = days in choices.leadDays
                            FilterChip(selected = selected,
                                enabled = !selected || choices.allLeadDays.size > 1 || choices.customLead == days,
                                onClick = { save(choices.copy(leadDays = if (selected) choices.leadDays - days else choices.leadDays + days)) },
                                label = { Text(if (days == 0) stringResource(R.string.reminder_on_day)
                                    else pluralStringResource(R.plurals.reminder_days_before, days, days)) })
                        }
                    }
                    TextButton(onClick = { customDialog = true }) {
                        val days = choices.customLead
                        Text(if (days == null) stringResource(R.string.reminder_custom)
                            else stringResource(R.string.label_value, stringResource(R.string.reminder_custom),
                                if (days == 0) stringResource(R.string.reminder_on_day)
                                else pluralStringResource(R.plurals.reminder_days_before, days, days)))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ReminderToggle(stringResource(R.string.followups), choices.followUps) { value ->
                    save(choices.copy(followUps = value), permissionOnEnable = value)
                }
                if (choices.followUps) Text(stringResource(R.string.reminder_followup_hint))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ReminderToggle(stringResource(R.string.warranties), choices.warranties) { value ->
                    save(choices.copy(warranties = value), permissionOnEnable = value)
                }
                if (choices.warranties) Text(stringResource(R.string.reminder_warranty_hint))
                OutlinedButton(onClick = { selectTime(choices.time) { save(choices.copy(time = it)) } }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.label_value, stringResource(R.string.reminder_time), choices.time.format(formatTime)))
                }
                ReminderToggle(stringResource(R.string.quiet_hours), choices.quietHours != null) { enabled ->
                    quietTimeError = false
                    save(choices.copy(quietHours = if (enabled) QuietHours(LocalTime.of(22, 0), LocalTime.of(7, 0)) else null))
                }
                choices.quietHours?.let { quiet ->
                    Text(stringResource(R.string.quiet_hours_hint), style = MaterialTheme.typography.bodySmall)
                    for ((label, current, other) in listOf(Triple(R.string.quiet_start, quiet.start, quiet.end),
                        Triple(R.string.quiet_end, quiet.end, quiet.start))) {
                        OutlinedButton(onClick = { selectTime(current) { updated ->
                            quietTimeError = updated == other
                            if (!quietTimeError) save(choices.copy(quietHours = if (label == R.string.quiet_start)
                                quiet.copy(start = updated) else quiet.copy(end = updated)))
                        } }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.label_value, stringResource(label), current.format(formatTime)))
                        }
                    }
                    if (quietTimeError) Text(stringResource(R.string.quiet_time_error), color = MaterialTheme.colorScheme.error)
                }
                Text(stringResource(R.string.reminder_timing), style = MaterialTheme.typography.bodySmall)
                if (blocked) {
                    Text(stringResource(R.string.reminder_blocked), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                    }) { Text(stringResource(R.string.reminder_system_settings)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text(stringResource(R.string.close)) } })

    if (customDialog) {
        var raw by rememberSaveable { mutableStateOf(choices.customLead?.toString().orEmpty()) }
        val days = raw.toIntOrNull()?.takeIf { it in 0..30 }
        AlertDialog(onDismissRequest = { customDialog = false },
            title = { Text(stringResource(R.string.reminder_custom)) },
            text = {
                OutlinedTextField(raw, { if (it.length <= 2) raw = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.reminder_custom_range)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            },
            confirmButton = { Button(onClick = {
                if (days != null) save(choices.copy(customLead = days))
                customDialog = false
            }, enabled = days != null) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = {
                if (choices.customLead != null) {
                    save(choices.copy(customLead = null, leadDays = choices.leadDays.ifEmpty { setOf(1) }))
                }
                customDialog = false
            }) { Text(stringResource(if (choices.customLead != null) R.string.reminder_clear_custom else R.string.cancel)) } })
    }
}

@Composable
private fun ReminderToggle(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Switch, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Switch(checked, onCheckedChange = null)
    }
}
