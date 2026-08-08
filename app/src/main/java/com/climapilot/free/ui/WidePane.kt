package com.climapilot.free.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.climapilot.free.AcViewModel
import com.climapilot.free.FanPreset
import com.climapilot.free.R
import com.climapilot.free.midea.MideaAc

/**
 * EN: The control surface for a wide screen, rebuilt from scratch. Two columns with one job each —
 *     left is what you touch to change the unit, right is what you set once and then read. It uses
 *     one surface level throughout, grouped lists with hairline separators, and segmented controls
 *     instead of rows of coloured tiles, so nothing competes for attention with the temperature.
 *
 * DE: Die Steuerfläche für breite Bildschirme, von Grund auf neu. Zwei Spalten mit je einer
 *     Aufgabe — links, was man anfasst, um etwas zu ändern; rechts, was man einmal einstellt und
 *     dann abliest. Durchgehend eine Flächenebene, gruppierte Listen mit Haarlinien und
 *     segmentierte Schalter statt Reihen farbiger Kacheln, damit der Temperatur nichts die
 *     Aufmerksamkeit streitig macht.
 */
@Composable
fun WideControlPane(vm: AcViewModel) {
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints {
        // EN: Two columns only where they earn their keep. Narrower than this and the second column
        //     would squeeze both — so a phone gets the same pieces in one column, and reaches the
        //     options and the live readings through their own tabs instead.
        // DE: Zwei Spalten nur dort, wo sie sich lohnen. Schmaler würde die zweite Spalte beide
        //     quetschen — ein Handy bekommt daher dieselben Bausteine in einer Spalte und erreicht
        //     Optionen und Live-Werte über deren eigene Reiter.
        val twoColumns = maxWidth >= 820.dp
        Column(Modifier.fillMaxSize().background(cs.background)) {
            Box(Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp)) { ConnectedTopBar(vm) }
            if (twoColumns) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        Modifier.weight(1.1f).fillMaxHeight(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                    ) { mainControls(vm) }
                    Box(Modifier.width(0.5.dp).fillMaxHeight().background(cs.outlineVariant))
                    LazyColumn(
                        Modifier.weight(1f).fillMaxHeight(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        item { OptionsGroup(vm) }
                        item { LiveGroup(vm) }
                    }
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) { mainControls(vm) }
            }
        }
    }
}

/**
 * EN: What you touch to change the unit — the same list in one column or two. Infrared has no tabs of
 *     its own, so its extra switches ride along here, and an error is shown wherever it happens.
 * DE: Was man anfasst, um etwas zu ändern — dieselbe Liste, ob ein- oder zweispaltig. Infrarot hat
 *     keine eigenen Reiter, daher fahren seine Extra-Schalter hier mit, und ein Fehler wird dort
 *     gezeigt, wo er auftritt.
 */
private fun LazyListScope.mainControls(vm: AcViewModel) {
    item { PowerAndTemperature(vm) }
    item { Readouts(vm) }
    item { ModeGroup(vm) }
    item { FanGroup(vm) }
    if (vm.irMode) item { IrOptionsCard(vm) }
    errorItem(vm)
}


@Composable
private fun PowerAndTemperature(vm: AcViewModel) {
    val cs = MaterialTheme.colorScheme
    val on = vm.powerOn
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        // EN: A coloured power toggle, not the neutral Segmented control — on/off is the one state you
        //     read from across the room, and the faint grey highlight was too weak to tell running from
        //     off (a user missed that a sleep-timer had switched the unit off). Green = on, red = off.
        // DE: Ein farbiger Power-Schalter statt des neutralen Segmented — Ein/Aus ist der eine Zustand,
        //     den man aus der Ferne abliest, und die blasse graue Hervorhebung war zu schwach, um Laufen
        //     von Aus zu unterscheiden (ein Nutzer übersah, dass ein Sleep-Timer die Anlage abgeschaltet
        //     hatte). Grün = ein, Rot = aus.
        PowerToggle(on, Modifier.padding(horizontal = 40.dp)) { want -> if (want != on) vm.togglePower() }

        Spacer(Modifier.height(26.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton(Icons.Default.Remove, on) { vm.nudgeTemp(-1) }
            Text(
                formatTemp(vm.tempC, vm.useFahrenheit),
                modifier = Modifier.padding(horizontal = 22.dp),
                fontSize = 68.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-2).sp,
                color = if (on) cs.onBackground else cs.onSurfaceVariant,
            )
            StepButton(Icons.Default.Add, on) { vm.nudgeTemp(1) }
        }
        Text(
            stringResource(R.string.target_temp),
            fontSize = 13.sp,
            color = cs.onSurfaceVariant,
        )
    }
}

