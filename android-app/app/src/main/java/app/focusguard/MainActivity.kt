package app.focusguard

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            startForegroundService(svc(GuardService.ACTION_START)); refresh()
        }
        findViewById<Button>(R.id.btnBreak30s).setOnClickListener {
            startForegroundService(breakSvc(30_000L))
            refresh()
        }
        findViewById<Button>(R.id.btnBreak10m).setOnClickListener {
            startForegroundService(breakSvc(10 * 60_000L))
            refresh()
        }
        findViewById<Button>(R.id.btnBreak1h).setOnClickListener {
            startForegroundService(breakSvc(60 * 60_000L))
            refresh()
        }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            startForegroundService(svc(GuardService.ACTION_STOP)); refresh()
        }
    }

    private fun svc(action: String) = Intent(this, GuardService::class.java).setAction(action)

    private fun breakSvc(ms: Long) =
        svc(GuardService.ACTION_BREAK).putExtra(GuardService.EXTRA_BREAK_MS, ms)

    private fun refresh() {
        val tv = findViewById<TextView>(R.id.status)
        tv.text = if (GrayscaleController.hasPermission(this)) {
            "✅ 权限已授予。守护：" + if (GuardService.running) "运行中" else "未运行"
        } else {
            "⚠️ 还没授权。用电脑连一次，跑这条（只此一次）：\n\n" +
                "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS\n\n" +
                "之后永远不用再连电脑。"
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }
}
