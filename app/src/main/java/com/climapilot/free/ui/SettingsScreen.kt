package com.climapilot.free.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.climapilot.free.AcViewModel
import com.climapilot.free.R
import com.climapilot.free.TokenRepo

// EN: Outbound links for the support and credits cards. / DE: Externe Links für die Unterstützungs- und Danksagungs-Karten.
private const val KOFI_URL = "https://ko-fi.com/711it"
private const val PAYPAL_URL = "https://paypal.me/711IT"
private const val CREDITS_URL = "https://github.com/mill1000/midea-msmart"

/**
 * EN: Settings screen: app header with version, donation/support links, changelog and credits.
 * DE: Einstellungs-Bildschirm: App-Kopf mit Version, Spenden-/Unterstützungs-Links, Änderungsliste und Danksagung.
 */
@Composable
fun SettingsScreen(vm: AcViewModel, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    val openUrl: (String) -> Unit = { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 48.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, stringResource(R.string.cd_back))
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.settings),
                    fontWeight = FontWeight.Bold, fontSize = 22.sp, color = cs.onBackground,
                )
            }
        }
        item { AppHeaderCard(version) }
        item { DisplayCard(vm) }
        item { ExportTokenCard() }
        item { SupportCard(openUrl) }
        item { ChangelogCard() }
        item { CreditsCard(openUrl) }
    }
}

/** EN: Credits to the midea-msmart project the protocol is ported from. / DE: Danksagung an das midea-msmart-Projekt, aus dem das Protokoll portiert ist. */
@Composable
private fun CreditsCard(onOpen: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Code, null, tint = cs.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.credits_title), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = cs.onSurface)
            }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.credits_body), fontSize = 14.sp, color = cs.onSurfaceVariant, lineHeight = 20.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { onOpen(CREDITS_URL) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Code, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.credits_link))
            }
        }
    }
}

/** EN: Donation links (Ko-fi / PayPal) — the app is free and ad-free. / DE: Spenden-Links (Ko-fi / PayPal) — die App ist kostenlos und werbefrei. */
@Composable
private fun SupportCard(onOpen: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Favorite, null, tint = cs.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.support_title), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = cs.onSurface)
            }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.support_body), fontSize = 14.sp, color = cs.onSurfaceVariant, lineHeight = 20.sp)
            Spacer(Modifier.height(14.dp))
            FilledTonalButton(onClick = { onOpen(KOFI_URL) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Coffee, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.support_kofi))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { onOpen(PAYPAL_URL) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Payments, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.support_paypal))
            }
        }
    }
}

/** EN: App icon, name and installed version. / DE: App-Icon, Name und installierte Version. */
@Composable
private fun AppHeaderCard(version: String) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(cs.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.AcUnit, null, tint = cs.onPrimaryContainer) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = cs.onSurface)
                Text(stringResource(R.string.settings_version, version), color = cs.onSurfaceVariant, fontSize = 13.sp)
            }
        }
    }
}

/** EN: Per-version changelog entries. / DE: Änderungsliste je Version. */
/**
 * EN: Token export. Lists every device whose token/key we have cached and lets the user copy or share
 *     each one as a text block — an offline backup, or for reuse in other local-control tools.
 * DE: Token-Export. Listet jedes Gerät, dessen Token/Key gecacht ist, und erlaubt das Kopieren oder
 *     Teilen als Textblock — als Offline-Backup oder zur Nutzung in anderen Tools zur lokalen Steuerung.
 */
