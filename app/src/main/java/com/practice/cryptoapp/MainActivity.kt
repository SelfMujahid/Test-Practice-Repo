package com.practice.cryptoapp

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
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

    private val allMasterList = mutableListOf<CryptoItem>() // All 300+ coins
    private val displayedList = mutableListOf<CryptoItem>()  // Currently shown
    private val symbolMap = HashMap<String, CryptoItem>()

    // Pagination variables
    private var currentLimit = 20
    private lateinit var btnPrevious: Button
    private lateinit var btnNext: Button
    private lateinit var tvPageCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Logo Popup Menu
        val logoImg = findViewById<ImageView>(R.id.imgAppLogo)
        logoImg.setOnClickListener { showLogoMenu(it) }

        // 2. Views Setup
        btnPrevious = findViewById(R.id.btnPrevious)
        btnNext = findViewById(R.id.btnNext)
        tvPageCount = findViewById(R.id.tvPageCount)

        recyclerView = findViewById(R.id.cryptoRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = CryptoAdapter(emptyList())
        recyclerView.adapter = adapter

        // 3. Setup Buttons & Listeners
        setupPaginationListeners()
        setupPnlChips()

        // 4. Fetch Coins Data
        fetchTop300CoinGecko()
    }

    private fun setupPaginationListeners() {
        btnNext.setOnClickListener {
            if (currentLimit < allMasterList.size) {
                currentLimit += 50
                if (currentLimit > allMasterList.size) currentLimit = allMasterList.size
                updateListDisplay()
            }
        }

        btnPrevious.setOnClickListener {
            if (currentLimit > 20) {
                currentLimit -= 50
                if (currentLimit < 20) currentLimit = 20
                updateListDisplay()
            }
        }
    }

    private fun updateListDisplay() {
        displayedList.clear()
        displayedList.addAll(allMasterList.take(currentLimit))
        adapter.updateData(displayedList.toList())

        // UI Button States Update
        tvPageCount.text = "Showing $currentLimit of ${allMasterList.size}"
        btnPrevious.isEnabled = currentLimit > 20
        btnNext.isEnabled = currentLimit < allMasterList.size

        // Connect WebSocket for currently displayed coins
        connectBinanceWebSocket()
    }

    private fun showLogoMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menu.add("Liquidations")
        popup.menu.add("Wallet Transactions")
        popup.menu.add("Drops")
        popup.menu.add("Bubbles")
        popup.menu.add("Order Flow")
        popup.setOnMenuItemClickListener {
            Toast.makeText(this, "Opened: ${it.title}", Toast.LENGTH_SHORT).show()
            true
        }
        popup.show()
    }

    private fun setupPnlChips() {
        val container = findViewById<LinearLayout>(R.id.pnlChipsContainer)
        val timeframes = listOf(
            "24h" to -3.2, "2d" to 45.0, "3d" to 18.2,
            "4d" to -5.1, "5d" to 22.0, "6d" to 14.8, "7d" to 35.4,
            "15d" to -12.0, "30d" to 110.5
        )

        for ((tf, pnl) in timeframes) {
            val tv = TextView(this)
            val isProfit = pnl >= 0
            tv.text = "$tf: ${if (isProfit) "+" else ""}$pnl%"
            tv.setTextColor(if (isProfit) Color.parseColor("#00C853") else Color.parseColor("#FF1744"))
            tv.setBackgroundColor(Color.parseColor("#F0F0F0"))
            tv.setPadding(16, 8, 16, 8)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 12, 0)
            tv.layoutParams = params
            container.addView(tv)
        }
    }

    private fun fetchTop300CoinGecko() {
        activityScope.launch(Dispatchers.IO) {
            try {
                val tempList = mutableListOf<CryptoItem>()

                for (page in 1..2) {
                    val url = "https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=150&page=$page&sparkline=false&price_change_percentage=1h,24h,7d"
                    val jsonString = URL(url).readText()
                    val jsonArray = JSONArray(jsonString)

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val symbol = obj.getString("symbol").uppercase()
                        val name = obj.getString("name")
                        val rank = obj.optInt("market_cap_rank", tempList.size + 1)
                        val price = obj.optDouble("current_price", 0.0)
                        val logo = obj.optString("image", "")
                        val c1h = obj.optDouble("price_change_percentage_1h_in_currency", 0.0)
                        val c24h = obj.optDouble("price_change_percentage_24h_in_currency", 0.0)
                        val c7d = obj.optDouble("price_change_percentage_7d_in_currency", 0.0)

                        val item = CryptoItem(
                            rank = rank,
                            name = name,
                            symbol = symbol,
                            price = "%.2f".format(price),
                            logoUrl = logo,
                            change1h = c1h,
                            change24h = c24h,
                            change7d = c7d,
                            rawPrice = price,
                            priceState = PriceState.NEUTRAL
                        )
                        tempList.add(item)
                        symbolMap["${symbol}USDT"] = item
                    }
                }

                allMasterList.clear()
                allMasterList.addAll(tempList)

                withContext(Dispatchers.Main) {
                    updateListDisplay()
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun connectBinanceWebSocket() {
        webSocket?.close(1000, "Switching Limit")

        val streams = displayedList.joinToString("/") { "${it.symbol.lowercase()}usdt@ticker" }
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
            val root = org.json.JSONObject(jsonText)
            if (!root.has("data")) return
            val data = root.getJSONObject("data")
            val symbol = data.getString("s")
            val newRawPrice = data.getDouble("c")

            symbolMap[symbol]?.let { item ->
                if (item.rawPrice != newRawPrice) {
                    item.priceState = if (newRawPrice > item.rawPrice) PriceState.UP else PriceState.DOWN
                    item.rawPrice = newRawPrice
                    item.price = "%.2f".format(newRawPrice)
                    item.isFlashed = true

                    runOnUiThread {
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
        webSocket?.close(1000, "App Closed")
    }
}
