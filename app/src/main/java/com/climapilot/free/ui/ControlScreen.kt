package com.climapilot.free.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.ui.BiasAlignment
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.climapilot.free.AcViewModel
import com.climapilot.free.CardId
import com.climapilot.free.CardOrderRepo
import com.climapilot.free.FanPreset
import com.climapilot.free.SettingsRepo
import com.climapilot.free.R
import com.climapilot.free.Scene
import com.climapilot.free.Status
import com.climapilot.free.midea.MideaAc
import kotlin.math.roundToInt

// EN: One selectable operating mode + the icon/label shown for it in the mode chips.
// DE: Ein auswählbarer Betriebsmodus + das Icon/Label, das dafür in den Modus-Chips angezeigt wird.
private data class ModeOption(val id: Int, val labelRes: Int, val icon: ImageVector)

private val MODES = listOf(
    ModeOption(MideaAc.MODE_AUTO, R.string.mode_auto, Icons.Default.Autorenew),
    ModeOption(MideaAc.MODE_COOL, R.string.mode_cool, Icons.Default.AcUnit),
    ModeOption(MideaAc.MODE_DRY, R.string.mode_dry, Icons.Default.WaterDrop),
    ModeOption(MideaAc.MODE_HEAT, R.string.mode_heat, Icons.Default.Whatshot),
    ModeOption(MideaAc.MODE_FAN_ONLY, R.string.mode_fan, Icons.Default.Air),
)

/**
 * EN: The main control screen, shown once a device (or the demo) is connected. It is a single
 *     scrollable column of cards; every card reads from and writes to [AcViewModel] so the UI stays
 *     in sync with both the user's intent and the device's reported state.
 * DE: Der Hauptsteuerungs-Bildschirm, sichtbar sobald ein Gerät (oder die Demo) verbunden ist. Er
 *     besteht aus einer einzigen scrollbaren Spalte von Karten; jede Karte liest aus und schreibt in
 *     das [AcViewModel], damit die UI sowohl mit der Absicht des Nutzers als auch mit dem vom Gerät
 *     gemeldeten Zustand synchron bleibt.
 */
/** EN: Steuern tab — power, temperature, mode, fan. DE: Steuern-Reiter — Ein/Aus, Temperatur, Modus, Lüfter. */
@Composable
fun ControlScreen(vm: AcViewModel) {
    TabList {
        item(key = "topbar") { ConnectedTopBar(vm) }
        item(key = "hero") { PowerHero(vm) }
        item(key = "mode") { ModeSelector(vm) }
        item(key = "fan") { FanCard(vm) }
        errorItem(vm)
    }
}

/** EN: Status tab — live readouts (power, consumption, cost, error). DE: Status-Reiter — Live-Anzeigen (Leistung, Verbrauch, Kosten, Fehler). */
@Composable
fun StatusTab(vm: AcViewModel) {
    TabList {
        item(key = "topbar") { ConnectedTopBar(vm) }
        item(key = "status") { LiveStatusCard(vm) }
        errorItem(vm)
    }
}

/** EN: Options tab — unit toggles + compressor throttle (where supported). DE: Optionen-Reiter — Geräteschalter + Kompressor-Drossel (wo unterstützt). */
@Composable
fun OptionsTab(vm: AcViewModel) {
    TabList {
        item(key = "topbar") { ConnectedTopBar(vm) }
        item(key = "options") { OptionsCard(vm) }
        if (vm.rateLevels > 0) item(key = "gear") { GearCard(vm) }
        errorItem(vm)
    }
}

/** EN: Scenes tab — quick scenes + sleep timer. DE: Szenen-Reiter — Schnell-Szenen + Sleep-Timer. */
@Composable
fun ScenesTab(vm: AcViewModel) {
    TabList {
        item(key = "topbar") { ConnectedTopBar(vm) }
        item(key = "scenes") { ScenesCard(vm) }
        item(key = "sleep") { SleepTimerCard(vm) }
        errorItem(vm)
    }
}

