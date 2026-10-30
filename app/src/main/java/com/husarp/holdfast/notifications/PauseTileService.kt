package com.husarp.holdfast.notifications

import android.content.Intent
import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.TileService
import com.husarp.holdfast.MainActivity

class PauseTileService : TileService() {
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).putExtra("pause", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 91, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        // The PendingIntent overload is only available from Android 14.
        else { @Suppress("DEPRECATION") startActivityAndCollapse(intent) }
    }
}
