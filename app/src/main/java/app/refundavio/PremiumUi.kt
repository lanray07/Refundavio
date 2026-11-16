package app.refundavio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun PremiumDialog(billing: PremiumBilling, state: PremiumState, close: () -> Unit) {
    LaunchedEffect(billing) { billing.open() }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(stringResource(R.string.premium_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.premium_value))
                Text(stringResource(R.string.premium_privacy))
                val status = when {
                    !PremiumBilling.configured -> R.string.premium_unavailable
                    state.busy -> R.string.premium_checking
                    state.active -> R.string.premium_active
                    state.message != null -> state.message
                    state.price == null -> R.string.premium_unavailable
                    else -> null
                }
                if (status != null) Text(stringResource(status))
                if (PremiumBilling.configured) {
                    val price = state.price
                    if (state.canBuy && price != null) {
                        Button(onClick = billing::buy, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.premium_buy, price))
                        }
                    }
                    OutlinedButton(onClick = billing::restore, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.premium_restore))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text(stringResource(R.string.close)) } },
    )
}