/** EN: Shared scrollable card list used by every control tab. DE: Geteilte scrollbare Kartenliste für jeden Steuer-Reiter. */
@Composable
private fun TabList(content: LazyListScope.() -> Unit) {
    // EN: Centre + cap width so cards don't stretch absurdly wide on tablets / landscape. DE: Zentrieren + Breite begrenzen, damit Karten auf Tablets / im Querformat nicht absurd breit werden.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .testTag("control_list")
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 40.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/** EN: Append an inline error banner item when the last command failed. DE: Ein Inline-Fehlerbanner anhängen, falls der letzte Befehl fehlschlug. */
private fun LazyListScope.errorItem(vm: AcViewModel) {
    val msg = vm.error ?: return
    item(key = "error") { ErrorBanner(msg) }
}

@Composable
private fun ErrorBanner(msg: String) {
    val cs = MaterialTheme.colorScheme
    Card(colors = CardDefaults.cardColors(containerColor = cs.errorContainer)) {
        Text(msg, Modifier.padding(16.dp), color = cs.onErrorContainer, fontSize = 13.sp)
    }
}

/**
 * EN: Top bar: back (disconnect), device name + connection indicator, and a manual refresh button.
 * DE: Kopfzeile: Zurück (trennen), Gerätename + Verbindungsanzeige und ein manueller Aktualisieren-Knopf.
 */
@Composable
fun ConnectedTopBar(vm: AcViewModel) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = { vm.disconnect() }) {
            Icon(Icons.Default.ArrowBack, stringResource(R.string.cd_back))
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                vm.connectedDevice?.name?.ifBlank { stringResource(R.string.default_device_name) }
                    ?: stringResource(R.string.default_device_name),
                fontWeight = FontWeight.Bold, fontSize = 19.sp, color = cs.onBackground,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val connected = vm.status == Status.Connected
                Box(
                    Modifier.size(8.dp).clip(CircleShape)
                        .background(if (vm.irMode) cs.primary else if (connected) Color(0xFF2ECC71) else cs.error)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        vm.irMode -> stringResource(R.string.ir_remote_subtitle)
                        connected -> stringResource(R.string.connected_to, vm.connectedDevice?.ip ?: "")
                        else -> stringResource(R.string.disconnected)
                    },
                    fontSize = 12.sp, color = if (vm.irMode) cs.primary else cs.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = { vm.refreshNow() }) {
            Icon(Icons.Default.Refresh, stringResource(R.string.cd_refresh), tint = cs.primary)
        }
    }
}

/**
 * EN: The hero card: power toggle, big target-temperature stepper and three at-a-glance readouts
 *     (indoor / outdoor temperature and live power draw in watts).
 * DE: Die Hero-Karte: Ein/Aus-Schalter, großer Zieltemperatur-Regler und drei Kurz-Anzeigen
 *     (Innen-/Außentemperatur und aktuelle Leistungsaufnahme in Watt).
 */
