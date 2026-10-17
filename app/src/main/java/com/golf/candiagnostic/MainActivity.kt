package com.golf.candiagnostic

import android.app.Activity
import android.os.Bundle
import android.os.Build
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.view.View
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var output: TextView
    private val log = StringBuilder()
    private var receiver: BroadcastReceiver? = null
    private val actions = listOf(
        "com.microntek.CANBUS", "com.microntek.canbus", "com.microntek.carinfo",
        "com.syu.ms", "com.syu.canbus", "com.syu.carinfo",
        "com.ts.can", "com.ts.main.CAN_INFO",
        "com.android.car.CAR_INFO"
    ) // Hypotheses only: NOT verified Raise protocols.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(22, 16, 22, 16); setBackgroundColor(Color.rgb(10, 17, 28))
        }
        val heading = TextView(this).apply { text = "GOLF CAN DIAGNOSTIC  v0.1"; textSize = 23f; setTextColor(Color.rgb(60, 174, 255)) }
        root.addView(heading)
        val sub = TextView(this).apply { text = "Android 11 • Raise CAN Pro • пасивна діагностика"; textSize = 14f; setTextColor(Color.LTGRAY) }
        root.addView(sub)
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun addButton(label: String, action: () -> Unit) {
            bar.addView(Button(this).apply { text = label; textSize = 12f; setOnClickListener { action() } }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        addButton("СКАНУВАТИ") { scan() }
        addButton("СЛУХАТИ") { startListening() }
        addButton("ЕКСПОРТ") { export() }
        root.addView(bar)
        output = TextView(this).apply { textSize = 13f; typeface = android.graphics.Typeface.MONOSPACE; setTextColor(Color.WHITE); setTextIsSelectable(true) }
        val scroll = ScrollView(this).apply { addView(output) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        append("Запущено. Натисни СКАНУВАТИ, потім СЛУХАТИ.\nЖодних CAN-команд застосунок не надсилає.")
    }

    private fun append(line: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        log.append("[").append(timestamp).append("] ").append(line).append('\n')
        output.text = log.toString().takeLast(28000)
    }

    private fun scan() {
        append("--- SYSTEM ---")
        append("Manufacturer=${Build.MANUFACTURER}, model=${Build.MODEL}, device=${Build.DEVICE}")
        append("SDK=${Build.VERSION.SDK_INT}, display=${Build.DISPLAY}")
        append("--- INSTALLED PACKAGES (CAN/MCU candidates) ---")
        try {
            @Suppress("DEPRECATION")
            val apps = packageManager.getInstalledPackages(0)
            val found = apps.filter { p ->
                val s = p.packageName.lowercase(Locale.ROOT)
                listOf("can", "mcu", "raise", "carinfo", "vehicle", "syu", "microntek", "tsmain", "carservice").any { s.contains(it) }
            }.sortedBy { it.packageName }
            if (found.isEmpty()) append("No candidate packages visible (package visibility may restrict results).")
            found.forEach { p ->
                val system = (p.applicationInfo?.flags ?: 0) and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
                append("${p.packageName} | version=${p.versionName ?: "?"} | system=$system")
            }
            append("Visible packages: ${apps.size}; candidates: ${found.size}")
        } catch (e: Exception) { append("Package scan error: ${e.javaClass.simpleName}: ${e.message}") }
    }

    private fun startListening() {
        if (receiver != null) { append("Listener already active"); return }
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val extras = intent.extras
                val keys = try { extras?.keySet()?.take(20)?.joinToString(",") ?: "none" } catch (_: Exception) { "unreadable" }
                append("BROADCAST action=${intent.action}; extras keys=$keys")
            }
        }
        try {
            val filter = IntentFilter().apply { actions.forEach { addAction(it) } }
            @Suppress("DEPRECATION")
            registerReceiver(r, filter)
            receiver = r
            append("Listening for ${actions.size} hypothetical broadcast actions. No events does NOT mean CAN is absent.")
        } catch (e: Exception) { append("Listener error: ${e.message}") }
    }

    private fun export() {
        try {
            // Share the diagnostic log as text; no file provider is needed.
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Golf CAN Diagnostic log")
                putExtra(Intent.EXTRA_TEXT, log.toString())
            }
            startActivity(Intent.createChooser(intent, "Надіслати лог"))
        } catch (e: Exception) { append("Export error: ${e.message}") }
    }

    override fun onDestroy() {
        receiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        receiver = null
        super.onDestroy()
    }
}