@Composable
private fun ExportTokenCard() {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    // EN: Re-read after an import so the list refreshes. DE: Nach einem Import neu einlesen, damit die Liste aktualisiert.
    var entries by remember { mutableStateOf(TokenRepo.list(context)) }
    var showImport by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Key, null, tint = cs.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.export_title), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = cs.onSurface)
            }
            Spacer(Modifier.height(10.dp))
            if (entries.isEmpty()) {
                Text(stringResource(R.string.export_none), fontSize = 14.sp, color = cs.onSurfaceVariant, lineHeight = 20.sp)
            } else {
                Text(stringResource(R.string.export_hint), fontSize = 13.sp, color = cs.onSurfaceVariant, lineHeight = 18.sp)
                entries.forEach { e ->
                    Spacer(Modifier.height(10.dp))
                    Text("${e.name.ifBlank { "Midea" }}  ·  ${e.ip}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = cs.onSurface)
                    Row {
                        TextButton(onClick = {
                            // EN: Copy the credential block to the clipboard. / DE: Den Zugangsdaten-Block in die Zwischenablage kopieren.
                            clipboard.setText(AnnotatedString(TokenRepo.exportText(e)))
                            Toast.makeText(context, context.getString(R.string.export_copied), Toast.LENGTH_SHORT).show()
                        }) { Text(stringResource(R.string.export_copy)) }
                        TextButton(onClick = {
                            // EN: Hand off to the Android share sheet. / DE: An die Android-Teilen-Funktion übergeben.
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, TokenRepo.exportText(e))
                            }
                            context.startActivity(Intent.createChooser(send, null))
                        }) { Text(stringResource(R.string.export_share)) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { showImport = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.import_button))
            }
        }
    }
    if (showImport) {
        ImportTokenDialog(
            onDismiss = { showImport = false },
            onImport = { text ->
                val ok = TokenRepo.importText(context, text)
                Toast.makeText(
                    context,
                    context.getString(if (ok) R.string.import_ok else R.string.import_error),
                    Toast.LENGTH_SHORT,
                ).show()
                if (ok) {
                    entries = TokenRepo.list(context)
                    showImport = false
                }
            },
        )
    }
}

/**
 * EN: Dialog to paste an exported token block and import it (makes that device offline-ready).
 * DE: Dialog zum Einfügen und Importieren eines exportierten Token-Blocks (macht das Gerät offline-bereit).
 */
@Composable
private fun ImportTokenDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_title)) },
        text = {
            Column {
                Text(stringResource(R.string.import_hint), fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.import_field)) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onImport(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.import_button))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * EN: Display & price settings: temperature unit (°C/°F) and electricity price per kWh used for the
 *     cost estimate on the control screen.
 * DE: Anzeige- & Preis-Einstellungen: Temperatureinheit (°C/°F) und Strompreis pro kWh für die
 *     Kostenschätzung im Steuer-Bildschirm.
 */
@Composable
private fun DisplayCard(vm: AcViewModel) {
    val cs = MaterialTheme.colorScheme
    var priceText by remember { mutableStateOf(if (vm.pricePerKwh > 0) vm.pricePerKwh.toString() else "") }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, null, tint = cs.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_display), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = cs.onSurface)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.unit_fahrenheit), Modifier.weight(1f), color = cs.onSurface, fontSize = 15.sp)
                Switch(checked = vm.useFahrenheit, onCheckedChange = { vm.setFahrenheit(it) })
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = priceText,
                onValueChange = { input ->
                    // EN: Accept digits + one separator; persist immediately (0 when empty/invalid).
                    // DE: Ziffern + ein Trennzeichen zulassen; sofort speichern (0 bei leer/ungültig).
                    priceText = input.replace(',', '.').filter { it.isDigit() || it == '.' }
                    vm.updatePricePerKwh(priceText.toDoubleOrNull() ?: 0.0)
                },
                label = { Text(stringResource(R.string.price_per_kwh)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.price_hint), fontSize = 12.sp, color = cs.onSurfaceVariant, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun ChangelogCard() {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, null, tint = cs.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.changelog_title), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = cs.onSurface)
            }
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.changelog_0_3_1_title), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = cs.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.changelog_0_3_1_body), fontSize = 14.sp, color = cs.onSurfaceVariant, lineHeight = 22.sp)
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.changelog_0_3_title), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = cs.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.changelog_0_3_body), fontSize = 14.sp, color = cs.onSurfaceVariant, lineHeight = 22.sp)
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.changelog_0_2_title), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = cs.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.changelog_0_2_body), fontSize = 14.sp, color = cs.onSurfaceVariant, lineHeight = 22.sp)
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.changelog_0_1_title), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = cs.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.changelog_0_1_body), fontSize = 14.sp, color = cs.onSurfaceVariant, lineHeight = 22.sp)
        }
    }
}