@Composable
private fun PowerHero(vm: AcViewModel) {
    val cs = MaterialTheme.colorScheme
    val on = vm.powerOn
    val bg by animateColorAsState(if (on) cs.primary else cs.surfaceVariant, label = "herobg")
    val fg = if (on) cs.onPrimary else cs.onSurfaceVariant

    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(
                if (on) Brush.linearGradient(listOf(cs.primary, cs.secondary))
                else Brush.linearGradient(listOf(bg, bg))
            )
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column {
            // EN: Centered EIN/AUS slider toggle — clearly labelled, reflects the real power state. DE: Zentrierter EIN/AUS-Schieberegler — klar beschriftet, spiegelt den echten Ein/Aus-Zustand.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PowerSlider(on = on, fg = fg, onToggle = { vm.togglePower() })
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                RoundIconButton(Icons.Default.Remove, enabled = on, tint = fg) { vm.nudgeTemp(-0.5) }
                Spacer(Modifier.width(20.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        formatTemp(vm.tempC, vm.useFahrenheit),
                        color = fg, fontSize = 52.sp, fontWeight = FontWeight.Bold,
                    )
                    Text(stringResource(R.string.target_temp), color = fg.copy(alpha = 0.8f), fontSize = 13.sp)
                }
                Spacer(Modifier.width(20.dp))
                RoundIconButton(Icons.Default.Add, enabled = on, tint = fg) { vm.nudgeTemp(0.5) }
            }

            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MiniReadout(stringResource(R.string.readout_indoor), vm.live?.indoorTemp?.let { formatTemp(it, vm.useFahrenheit) } ?: "–", fg)
                MiniReadout(stringResource(R.string.readout_outdoor), vm.live?.outdoorTemp?.let { formatTemp(it, vm.useFahrenheit) } ?: "–", fg)
                // EN: NaN power means the unit has no energy monitoring → show a dash.
                // DE: NaN-Leistung bedeutet, das Gerät hat keine Energiemessung → Strich anzeigen.
                MiniReadout(
                    stringResource(R.string.readout_power),
                    vm.energy?.powerW?.takeIf { !it.isNaN() }?.let { "${it.roundToInt()} W" } ?: "–",
                    fg,
                )
            }
        }
    }
}

/**
 * EN: A clearly labelled EIN/AUS sliding toggle for power. A pill track with two halves (AUS | EIN); a
 *     thumb slides to the active side (green when on, grey when off) and the active label sits on it in
 *     white, the inactive one dimmed. Tapping anywhere toggles power. Reflects the device's real state.
 * DE: Ein klar beschrifteter EIN/AUS-Schieberegler für Ein/Aus. Eine Pille mit zwei Hälften (AUS | EIN);
 *     ein Knauf gleitet auf die aktive Seite (grün bei An, grau bei Aus), die aktive Beschriftung sitzt
 *     in Weiß darauf, die inaktive gedimmt. Tippen schaltet um. Spiegelt den echten Gerätezustand.
 */
@Composable
private fun PowerSlider(on: Boolean, fg: Color, onToggle: () -> Unit) {
    val thumbBias by animateFloatAsState(if (on) 1f else -1f, label = "thumbBias")
    val thumbColor by animateColorAsState(
        if (on) Color(0xFF34C759) else Color(0xFF8E8E93), label = "thumbColor",
    )
    Box(
        Modifier
            .width(220.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(fg.copy(alpha = 0.18f))
            .clickable { onToggle() },
    ) {
        // EN: Sliding thumb covering the active half. DE: Gleitender Knauf, der die aktive Hälfte abdeckt.
        Box(
            Modifier
                .align(BiasAlignment(horizontalBias = thumbBias, verticalBias = 0f))
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .padding(4.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(thumbColor),
        )
        // EN: Both labels; the active one (under the thumb) white, the other dimmed. DE: Beide Beschriftungen; die aktive (unter dem Knauf) weiß, die andere gedimmt.
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.state_off),
                    color = if (!on) Color.White else fg.copy(alpha = 0.55f),
                    fontWeight = FontWeight.Bold, fontSize = 16.sp,
                )
            }
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.state_on),
                    color = if (on) Color.White else fg.copy(alpha = 0.55f),
                    fontWeight = FontWeight.Bold, fontSize = 16.sp,
                )
            }
        }
    }
}

/** EN: A round +/- stepper button used by the temperature control. DE: Runder +/- Schrittknopf für die Temperatur. */
@Composable
private fun RoundIconButton(icon: ImageVector, enabled: Boolean, tint: Color, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(56.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = cs.onPrimary.copy(alpha = 0.18f),
            contentColor = tint,
        ),
    ) { Icon(icon, null, modifier = Modifier.size(28.dp)) }
}

/** EN: A small value-over-label readout used inside the hero. DE: Kleine Wert-über-Label-Anzeige in der Hero-Karte. */
@Composable
private fun MiniReadout(label: String, value: String, fg: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = fg, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, color = fg.copy(alpha = 0.8f), fontSize = 12.sp)
    }
}

