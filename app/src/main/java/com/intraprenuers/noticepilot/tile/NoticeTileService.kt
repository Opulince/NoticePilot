package com.intraprenuers.noticepilot.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.intraprenuers.noticepilot.MainActivity
import com.intraprenuers.noticepilot.capture.CaptureActions

/** Quick Settings tile: opens NoticePilot, which asks for screen-capture consent and reads the screen. */
class NoticeTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.label = "Scan Notice"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = "NoticePilot"
        tile.state = Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        // On a locked phone, ask the user to unlock first (the capture needs their screen anyway).
        if (isLocked) unlockAndRun { launch() } else launch()
    }

    private fun launch() {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(CaptureActions.ACTION_TILE_CAPTURE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
