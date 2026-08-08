package com.climapilot.free.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * EN: A small set of grouped-list building blocks, in the spirit of iOS settings screens: one
 *     surface level instead of three, a quiet uppercase caption above each group, hairline
 *     separators between rows, and colour reserved for what is active or tappable.
 *
 *     The old screens nested a card inside a card inside a tile, which made every section shout for
 *     attention. These pieces are deliberately plain so the content carries the page.
 *
 * DE: Ein kleiner Satz Bausteine für gruppierte Listen, im Geist der iOS-Einstellungen: eine
 *     Flächenebene statt drei, eine ruhige Versal-Überschrift über jeder Gruppe, Haarlinien
 *     zwischen den Zeilen, und Farbe nur für das, was aktiv oder antippbar ist.
 *
 *     Die bisherigen Bildschirme verschachtelten Karte in Karte in Kachel — dadurch rief jeder
 *     Abschnitt gleich laut nach Aufmerksamkeit. Diese Teile sind bewusst schlicht, damit der
 *     Inhalt die Seite trägt.
 */

/** EN: Quiet caption above a group. DE: Ruhige Überschrift über einer Gruppe. */
@Composable
fun GroupCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(start = 16.dp, end = 16.dp, bottom = 7.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.07.sp,
    )
}

/**
 * EN: One rounded block holding a stack of rows. Separators are drawn between children only,
 *     never above the first or below the last — the classic inset-grouped look.
 * DE: Ein abgerundeter Block mit gestapelten Zeilen. Trenner nur zwischen den Kindern, nie über
 *     der ersten oder unter der letzten — die klassische Inset-Gruppe.
 */
@Composable
fun InsetGroup(
    modifier: Modifier = Modifier,
    caption: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        caption?.let { GroupCaption(it) }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface),
            content = content,
        )
    }
}

/** EN: Hairline between rows, inset from the left like on iOS. DE: Haarlinie zwischen Zeilen, links eingerückt wie unter iOS. */
@Composable
fun Separator() {
    Box(
        Modifier
            .padding(start = 16.dp)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** EN: Label on the left, value on the right. DE: Bezeichnung links, Wert rechts. */
@Composable
fun ValueRow(label: String, value: String, tint: androidx.compose.ui.graphics.Color? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            fontSize = 16.sp,
            color = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** EN: Label with a switch. DE: Bezeichnung mit Schalter. */
@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    subtitle: String? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 7.dp)) {
            Text(
                label,
                fontSize = 16.sp,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            subtitle?.let {
                Text(it, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** EN: Tappable row, e.g. to open something. DE: Antippbare Zeile, z. B. um etwas zu öffnen. */
@Composable
fun ActionRow(label: String, onClick: () -> Unit, trailing: String? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp).clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.weight(1f))
        trailing?.let {
            Text(it, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * EN: Segmented control — the iOS way of picking one of a few options. Replaces the rows of
 *     coloured tiles, which needed a lot of ink to say very little.
 * DE: Segmentierter Schalter — die iOS-Art, eine von wenigen Möglichkeiten zu wählen. Ersetzt die
 *     Reihen farbiger Kacheln, die viel Fläche brauchten, um wenig zu sagen.
 */
@Composable
fun <T> Segmented(
    options: List<Pair<T, String>>,
    selected: T,
    modifier: Modifier = Modifier,
    // EN: Disabled segments dim and swallow taps — used while another setting (e.g. turbo) owns the
    //     value, so a tap doesn't look like it worked and then silently snap back.
    // DE: Deaktivierte Segmente dimmen und schlucken Tipper — genutzt, solange eine andere Einstellung
    //     (z. B. Turbo) den Wert bestimmt, damit ein Tipper nicht scheinbar wirkt und still zurückspringt.
    enabled: Boolean = true,
    onSelect: (T) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // EN: Size the label to the space each segment actually gets. Seven fan speeds on a 360dp phone
        //     leave about 45dp per segment, and at a fixed 14sp "Niedrig" was clipped to "Niedri" — a
        //     truncated control reads as a rendering fault, so the type gives way instead.
        // DE: Die Beschriftung an den Platz anpassen, den ein Segment wirklich bekommt. Sieben
        //     Lüfterstufen auf einem 360dp-Handy lassen je etwa 45dp, und bei festen 14sp wurde
        //     „Niedrig" zu „Niedri" abgeschnitten — ein beschnittener Schalter wirkt wie ein
        //     Darstellungsfehler, also weicht die Schrift.
        val perSegment = maxWidth / options.size.coerceAtLeast(1)
        val longest = options.maxOf { it.second.length }
        val fontSize = when {
            perSegment >= 70.dp || longest <= 5 -> 14.sp
            perSegment >= 52.dp -> 13.sp
            perSegment >= 44.dp -> 11.5.sp
            else -> 10.5.sp
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(cs.onSurface.copy(alpha = 0.07f))
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            options.forEach { (value, label) ->
                val on = value == selected
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            if (on && enabled) cs.onSurface.copy(alpha = 0.22f)
                            else androidx.compose.ui.graphics.Color.Transparent
                        )
                        .clickable(enabled = enabled) { onSelect(value) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        fontSize = fontSize,
                        maxLines = 1,
                        letterSpacing = (-0.2).sp,
                        fontWeight = if (on && enabled) FontWeight.SemiBold else FontWeight.Normal,
                        color = when {
                            !enabled -> cs.onSurfaceVariant.copy(alpha = 0.38f)
                            on -> cs.onSurface
                            else -> cs.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}
