package com.climapilot.free.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
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
import com.climapilot.free.ReliabilityHelper
import com.climapilot.free.SettingsRepo
import com.climapilot.free.TokenRepo

// EN: Outbound links for the support and credits cards. / DE: Externe Links für die Unterstützungs- und Danksagungs-Karten.
private const val KOFI_URL = "https://ko-fi.com/711it"
private const val PAYPAL_URL = "https://paypal.me/711IT"
private const val CREDITS_URL = "https://github.com/mill1000/midea-msmart"

/**
 * EN: Settings screen: app header with version, donation/support links, changelog and credits.
 *
 *     Built from the grouped-list pieces in [Inset.kt]: a quiet uppercase caption over each group, one
 *     surface level, hairline separators between rows, and the explaining sentence set *below* its
 *     group as a footnote. The icon-headed cards it replaces gave every section the same weight, which
 *     on a page this long turned into a wall of boxes.
 *
 * DE: Einstellungs-Bildschirm: App-Kopf mit Version, Spenden-/Unterstützungs-Links, Änderungsliste und
 *     Danksagung.
 *
 *     Gebaut aus den Bausteinen für gruppierte Listen in [Inset.kt]: eine ruhige Versal-Überschrift über
 *     jeder Gruppe, eine Flächenebene, Haarlinien zwischen den Zeilen, und der erklärende Satz *unter*
 *     seiner Gruppe als Fußnote. Die abgelösten Karten mit Icon-Kopf gaben jedem Abschnitt dasselbe
 *     Gewicht — auf einer so langen Seite wurde daraus eine Wand aus Kästen.
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
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 48.dp, bottom = 40.dp),
        // EN: The same rhythm the control surface uses between groups — a caption needs air above it to
        //     read as a heading rather than as a stray line of the group before.
        // DE: Derselbe Rhythmus wie zwischen den Gruppen der Steuerfläche — eine Überschrift braucht Luft
        //     darüber, damit sie als Überschrift wirkt und nicht als verirrte Zeile der Gruppe davor.
        verticalArrangement = Arrangement.spacedBy(22.dp),
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
        item { UpdateCard(vm) }
        item { DisplayCard(vm) }
        item { PollingCard(vm) }
        item { BetaCard(vm) }
        item { ReliabilityCard() }
        item { AutoOffCard(vm) }
        item { AppLockCard() }
        item { ExportTokenCard() }
        item { SupportCard(openUrl) }
        item { ChangelogCard() }
        item { CreditsCard(openUrl) }
    }
    }
}

/*
 * EN: Three small pieces the settings page needs on top of the shared ones in Inset.kt. They are kept
 *     private here because they only make sense on a page of explanations: a footnote under a group, a
 *     plain sentence as a row, and a tappable row that can show it is busy.
 * DE: Drei kleine Teile, die diese Seite zusätzlich zu den gemeinsamen aus Inset.kt braucht. Sie bleiben
 *     hier privat, weil sie nur auf einer Seite voller Erklärungen Sinn ergeben: eine Fußnote unter einer
 *     Gruppe, ein schlichter Satz als Zeile, und eine antippbare Zeile, die „beschäftigt" zeigen kann.
 */

/**
 * EN: The sentence that explains a whole group, set below it. Inside the group it would read as one more
 *     setting; below it, it reads as what it is — a note about all of them.
 * DE: Der Satz, der eine ganze Gruppe erklärt, darunter gesetzt. In der Gruppe läse er sich wie eine
 *     weitere Einstellung; darunter liest er sich als das, was er ist — eine Anmerkung zu allen.
 */
