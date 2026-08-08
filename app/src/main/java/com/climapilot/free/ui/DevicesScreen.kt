package com.climapilot.free.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.climapilot.free.AcViewModel
import com.climapilot.free.R
import com.climapilot.free.Status
import com.climapilot.free.ir.IrRemote
import com.climapilot.free.TokenRepo
import com.climapilot.free.midea.MideaDevice

/** EN: Channel accent for Wi-Fi device entries. DE: Kanal-Akzentfarbe für WLAN-Geräteeinträge. */
private val WifiColor = Color(0xFF3B9EFF)

/**
 * EN: The landing screen: a hero header, the discover/manual-add actions, a demo-preview link, and
 *     the list of found devices. Tapping a device connects to it.
 * DE: Der Startbildschirm: ein Hero-Kopf, die Aktionen Suchen/Manuell-Hinzufügen, ein
 *     Demo-Vorschau-Link und die Liste der gefundenen Geräte. Ein Tippen verbindet mit dem Gerät.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(vm: AcViewModel, onOpenSettings: () -> Unit = {}) {
    var showManual by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    // EN: Devices we have a cached token for — connect instantly & offline, no discovery needed.
    // DE: Geräte mit gecachtem Token — sofort und offline verbinden, ohne Suche.
    var known by remember { mutableStateOf(TokenRepo.list(context)) }
    // EN: Reload after every connect or disconnect. Connecting is what saves a device (once its token
    //     arrives) — so without this the unit you just connected to keeps sitting under the search
    //     results instead of moving to the saved list, where it can be renamed.
    // DE: Nach jedem Verbinden und Trennen neu laden. Das Verbinden ist es, was ein Gerät speichert
    //     (sobald sein Token da ist). Ohne das bliebe die gerade verbundene Anlage unter den
    //     Suchtreffern stehen, statt in die gespeicherte Liste zu wandern, wo sie sich umbenennen lässt.
    androidx.compose.runtime.LaunchedEffect(vm.connectedDevice) {
        known = TokenRepo.list(context)
    }
    // EN: Long-press a saved device to rename or forget it. DE: Langes Drücken auf ein gespeichertes Gerät zum Umbenennen oder Entfernen.
    var editing by remember { mutableStateOf<TokenRepo.Entry?>(null) }
    // EN: Drop discovery hits that are already shown under "known devices" (match by id, ip as
    //     fallback) so the same unit never appears twice after a re-scan.
    // DE: Suchtreffer ausblenden, die bereits unter „Bekannte Geräte" stehen (per ID, IP als
    //     Ausweich-Kriterium), damit dasselbe Gerät nach einer erneuten Suche nie doppelt erscheint.
    val knownIds = remember(known) { known.map { it.id }.toSet() }
    val knownIps = remember(known) { known.map { it.ip }.toSet() }
    val discovered = vm.devices.filterNot { it.id in knownIds || it.ip in knownIps }

    Scaffold { inner ->
      // EN: Centre + cap width so the hero/buttons don't stretch across a tablet / landscape. DE: Zentrieren + Breite begrenzen, damit Hero/Buttons auf Tablet / im Querformat nicht über die ganze Breite gehen.
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = inner.calculateTopPadding() + 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Hero(onOpenSettings) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { vm.discover() },
                        modifier = Modifier.weight(1f).height(52.dp),
                        enabled = vm.status != Status.Discovering,
                    ) {
                        if (vm.status == Status.Discovering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp), strokeWidth = 2.dp,
                                color = cs.onPrimary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.action_discovering))
                        } else {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_discover))
                        }
                    }
                    OutlinedButton(
                        onClick = { showManual = true },
                        modifier = Modifier.height(52.dp),
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_manual))
                    }
                }
            }

            item {
                TextButton(
                    onClick = { vm.connectDemo() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.demo_preview)) }
            }

            // EN: IR-remote entry — only on phones with an IR blaster. DE: IR-Fernbedienungs-Einstieg — nur auf Handys mit IR-Blaster.
            if (IrRemote.hasEmitter(context)) {
                item {
                    OutlinedButton(
                        onClick = { vm.enterIrMode() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(Icons.Default.SettingsRemote, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ir_remote_entry))
                    }
                }
            }

            if (known.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.known_devices),
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        color = cs.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                }
                items(known, key = { "known_${it.id}" }) { e ->
                    val dev = MideaDevice(ip = e.ip, port = e.port, id = e.id, sn = "", name = e.name, type = 0xAC, version = 3)
                    DeviceCard(
                        dev,
                        connecting = vm.status == Status.Connecting,
                        onLongPress = { editing = e },
                    ) { vm.connect(dev) }
                }
            }

            vm.error?.let { msg ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = cs.errorContainer)) {
                        Text(
                            msg,
                            modifier = Modifier.padding(16.dp),
                            color = cs.onErrorContainer,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            if (discovered.isEmpty() && known.isEmpty() && vm.status != Status.Discovering) {
                item { EmptyHint() }
            }

            items(discovered, key = { it.ip }) { dev ->
                DeviceCard(dev, connecting = vm.status == Status.Connecting) { vm.connect(dev) }
            }
        }
      }
    }

    editing?.let { entry ->
        DeviceEditDialog(
            entry = entry,
            onDismiss = { editing = null },
            onRename = { name ->
                TokenRepo.rename(context, entry.id, name)
                known = TokenRepo.list(context)
                editing = null
            },
            onForget = {
                TokenRepo.clear(context, entry.id)
                known = TokenRepo.list(context)
                editing = null
            },
        )
    }

    if (showManual) {
        ManualDeviceDialog(
            onDismiss = { showManual = false },
            onAdd = { ip, port, id, name ->
                vm.addManualDevice(ip, port, id, name)
                showManual = false
            },
        )
    }
}

/** EN: Gradient header with the app name and a settings entry point. / DE: Farbverlauf-Kopf mit App-Name und Einstiegspunkt zu den Einstellungen. */
@Composable
private fun Hero(onOpenSettings: () -> Unit) {
    // EN: A large title, not a billboard. The old gradient block took 180 dp and a full colour
    //     field to say the app's own name — which the user already knows, having just opened it.
    //     The devices below are what the screen is for, so they get the attention now.
    // DE: Ein großer Titel, kein Plakat. Der frühere Verlaufsklotz brauchte 180 dp und eine volle
    //     Farbfläche, um den Namen der App zu nennen — den der Nutzer kennt, er hat sie ja gerade
    //     geöffnet. Der Bildschirm ist für die Geräte darunter da, also gehört ihnen die
    //     Aufmerksamkeit.
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // EN: The name alone. The tagline underneath repeated what the device list shows anyway — each
        //     entry already carries a channel icon — and cost two lines at the top of the screen the
        //     devices themselves could use.
        // DE: Nur der Name. Der Untertitel wiederholte, was die Geräteliste ohnehin zeigt — jeder
        //     Eintrag trägt bereits ein Kanal-Symbol — und kostete oben zwei Zeilen, die den Geräten
        //     selbst zustehen.
        Text(
            stringResource(R.string.app_name),
            modifier = Modifier.weight(1f),
            color = cs.onBackground,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        )
        IconButton(onClick = onOpenSettings) {
            Icon(
                Icons.Default.Settings,
                stringResource(R.string.cd_settings),
                tint = cs.onSurfaceVariant,
            )
        }
    }
}