/** EN: Operating-mode chooser (auto/cool/dry/heat/fan) — a single compact row of icon+label segments. DE: Betriebsmodus-Auswahl (Auto/Kühlen/Trocknen/Heizen/Lüften) — eine kompakte Zeile aus Icon+Label-Segmenten. */
@Composable
private fun ModeSelector(vm: AcViewModel) {
    val cs = MaterialTheme.colorScheme
    SectionCard(stringResource(R.string.section_mode), Icons.Default.Thermostat) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // EN: IR can't address Dry (no slot in the 2-bit IR mode field), so hide it in IR mode. DE: IR kann Trocknen nicht ansteuern (kein Platz im 2-Bit-IR-Modusfeld), daher im IR-Modus ausblenden.
            MODES.filter { !vm.irMode || it.id != MideaAc.MODE_DRY }.forEach { m ->
                val selected = vm.mode == m.id
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) cs.primary else cs.surfaceVariant)
                        .clickable { vm.applyMode(m.id) }
                        .padding(vertical = 10.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(m.icon, null, Modifier.size(20.dp), tint = if (selected) cs.onPrimary else cs.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(m.labelRes),
                        fontSize = 10.sp, maxLines = 1,
                        color = if (selected) cs.onPrimary else cs.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * EN: Fan control: named presets (auto/silent/…/turbo) plus a fine 1–100 % slider for exact speeds.
 * DE: Lüftersteuerung: benannte Vorgaben (Auto/Leise/…/Turbo) plus ein 1–100 %-Feinregler für exakte Stufen.
 */
@Composable
private fun FanCard(vm: AcViewModel) {
    val cs = MaterialTheme.colorScheme
    SectionCard(stringResource(R.string.section_fan), Icons.Default.Air) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FanPreset.entries.forEach { p ->
                val selected = vm.fan == p.value
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) cs.primary else cs.surfaceVariant)
                        .clickable { vm.applyFan(p.value) }
                        .padding(vertical = 10.dp, horizontal = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(p.labelRes),
                        fontSize = 11.sp, maxLines = 1,
                        color = if (selected) cs.onPrimary else cs.onSurfaceVariant,
                    )
                }
            }
        }
        // EN: Show the exact level only when it's a non-preset custom value (keeps the card compact). DE: Die genaue Stufe nur bei einem Nicht-Preset-Wert zeigen (hält die Karte kompakt).
        val isPreset = FanPreset.entries.any { it.value == vm.fan }
        if (!isPreset && vm.fan in 1..100) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.fan_level, vm.fan), fontSize = 13.sp, color = cs.onSurfaceVariant)
        }
    }
}

/**
 * EN: Live-status card — read-only telemetry from the device: indoor/outdoor temperature, live power
 *     draw (W), total consumption (kWh) and the unit's error code. Answers the common "where do I see
 *     the power?" question.
 * DE: Live-Status-Karte — schreibgeschützte Telemetrie vom Gerät: Innen-/Außentemperatur, aktuelle
 *     Leistungsaufnahme (W), Gesamtverbrauch (kWh) und der Fehlercode des Geräts. Beantwortet die
 *     häufige Frage „Wo sehe ich die Leistung?".
 */
@Composable
private fun LiveStatusCard(vm: AcViewModel) {
    val e = vm.energy
    // EN: Indoor/outdoor are already shown in the hero, so this card focuses on energy + diagnostics.
    // DE: Innen/Außen stehen schon im Hero, daher konzentriert sich diese Karte auf Energie + Diagnose.
    SectionCard(stringResource(R.string.section_status), Icons.Default.Speed) {
        StatusRow(stringResource(R.string.status_power), e?.powerW?.takeIf { !it.isNaN() }?.let { "${it.roundToInt()} W" } ?: "–")
        StatusRow(stringResource(R.string.status_total), e?.totalKwh?.takeIf { !it.isNaN() }?.let { "%.1f kWh".format(it) } ?: "–")
        // EN: Estimated total cost, only when a price per kWh is set. DE: Geschätzte Gesamtkosten, nur wenn ein Preis pro kWh gesetzt ist.
        if (vm.pricePerKwh > 0) {
            val cost = e?.totalKwh?.takeIf { !it.isNaN() }?.let { it * vm.pricePerKwh }
            StatusRow(stringResource(R.string.status_cost), cost?.let { "%.2f".format(it) } ?: "–")
        }
        val err = vm.live?.errorCode ?: 0
        StatusRow(
            stringResource(R.string.status_error),
            if (err == 0) stringResource(R.string.status_error_none) else stringResource(R.string.status_error_code, err),
        )
    }
}

