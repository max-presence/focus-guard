package app.focusguard

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** 下拉快捷磁贴：一键开/关黑白守护。 */
class GrayscaleTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val action = if (GuardService.running) GuardService.ACTION_STOP else GuardService.ACTION_START
        val i = Intent(this, GuardService::class.java).setAction(action)
        if (GuardService.running) startService(i) else startForegroundService(i)
        updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    private fun updateTile() {
        qsTile?.apply {
            state = if (GuardService.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "黑白守护"
            updateTile()
        }
    }
}
