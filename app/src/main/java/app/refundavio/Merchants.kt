package app.refundavio

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

private fun httpsWebsite(value: String): Uri? = Uri.parse(value).takeIf {
    value.none { c -> c.isWhitespace() || c == '\\' } &&
        it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrBlank() && it.userInfo == null
}

@Composable
internal fun MerchantDirectory(
    profiles: List<MerchantRow>, cases: List<CaseSnapshot>, save: (MerchantRow) -> Unit,
    openRecord: (Long) -> Unit, close: () -> Unit,
) {
    val context = LocalContext.current
    val locale = appLocale()
    val recordsByMerchant = remember(cases) { cases.groupBy { merchantKey(it.record.merchant) } }
    val directory = remember(profiles, recordsByMerchant) {
        val stored = profiles.associateBy(MerchantRow::key)
        val derived = recordsByMerchant.mapValues { (key, records) ->
            MerchantRow(key, records.first().record.merchant, "", "", "", "")
        }
        derived + stored
    }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var linkError by rememberSaveable { mutableStateOf(false) }
    val merchant = selected?.let(directory::get)
    if (editing) {
        MerchantEditor(merchant, directory.keys, { editing = false }) {
            save(it)
            editing = false
        }
        return
    }
    if (merchant == null) {
        AlertDialog(onDismissRequest = close, title = { Text(stringResource(R.string.merchants)) }, text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                if (directory.isEmpty()) item { Text(stringResource(R.string.no_cases)) }
                val collator = java.text.Collator.getInstance(locale)
                items(directory.values.sortedWith { left, right -> collator.compare(left.name, right.name) }, key = { it.key }) { row ->
                    OutlinedButton(onClick = { selected = row.key; linkError = false }, modifier = Modifier.fillMaxWidth()) {
                        Text(row.name)
                    }
                }
            }
        }, confirmButton = { Button(onClick = { selected = null; editing = true }) { Text(stringResource(R.string.add_merchant)) } },
            dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.close)) } })
        return
    }

    val entries = recordsByMerchant[merchant.key].orEmpty()
    val contacts = remember(entries) {
        entries.flatMap { entry -> entry.contacts.map { entry.record.item to it } }.sortedByDescending { it.second.day }
    }
    AlertDialog(onDismissRequest = { selected = null }, title = { Text(merchant.name) }, text = {
        LazyColumn(Modifier.heightIn(max = 430.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                for ((label, value) in listOf(R.string.merchant_website to merchant.website,
                    R.string.merchant_phone to merchant.phone, R.string.merchant_email to merchant.email,
                    R.string.merchant_policy to merchant.policy)) {
                    if (value.isNotBlank()) SelectionContainer { Text(stringResource(R.string.label_value, stringResource(label), value)) }
                }
                httpsWebsite(merchant.website)?.let { uri ->
                    TextButton(onClick = { linkError = !openWebsite(context, uri) }) { Text(stringResource(R.string.merchant_open_website)) }
                }
                if (linkError) Text(stringResource(R.string.merchant_link_error), color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.purchases), style = MaterialTheme.typography.titleSmall)
            }
            if (entries.isEmpty()) item { Text(stringResource(R.string.no_cases)) }
            items(entries, key = { it.record.id }) { entry ->
                OutlinedButton(onClick = { openRecord(entry.record.id) }, modifier = Modifier.fillMaxWidth()) {
                    val status = stringResource(entry.stage.label)
                    Text(stringResource(R.string.label_value, entry.record.item, status))
                }
                if (entry.received.signum() > 0 || entry.outstanding.signum() > 0) {
                    for ((label, value) in listOf(R.string.received to entry.received, R.string.waiting_for to entry.outstanding)) {
                        Text(stringResource(R.string.label_value, stringResource(label), Money.format(value, entry.record.currency, locale)))
                    }
                }
            }
            if (contacts.isNotEmpty()) item { Text(stringResource(R.string.contact_history), style = MaterialTheme.typography.titleSmall) }
            items(contacts) { (item, contact) ->
                Text(stringResource(R.string.label_value, dateLabel(contact.day, locale), item))
                SelectionContainer { Text(contact.note) }
            }
        }
    }, confirmButton = { Button(onClick = { editing = true }) { Text(stringResource(R.string.edit_merchant)) } },
        dismissButton = { TextButton(onClick = { selected = null }) { Text(stringResource(R.string.close)) } })
}

private fun openWebsite(context: Context, uri: Uri): Boolean = runCatching {
    context.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
}.isSuccess

@Composable
private fun MerchantEditor(merchant: MerchantRow?, knownKeys: Set<String>, close: () -> Unit, save: (MerchantRow) -> Unit) {
    var name by rememberSaveable { mutableStateOf(merchant?.name.orEmpty()) }
    var website by rememberSaveable { mutableStateOf(merchant?.website.orEmpty()) }
    var phone by rememberSaveable { mutableStateOf(merchant?.phone.orEmpty()) }
    var email by rememberSaveable { mutableStateOf(merchant?.email.orEmpty()) }
    var policy by rememberSaveable { mutableStateOf(merchant?.policy.orEmpty()) }
    val validWebsite = website.isBlank() || httpsWebsite(website.trim()) != null
    val validEmail = email.isBlank() || android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val duplicate = merchant == null && merchantKey(name) in knownKeys
    AlertDialog(onDismissRequest = close, title = { Text(stringResource(if (merchant == null) R.string.add_merchant else R.string.edit_merchant)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(name, { name = it }, R.string.merchant, 100, enabled = merchant == null)
                Field(website, { website = it }, R.string.merchant_website, 350)
                Field(phone, { phone = it }, R.string.merchant_phone, 80)
                Field(email, { email = it }, R.string.merchant_email, 254)
                Field(policy, { policy = it }, R.string.merchant_policy, 2000, singleLine = false)
                if (!validWebsite || !validEmail) Text(stringResource(R.string.merchant_check_contact), color = MaterialTheme.colorScheme.error)
                if (duplicate) Text(stringResource(R.string.merchant_duplicate), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { Button(enabled = name.isNotBlank() && validWebsite && validEmail && !duplicate, onClick = {
            save(MerchantRow(merchantKey(name), name.trim(), website.trim(), phone.trim(), email.trim(), policy.trim()))
        }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.cancel)) } })
}