/** EN: A single label-left / value-right row in the status card. DE: Eine Zeile (Label links / Wert rechts) in der Status-Karte. */
@Composable
private fun StatusRow(label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.weight(1f), color = cs.onSurfaceVariant, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.SemiBold, color = cs.onSurface, fontSize = 14.sp)
    }
}

/**
 * EN: Options card — quick toggles for louver swing, eco mode and the device prompt tone (beep).
 * DE: Optionen-Karte — Schnellschalter für Lamellen-Swing, Eco-Modus und den Signalton des Geräts.
 */
@Composable
private fun OptionsCard(vm: AcViewModel) {
    SectionCard(stringResource(R.string.section_options), Icons.Default.Eco) {
        ToggleRow(Icons.Default.SwapVert, stringResource(R.string.option_swing), vm.swing) { vm.toggleSwing() }
        ToggleRow(Icons.Default.Eco, stringResource(R.string.option_eco), vm.eco) { vm.toggleEco() }
        ToggleRow(Icons.Default.NotificationsActive, stringResource(R.string.option_beep), vm.beep) { vm.applyBeep(it) }
        // EN: Device-specific toggles — only shown when the unit reported the capability (B5).
        // DE: Gerätespezifische Schalter — nur sichtbar, wenn das Gerät die Fähigkeit gemeldet hat (B5).
        if (vm.capAnion) ToggleRow(Icons.Default.Spa, stringResource(R.string.option_anion), vm.anion) { vm.toggleAnion() }
        if (vm.capOutSilent) ToggleRow(Icons.Default.VolumeOff, stringResource(R.string.option_out_silent), vm.outSilent) { vm.toggleOutdoorSilent() }
        if (vm.capSelfClean) ToggleRow(Icons.Default.CleaningServices, stringResource(R.string.option_self_clean), vm.selfClean) { vm.toggleSelfClean() }
    }
}