/**
 * EN: Two-segment power switch with a colour fill on the active side — green when the unit runs, red
 *     when it is off — so the running/off state reads at a glance from across the room.
 * DE: Zwei-Segment-Power-Schalter mit farbiger Füllung auf der aktiven Seite — grün, wenn die Anlage
 *     läuft, rot, wenn sie aus ist — damit man den Zustand aus der Ferne auf einen Blick erkennt.
 */
@Composable
private fun PowerToggle(on: Boolean, modifier: Modifier = Modifier, onSelect: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val onColor = Color(0xFF2ECC71)
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cs.onSurface.copy(alpha = 0.07f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        listOf(false, true).forEach { seg ->
            val active = seg == on
            val fill = when {
                !active -> Color.Transparent
                seg -> onColor
                else -> cs.error
            }
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(fill)
                    .clickable { onSelect(seg) }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(if (seg) R.string.state_on else R.string.state_off),
                    fontSize = 15.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    color = when {
                        active -> Color.White
                        else -> cs.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp).clip(CircleShape).background(cs.surface),
    ) {
        Icon(icon, null, tint = if (enabled) cs.primary else cs.onSurfaceVariant)
    }
}

/** EN: The three numbers you glance at, as one quiet group. DE: Die drei Zahlen, auf die man schaut — als eine ruhige Gruppe. */
@Composable
private fun Readouts(vm: AcViewModel) {
    InsetGroup {
        ValueRow(
            stringResource(R.string.readout_indoor),
            vm.live?.indoorTemp?.let { formatTemp(it, vm.useFahrenheit) } ?: "–",
        )
        Separator()
        ValueRow(
            stringResource(R.string.readout_outdoor),
            vm.live?.outdoorTemp?.let { formatTemp(it, vm.useFahrenheit) } ?: "–",
        )
        Separator()
        ValueRow(
            stringResource(R.string.readout_power),
            vm.energy?.powerW?.let { "${it.toInt()} W" } ?: "–",
        )
    }
}

@Composable
private fun ModeGroup(vm: AcViewModel) {
    Column {
        GroupCaption(stringResource(R.string.section_mode))
        Segmented(
            options = listOf(
                MideaAc.MODE_AUTO to stringResource(R.string.mode_auto),
                MideaAc.MODE_COOL to stringResource(R.string.mode_cool),
                MideaAc.MODE_DRY to stringResource(R.string.mode_dry),
                MideaAc.MODE_HEAT to stringResource(R.string.mode_heat),
                MideaAc.MODE_FAN_ONLY to stringResource(R.string.mode_fan),
            ),
            selected = vm.mode,
        ) { vm.applyMode(it) }
    }
}

/**
 * EN: Fan speed: the named steps, plus a slider for anything in between. The unit takes any value from
 *     1 to 100 %, and the seven presets only ever reached seven of them — 45 % was simply not
 *     expressible. The slider sends on release, never while dragging: a value per pixel would queue up
 *     dozens of commands on the single device socket.
 * DE: Lüfterstufe: die benannten Stufen, dazu ein Schieber für alles dazwischen. Das Gerät nimmt jeden
 *     Wert von 1 bis 100 %, die sieben Voreinstellungen erreichten davon aber nur sieben — 45 % ließ
 *     sich schlicht nicht einstellen. Der Schieber sendet beim Loslassen, nie während des Ziehens: Ein
 *     Wert pro Pixel würde Dutzende Befehle auf dem einen Geräte-Socket auflaufen lassen.
 */
@Composable
private fun FanGroup(vm: AcViewModel) {
    val cs = MaterialTheme.colorScheme
    // EN: Above 100 is the unit's own automatic. DE: Über 100 liegt die geräteeigene Automatik.
    val auto = vm.fan > 100
    var dragging by remember { mutableStateOf(false) }
    var slider by remember { mutableStateOf(vm.fan.coerceIn(1, 100).toFloat()) }
    // EN: Follow the device while the user isn't holding the handle. DE: Dem Gerät folgen, solange der Nutzer den Griff nicht hält.
    LaunchedEffect(vm.fan) { if (!dragging && !auto) slider = vm.fan.coerceIn(1, 100).toFloat() }

    // EN: Verified on the real unit: while turbo is engaged the AC ignores fan-speed commands. Grey the
    //     controls out instead of letting a tap look like it worked and silently snap back.
    // DE: Am echten Gerät belegt: Solange Turbo aktiv ist, ignoriert die Anlage Lüfterstufen-Befehle.
    //     Die Regler werden ausgegraut, statt dass ein Tipper scheinbar wirkt und still zurückspringt.
    val fanLocked = vm.turbo
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GroupCaption(stringResource(R.string.section_fan), Modifier.weight(1f))
            Text(
                if (auto) stringResource(R.string.fan_auto)
                else stringResource(R.string.fan_percent, slider.roundToInt()),
                modifier = Modifier.padding(end = 16.dp, bottom = 7.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = when {
                    fanLocked -> cs.onSurfaceVariant.copy(alpha = 0.38f)
                    auto -> cs.onSurfaceVariant
                    else -> cs.primary
                },
            )
        }
        Segmented(
            options = FanPreset.entries.map { it.value to stringResource(it.labelRes) },
            selected = vm.fan,
            enabled = !fanLocked,
        ) { vm.applyFan(it) }
        Slider(
            value = slider,
            onValueChange = { dragging = true; slider = it },
            onValueChangeFinished = {
                dragging = false
                vm.applyFan(slider.roundToInt().coerceIn(1, 100))
            },
            valueRange = 1f..100f,
            enabled = !auto && !fanLocked,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (fanLocked) {
            Text(
                stringResource(R.string.option_turbo_fan_hint),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OptionsGroup(vm: AcViewModel) {
    InsetGroup(caption = stringResource(R.string.section_options)) {
        ToggleRow(stringResource(R.string.option_swing), vm.swing) { vm.toggleSwing() }
        Separator()
        ToggleRow(stringResource(R.string.option_eco), vm.eco) { vm.toggleEco() }
        Separator()
        // EN: Turbo/boost (issue #13) — same read-back toggle as in the single-column options card.
        // DE: Turbo/Boost (Issue #13) — derselbe zurückgelesene Schalter wie in der einspaltigen Optionen-Karte.
        ToggleRow(stringResource(R.string.option_turbo), vm.turbo) { vm.toggleTurbo() }
        Separator()
        ToggleRow(stringResource(R.string.option_display), vm.display) { vm.toggleDisplay() }
        Separator()
        ToggleRow(stringResource(R.string.option_beep), vm.beep) { vm.applyBeep(it) }
        if (vm.capAnion) {
            Separator()
            ToggleRow(stringResource(R.string.option_anion), vm.anion) { vm.toggleAnion() }
        }
        if (vm.capOutSilent) {
            Separator()
            ToggleRow(stringResource(R.string.option_out_silent), vm.outSilent) { vm.toggleOutdoorSilent() }
        }
        if (vm.capSelfClean) {
            Separator()
            ToggleRow(stringResource(R.string.option_self_clean), vm.selfClean) { vm.toggleSelfClean() }
            // EN: A cleaning run takes a while and pauses normal cooling — say so before the switch is
            //     flipped in passing. DE: Ein Reinigungslauf dauert eine Weile und unterbricht das normale
            //     Kühlen — das gehört gesagt, bevor der Schalter im Vorbeigehen kippt.
            Text(
                stringResource(R.string.option_self_clean_hint),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LiveGroup(vm: AcViewModel) {
    InsetGroup(caption = stringResource(R.string.section_status)) {
        ValueRow(
            stringResource(R.string.status_total),
            vm.energy?.totalKwh?.let { String.format("%.1f kWh", it) } ?: "–",
        )
        Separator()
        ValueRow(
            stringResource(R.string.status_error),
            vm.live?.errorCode?.takeIf { it != 0 }?.toString()
                ?: stringResource(R.string.status_error_none),
        )
    }
}
