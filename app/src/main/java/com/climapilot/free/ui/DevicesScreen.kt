package com.climapilot.free.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.climapilot.free.AcViewModel
import com.climapilot.free.R
import com.climapilot.free.Status
import com.climapilot.free.ir.IrRemote
import com.climapilot.free.TokenRepo
import com.climapilot.free.midea.MideaDevice

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
    val known = remember { TokenRepo.list(context) }
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
                    DeviceCard(dev, connecting = vm.status == Status.Connecting) { vm.connect(dev) }
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
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(cs.primary, cs.secondary))),
    ) {
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        ) {
            Icon(Icons.Default.Settings, stringResource(R.string.cd_settings), tint = cs.onPrimary)
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp),
        ) {
            Icon(
                Icons.Default.AcUnit,
                contentDescription = null,
                tint = cs.onPrimary,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.app_name),
                color = cs.onPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.hero_subtitle),
                color = cs.onPrimary.copy(alpha = 0.85f),
                fontSize = 15.sp,
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
private fun DeviceCard(dev: MideaDevice, connecting: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
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
                    .background(cs.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.AcUnit, null, tint = cs.onPrimaryContainer)
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
