package com.practice.cryptoapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONArray
import java.net.URL

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

        // Step 1: Binance Trading Volume ke hisaab se Ranking Load Karein
        fetchBinanceRankings()
    }

    private fun fetchBinanceRankings() {
        activityScope.launch(Dispatchers.IO) {
            try {
                // Binance 24hr ticker endpoint (returns volume & prices for all pairs)
                val jsonString = URL("https://api.binance.com/api/v3/ticker/24hr").readText()
                val jsonArray = JSONArray(jsonString)

                val tempList = mutableListOf<CryptoItem>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val symbol = obj.getString("symbol")
                    val quoteVolume = obj.getDouble("quoteVolume") // 24H Volume in USDT
                    val lastPrice = obj.getString("lastPrice")

                    if (symbol.endsWith("USDT")) {
                        val cleanSymbol = symbol.replace("USDT", " / USDT")
                        val formattedPrice = lastPrice.toDoubleOrNull()?.let {
                            String.format("%.4f", it)
                        } ?: lastPrice

                        tempList.add(CryptoItem(0, cleanSymbol, formattedPrice, quoteVolume))
                    }
                }

                // Binance Par Highest Trading Volume wale Coins Top Par Rank Hoonge
                tempList.sortByDescending { it.volume }

                // Assign Ranking #1, #2, #3...
                tempList.forEachIndexed { index, item ->
                    item.rank = index + 1
                    val rawSymbol = item.symbol.replace(" / USDT", "USDT")
                    symbolMap[rawSymbol] = item
                }

                rankedList.clear()
                rankedList.addAll(tempList)

                withContext(Dispatchers.Main) {
                    adapter.updateData(rankedList.toList())
                    // Step 2: Live Prices Stream karne ke liye WebSocket Tunnel Start Karein
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
                val closePrice = obj.getString("c")

                symbolMap[symbol]?.let { item ->
                    val formattedPrice = closePrice.toDoubleOrNull()?.let {
                        String.format("%.4f", it)
                    } ?: closePrice

                    if (item.price != formattedPrice) {
                        item.price = formattedPrice
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

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
        webSocket?.close(1000, "App closed")
    }
}
