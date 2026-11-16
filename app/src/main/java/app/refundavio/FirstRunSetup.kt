package app.refundavio

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale

private const val PREFERRED_CURRENCY = "preferred_currency"

internal fun preferredCurrency(context: Context, locale: Locale): String =
    context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        .getString(PREFERRED_CURRENCY, null)?.takeIf(Money::valid) ?: defaultCurrency(locale)

@Composable
internal fun SetupCard(open: () -> Unit, skip: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.setup_hint), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = open, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.setup_open)) }
            TextButton(onClick = skip) { Text(stringResource(R.string.setup_skip)) }
        }
    }
}

@Composable
internal fun FirstRunSetup(spoken: Boolean, changeSpoken: (Boolean) -> Unit, addPurchase: () -> Unit, close: () -> Unit) {
    var section by rememberSaveable { mutableStateOf(0) }
    when (section) {
        1 -> ReminderSettingsDialog { section = 0 }
        2 -> PrivacyPolicyDialog { section = 0 }
        else -> AlertDialog(onDismissRequest = close, title = { Text(stringResource(R.string.setup_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.setup_hint))
                    AppLanguagePicker()
                    PreferredCurrencyPicker()
                    OutlinedButton(onClick = { section = 1 }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.reminder_settings))
                    }
                    OutlinedButton(onClick = { section = 2 }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.privacy_policy_title))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.spoken_feedback), Modifier.weight(1f))
                        Switch(spoken, changeSpoken)
                    }
                    Text(stringResource(R.string.spoken_privacy), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { Button(onClick = addPurchase) { Text(stringResource(R.string.add_purchase)) } },
            dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.close)) } },
        )
    }
}

@Composable
internal fun PreferredCurrencyPicker() {
    val context = LocalContext.current
    val locale = appLocale()
    val prefs = remember { context.getSharedPreferences("preferences", Context.MODE_PRIVATE) }
    var currency by remember { mutableStateOf(preferredCurrency(context, locale)) }
    var editing by rememberSaveable { mutableStateOf(false) }
    fun save(code: String?) {
        prefs.edit().putString(PREFERRED_CURRENCY, code).apply()
        currency = preferredCurrency(context, locale)
        editing = false
    }
    OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.label_value, stringResource(R.string.preferred_currency), currency))
    }
    if (editing) {
        var input by rememberSaveable { mutableStateOf(currency) }
        AlertDialog(onDismissRequest = { editing = false }, title = { Text(stringResource(R.string.preferred_currency)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(input, { input = it.uppercase(Locale.ROOT).filter { c -> c in 'A'..'Z' }.take(3) },
                        singleLine = true, label = { Text(stringResource(R.string.currency)) })
                    TextButton(onClick = { save(null) }) { Text(stringResource(R.string.device_currency)) }
                }
            },
            confirmButton = { Button(enabled = Money.valid(input), onClick = { save(input) }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