/** EN: Shown when no devices have been found yet, nudging the user to search. / DE: Wird gezeigt, wenn noch keine Geräte gefunden wurden, und stupst zum Suchen an. */
@Composable
private fun EmptyHint() {
    val cs = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Wifi, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.empty_title),
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.empty_body),
                color = cs.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
    }
}

/** EN: One row in the device list: icon, name, IP/version/type, and a connect affordance. / DE: Eine Zeile der Geräteliste: Icon, Name, IP/Version/Typ und ein Verbinden-Element. */
@Composable
private fun DeviceCard(
    dev: MideaDevice,
    connecting: Boolean,
    onLongPress: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { onLongPress?.invoke() }),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(WifiColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Wifi, null, tint = WifiColor)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    dev.name.ifBlank { "Midea ${dev.ip}" },
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface,
                    fontSize = 16.sp,
                )
                Text(
                    stringResource(R.string.device_subtitle, dev.ip, dev.version, dev.typeHex),
                    color = cs.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
            if (connecting) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = onClick) { Text(stringResource(R.string.action_connect)) }
            }
        }
    }
}

/**
 * EN: Rename or forget a saved device. Opened by long-pressing its card — units announce themselves
 *     as "net_ac_E6DE", which tells you nothing about which room they are in, and until now a device
 *     once saved could never be removed again.
 * DE: Ein gespeichertes Gerät umbenennen oder entfernen. Öffnet sich bei langem Druck auf die Karte —
 *     Anlagen melden sich als „net_ac_E6DE", was nichts über den Raum verrät, und ein einmal
 *     gespeichertes Gerät ließ sich bisher nie wieder loswerden.
 */
@Composable
private fun DeviceEditDialog(
    entry: TokenRepo.Entry,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onForget: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var name by remember { mutableStateOf(entry.name) }
    var confirmForget by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.device_edit_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.device_edit_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    entry.ip,
                    fontSize = 12.sp,
                    color = cs.onSurfaceVariant,
                )
                if (confirmForget) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.device_forget_confirm),
                        fontSize = 13.sp,
                        color = cs.error,
                        lineHeight = 18.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(name) },
                enabled = name.isNotBlank() && name != entry.name,
            ) { Text(stringResource(R.string.device_edit_save)) }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = { if (confirmForget) onForget() else confirmForget = true },
                ) {
                    Text(
                        stringResource(
                            if (confirmForget) R.string.device_forget_really
                            else R.string.device_forget
                        ),
                        color = cs.error,
                    )
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
