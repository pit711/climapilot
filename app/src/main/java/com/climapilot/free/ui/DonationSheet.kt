package com.climapilot.free.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.climapilot.free.R

private const val KOFI_URL = "https://ko-fi.com/711it"
private const val PAYPAL_URL = "https://paypal.me/711IT"

/**
 * EN: The (rare, dismissible) "enjoying ClimaPilot?" donation bottom sheet. Shows a short thank-you and
 *     the Ko-fi / PayPal buttons, plus "maybe later" and a permanent "already supported" opt-out. When
 *     and how often it appears is decided by [com.climapilot.free.DonationPrompt].
 * DE: Das (seltene, schließbare) „Gefällt dir ClimaPilot?"-Spenden-Bottom-Sheet. Zeigt einen kurzen Dank
 *     und die Ko-fi-/PayPal-Buttons sowie „vielleicht später" und ein dauerhaftes „schon unterstützt"-Aus.
 *     Wann und wie oft es erscheint, entscheidet [com.climapilot.free.DonationPrompt].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonationSheet(onDismiss: () -> Unit, onNever: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val open: (String) -> Unit = { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
        onDismiss()
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Favorite, null, tint = cs.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.donate_prompt_title), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = cs.onSurface)
            }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.donate_prompt_body), fontSize = 14.sp, color = cs.onSurfaceVariant, lineHeight = 20.sp)
            Spacer(Modifier.height(18.dp))
            FilledTonalButton(onClick = { open(KOFI_URL) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Coffee, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.support_kofi))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { open(PAYPAL_URL) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Payments, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.support_paypal))
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.donate_later)) }
                TextButton(onClick = onNever) { Text(stringResource(R.string.donate_already)) }
            }
        }
    }
}