@Composable
private fun GroupFootnote(text: String, tint: Color? = null) {
    Text(
        text,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp),
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** EN: A plain line of text as a row of a group. DE: Eine schlichte Textzeile als Zeile einer Gruppe. */
@Composable
private fun TextRow(text: String, tint: Color? = null, weight: FontWeight = FontWeight.Normal) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = weight,
            color = tint ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * EN: Like [ActionRow], but it can be busy: the label greys out, taps are swallowed and a small spinner
 *     sits where the chevron would. Needed because the update check runs for a few seconds and a row
 *     that looks tappable but does nothing reads as a fault.
 * DE: Wie [ActionRow], aber mit „beschäftigt": die Beschriftung ergraut, Tipper werden geschluckt und ein
 *     kleiner Kreisel steht dort, wo sonst der Pfeil wäre. Nötig, weil die Update-Prüfung ein paar
 *     Sekunden läuft und eine Zeile, die antippbar aussieht aber nichts tut, wie ein Fehler wirkt.
 */
@Composable
private fun BusyActionRow(label: String, busy: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp)
            .clickable(enabled = !busy, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 16.sp, color = if (busy) cs.onSurfaceVariant else cs.primary)
        Spacer(Modifier.weight(1f))
        if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
    }
}

/**
 * EN: In-app updater group (GitHub/sideload build): shows the installed version, a "check now" row and an
 *     auto-check toggle. When a newer release is found, the prompt to download & install it is the
 *     app-wide dialog (see [com.climapilot.free.MainActivity]); this group only drives the check.
 * DE: In-App-Updater-Gruppe (GitHub-/Sideload-Build): zeigt die installierte Version, eine „Jetzt
 *     prüfen"-Zeile und einen Auto-Check-Schalter. Wird ein neueres Release gefunden, erscheint der
 *     Lade-&-Installieren-Hinweis als app-weiter Dialog (siehe [com.climapilot.free.MainActivity]);
 *     diese Gruppe stößt nur die Prüfung an.
 */
@Composable
private fun UpdateCard(vm: AcViewModel) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull().orEmpty()
    }
    Column {
        InsetGroup(caption = stringResource(R.string.update_title)) {
            TextRow(stringResource(R.string.update_current, version), tint = cs.onSurfaceVariant)
            Separator()

            // EN: A result line for the manual check (up to date / error / permission hint). DE: Ergebniszeile für die manuelle Prüfung (aktuell / Fehler / Berechtigungs-Hinweis).
            vm.updateMessage?.let { msg ->
                TextRow(msg, tint = cs.primary, weight = FontWeight.SemiBold)
                Separator()
            }

            BusyActionRow(
                label = stringResource(
                    if (vm.updateChecking) R.string.update_checking else R.string.update_check_button
                ),
                busy = vm.updateChecking,
            ) { vm.checkForUpdates(manual = true) }
            Separator()
            ToggleRow(stringResource(R.string.update_auto_label), vm.autoUpdateCheck) {
                vm.updateAutoUpdateCheck(it)
            }
        }
        GroupFootnote(stringResource(R.string.update_body))
    }
}

/**
 * EN: Background-reliability group: lets the user exempt the app from battery optimisation and the OEM
 *     auto-start killer, so timers and scene schedules fire even when the app is closed.
 * DE: Gruppe für Hintergrund-Zuverlässigkeit: nimmt die App von der Akku-Optimierung und dem
 *     Autostart-Killer des Herstellers aus, damit Timer und Szenen auch bei geschlossener App auslösen.
 */
@Composable
private fun ReliabilityCard() {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val ignoring = ReliabilityHelper.isIgnoringBatteryOptimizations(context)
    val oemAutoStart = ReliabilityHelper.hasOemAutoStart()
    Column {
        InsetGroup(caption = stringResource(R.string.reliability_title)) {
            TextRow(
                stringResource(if (ignoring) R.string.reliability_battery_ok else R.string.reliability_battery_warn),
                tint = if (ignoring) cs.primary else cs.error,
                weight = FontWeight.SemiBold,
            )
            if (!ignoring) {
                Separator()
                ActionRow(
                    stringResource(R.string.reliability_battery_action),
                    onClick = { ReliabilityHelper.openBatteryOptimizationSettings(context) },
                )
            }
            if (oemAutoStart) {
                Separator()
                ActionRow(
                    stringResource(R.string.reliability_autostart_action),
                    onClick = { ReliabilityHelper.openAutoStartSettings(context) },
                )
            }
        }
        GroupFootnote(stringResource(R.string.reliability_body))
    }
}

