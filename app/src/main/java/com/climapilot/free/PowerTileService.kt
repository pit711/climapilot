package com.climapilot.free

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.climapilot.free.widget.AcWidgetProvider
import com.climapilot.free.widget.WidgetRepo

/**
 * EN: Quick Settings tile to toggle the AC power straight from the notification shade. It reuses the
 *     widget's robust offline control path: tapping sends the same broadcast the home-screen widget
 *     uses, which connects with the cached token and toggles power — no app, no cloud, no internet.
 *     The tile reflects the last known power state stored in [WidgetRepo].
 * DE: Schnelleinstellungen-Kachel, um die Klima direkt aus der Statusleiste ein-/auszuschalten. Sie
 *     nutzt den robusten Offline-Steuerpfad des Widgets wieder: Tippen sendet denselben Broadcast wie
 *     das Homescreen-Widget, der mit dem gecachten Token verbindet und Ein/Aus umschaltet — ohne App,
 *     ohne Cloud, ohne Internet. Die Kachel spiegelt den letzten bekannten Zustand aus [WidgetRepo].
 */
class PowerTileService : TileService() {

    override fun onStartListening() {
        refresh()
    }

    override fun onClick() {
        val ctx = applicationContext
        val snap = WidgetRepo.load(ctx)
        if (snap.device == null) {
            // EN: Nothing to control yet — open the app to connect first. DE: Noch nichts zu steuern — die App zum Verbinden öffnen.
            startActivity(
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        // EN: Reuse the widget's offline power toggle (handles the LAN command + optimistic state).
        // DE: Den Offline-Ein/Aus-Schalter des Widgets wiederverwenden (erledigt LAN-Befehl + optimistischen Zustand).
        ctx.sendBroadcast(
            Intent(ctx, AcWidgetProvider::class.java).setAction(AcWidgetProvider.ACTION_POWER)
        )
        setTile(active = !snap.powerOn, name = snap.name)
    }

    private fun refresh() {
        val snap = WidgetRepo.load(applicationContext)
        if (snap.device == null) {
            val tile = qsTile ?: return
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = getString(R.string.app_name)
            tile.updateTile()
        } else {
            setTile(active = snap.powerOn, name = snap.name)
        }
    }

    private fun setTile(active: Boolean, name: String) {
        val tile = qsTile ?: return
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = name.ifBlank { getString(R.string.tile_label) }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_w_power)
        tile.updateTile()
    }
}
