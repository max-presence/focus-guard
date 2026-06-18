package app.focusguard

import android.content.Context
import android.os.SystemClock

object GuardState {
    private const val PREFS = "guard"
    private const val KEY_GUARD_ENABLED = "guard_enabled"
    private const val KEY_BREAK_UNTIL = "break_until_elapsed"

    fun enforce(ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_GUARD_ENABLED, false)) return false

        val breakUntil = prefs.getLong(KEY_BREAK_UNTIL, 0L)
        val inBreak = breakUntil - SystemClock.elapsedRealtime() > 0L
        if (inBreak) {
            if (GrayscaleController.isOn(ctx)) {
                GrayscaleController.setGrayscale(ctx, false)
            }
            return true
        }

        if (breakUntil != 0L) {
            prefs.edit().remove(KEY_BREAK_UNTIL).apply()
        }
        if (!GrayscaleController.isOn(ctx)) {
            GrayscaleController.setGrayscale(ctx, true)
        }
        return false
    }
}

