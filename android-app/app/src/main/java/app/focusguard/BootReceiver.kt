package app.focusguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机自启：有权限就把守护拉起来。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED &&
            GrayscaleController.hasPermission(context)
        ) {
            context.startForegroundService(
                Intent(context, GuardService::class.java).setAction(GuardService.ACTION_START)
            )
        }
    }
}
