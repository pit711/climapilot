package com.climapilot.free

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/**
 * EN: Pushes the current AC state (power, target temp) to the watch over the Wearable Data Layer, so the
 *     watch face shows live values. Best-effort: if no watch is paired the put simply has no effect.
 * DE: Schiebt den aktuellen Klima-Zustand (Ein/Aus, Zieltemperatur) über den Wearable-Data-Layer an die
 *     Uhr, damit die Watch-App Live-Werte zeigt. Best-Effort: ist keine Uhr gekoppelt, läuft das Put
 *     einfach ins Leere.
 */
object WearSync {
    private const val PATH_STATE = "/climapilot/state"

    fun publish(ctx: Context, present: Boolean, powerOn: Boolean, tempC: Double) {
        val req = PutDataMapRequest.create(PATH_STATE).apply {
            dataMap.putBoolean("present", present)
            dataMap.putBoolean("power", powerOn)
            dataMap.putDouble("temp", tempC)
            // EN: Changing timestamp so the data layer treats each publish as a real change. DE: Wechselnder Zeitstempel, damit der Data-Layer jedes Publish als echte Änderung behandelt.
            dataMap.putLong("ts", System.currentTimeMillis())
        }
        runCatching {
            Wearable.getDataClient(ctx.applicationContext)
                .putDataItem(req.asPutDataRequest().setUrgent())
        }
    }
}
