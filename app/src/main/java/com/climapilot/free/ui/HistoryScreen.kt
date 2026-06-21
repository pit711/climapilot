package com.climapilot.free.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.climapilot.free.AcViewModel
import com.climapilot.free.R
import com.climapilot.free.UsageHistory
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

private enum class Range(val labelRes: Int, val spanMs: Long) {
    HOUR(R.string.range_hour, 3_600_000L),
    DAY(R.string.range_day, 86_400_000L),
    WEEK(R.string.range_week, 7 * 86_400_000L),
    MONTH(R.string.range_month, 30 * 86_400_000L),
}

/**
 * EN: Per-AC energy & runtime history. A recording switch, a time-range filter (hour/day/week/month) or
 *     a specific calendar day, axis-labelled line charts (power, indoor & outdoor temp, fan level) that
 *     fill from the very first sample and refresh live while open, total consumption + cost, and a filter
 *     cleaning reminder. Recording is kept indefinitely. All offline.
 * DE: Energie- & Laufzeit-Historie pro Klima. Aufzeichnungs-Schalter, Zeitbereich-Filter
 *     (Stunde/Tag/Woche/Monat) oder ein bestimmter Kalendertag, achsenbeschriftete Liniendiagramme
 *     (Leistung, Innen-/Außentemperatur, Lüfterstufe), die sich ab dem ersten Messwert füllen und im
 *     geöffneten Zustand live aktualisieren, Gesamtverbrauch + Kosten und eine Filter-Erinnerung.
 *     Aufzeichnung bleibt unbegrenzt. Alles offline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: AcViewModel) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val deviceId = vm.connectedDevice?.id ?: 0L

    var mode by remember { mutableStateOf(Range.DAY) }
    var pickedDay by remember { mutableStateOf<Long?>(null) } // local start-of-day ms; null = relative window
    var showPicker by remember { mutableStateOf(false) }

    // EN: Re-query every 15 s so the charts fill live as new samples land. DE: Alle 15 s neu abfragen, damit sich die Charts live mit neuen Messwerten füllen.
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); tick++ } }

    val samples = remember(deviceId, mode, pickedDay, tick) {
        val (from, to) = if (pickedDay != null) {
            pickedDay!! to (pickedDay!! + 86_400_000L)
        } else {
            val now = System.currentTimeMillis()
            (now - mode.spanMs) to now
        }
        UsageHistory.range(context, deviceId, from, to)
    }
    var filterHours by remember { mutableStateOf(UsageHistory.filterHours(context, deviceId)) }
    LaunchedEffect(tick, deviceId) { filterHours = UsageHistory.filterHours(context, deviceId) }

    // EN: Time-axis formatter: clock for hour/day/a picked day, date for week/month. DE: Zeitachsen-Format: Uhr für Stunde/Tag/gewählten Tag, Datum für Woche/Monat.
    val timeFmt: (Long) -> String = remember(mode, pickedDay) {
        val pattern = if (pickedDay != null || mode == Range.HOUR || mode == Range.DAY) "HH:mm" else "dd.MM."
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        ({ t: Long -> sdf.format(Date(t)) })
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      Column(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        ConnectedTopBar(vm)
        Spacer(Modifier.height(14.dp))

        RecordToggleCard(vm)
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Range.entries.forEach { r ->
                FilterChip(
                    selected = pickedDay == null && mode == r,
                    onClick = { mode = r; pickedDay = null },
                    label = { Text(stringResource(r.labelRes)) },
                )
                Spacer(Modifier.width(8.dp))
            }
            IconButton(onClick = { showPicker = true }) {
                Icon(Icons.Default.CalendarMonth, stringResource(R.string.history_pick_day), tint = cs.primary)
            }
        }
        if (pickedDay != null) {
            Text(
                stringResource(R.string.history_day, DateFormat.getDateInstance(DateFormat.FULL).format(Date(pickedDay!!))),
                fontSize = 13.sp, color = cs.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        }
        Spacer(Modifier.height(14.dp))

        if (samples.isEmpty()) {
            SectionCard(Icons.Default.ShowChart, stringResource(R.string.history_power_title)) {
                Text(stringResource(R.string.history_empty), fontSize = 14.sp, color = cs.onSurfaceVariant, lineHeight = 20.sp)
            }
        } else {
            MetricChartCard(Icons.Default.Bolt, stringResource(R.string.history_power_title), "W", cs.primary,
                samples.map { it.ts to it.powerW }, timeFmt)
            Spacer(Modifier.height(14.dp))
            MetricChartCard(Icons.Default.Thermostat, stringResource(R.string.history_indoor_title), "°", Color(0xFF34A0FF),
                samples.mapNotNull { s -> s.indoorTemp?.let { s.ts to it } }, timeFmt)
            Spacer(Modifier.height(14.dp))
            MetricChartCard(Icons.Default.Thermostat, stringResource(R.string.history_outdoor_title), "°", Color(0xFFFF8A3D),
                samples.mapNotNull { s -> s.outdoorTemp?.let { s.ts to it } }, timeFmt)
            Spacer(Modifier.height(14.dp))
            MetricChartCard(Icons.Default.Air, stringResource(R.string.history_fan_title), "", Color(0xFF59C36A),
                samples.map { it.ts to it.fanSpeed.toDouble() }, timeFmt)
            Spacer(Modifier.height(14.dp))
            TotalsCard(samples, vm.pricePerKwh)
        }
        Spacer(Modifier.height(14.dp))
        FilterCard(filterHours, onReset = { UsageHistory.resetFilter(context, deviceId); filterHours = 0.0 })
        Spacer(Modifier.height(40.dp))
      }
    }

    if (showPicker) {
        val dpState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { pickedDay = localDayStart(it) }
                    showPicker = false
                }) { Text(stringResource(R.string.action_close)) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = dpState) }
    }
}

private fun localDayStart(utcMidnightMs: Long): Long {
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMidnightMs }
    return Calendar.getInstance().apply {
        clear()
        set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
}

@Composable
private fun RecordToggleCard(vm: AcViewModel) {
    val cs = MaterialTheme.colorScheme
    SectionCard(Icons.Default.ShowChart, stringResource(R.string.history_record_title)) {
        Text(stringResource(R.string.history_record_body), fontSize = 14.sp, color = cs.onSurfaceVariant, lineHeight = 20.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.history_record_switch), Modifier.weight(1f), color = cs.onSurface, fontSize = 15.sp)
            Switch(checked = vm.historyEnabled, onCheckedChange = { vm.updateHistoryEnabled(it) })
        }
    }
}

/** EN: One metric's line chart with min/max (Y) and start/end-time (X) axis labels. Fills from one point. DE: Liniendiagramm einer Größe mit Min/Max- (Y) und Start/End-Zeit- (X) Achsenbeschriftung. Füllt sich ab einem Punkt. */
@Composable
private fun MetricChartCard(
    icon: ImageVector,
    title: String,
    unit: String,
    color: Color,
    values: List<Pair<Long, Double>>,
    timeFmt: (Long) -> String,
) {
    val cs = MaterialTheme.colorScheme
    SectionCard(icon, title) {
        if (values.isEmpty()) {
            Text(stringResource(R.string.history_empty), fontSize = 13.sp, color = cs.onSurfaceVariant)
            return@SectionCard
        }
        val ys = values.map { it.second }
        val minY = ys.min()
        val maxY = ys.max()
        val span = (maxY - minY).takeIf { it > 0.0001 } ?: 1.0
        Box(Modifier.fillMaxWidth().height(130.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                drawLine(color.copy(alpha = 0.18f), Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
                drawLine(color.copy(alpha = 0.25f), Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                val n = values.size
                if (n == 1) {
                    drawCircle(color, radius = 4.dp.toPx(), center = Offset(size.width / 2, size.height / 2))
                } else {
                    val path = Path()
                    values.forEachIndexed { i, (_, v) ->
                        val x = size.width * i / (n - 1)
                        val y = size.height - (size.height * ((v - minY) / span)).toFloat()
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, color = color, style = Stroke(width = 3.dp.toPx()))
                }
            }
            // EN: Y-axis labels (max top, min bottom). DE: Y-Achsen-Beschriftung (Max oben, Min unten).
            AxisLabel("${fmt(maxY)}$unit", Modifier.align(Alignment.TopStart))
            AxisLabel("${fmt(minY)}$unit", Modifier.align(Alignment.BottomStart))
        }
        Spacer(Modifier.height(4.dp))
        // EN: X-axis labels (first/last timestamp). DE: X-Achsen-Beschriftung (erster/letzter Zeitstempel).
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(timeFmt(values.first().first), fontSize = 11.sp, color = cs.onSurfaceVariant)
            Text(timeFmt(values.last().first), fontSize = 11.sp, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.history_metric_summary, fmt(values.last().second), unit, fmt(minY), fmt(maxY), unit),
            fontSize = 13.sp, color = cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun AxisLabel(text: String, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Text(
        text,
        modifier = modifier.background(cs.surface.copy(alpha = 0.65f)).padding(horizontal = 3.dp),
        fontSize = 10.sp,
        color = cs.onSurfaceVariant,
    )
}

private fun fmt(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)

@Composable
private fun TotalsCard(samples: List<UsageHistory.Sample>, pricePerKwh: Double) {
    val cs = MaterialTheme.colorScheme
    val withKwh = samples.filter { it.totalKwh > 0.0 }
    val deltaKwh = if (withKwh.size >= 2) (withKwh.last().totalKwh - withKwh.first().totalKwh).coerceAtLeast(0.0) else 0.0
    SectionCard(Icons.Default.ShowChart, stringResource(R.string.history_totals_title)) {
        Text(stringResource(R.string.history_range_kwh, deltaKwh), fontSize = 15.sp, color = cs.onSurface, fontWeight = FontWeight.SemiBold)
        if (pricePerKwh > 0.0) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.history_range_cost, deltaKwh * pricePerKwh), fontSize = 14.sp, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun FilterCard(filterHours: Double, onReset: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val due = filterHours >= UsageHistory.FILTER_LIMIT_HOURS
    SectionCard(Icons.Default.Air, stringResource(R.string.history_filter_title)) {
        Text(
            stringResource(R.string.history_filter_runtime, filterHours.roundToInt(), UsageHistory.FILTER_LIMIT_HOURS.roundToInt()),
            fontSize = 15.sp, color = cs.onSurface, fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { (filterHours / UsageHistory.FILTER_LIMIT_HOURS).coerceIn(0.0, 1.0).toFloat() },
            modifier = Modifier.fillMaxWidth(),
            color = if (due) cs.error else cs.primary,
        )
        if (due) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.history_filter_due), fontSize = 13.sp, color = cs.error, lineHeight = 18.sp)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.history_filter_reset))
        }
    }
}

@Composable
private fun SectionCard(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = cs.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = cs.onSurface)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
