package com.climapilot.free

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.climapilot.free.widget.AcWidgetProvider
import com.climapilot.free.widget.WidgetRepo

/**
 * EN: Quick Settings tile that cycles the AC operating mode (Auto → Cool → Dry → Heat → Fan) straight
 *     from the shade, reusing the home-screen widget's robust offline control path. The tile label
 *     shows the current mode; tapping advances to the next one — no app, no cloud.
 * DE: Schnelleinstellungen-Kachel, die den Betriebsmodus der Klima durchschaltet (Auto → Kühlen →
 *     Trocknen → Heizen → Lüften) direkt aus der Statusleiste, über den robusten Offline-Steuerpfad des
 *     Homescreen-Widgets. Das Kachel-Label zeigt den aktuellen Modus; Tippen schaltet weiter — ohne
 *     App, ohne Cloud.
 */
class ModeTileService : TileService() {

    override fun onStartListening() = refresh()

    override fun onClick() {
        val ctx = applicationContext
        val snap = WidgetRepo.load(ctx)
        if (snap.device == null) {
            startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        // EN: Reuse the widget's offline mode-cycle broadcast (handles the LAN command + optimistic state).
        // DE: Den Offline-Modus-Durchschalt-Broadcast des Widgets wiederverwenden (LAN-Befehl + optimistischer Zustand).
        ctx.sendBroadcast(Intent(ctx, AcWidgetProvider::class.java).setAction(AcWidgetProvider.ACTION_MODE))
        // EN: Optimistically reflect the next mode on the tile. DE: Den nächsten Modus optimistisch auf der Kachel zeigen.
        val next = if (snap.mode in 1..4) snap.mode + 1 else 1
        setTile(next, snap.powerOn)
    }

    private fun refresh() {
        val snap = WidgetRepo.load(applicationContext)
        if (snap.device == null) {
            val tile = qsTile ?: return
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = getString(R.string.tile_mode_label)
            tile.updateTile()
        } else {
            setTile(snap.mode, snap.powerOn)
        }
    }

    private fun setTile(mode: Int, powerOn: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (powerOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(AcWidgetProvider.modeNameRes(mode))
        tile.icon = Icon.createWithResource(this, R.drawable.ic_w_mode)
        tile.updateTile()
    }
}
