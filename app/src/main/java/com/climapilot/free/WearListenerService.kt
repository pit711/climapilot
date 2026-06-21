package com.climapilot.free

import android.content.Intent
import com.climapilot.free.widget.AcWidgetProvider
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * EN: Receives control commands from the watch app over the Wearable Data Layer and routes them through
 *     the home-screen widget's robust offline control path — so a tap on the watch connects with the
 *     cached token and drives the AC over the LAN, no cloud. The phone does the LAN work; the watch is a
 *     thin remote.
 * DE: Empfängt Steuerbefehle der Watch-App über den Wearable-Data-Layer und leitet sie über den robusten
 *     Offline-Steuerpfad des Homescreen-Widgets — ein Tippen auf der Uhr verbindet sich also mit dem
 *     gecachten Token und steuert die Klima über das LAN, ohne Cloud. Das Phone macht die LAN-Arbeit, die
 *     Uhr ist eine dünne Fernbedienung.
 */
class WearListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != "/climapilot/cmd") return
        val action = when (String(event.data)) {
            "power" -> AcWidgetProvider.ACTION_POWER
            "temp_up" -> AcWidgetProvider.ACTION_TEMP_UP
            "temp_down" -> AcWidgetProvider.ACTION_TEMP_DOWN
            "mode" -> AcWidgetProvider.ACTION_MODE
            else -> return
        }
        sendBroadcast(Intent(this, AcWidgetProvider::class.java).setAction(action))
    }
}
