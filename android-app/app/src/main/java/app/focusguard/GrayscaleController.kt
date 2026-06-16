package app.focusguard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

/**
 * 真灰度 = 系统 secure setting `accessibility_display_daltonizer`。
 * 写它需要 WRITE_SECURE_SETTINGS（signature 级），只能 adb 一次性授予后由本 app 长期使用。
 */
object GrayscaleController {
    private const val KEY_ENABLED = "accessibility_display_daltonizer_enabled"
    private const val KEY_MODE = "accessibility_display_daltonizer"
    private const val MODE_MONOCHROME = 0 // 0 = MONOCHROMACY 全灰；vivo 若不同需真机调

    val enabledUri: Uri = Settings.Secure.getUriFor(KEY_ENABLED)

    fun hasPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    fun isOn(ctx: Context): Boolean =
        Settings.Secure.getInt(ctx.contentResolver, KEY_ENABLED, 0) == 1

    /** @return 是否成功（无权限时返回 false，不抛异常） */
    fun setGrayscale(ctx: Context, on: Boolean): Boolean = try {
        val cr = ctx.contentResolver
        if (on) {
            Settings.Secure.putInt(cr, KEY_MODE, MODE_MONOCHROME)
            Settings.Secure.putInt(cr, KEY_ENABLED, 1)
        } else {
            Settings.Secure.putInt(cr, KEY_ENABLED, 0)
        }
        true
    } catch (e: SecurityException) {
        false
    }
}
