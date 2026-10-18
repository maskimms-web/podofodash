package com.golf.candiagnostic

import android.app.Activity
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.view.View
import android.widget.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {
    private lateinit var output: TextView
    private lateinit var address: TextView
    private val log = StringBuilder()
    private val lock = Any()
    private val uiHandler = Handler(Looper.getMainLooper())
    private val updatePending = AtomicBoolean(false)
    private val scanning = AtomicBoolean(false)
    private var receiver: BroadcastReceiver? = null
    private var server: ServerSocket? = null
    private var serverThread: Thread? = null
    private val running = AtomicBoolean(false)
    private var token = ""
    private val actions = listOf(
        "com.microntek.CANBUS", "com.microntek.canbus", "com.microntek.carinfo",
        "com.syu.ms", "com.syu.canbus", "com.syu.carinfo",
        "com.ts.can", "com.ts.main.CAN_INFO", "com.android.car.CAR_INFO"
    ) // Unverified broadcast hypotheses; not Raise protocol definitions.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 12, 18, 12)
            setBackgroundColor(Color.rgb(10, 17, 28))
        }
        root.addView(TextView(this).apply {
            text = "GOLF CAN DIAGNOSTIC v0.2.1"
            textSize = 21f; setTextColor(Color.rgb(60, 174, 255))
        })
        root.addView(TextView(this).apply {
            text = "Raise CAN Pro • пасивна діагностика • Wi-Fi звіт"
            textSize = 13f; setTextColor(Color.LTGRAY)
        })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun button(text: String, click: () -> Unit) {
            row.addView(Button(this).apply {
                this.text = text; textSize = 11f; setOnClickListener { click() }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        button("СКАНУВАТИ") { scan() }
        button("СЛУХАТИ") { startListening() }
        button("Wi-Fi СТАРТ") { startWeb() }
        button("СТОП") { stopWeb() }
        root.addView(row)
        address = TextView(this).apply {
            textSize = 14f; setTextColor(Color.rgb(110, 220, 255))
            setTextIsSelectable(true)
            text = "Wi-Fi сервер вимкнено"
        }
        root.addView(address)
        output = TextView(this).apply {
            textSize = 12f; typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.WHITE); setTextIsSelectable(true)
        }
        root.addView(ScrollView(this).apply { addView(output) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        append("Запущено. СКАНУВАТИ → СЛУХАТИ → Wi-Fi СТАРТ. CAN-команди не надсилаються.")
    }

    private fun append(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        synchronized(lock) {
            log.append("[").append(time).append("] ").append(message).append('\n')
            if (log.length > 200_000) log.delete(0, log.length - 150_000)
        }
        if (updatePending.compareAndSet(false, true)) {
            uiHandler.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    output.text = synchronized(lock) { log.toString().takeLast(18_000) }
                }
                updatePending.set(false)
            }, 250)
        }
    }

    private fun scan() {
        if (!scanning.compareAndSet(false, true)) { append("Scan already running"); return }
        Thread {
            try { performScan() }
            catch (t: Throwable) {
                append("SCAN FAILED: ${t.javaClass.name}: ${t.message}")
                append(t.stackTrace.take(8).joinToString("\n"))
            } finally { scanning.set(false); append("Scan finished") }
        }.start()
    }

    private fun performScan() {
        append("--- SYSTEM ---")
        append("Manufacturer=${Build.MANUFACTURER}; model=${Build.MODEL}; device=${Build.DEVICE}")
        append("SDK=${Build.VERSION.SDK_INT}; display=${Build.DISPLAY}")
        val names = listOf("com.xygala.canbus", "com.xygala.pvcanset", "com.kyhero.car.myhost")
        for (name in names) {
            try {
                @Suppress("DEPRECATION")
                val p = packageManager.getPackageInfo(name,
                    PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS)
                append("--- PACKAGE $name v${p.versionName} ---")
                p.activities?.forEach { append("Activity ${it.name}; exported=${it.exported}") }
                p.services?.forEach { append("Service ${it.name}; exported=${it.exported}; permission=${it.permission ?: "none"}") }
                p.receivers?.forEach { append("Receiver ${it.name}; exported=${it.exported}") }
                p.providers?.forEach { append("Provider ${it.name}; exported=${it.exported}") }
            } catch (e: PackageManager.NameNotFoundException) {
                append("Package $name not visible/installed")
            } catch (e: Exception) { append("Package $name: ${e.javaClass.simpleName}: ${e.message}") }
        }
        append("--- CAN/MCU PACKAGE CANDIDATES ---")
        try {
            @Suppress("DEPRECATION")
            val pkgs = packageManager.getInstalledPackages(0)
            val matches = pkgs.filter {
                val s = it.packageName.lowercase(Locale.ROOT)
                listOf("can", "mcu", "raise", "carinfo", "vehicle", "kyhero").any(s::contains)
            }
            matches.forEach { append("${it.packageName} | ${it.versionName}") }
            append("Visible packages=${pkgs.size}; candidates=${matches.size}")
        } catch (e: Throwable) { append("Package scan: ${e.javaClass.simpleName}: ${e.message}") }
    }

    private fun startListening() {
        if (receiver != null) { append("Listener already active"); return }
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val keys = try { intent.extras?.keySet()?.take(20)?.joinToString(",") ?: "none" }
                           catch (_: Exception) { "unreadable" }
                append("BROADCAST action=${intent.action}; extras keys=$keys")
            }
        }
        try {
            val filter = IntentFilter().apply { actions.forEach(::addAction) }
            @Suppress("DEPRECATION")
            registerReceiver(r, filter)
            receiver = r
            append("Listening for ${actions.size} hypothetical broadcasts. No events does not mean CAN is absent.")
        } catch (e: Exception) { append("Listener error: ${e.message}") }
    }

    private fun ips(): List<String> {
        val result = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return result
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val ip = addresses.nextElement()
                    if (ip is java.net.Inet4Address && ip.isSiteLocalAddress) result.add(ip.hostAddress ?: "")
                }
            }
        } catch (_: Exception) {}
        return result.distinct()
    }

    private fun startWeb() {
        if (running.get()) { append("Wi-Fi server already running"); return }
        try {
            val bytes = ByteArray(12).also { SecureRandom().nextBytes(it) }
            token = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
            val socket = ServerSocket(8080)
            socket.soTimeout = 1000
            server = socket
            running.set(true)
            val addresses = ips()
            address.text = if (addresses.isEmpty()) "Немає локальної IP-адреси. Підключи Wi-Fi." else
                "Safari (та сама мережа):\n" + addresses.joinToString("\n") { "http://$it:8080/$token" }
            append("Wi-Fi report server started on port 8080. Only share the address with your iPhone.")
            serverThread = Thread {
                while (running.get()) {
                    try {
                        val client = socket.accept()
                        handleClient(client)
                    } catch (_: java.net.SocketTimeoutException) {
                    } catch (e: SocketException) { if (running.get()) append("Web socket: ${e.message}") }
                    catch (e: Exception) { if (running.get()) append("Web error: ${e.message}") }
                }
            }.apply { isDaemon = true; start() }
        } catch (e: Exception) { append("Web start failed: ${e.message}"); running.set(false) }
    }

    private fun handleClient(client: Socket) {
        client.use { s ->
            s.soTimeout = 2500
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.US_ASCII))
            val request = reader.readLine() ?: return
            val parts = request.split(" ")
            val route = parts.getOrNull(1) ?: ""
            val method = parts.getOrNull(0) ?: ""
            // The token is unguessable and is required for both routes. GET only.
            val valid = method == "GET" && (route == "/$token" || route == "/$token/report.txt")
            val body: ByteArray
            val headers: String
            if (!valid) {
                body = "Not found".toByteArray(Charsets.UTF_8)
                headers = "HTTP/1.1 404 Not Found\r\nContent-Type: text/plain; charset=utf-8\r\n"
            } else if (route.endsWith("/report.txt")) {
                body = synchronized(lock) { log.toString().toByteArray(Charsets.UTF_8) }
                headers = "HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Disposition: attachment; filename=GolfCanDiagnostic-report.txt\r\n"
            } else {
                val html = """<!doctype html><html lang="uk"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Golf CAN Diagnostic</title><body style="font:18px sans-serif;background:#0a111c;color:white;padding:20px"><h2>Golf CAN Diagnostic v0.2</h2><p>Локальний діагностичний звіт</p><p><a style="color:#59caff" href="/$token/report.txt">Завантажити TXT-звіт</a></p><p>Після завантаження натисни СТОП на магнітолі.</p></body></html>"""
                body = html.toByteArray(Charsets.UTF_8)
                headers = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n"
            }
            val out = s.getOutputStream()
            out.write((headers + "Cache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII))
            out.write(body)
            out.flush()
        }
    }

    private fun stopWeb() {
        running.set(false)
        try { server?.close() } catch (_: Exception) {}
        server = null
        token = ""
        address.text = "Wi-Fi сервер вимкнено"
        append("Wi-Fi report server stopped")
    }

    override fun onDestroy() {
        stopWeb()
        receiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        receiver = null
        super.onDestroy()
    }
}
