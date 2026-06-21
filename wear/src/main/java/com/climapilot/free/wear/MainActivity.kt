package com.climapilot.free.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlin.math.roundToInt

/**
 * EN: The watch app. It is a thin remote for the phone: it shows the last-known AC state (pushed by the
 *     phone over the Wearable Data Layer at /climapilot/state) and sends control commands back at
 *     /climapilot/cmd. The phone does the actual LAN control (it holds the token), so the watch never
 *     talks to the AC directly.
 * DE: Die Watch-App. Sie ist eine dünne Fernbedienung fürs Phone: zeigt den zuletzt bekannten
 *     Klima-Zustand (vom Phone über den Wearable-Data-Layer unter /climapilot/state gepusht) und sendet
 *     Steuerbefehle zurück unter /climapilot/cmd. Das Phone macht die eigentliche LAN-Steuerung (es hält
 *     das Token), die Watch spricht also nie direkt mit der Klima.
 */
class MainActivity : ComponentActivity() {

    private var present by mutableStateOf(false)
    private var powerOn by mutableStateOf(false)
    private var temp by mutableStateOf(Double.NaN)

    private val dataListener = DataClient.OnDataChangedListener { events ->
        for (e in events) {
            if (e.type == DataEvent.TYPE_CHANGED && e.dataItem.uri.path == PATH_STATE) {
                applyState(DataMapItem.fromDataItem(e.dataItem))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (!present) {
                        Text(
                            getString(R.string.wear_no_phone),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colors.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        WearControls()
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun WearControls() {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                if (temp.isNaN()) getString(R.string.wear_temp_placeholder) else "${temp.roundToInt()}°",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = if (powerOn) MaterialTheme.colors.onBackground else MaterialTheme.colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundLabelButton("−") { send(CMD_TEMP_DOWN) }
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = { send(CMD_POWER) },
                    colors = if (powerOn) ButtonDefaults.primaryButtonColors() else ButtonDefaults.secondaryButtonColors(),
                    modifier = Modifier.size(56.dp),
                ) { Text("⏻", fontSize = 22.sp) }
                Spacer(Modifier.width(10.dp))
                RoundLabelButton("+") { send(CMD_TEMP_UP) }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { send(CMD_MODE) }, colors = ButtonDefaults.secondaryButtonColors()) {
                Text(getString(R.string.wear_mode), fontSize = 14.sp)
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun RoundLabelButton(label: String, onClick: () -> Unit) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.secondaryButtonColors(),
            modifier = Modifier.size(48.dp),
        ) { Text(label, fontSize = 24.sp) }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(dataListener)
        // EN: Pull the current state once on open. DE: Beim Öffnen einmal den aktuellen Zustand holen.
        Wearable.getDataClient(this).dataItems.addOnSuccessListener { buffer ->
            buffer.firstOrNull { it.uri.path == PATH_STATE }
                ?.let { applyState(DataMapItem.fromDataItem(it)) }
            buffer.release()
        }
    }

    override fun onPause() {
        super.onPause()
        Wearable.getDataClient(this).removeListener(dataListener)
    }

    private fun applyState(item: DataMapItem) {
        val map = item.dataMap
        powerOn = map.getBoolean("power", false)
        temp = if (map.containsKey("temp")) map.getDouble("temp") else Double.NaN
        present = true
    }

    /** EN: Send a one-word command to the phone over every connected node. DE: Einen Ein-Wort-Befehl über jeden verbundenen Knoten ans Phone senden. */
    private fun send(cmd: String) {
        val mc = Wearable.getMessageClient(this)
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            for (n in nodes) mc.sendMessage(n.id, PATH_CMD, cmd.toByteArray())
        }
    }

    private companion object {
        const val PATH_STATE = "/climapilot/state"
        const val PATH_CMD = "/climapilot/cmd"
        const val CMD_POWER = "power"
        const val CMD_TEMP_UP = "temp_up"
        const val CMD_TEMP_DOWN = "temp_down"
        const val CMD_MODE = "mode"
    }
}