/**
 * EN: Auto power-off (max runtime): a local safety cut-off that switches the AC off after it has run
 *     for N hours — the workable stand-in for an "off when away" timer on a LAN-only app.
 * DE: Auto-Aus (Max-Laufzeit): eine lokale Sicherheits-Abschaltung, die die Klima nach N Stunden
 *     Laufzeit ausschaltet — die praktikable Entsprechung eines „Aus, wenn weg"-Timers bei einer reinen LAN-App.
 */
@Composable
private fun AutoOffCard(vm: AcViewModel) {
    var hoursText by remember { mutableStateOf(if (vm.maxRuntimeHours > 0) vm.maxRuntimeHours.toString() else "") }
    Column {
        InsetGroup(caption = stringResource(R.string.autooff_title)) {
            // EN: A free number stays a text field — a segmented control would have to guess the useful
            //     hours for everyone. DE: Eine freie Zahl bleibt ein Eingabefeld — ein segmentierter
            //     Schalter müsste die sinnvollen Stunden für alle erraten.
            FieldRow {
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { input ->
                        hoursText = input.filter { it.isDigit() }.take(2)
                        vm.updateMaxRuntimeHours(hoursText.toIntOrNull() ?: 0)
                    },
                    label = { Text(stringResource(R.string.autooff_hours_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        GroupFootnote(stringResource(R.string.autooff_body))
    }
}

/** EN: A text field sitting inside a group, inset like a row. DE: Ein Eingabefeld in einer Gruppe, eingerückt wie eine Zeile. */
@Composable
private fun FieldRow(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp)) {
        content()
    }
}

/**
 * EN: App-lock group: a switch to require biometric/PIN unlock when opening the app, so only you can
 *     control the AC. Reads/writes the flag directly; the gate itself lives in MainActivity.
 * DE: App-Sperre-Gruppe: ein Schalter, der beim Öffnen der App Biometrie/PIN verlangt, sodass nur du die
 *     Klima steuern kannst. Liest/schreibt das Flag direkt; die Sperre selbst sitzt in der MainActivity.
 */
@Composable
private fun AppLockCard() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(SettingsRepo.appLock(context)) }
    Column {
        InsetGroup(caption = stringResource(R.string.lock_card_title)) {
            ToggleRow(stringResource(R.string.lock_card_switch), enabled) { v ->
                enabled = v
                SettingsRepo.setAppLock(context, v)
            }
        }
        GroupFootnote(stringResource(R.string.lock_card_body))
    }
}

/** EN: Credits to the midea-msmart project the protocol is ported from. / DE: Danksagung an das midea-msmart-Projekt, aus dem das Protokoll portiert ist. */
@Composable
private fun CreditsCard(onOpen: (String) -> Unit) {
    Column {
        InsetGroup(caption = stringResource(R.string.credits_title)) {
            ActionRow(stringResource(R.string.credits_link), onClick = { onOpen(CREDITS_URL) })
        }
        GroupFootnote(stringResource(R.string.credits_body))
    }
}

/** EN: Donation links (Ko-fi / PayPal) — the app is free and ad-free. / DE: Spenden-Links (Ko-fi / PayPal) — die App ist kostenlos und werbefrei. */
@Composable
private fun SupportCard(onOpen: (String) -> Unit) {
    Column {
        InsetGroup(caption = stringResource(R.string.support_title)) {
            ActionRow(stringResource(R.string.support_kofi), onClick = { onOpen(KOFI_URL) })
            Separator()
            ActionRow(stringResource(R.string.support_paypal), onClick = { onOpen(PAYPAL_URL) })
        }
        GroupFootnote(stringResource(R.string.support_body))
    }
}

/** EN: App icon, name and installed version. / DE: App-Icon, Name und installierte Version. */
@Composable
private fun AppHeaderCard(version: String) {
    val cs = MaterialTheme.colorScheme
    // EN: No caption — the app's own name is the heading. DE: Keine Überschrift — der App-Name ist die Überschrift.
    InsetGroup {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
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

/**
 * EN: Token export. Lists every device whose token/key we have cached and lets the user copy or share
 *     each one as a text block — an offline backup, or for reuse in other local-control tools.
 * DE: Token-Export. Listet jedes Gerät, dessen Token/Key gecacht ist, und erlaubt das Kopieren oder
 *     Teilen als Textblock — als Offline-Backup oder zur Nutzung in anderen Tools zur lokalen Steuerung.
 */
@Composable
private fun ExportTokenCard() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    // EN: Re-read after an import so the list refreshes. DE: Nach einem Import neu einlesen, damit die Liste aktualisiert.
    var entries by remember { mutableStateOf(TokenRepo.list(context)) }
    var showImport by remember { mutableStateOf(false) }
    Column {
        InsetGroup(caption = stringResource(R.string.export_title)) {
            if (entries.isEmpty()) {
                TextRow(stringResource(R.string.export_none), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                entries.forEachIndexed { i, e ->
                    if (i > 0) Separator()
                    TokenRow(
                        name = e.name.ifBlank { "Midea" },
                        ip = e.ip,
                        onCopy = {
                            // EN: Copy the credential block to the clipboard. / DE: Den Zugangsdaten-Block in die Zwischenablage kopieren.
                            clipboard.setText(AnnotatedString(TokenRepo.exportText(e)))
                            Toast.makeText(context, context.getString(R.string.export_copied), Toast.LENGTH_SHORT).show()
                        },
                        onShare = {
                            // EN: Hand off to the Android share sheet. / DE: An die Android-Teilen-Funktion übergeben.
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, TokenRepo.exportText(e))
                            }
                            context.startActivity(Intent.createChooser(send, null))
                        },
                    )
                }
            }
            Separator()
            ActionRow(stringResource(R.string.import_button), onClick = { showImport = true })
        }
        // EN: The "keep them private" note describes the listed credentials — with none listed it warns
        //     about nothing. DE: Der „geheim halten"-Hinweis beschreibt die aufgeführten Zugangsdaten —
        //     ohne Einträge warnt er vor nichts.
        if (entries.isNotEmpty()) GroupFootnote(stringResource(R.string.export_hint))
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

/** EN: One cached device with its two export actions. DE: Ein gecachtes Gerät mit seinen beiden Export-Aktionen. */
@Composable
private fun TokenRow(name: String, ip: String, onCopy: () -> Unit, onShare: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 7.dp)) {
            Text(name, fontSize = 16.sp, color = cs.onSurface)
            Text(ip, fontSize = 12.5.sp, color = cs.onSurfaceVariant)
        }
        TextButton(onClick = onCopy) { Text(stringResource(R.string.export_copy)) }
        TextButton(onClick = onShare) { Text(stringResource(R.string.export_share)) }
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
    var priceText by remember { mutableStateOf(if (vm.pricePerKwh > 0) vm.pricePerKwh.toString() else "") }
    Column {
        InsetGroup(caption = stringResource(R.string.settings_display)) {
            ToggleRow(stringResource(R.string.unit_fahrenheit), vm.useFahrenheit) { vm.setFahrenheit(it) }
            Separator()
            FieldRow {
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
            }
        }
        GroupFootnote(stringResource(R.string.price_hint))
    }
}

/**
 * EN: Poll interval — how often the app queries state/energy/diagnostics while connected. A segmented
 *     control over the presets (2/6/10/30 s); the refresh loop picks a change up on its next cycle.
 *     Shorter intervals mean more LAN traffic and slightly higher battery use while the app is open.
 * DE: Poll-Intervall — wie oft die App Zustand/Energie/Diagnose abfragt, solange sie verbunden ist. Ein
 *     segmentierter Schalter über die Vorgaben (2/6/10/30 s); die Refresh-Schleife übernimmt eine
 *     Änderung im nächsten Zyklus. Kürzere Intervalle bedeuten mehr LAN-Verkehr und etwas mehr
 *     Akkuverbrauch bei offener App.
 */
@Composable
private fun PollingCard(vm: AcViewModel) {
    Column {
        GroupCaption(stringResource(R.string.settings_poll))
        Segmented(
            options = listOf(2, 6, 10, 30).map { it to stringResource(R.string.poll_seconds, it) },
            selected = vm.pollIntervalSec,
        ) { vm.updatePollInterval(it) }
        GroupFootnote(stringResource(R.string.settings_poll_hint))
    }
}

/**
 * EN: Beta group — opt-in experimental diagnostics ported from midea-msmart PR #278. Each switch enables
 *     one extra "group data" query per refresh (compressor performance, indoor fan/pump, outdoor power).
 *     Not every unit answers these — hence "beta". Values appear on the Status tab.
 * DE: Beta-Gruppe — freiwillige experimentelle Diagnose, portiert aus midea-msmart PR #278. Jeder Schalter
 *     aktiviert eine zusätzliche „Gruppendaten"-Abfrage pro Refresh (Kompressor-Leistung, Innenlüfter/Pumpe,
 *     Außengerät-Leistung). Nicht jedes Gerät antwortet darauf — daher „Beta". Die Werte erscheinen im
 *     Status-Reiter.
 */
@Composable
private fun BetaCard(vm: AcViewModel) {
    Column {
        InsetGroup(caption = stringResource(R.string.settings_beta)) {
            ToggleRow(
                stringResource(R.string.beta_group1),
                vm.diagGroup1,
                subtitle = stringResource(R.string.beta_group1_desc),
            ) { vm.updateDiagGroup1(it) }
            Separator()
            ToggleRow(
                stringResource(R.string.beta_group2),
                vm.diagGroup2,
                subtitle = stringResource(R.string.beta_group2_desc),
            ) { vm.updateDiagGroup2(it) }
            Separator()
            ToggleRow(
                stringResource(R.string.beta_group7),
                vm.diagGroup7,
                subtitle = stringResource(R.string.beta_group7_desc),
            ) { vm.updateDiagGroup7(it) }
        }
        GroupFootnote(stringResource(R.string.settings_beta_hint))
    }
}

/**
 * EN: Per-version changelog entries, newest first. Kept as a table of string pairs rather than a hundred
 *     lines of copy-pasted rows — adding a version is then one line.
 * DE: Änderungsliste je Version, neueste zuerst. Als Tabelle aus String-Paaren statt hundert Zeilen
 *     kopierter Blöcke — eine neue Version ist damit eine Zeile.
 */
private val CHANGELOG = listOf(
    R.string.changelog_0_6_9_title to R.string.changelog_0_6_9_body,
    R.string.changelog_0_6_7_title to R.string.changelog_0_6_7_body,
    R.string.changelog_0_6_6_title to R.string.changelog_0_6_6_body,
    R.string.changelog_0_6_5_title to R.string.changelog_0_6_5_body,
    R.string.changelog_0_6_4_title to R.string.changelog_0_6_4_body,
    R.string.changelog_0_6_3_title to R.string.changelog_0_6_3_body,
    R.string.changelog_0_6_2_title to R.string.changelog_0_6_2_body,
    R.string.changelog_0_6_1_title to R.string.changelog_0_6_1_body,
    R.string.changelog_0_6_title to R.string.changelog_0_6_body,
    R.string.changelog_0_5_title to R.string.changelog_0_5_body,
    R.string.changelog_0_4_2_title to R.string.changelog_0_4_2_body,
    R.string.changelog_0_4_1_title to R.string.changelog_0_4_1_body,
    R.string.changelog_0_4_title to R.string.changelog_0_4_body,
    R.string.changelog_0_3_2_title to R.string.changelog_0_3_2_body,
    R.string.changelog_0_3_1_title to R.string.changelog_0_3_1_body,
    R.string.changelog_0_3_title to R.string.changelog_0_3_body,
    R.string.changelog_0_2_title to R.string.changelog_0_2_body,
    R.string.changelog_0_1_title to R.string.changelog_0_1_body,
)

@Composable
private fun ChangelogCard() {
    val cs = MaterialTheme.colorScheme
    InsetGroup(caption = stringResource(R.string.changelog_title)) {
        CHANGELOG.forEachIndexed { i, (titleRes, bodyRes) ->
            if (i > 0) Separator()
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 13.dp)) {
                Text(stringResource(titleRes), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = cs.onSurface)
                Spacer(Modifier.height(5.dp))
                Text(stringResource(bodyRes), fontSize = 13.5.sp, color = cs.onSurfaceVariant, lineHeight = 19.sp)
            }
        }
    }
}
