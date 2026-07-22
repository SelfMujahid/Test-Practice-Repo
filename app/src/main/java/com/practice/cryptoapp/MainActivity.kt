package com.practice.cryptoapp

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
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
    private lateinit var etSearch: EditText
    private lateinit var tvTotalCoins: TextView
    private lateinit var tvBtcPrice: TextView

    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val allCoinsList = mutableListOf<CryptoItem>()
    private val displayedList = mutableListOf<CryptoItem>()
    private val symbolMap = HashMap<String, CryptoItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.cryptoRecyclerView)
        etSearch = findViewById(R.id.etSearch)
        tvTotalCoins = findViewById(R.id.tvTotalCoins)
        tvBtcPrice = findViewById(R.id.tvBtcPrice)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = CryptoAdapter(displayedList)
        recyclerView.adapter = adapter

        setupSearchFilter()
        fetchInitialMarketData()
    }

    private fun setupSearchFilter() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterList(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterList(query: String) {
        displayedList.clear()
        if (query.isEmpty()) {
            displayedList.addAll(allCoinsList)
        } else {
            val q = query.lowercase(Locale.ROOT)
            val filtered = allCoinsList.filter {
                it.name.lowercase(Locale.ROOT).contains(q) || it.symbol.lowercase(Locale.ROOT).contains(q)
            }
            displayedList.addAll(filtered)
        }
        adapter.updateData(displayedList)
    }

    private fun fetchInitialMarketData() {
        scope.launch(Dispatchers.IO) {
            try {
                val url = "https://api.binance.com/api/v3/ticker/24hr"
                val response = URL(url).readText()
                val jsonArray = JSONArray(response)

                val tempList = mutableListOf<CryptoItem>()
                var rank = 1

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val symbol = obj.getString("symbol")

                    if (symbol.endsWith("USDT") && !symbol.contains("UP") && !symbol.contains("DOWN")) {
                        val baseSymbol = symbol.replace("USDT", "")
                        val price = obj.getDouble("lastPrice")
                        val change24h = obj.getDouble("priceChangePercent")

                        val item = CryptoItem(
                            rank = rank,
                            symbol = baseSymbol,
                            name = baseSymbol,
                            price = formatPrice(price),
                            rawPrice = price,
                            change24h = change24h
                        )

                        tempList.add(item)
                        symbolMap[symbol] = item
                        rank++
                        if (rank > 100) break // Top 100
                    }
                }

                withContext(Dispatchers.Main) {
                    allCoinsList.clear()
                    allCoinsList.addAll(tempList)
                    filterList(etSearch.text.toString())

                    tvTotalCoins.text = "Total Coins: ${allCoinsList.size}"
                    updateBtcHeaderPrice()

                    connectBinanceWebSocket()
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun connectBinanceWebSocket() {
        val streams = allCoinsList.take(50).joinToString("/") { "${it.symbol.lowercase()}usdt@ticker" }
        val request = Request.Builder()
            .url("wss://stream.binance.com:9443/stream?streams=$streams")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                parseWebSocketStream(text)
            }
        })
    }

    private fun parseWebSocketStream(jsonText: String) {
        try {
            val jsonObject = org.json.JSONObject(jsonText)
            if (!jsonObject.has("data")) return
            val data = jsonObject.getJSONObject("data")

            val symbol = data.getString("s")
            val newPrice = data.getDouble("c")
            val change24h = data.getDouble("P")

            symbolMap[symbol]?.let { item ->
                if (item.rawPrice != newPrice) {
                    val direction = if (newPrice > item.rawPrice) "UP" else "DOWN"

                    item.rawPrice = newPrice
                    item.price = formatPrice(newPrice)
                    item.change24h = change24h

                    runOnUiThread {
                        val index = displayedList.indexOf(item)
                        if (index != -1) {
                            adapter.notifyItemChanged(index, direction)
                        }
                        if (item.symbol == "BTC") {
                            updateBtcHeaderPrice()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateBtcHeaderPrice() {
        symbolMap["BTCUSDT"]?.let { btc ->
            tvBtcPrice.text = "BTC: $${btc.price}"
        }
    }

    private fun formatPrice(price: Double): String {
        val formatter = NumberFormat.getNumberInstance(Locale.US)
        return if (price >= 1.0) {
            formatter.maximumFractionDigits = 2
            formatter.minimumFractionDigits = 2
            formatter.format(price)
        } else {
            formatter.maximumFractionDigits = 5
            formatter.minimumFractionDigits = 2
            formatter.format(price)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        webSocket?.close(1000, "App closed")
    }
}
