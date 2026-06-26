package com.crypto.exchangeapp

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import okhttp3.*
import java.util.concurrent.TimeUnit

class FutureTradingActivity : AppCompatActivity() {

    private var tvStatus: TextView? = null
    private var tvMetrics: TextView? = null
    private var tvDataList: TextView? = null
    private var webSocket: WebSocket? = null
    private lateinit var client: OkHttpClient
    
    // UI updates ko optimize karne aur bandwidth bachane ke liye memory state
    private val coinsMap = LinkedHashMap<String, CryptoCoinState>()
    private var updateCounter = 0
    private var metricsJob: Job? = null

    // Helper data structure coins ko track karne ke liye
    data class CryptoCoinState(
        val symbol: String,
        var price: Double = 0.0,
        var changePercent: Double = 0.0,
        var high: Double = 0.0,
        var low: Double = 0.0,
        var volume: Double = 0.0
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android UI Setup - Pure list design optimized for multi-tickers
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0E11"))
            setPadding(20, 40, 20, 20)
        }

        tvStatus = TextView(this).apply {
            text = "⏳ Fetching Initial Layout..."
            textSize = 15f
            setTextColor(Color.parseColor("#F0B90B"))
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 10)
        }
        rootLayout.addView(tvStatus)

        tvMetrics = TextView(this).apply {
            text = "Coins Trace: 0 | Updates/sec: 0"
            textSize = 12f
            setTextColor(Color.parseColor("#848E9C"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        rootLayout.addView(tvMetrics)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        tvDataList = TextView(this).apply {
            text = "Initializing stream network..."
            textSize = 13f
            fontFamily = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.WHITE)
        }
        
        scrollView.addView(tvDataList)
        rootLayout.addView(scrollView)
        setContentView(rootLayout)

        // OkHttpClient Builder configured for global stream arrays
        client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()

        // Pehle bootstrap data load hoga phir socket chalega
        bootstrapCoinsList()
        startMetricsTimer()
    }

    private fun bootstrapCoinsList() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Futures REST API se data lekar base list banana jaisa web mein kiya
                val request = Request.Builder()
                    .url("https://fapi.binance.com/fapi/v1/ticker/24hr")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw java.io.IOException("Unexpected code $response")
                    
                    val bodyString = response.body?.string() ?: ""
                    val jsonArray = JsonParser.parseString(bodyString).asJsonArray
                    
                    val tempList = mutableListOf<CryptoCoinState>()
                    for (element in jsonArray) {
                        val obj = element.asJsonObject
                        val sym = obj.get("symbol").asString
                        
                        // Sirf USDT pairs filter kiye
                        if (sym.endsWith("USDT")) {
                            tempList.add(
                                CryptoCoinState(
                                    symbol = sym,
                                    price = obj.get("lastPrice").asString.toDouble(),
                                    changePercent = obj.get("priceChangePercent").asString.toDouble(),
                                    high = obj.get("highPrice").asString.toDouble(),
                                    low = obj.get("lowPrice").asString.toDouble(),
                                    volume = obj.get("quoteVolume").asString.toDouble()
                                )
                            )
                        }
                    }

                    // Volume par sort karke top 400 nikal liye
                    tempList.sortByDescending { it.volume }
                    val top400 = tempList.take(400)

                    withContext(Dispatchers.Main) {
                        coinsMap.clear()
                        for (coin in top400) {
                            coinsMap[coin.symbol] = coin
                        }
                        tvStatus?.text = "⌛ Connecting Pipeline Channel..."
                        connectWebSocketEngine()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvStatus?.text = "❌ Sync Failure: ${e.localizedMessage}"
                    tvStatus?.setTextColor(Color.parseColor("#F6465D"))
                }
            }
        }
    }

    private fun connectWebSocketEngine() {
        webSocket?.close(1000, "Reset")

        // Multi-array stream endpoint jise aapne web code mein test kiya
        val request = Request.Builder()
            .url("wss://fstream.binance.com/ws/!ticker@arr")
            .header("User-Agent", "Mozilla/5.0")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                lifecycleScope.launch(Dispatchers.Main) {
                    tvStatus?.text = "🟢 Live Connected — Processing Stream"
                    tvStatus?.setTextColor(Color.parseColor("#0ECB81"))
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                updateCounter++
                
                // Background execution to prevent main-thread/UI skipping frames
                lifecycleScope.launch(Dispatchers.Default) {
                    try {
                        val jsonArray = JsonParser.parseString(text).asJsonArray
                        var hasUpdates = false

                        for (element in jsonArray) {
                            val u = element.asJsonObject
                            val sym = u.get("s").asString

                            if (coinsMap.containsKey(sym)) {
                                val coin = coinsMap[sym]!!
                                coin.price = u.get("c").asString.toDouble()
                                coin.changePercent = u.get("P").asString.toDouble()
                                coin.high = u.get("h").asString.toDouble()
                                coin.low = u.get("l").asString.toDouble()
                                coin.volume = u.get("q").asString.toDouble()
                                hasUpdates = true
                            }
                        }

                        if (hasUpdates) {
                            // Render processing loop equivalent to requestAnimationFrame
                            val displayBuilder = StringBuilder()
                            val renderList = synchronized(coinsMap) { coinsMap.values.toList() }
                            
                            // Top 15 coins UI par dikhane ke liye render string builder
                            renderList.take(15).forEachIndexed { index, coin ->
                                val arrow = if (coin.changePercent >= 0) "▲" else "▼"
                                displayBuilder.append(
                                    String.format(
                                        "#%d %-9s Price: $%-10.2f  %s %-6.2f%%\nHigh: $%-8.2f Low: $%-8.2f Vol: %s\n---------------------------------------------\n",
                                        index + 1, coin.symbol, coin.price, arrow, coin.changePercent, coin.high, coin.low, formatVolume(coin.volume)
                                    )
                                )
                            }

                            withContext(Dispatchers.Main) {
                                tvDataList?.text = displayBuilder.toString()
                            }
                        }
                    } catch (e: Exception) {
                        // Suppressing parsing noise to maintain continuous rendering
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                lifecycleScope.launch(Dispatchers.Main) {
                    tvStatus?.text = "⚠️ Network Glitch. Reconnecting..."
                    tvStatus?.setTextColor(Color.parseColor("#F6465D"))
                    delay(3000)
                    connectWebSocketEngine()
                }
            }
        })
    }

    private fun startMetricsTimer() {
        metricsJob = lifecycleScope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(1000)
                tvMetrics?.text = "Coins Tracked: ${coinsMap.size} | Updates/sec: $updateCounter"
                updateCounter = 0
            }
        }
    }

    private fun formatVolume(v: Double): String {
        return when {
            v >= 1e9 -> String.format("%.2fB", v / 1e9)
            v >= 1e6 -> String.format("%.2fM", v / 1e6)
            v >= 1e3 -> String.format("%.2fK", v / 1e3)
            else -> String.format("%.2f", v)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        metricsJob?.cancel()
        webSocket?.close(1000, "App Destroyed")
    }
}
