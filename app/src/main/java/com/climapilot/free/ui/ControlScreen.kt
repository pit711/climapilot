package com.climapilot.free.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.CalendarMonth
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.climapilot.free.EndAction
import com.climapilot.free.FanPreset
import com.climapilot.free.PlanEntry
import com.climapilot.free.SettingsRepo
import com.climapilot.free.R
import com.climapilot.free.Scene
import com.climapilot.free.Status
import com.climapilot.free.midea.MideaAc
import java.text.DateFormatSymbols
import java.util.Calendar
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
        // EN: IR mode shows no tabs, so its extra toggles live right here on the single screen. DE: Der IR-Modus hat keine Reiter, daher liegen seine Extra-Schalter direkt hier auf dem einen Bildschirm.
        if (vm.irMode) item(key = "iropts") { IrOptionsCard(vm) }
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

/** EN: Scenes tab — quick scenes, weekly plan + sleep timer. DE: Szenen-Reiter — Schnell-Szenen, Wochenplan + Sleep-Timer. */
@Composable
fun ScenesTab(vm: AcViewModel) {
    TabList {
        item(key = "topbar") { ConnectedTopBar(vm) }
        item(key = "scenes") { ScenesCard(vm) }
        item(key = "plan") { PlanCard(vm) }
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

/**
 * EN: IR-mode options — fire-and-forget special-command toggles (Swing/Quiet/Turbo/Econo). IR is
 *     one-way, so a switch reflects what we last sent, not a confirmed device state; the hint says so.
 * DE: IR-Modus-Optionen — Sonderbefehl-Umschalter ohne Rückmeldung (Swing/Leise/Turbo/Eco). IR ist
 *     einweg, daher zeigt ein Schalter das zuletzt Gesendete, nicht einen bestätigten Gerätezustand;
 *     der Hinweis sagt das.
 */
@Composable
private fun IrOptionsCard(vm: AcViewModel) {
    val cs = MaterialTheme.colorScheme
    SectionCard(stringResource(R.string.section_options), Icons.Default.Eco) {
        ToggleRow(Icons.Default.SwapVert, stringResource(R.string.option_swing), vm.irSwing) { vm.toggleIrSwing() }
        ToggleRow(Icons.Default.VolumeOff, stringResource(R.string.option_quiet), vm.irQuiet) { vm.toggleIrQuiet() }
        ToggleRow(Icons.Default.Bolt, stringResource(R.string.option_turbo), vm.irTurbo) { vm.toggleIrTurbo() }
        ToggleRow(Icons.Default.Eco, stringResource(R.string.option_eco), vm.irEcono) { vm.toggleIrEcono() }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.ir_options_hint), fontSize = 11.sp, color = cs.onSurfaceVariant)
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

// ===========================================================================================
// EN: Weekly day-planner — assign scenes to recurring weekday + time windows (e.g. "max cooling
//     Mondays 6–18"). The plan runs in the background via PlanScheduler even while the phone is idle.
// DE: Wochen-Tagesplaner — Szenen wiederkehrenden Wochentag-/Zeit-Fenstern zuweisen (z. B. „maximal
//     kühlen montags 6–18"). Der Plan läuft über PlanScheduler im Hintergrund, auch wenn das Handy ruht.
// ===========================================================================================

/**
 * EN: The weekly plan card: a visual week calendar on top, the editable list of windows below, plus an
 *     "add" action. Each window applies a chosen scene when it starts and (optionally) powers the unit
 *     off when it ends. Editing is non-destructive to a running unit — saving never sends a command; the
 *     plan only acts at its scheduled boundaries.
 * DE: Die Wochenplan-Karte: oben ein visueller Wochenkalender, darunter die editierbare Liste der
 *     Fenster plus „Hinzufügen". Jedes Fenster wendet beim Start eine gewählte Szene an und schaltet das
 *     Gerät beim Ende (optional) aus. Das Bearbeiten ist für ein laufendes Gerät unkritisch — Speichern
 *     sendet nie einen Befehl; der Plan wirkt nur an seinen geplanten Grenzen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanCard(vm: AcViewModel) {
    val cs = MaterialTheme.colorScheme
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PlanEntry?>(null) }
    SectionCard(stringResource(R.string.section_plan), Icons.Default.CalendarMonth) {
        // EN: Visual week overview — coloured blocks where a window is active each day. DE: Visuelle Wochenübersicht — farbige Blöcke, wo täglich ein Fenster aktiv ist.
        WeekCalendar(vm.plan, vm.scenes)
        Spacer(Modifier.height(12.dp))

        if (vm.plan.isEmpty()) {
            Text(stringResource(R.string.plan_empty), fontSize = 13.sp, color = cs.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                vm.plan.forEach { entry ->
                    PlanRow(
                        entry = entry,
                        scene = vm.scenes.firstOrNull { it.id == entry.sceneId },
                        onClick = { editing = entry },
                        onToggle = { vm.setPlanEntryEnabled(entry.id, it) },
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        AssistChip(
            onClick = { adding = true },
            enabled = vm.scenes.isNotEmpty(),
            leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) },
            label = { Text(stringResource(R.string.plan_add)) },
        )
        if (vm.scenes.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.plan_need_scene), fontSize = 12.sp, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.plan_hint), fontSize = 12.sp, color = cs.onSurfaceVariant)
    }

    if (adding) {
        PlanEntryDialog(
            entry = null,
            scenes = vm.scenes,
            onDismiss = { adding = false },
            onSave = { vm.savePlanEntry(it); adding = false },
            onDelete = null,
        )
    }
    editing?.let { e ->
        PlanEntryDialog(
            entry = e,
            scenes = vm.scenes,
            onDismiss = { editing = null },
            onSave = { vm.savePlanEntry(it); editing = null },
            onDelete = { vm.deletePlanEntry(e.id); editing = null },
        )
    }
}

/**
 * EN: A compact seven-row week timetable. Each row is a 24-hour track with a coloured block for every
 *     active window on that weekday, giving the plan a calendar-like feel at a glance.
 * DE: Eine kompakte Wochen-Stundenplan-Ansicht mit sieben Zeilen. Jede Zeile ist eine 24-Stunden-Spur
 *     mit einem farbigen Block je aktivem Fenster an diesem Wochentag — der Plan wirkt auf einen Blick
 *     wie ein Kalender.
 */
@Composable
private fun WeekCalendar(plan: List<PlanEntry>, scenes: List<Scene>) {
    val cs = MaterialTheme.colorScheme
    val track = cs.surfaceVariant
    val fill = cs.primary
    val sceneIds = scenes.map { it.id }.toHashSet()
    val active = plan.filter { it.enabled && it.sceneId in sceneIds }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        for (iso in 1..7) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(dayShort(iso), Modifier.width(36.dp), fontSize = 12.sp, color = cs.onSurfaceVariant)
                Canvas(Modifier.weight(1f).height(14.dp)) {
                    val r = size.height / 2f
                    drawRoundRect(color = track, cornerRadius = CornerRadius(r, r))
                    active.filter { iso in it.days }.forEach { e ->
                        windowSegments(e.startMinutes, e.endMinutes).forEach { (a, b) ->
                            val x0 = (a / 1440f) * size.width
                            val w = ((b - a) / 1440f * size.width).coerceAtLeast(3f)
                            drawRoundRect(
                                color = fill,
                                topLeft = Offset(x0, 0f),
                                size = Size(w, size.height),
                                cornerRadius = CornerRadius(r, r),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** EN: One window split into drawable [start,end] minute spans (two when it crosses midnight). DE: Ein Fenster in zeichenbare [Start,Ende]-Minuten-Spannen zerlegt (zwei bei Mitternachtsüberlauf). */
private fun windowSegments(start: Int, end: Int): List<Pair<Int, Int>> =
    if (end > start) listOf(start to end) else listOf(start to 1440, 0 to end)

/** EN: A plan window row: scene + days + time range, with an on/off switch; tap to edit. DE: Eine Plan-Fenster-Zeile: Szene + Tage + Zeitspanne, mit Ein/Aus-Schalter; Tippen zum Bearbeiten. */
@Composable
private fun PlanRow(entry: PlanEntry, scene: Scene?, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val faded = if (entry.enabled) 1f else 0.5f
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = cs.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                scene?.let { sceneIcon(it) } ?: Icons.Default.CalendarMonth,
                null,
                Modifier.size(20.dp).graphicsLayer { alpha = faded },
                tint = cs.primary,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).graphicsLayer { alpha = faded }) {
                Text(
                    scene?.name ?: "—",
                    fontSize = 15.sp, fontWeight = FontWeight.Medium, color = cs.onSurface,
                )
                val days = daysLabel(entry.days)
                val time = timeRangeLabel(entry.startMinutes, entry.endMinutes)
                val tail = if (entry.endAction == EndAction.OFF) " · " + stringResource(R.string.plan_to_off) else ""
                val paused = if (!entry.enabled) " · " + stringResource(R.string.plan_disabled) else ""
                Text(
                    "$days · $time$tail$paused",
                    fontSize = 12.sp, color = cs.onSurfaceVariant,
                )
            }
            Switch(checked = entry.enabled, onCheckedChange = onToggle)
        }
    }
}

/**
 * EN: Add/edit one plan window: pick the scene, the weekdays, the from/to times and the end behaviour.
 *     The native time picker keeps time entry simple and locale-correct, as in the scene editor.
 * DE: Ein Plan-Fenster anlegen/bearbeiten: Szene, Wochentage, Von-/Bis-Zeiten und Endverhalten wählen.
 *     Der native Zeitwähler hält die Eingabe einfach und gebietsschema-korrekt, wie im Szenen-Editor.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PlanEntryDialog(
    entry: PlanEntry?,
    scenes: List<Scene>,
    onDismiss: () -> Unit,
    onSave: (PlanEntry) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    var sceneId by remember { mutableStateOf(entry?.sceneId ?: scenes.firstOrNull()?.id ?: "") }
    var days by remember { mutableStateOf(entry?.days ?: setOf(1, 2, 3, 4, 5)) }
    var start by remember { mutableStateOf(entry?.startMinutes ?: 6 * 60) }
    var end by remember { mutableStateOf(entry?.endMinutes ?: 18 * 60) }
    var endOff by remember { mutableStateOf((entry?.endAction ?: EndAction.OFF) == EndAction.OFF) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.CalendarMonth, null) },
        title = { Text(stringResource(if (entry == null) R.string.plan_new_title else R.string.plan_edit_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // EN: Scene picker. DE: Szenen-Auswahl.
                Text(stringResource(R.string.plan_scene_label), color = cs.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    scenes.forEach { s ->
                        FilterChip(
                            selected = sceneId == s.id,
                            onClick = { sceneId = s.id },
                            label = { Text(s.name) },
                            leadingIcon = { Icon(sceneIcon(s), null, Modifier.size(18.dp)) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                // EN: Weekday multi-select. DE: Wochentag-Mehrfachauswahl.
                Text(stringResource(R.string.plan_days_label), color = cs.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (iso in 1..7) {
                        FilterChip(
                            selected = iso in days,
                            onClick = { days = if (iso in days) days - iso else days + iso },
                            label = { Text(dayShort(iso)) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                // EN: From / to times. DE: Von-/Bis-Zeiten.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        android.app.TimePickerDialog(context, { _, h, m -> start = h * 60 + m }, start / 60, start % 60, true).show()
                    }) { Text(stringResource(R.string.plan_from) + "  " + minutesLabel(start)) }
                    TextButton(onClick = {
                        android.app.TimePickerDialog(context, { _, h, m -> end = h * 60 + m }, end / 60, end % 60, true).show()
                    }) { Text(stringResource(R.string.plan_to) + "  " + minutesLabel(end)) }
                    if (end <= start) {
                        Text(stringResource(R.string.plan_overnight), fontSize = 12.sp, color = cs.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))

                // EN: End behaviour. DE: Endverhalten.
                Text(stringResource(R.string.plan_end_label), color = cs.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = endOff, onClick = { endOff = true }, label = { Text(stringResource(R.string.plan_end_off)) })
                    FilterChip(selected = !endOff, onClick = { endOff = false }, label = { Text(stringResource(R.string.plan_end_leave)) })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = sceneId.isNotEmpty() && days.isNotEmpty(),
                onClick = {
                    val base = entry ?: PlanEntry(sceneId = sceneId, days = days, startMinutes = start, endMinutes = end)
                    onSave(
                        base.copy(
                            sceneId = sceneId,
                            days = days,
                            startMinutes = start,
                            endMinutes = end,
                            endAction = if (endOff) EndAction.OFF else EndAction.LEAVE,
                        )
                    )
                },
            ) { Text(stringResource(R.string.scene_save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.scene_delete), color = cs.error) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

/** EN: "HH:mm" for minutes-since-midnight. DE: „HH:mm" für Minuten seit Mitternacht. */
private fun minutesLabel(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

/** EN: "06:00–18:00" (+1 day suffix when the window crosses midnight). DE: „06:00–18:00" (+1-Tag-Zusatz bei Mitternachtsüberlauf). */
@Composable
private fun timeRangeLabel(start: Int, end: Int): String {
    val base = "${minutesLabel(start)}–${minutesLabel(end)}"
    return if (end <= start) "$base (${stringResource(R.string.plan_overnight)})" else base
}

/** EN: Localised day list, collapsed to "Every day" when all seven are picked. DE: Lokalisierte Tagesliste, zu „Täglich" zusammengefasst, wenn alle sieben gewählt sind. */
@Composable
private fun daysLabel(days: Set<Int>): String =
    if (days.size >= 7) stringResource(R.string.plan_every_day)
    else days.sorted().joinToString(" ") { dayShort(it) }

/** EN: Short localised weekday name for ISO day 1=Mon … 7=Sun. DE: Kurzer, lokalisierter Wochentagsname für ISO-Tag 1=Mo … 7=So. */
private fun dayShort(iso: Int): String {
    // EN: DateFormatSymbols.shortWeekdays is indexed by Calendar day (1=Sun … 7=Sat). DE: DateFormatSymbols.shortWeekdays ist nach Calendar-Tag indiziert (1=So … 7=Sa).
    val calIdx = if (iso == 7) Calendar.SUNDAY else iso + 1
    return DateFormatSymbols.getInstance().shortWeekdays.getOrNull(calIdx)
        ?.trimEnd('.', ' ')
        ?.ifBlank { iso.toString() }
        ?: iso.toString()
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