/** EN: A labelled switch row used in the options card. DE: Eine beschriftete Schalter-Zeile in der Optionen-Karte. */
@Composable
private fun ToggleRow(icon: ImageVector, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = cs.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f), color = cs.onSurface, fontSize = 15.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * EN: Compressor-throttle card. Only shown for units that report gear support. Limiting compressor
 *     power trades cooling speed for lower, steadier energy use (verified against a Shelly plug).
 * DE: Kompressor-Drossel-Karte. Nur bei Geräten sichtbar, die Gang-Unterstützung melden. Das
 *     Begrenzen der Kompressorleistung tauscht Kühlgeschwindigkeit gegen geringeren, gleichmäßigeren
 *     Energieverbrauch (gegen eine Shelly-Steckdose verifiziert).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GearCard(vm: AcViewModel) {
    val gears = listOf(
        MideaAc.RATE_OFF to stringResource(R.string.gear_full),
        MideaAc.RATE_GEAR75 to stringResource(R.string.gear_75),
        MideaAc.RATE_GEAR50 to stringResource(R.string.gear_50),
    )
    SectionCard(stringResource(R.string.section_gear), Icons.Default.Speed) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            gears.forEach { (value, label) ->
                FilterChip(
                    selected = vm.rate == value,
                    onClick = { vm.applyRate(value) },
                    label = { Text(label, fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * EN: Sleep-timer card — pick 15/30/60/120 minutes to power the unit off later; shows a live
 *     countdown and a cancel button while a timer is running. The timer runs client-side.
 * DE: Sleep-Timer-Karte — 15/30/60/120 Minuten wählen, um das Gerät später auszuschalten; zeigt
 *     einen Live-Countdown und einen Abbrechen-Knopf, solange ein Timer läuft. Der Timer läuft
 *     auf dem Gerät der App (client-seitig).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SleepTimerCard(vm: AcViewModel) {
    val cs = MaterialTheme.colorScheme
    var showCustom by remember { mutableStateOf(false) }
    SectionCard(stringResource(R.string.section_sleep), Icons.Default.Bedtime) {
        val active = vm.sleepTimerMinutes
        if (active != null) {
            Text(
                stringResource(R.string.sleep_active, active),
                color = cs.primary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
            )
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { vm.cancelSleepTimer() }) {
                Text(stringResource(R.string.sleep_cancel))
            }
        } else {
            val presets = listOf(15, 30, 60, 120)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                presets.forEach { min ->
                    FilterChip(
                        selected = false,
                        onClick = { vm.startSleepTimer(min) },
                        label = { Text("${min}m", fontSize = 13.sp) },
                    )
                }
                // EN: Saved custom duration as a reusable quick chip. DE: Gespeicherte eigene Dauer als wiederverwendbarer Schnell-Chip.
                if (vm.sleepCustomMinutes > 0 && vm.sleepCustomMinutes !in presets) {
                    FilterChip(
                        selected = false,
                        onClick = { vm.startSleepTimer(vm.sleepCustomMinutes) },
                        label = { Text("${vm.sleepCustomMinutes}m", fontSize = 13.sp) },
                    )
                }
                // EN: Free-form duration (remembered after use). DE: Frei wählbare Dauer (wird nach Nutzung gemerkt).
                AssistChip(
                    onClick = { showCustom = true },
                    label = { Text(stringResource(R.string.sleep_custom)) },
                )
            }
        }
    }
    if (showCustom) {
        CustomSleepDialog(
            onDismiss = { showCustom = false },
            onStart = { mins ->
                vm.setSleepCustom(mins)   // EN: remember for next time / DE: für nächstes Mal merken
                vm.startSleepTimer(mins)
                showCustom = false
            },
        )
    }
}

/**
 * EN: Dialog to start a sleep timer with a free-form number of minutes (1–1440).
 * DE: Dialog zum Starten eines Sleep-Timers mit frei wählbarer Minutenzahl (1–1440).
 */
@Composable
private fun CustomSleepDialog(onDismiss: () -> Unit, onStart: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    val mins = text.toIntOrNull()
    val valid = mins != null && mins in 1..1440
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Bedtime, null) },
        title = { Text(stringResource(R.string.sleep_custom_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() }.take(4) },
                label = { Text(stringResource(R.string.sleep_custom_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (valid) onStart(mins!!) }, enabled = valid) {
                Text(stringResource(R.string.action_start))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * EN: Quick-scenes card. Tap a chip to apply a saved full-state preset; long-press to delete it; tap
 *     "Save" to capture the current settings as a new scene. Scenes are persisted via [com.climapilot.free.SceneRepo].
 * DE: Schnell-Szenen-Karte. Tippe auf einen Chip, um eine gespeicherte Komplett-Vorlage anzuwenden;
 *     lange drücken zum Löschen; „Speichern" erfasst die aktuellen Einstellungen als neue Szene.
 *     Szenen werden über [com.climapilot.free.SceneRepo] gespeichert.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScenesCard(vm: AcViewModel) {
    var showSave by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Scene?>(null) }
    SectionCard(stringResource(R.string.section_scenes), Icons.Default.Bolt) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            vm.scenes.forEach { scene ->
                SceneChip(
                    scene = scene,
                    onApply = { vm.applyScene(scene) },
                    onLongPress = { editing = scene },
                )
            }
            AssistChip(
                onClick = { showSave = true },
                leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) },
                label = { Text(stringResource(R.string.scene_save)) },
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.scene_hint),
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (showSave) {
        SaveSceneDialog(
            onDismiss = { showSave = false },
            onConfirm = { name -> vm.saveCurrentAsScene(name); showSave = false },
        )
    }
    editing?.let { scene ->
        EditSceneDialog(
            scene = scene,
            onDismiss = { editing = null },
            onSave = { updated -> vm.updateScene(updated); editing = null },
            onDelete = { vm.deleteScene(scene.id); editing = null },
        )
    }
}

/** EN: Picks an icon that hints at what a scene does. DE: Wählt ein Icon, das andeutet, was eine Szene tut. */
private fun sceneIcon(s: Scene): ImageVector = when {
    !s.powerOn -> Icons.Default.PowerSettingsNew
    s.eco -> Icons.Default.Eco
    s.mode == MideaAc.MODE_HEAT -> Icons.Default.Whatshot
    s.mode == MideaAc.MODE_DRY -> Icons.Default.WaterDrop
    s.mode == MideaAc.MODE_FAN_ONLY -> Icons.Default.Air
    s.mode == MideaAc.MODE_COOL -> Icons.Default.AcUnit
    else -> Icons.Default.Bolt
}

/** EN: A tappable (apply) / long-pressable (delete) scene chip. DE: Ein antippbarer (anwenden) / lange-drückbarer (löschen) Szenen-Chip. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SceneChip(scene: Scene, onApply: () -> Unit, onLongPress: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = cs.secondaryContainer,
        contentColor = cs.onSecondaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onApply, onLongClick = onLongPress),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(sceneIcon(scene), null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(scene.name, fontSize = 14.sp)
        }
    }
}

/** EN: Dialog to name and save the current state as a scene. DE: Dialog zum Benennen und Speichern des aktuellen Zustands als Szene. */
@Composable
private fun SaveSceneDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scene_save_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.scene_name_label)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.scene_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * EN: Full scene editor. Lets the user change the scene's name, power, mode, target temperature, fan
 *     preset, eco and swing, then Save (overwrite) or Delete it. Opened by long-pressing a scene chip.
 * DE: Vollständiger Szenen-Editor. Erlaubt das Ändern von Name, Ein/Aus, Modus, Zieltemperatur,
 *     Lüfter-Vorgabe, Eco und Swing der Szene und dann Speichern (überschreiben) oder Löschen.
 *     Wird durch langes Drücken auf einen Szenen-Chip geöffnet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditSceneDialog(
    scene: Scene,
    onDismiss: () -> Unit,
    onSave: (Scene) -> Unit,
    onDelete: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    // EN: Local working copy of the editable fields. DE: Lokale Arbeitskopie der editierbaren Felder.
    var name by remember { mutableStateOf(scene.name) }
    var powerOn by remember { mutableStateOf(scene.powerOn) }
    var mode by remember { mutableStateOf(scene.mode) }
    var tempC by remember { mutableStateOf(scene.tempC) }
    var fan by remember { mutableStateOf(scene.fan) }
    var eco by remember { mutableStateOf(scene.eco) }
    var swing by remember { mutableStateOf(scene.swing) }
    var scheduleMinutes by remember { mutableStateOf(scene.scheduleMinutes) }
    val context = LocalContext.current
    val fahrenheit = SettingsRepo.useFahrenheit(context)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Bolt, null) },
        title = { Text(stringResource(R.string.scene_edit_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.scene_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))

                // EN: Power on/off. DE: Ein/Aus.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.cd_power), Modifier.weight(1f), color = cs.onSurface, fontSize = 15.sp)
                    Switch(checked = powerOn, onCheckedChange = { powerOn = it })
                }
                Spacer(Modifier.height(8.dp))

                // EN: Operating mode. DE: Betriebsmodus.
                Text(stringResource(R.string.section_mode), color = cs.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MODES.forEach { m ->
                        FilterChip(
                            selected = mode == m.id,
                            onClick = { mode = m.id },
                            label = { Text(stringResource(m.labelRes)) },
                            leadingIcon = { Icon(m.icon, null, Modifier.size(18.dp)) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                // EN: Target temperature stepper. DE: Zieltemperatur-Regler.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.target_temp), Modifier.weight(1f), color = cs.onSurface, fontSize = 15.sp)
                    IconButton(onClick = { tempC = (tempC - 0.5).coerceIn(16.0, 30.0) }) { Icon(Icons.Default.Remove, null) }
                    Text(formatTemp(tempC, fahrenheit), fontWeight = FontWeight.Bold, color = cs.onSurface)
                    IconButton(onClick = { tempC = (tempC + 0.5).coerceIn(16.0, 30.0) }) { Icon(Icons.Default.Add, null) }
                }
                Spacer(Modifier.height(4.dp))

                // EN: Fan preset. DE: Lüfter-Vorgabe.
                Text(stringResource(R.string.section_fan), color = cs.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FanPreset.entries.forEach { p ->
                        FilterChip(
                            selected = fan == p.value,
                            onClick = { fan = p.value },
                            label = { Text(stringResource(p.labelRes)) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                // EN: Eco + swing toggles. DE: Eco- + Swing-Schalter.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.option_eco), Modifier.weight(1f), color = cs.onSurface, fontSize = 15.sp)
                    Switch(checked = eco, onCheckedChange = { eco = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.option_swing), Modifier.weight(1f), color = cs.onSurface, fontSize = 15.sp)
                    Switch(checked = swing, onCheckedChange = { swing = it })
                }

                Spacer(Modifier.height(8.dp))
                // EN: Optional daily schedule — when on, the scene applies automatically each day.
                // DE: Optionaler täglicher Zeitplan — wenn an, wird die Szene jeden Tag automatisch angewendet.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.scene_schedule), Modifier.weight(1f), color = cs.onSurface, fontSize = 15.sp)
                    Switch(
                        checked = scheduleMinutes != null,
                        onCheckedChange = { on -> scheduleMinutes = if (on) (scheduleMinutes ?: 8 * 60) else null },
                    )
                }
                scheduleMinutes?.let { mins ->
                    TextButton(onClick = {
                        // EN: Native time picker keeps it simple and locale-correct. DE: Nativer Zeitwähler hält es einfach und gebietsschema-korrekt.
                        android.app.TimePickerDialog(
                            context,
                            { _, h, m -> scheduleMinutes = h * 60 + m },
                            mins / 60, mins % 60, true,
                        ).show()
                    }) { Text(stringResource(R.string.scene_schedule_at, "%02d:%02d".format(mins / 60, mins % 60))) }
                    Text(stringResource(R.string.scene_schedule_note), fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    scene.copy(
                        name = name.trim().ifBlank { scene.name },
                        powerOn = powerOn, mode = mode, tempC = tempC, fan = fan, eco = eco, swing = swing,
                        scheduleMinutes = scheduleMinutes,
                    )
                )
            }) { Text(stringResource(R.string.scene_save)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.scene_delete), color = cs.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

/**
 * EN: Shared card shell: a titled, icon-headed surface that wraps each section's content.
 * DE: Gemeinsame Karten-Hülle: eine betitelte Fläche mit Icon-Kopf, die den Inhalt jedes Abschnitts umschließt.
 */
@Composable
private fun SectionCard(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = cs.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = cs.onSurface)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

/** EN: Format a temperature, dropping the ".0" for whole degrees. DE: Temperatur formatieren, „.0" bei ganzen Graden weglassen. */
private fun formatTemp(t: Double, fahrenheit: Boolean = false): String {
    // EN: Values are stored in °C; convert only for display when the user picked °F.
    // DE: Werte liegen in °C vor; nur für die Anzeige in °F umrechnen, wenn der Nutzer °F gewählt hat.
    val v = if (fahrenheit) t * 9.0 / 5.0 + 32.0 else t
    val unit = if (fahrenheit) "°F" else "°"
    return if (v % 1.0 == 0.0) "${v.toInt()}$unit" else "%.1f$unit".format(v)
}
