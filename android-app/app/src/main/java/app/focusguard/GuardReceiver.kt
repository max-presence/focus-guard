package app.focusguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class GuardReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        GuardState.enforce(context)
        if (GrayscaleController.hasPermission(context)) {
            context.startForegroundService(
                Intent(context, GuardService::class.java)
                    .setAction(intent?.action ?: GuardService.ACTION_TICK)
            )
        }
    }
}
