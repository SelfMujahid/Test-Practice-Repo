package com.practice.cryptoapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONArray
import java.net.URL
import java.text.NumberFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CryptoAdapter

    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val activityScope = CoroutineScope(Dispatchers.Main + Job())

    private val rankedList = mutableListOf<CryptoItem>()
    private val symbolMap = HashMap<String, CryptoItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.cryptoRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = CryptoAdapter(emptyList())
        recyclerView.adapter = adapter

        // Step 1: Binance Data Fetch Karein Aur Market Cap Rank Banayein
        fetchMarketCapRankings()
    }

    private fun fetchMarketCapRankings() {
        activityScope.launch(Dispatchers.IO) {
            try {
                // Binance 24hr Ticker Data
                val jsonString = URL("https://api.binance.com/api/v3/ticker/24hr").readText()
                val jsonArray = JSONArray(jsonString)

                val tempList = mutableListOf<CryptoItem>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val symbol = obj.getString("symbol")
                    val lastPrice = obj.getDouble("lastPrice")
                    val volume = obj.getDouble("volume") // Base Asset Volume

                    if (symbol.endsWith("USDT") && lastPrice > 0) {
                        val cleanSymbol = symbol.replace("USDT", " / USDT")
                        
                        // Market Cap Estimation (Price * Volume Proxy for Binance cap sorting)
                        val estimatedMarketCap = lastPrice * volume

                        val formattedPrice = formatPrice(lastPrice)

                        tempList.add(
                            CryptoItem(
                                rank = 0,
                                symbol = cleanSymbol,
                                price = formattedPrice,
                                marketCap = estimatedMarketCap
                            )
                        )
                    }
                }

                // Market Cap ke hisaab se Sort (Highest Market Cap Pehle)
                tempList.sortByDescending { it.marketCap }

                // Assign Rank #1, #2, #3...
                tempList.forEachIndexed { index, item ->
                    item.rank = index + 1
                    val rawSymbol = item.symbol.replace(" / USDT", "USDT")
                    symbolMap[rawSymbol] = item
                }

                rankedList.clear()
                rankedList.addAll(tempList)

                withContext(Dispatchers.Main) {
                    adapter.updateData(rankedList.toList())
                    // Step 2: Live Prices WebSocket Stream
                    connectBinanceWebSocket()
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun connectBinanceWebSocket() {
        val request = Request.Builder()
            .url("wss://stream.binance.com:9443/ws/!miniTicker@arr")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                parseWebSocketStream(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                t.printStackTrace()
            }
        })
    }

    private fun parseWebSocketStream(jsonText: String) {
        try {
            val jsonArray = JSONArray(jsonText)
            var priceUpdated = false

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val symbol = obj.getString("s") // e.g. BTCUSDT
                val closePrice = obj.getDouble("c")

                symbolMap[symbol]?.let { item ->
                    val newFormattedPrice = formatPrice(closePrice)

                    if (item.price != newFormattedPrice) {
                        item.price = newFormattedPrice
                        priceUpdated = true
                    }
                }
            }

            if (priceUpdated) {
                runOnUiThread {
                    adapter.updateData(rankedList.toList())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Number Formatting Function (66060.02 -> 66,060.02 ya 66,060)
    private fun formatPrice(price: Double): String {
        val formatter = NumberFormat.getNumberInstance(Locale.US)
        return if (price >= 1.0) {
            formatter.maximumFractionDigits = 2
            formatter.minimumFractionDigits = 2
            formatter.format(price)
        } else {
            // Chote coins ke liye decimal accuracy
            formatter.maximumFractionDigits = 6
            formatter.minimumFractionDigits = 2
            formatter.format(price)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
        webSocket?.close(1000, "App closed")
    }
}
