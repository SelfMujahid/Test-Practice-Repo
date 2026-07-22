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

        fetchMarketCapRankings()
    }

    private fun fetchMarketCapRankings() {
        activityScope.launch(Dispatchers.IO) {
            try {
                val url = "https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=250&page=1&sparkline=false"
                val jsonString = URL(url).readText()
                val jsonArray = JSONArray(jsonString)

                val tempList = mutableListOf<CryptoItem>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val rawSymbol = obj.getString("symbol").uppercase()
                    val currentPrice = obj.optDouble("current_price", 0.0)
                    val rank = obj.optInt("market_cap_rank", i + 1)
                    val marketCap = obj.optDouble("market_cap", 0.0)

                    val cleanSymbol = "$rawSymbol / USDT"
                    val formattedPrice = formatPrice(currentPrice)

                    val item = CryptoItem(
                        rank = rank,
                        symbol = cleanSymbol,
                        price = formattedPrice,
                        marketCap = marketCap,
                        rawPrice = currentPrice,
                        priceState = PriceState.NEUTRAL
                    )

                    tempList.add(item)
                    symbolMap["${rawSymbol}USDT"] = item
                }

                rankedList.clear()
                rankedList.addAll(tempList)

                withContext(Dispatchers.Main) {
                    adapter.updateData(rankedList.toList())
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
                val symbol = obj.getString("s")
                val newRawPrice = obj.getDouble("c")

                symbolMap[symbol]?.let { item ->
                    if (item.rawPrice != newRawPrice) {
                        item.priceState = when {
                            newRawPrice > item.rawPrice -> PriceState.UP
                            newRawPrice < item.rawPrice -> PriceState.DOWN
                            else -> PriceState.NEUTRAL
                        }

                        item.rawPrice = newRawPrice
                        item.price = formatPrice(newRawPrice)
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

    private fun formatPrice(price: Double): String {
        val formatter = NumberFormat.getNumberInstance(Locale.US)
        return if (price >= 1.0) {
            formatter.maximumFractionDigits = 2
            formatter.minimumFractionDigits = 2
            formatter.format(price)
        } else {
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
