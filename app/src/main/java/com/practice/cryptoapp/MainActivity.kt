package com.practice.cryptoapp

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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

    private val allCryptoList = mutableListOf<CryptoItem>()
    private val symbolMap = HashMap<String, CryptoItem>()
    
    // Batch UI Update Handler to prevent lag
    private var isUpdatePending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Logo + 3 Lines Menu Trigger
        val menuBtn = findViewById<View>(R.id.btnMenuLogo)
        menuBtn.setOnClickListener { view -> showLogoMenu(view) }

        // 2. Setup RecyclerView
        recyclerView = findViewById(R.id.cryptoRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = CryptoAdapter(emptyList())
        recyclerView.adapter = adapter

        // 3. Real Demo PnL Setup (Show 0 if no trades)
        setupRealDemoPnL(realPnlList = emptyList()) 

        // 4. Setup Real-time Search Filter
        setupSearch()

        // 5. Fetch 300+ Coins
        fetchTop300Coins()
    }

    private fun showLogoMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menu.add("Liquidations")
        popup.menu.add("Wallet Transactions")
        popup.menu.add("Drops")
        popup.menu.add("Bubbles")
        popup.menu.add("Order Flow")

        popup.setOnMenuItemClickListener { item ->
            Toast.makeText(this, "Opened: ${item.title}", Toast.LENGTH_SHORT).show()
            true
        }
        popup.show()
    }

    private fun setupRealDemoPnL(realPnlList: List<Pair<String, Double>>) {
        val container = findViewById<LinearLayout>(R.id.pnlChipsContainer)
        container.removeAllViews()

        val timeframes = listOf("8h", "24h", "2d", "3d", "4d", "5d", "6d", "7d", "15d", "30d")

        for (tf in timeframes) {
            val tv = TextView(this)
            val pnlVal = realPnlList.find { it.first == tf }?.second ?: 0.0

            tv.text = if (pnlVal == 0.0) "$tf: $0.00 (0%)" else "$tf: ${if (pnlVal > 0) "+" else ""}$pnlVal%"
            tv.setTextColor(
                when {
                    pnlVal > 0 -> Color.parseColor("#00C853")
                    pnlVal < 0 -> Color.parseColor("#FF1744")
                    else -> Color.parseColor("#888888") // GRAY COLOR FOR 0
                }
            )
            tv.setBackgroundColor(Color.parseColor("#EEEEEE"))
            tv.setPadding(12, 6, 12, 6)

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 10, 0)
            tv.layoutParams = params
            container.addView(tv)
        }
    }

    private fun setupSearch() {
        val etSearch = findViewById<EditText>(R.id.etSearch)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().lowercase().trim()
                val filtered = allCryptoList.filter { 
                    it.symbol.lowercase().contains(query) || it.name.lowercase().contains(query) 
                }
                adapter.updateData(filtered)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun fetchTop300Coins() {
        activityScope.launch(Dispatchers.IO) {
            try {
                val tempList = mutableListOf<CryptoItem>()
                for (page in 1..2) {
                    val url = "https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=150&page=$page&sparkline=false&price_change_percentage=1h,8h,24h,7d"
                    val jsonArray = JSONArray(URL(url).readText())

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val symbol = obj.getString("symbol").uppercase()
                        val item = CryptoItem(
                            rank = obj.optInt("market_cap_rank", tempList.size + 1),
                            name = obj.getString("name"),
                            symbol = symbol,
                            price = "%.2f".format(obj.optDouble("current_price", 0.0)),
                            logoUrl = obj.optString("image", ""),
                            change1h = obj.optDouble("price_change_percentage_1h_in_currency", 0.0),
                            change8h = obj.optDouble("price_change_percentage_8h_in_currency", 0.0),
                            change24h = obj.optDouble("price_change_percentage_24h_in_currency", 0.0),
                            change7d = obj.optDouble("price_change_percentage_7d_in_currency", 0.0),
                            rawPrice = obj.optDouble("current_price", 0.0)
                        )
                        tempList.add(item)
                        symbolMap["${symbol}USDT"] = item
                    }
                }

                allCryptoList.clear()
                allCryptoList.addAll(tempList)

                withContext(Dispatchers.Main) {
                    adapter.updateData(allCryptoList.toList())
                    connectBinanceWebSocket()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun connectBinanceWebSocket() {
        val streams = allCryptoList.take(80).joinToString("/") { "${it.symbol.lowercase()}usdt@ticker" }
        val request = Request.Builder().url("wss://stream.binance.com:9443/stream?streams=$streams").build()

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

                    // Batch update every 200ms to eliminate UI Lag
                    if (!isUpdatePending) {
                        isUpdatePending = true
                        recyclerView.postDelayed({
                            adapter.updateData(allCryptoList.toList())
                            isUpdatePending = false
                        }, 200)
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
        webSocket?.close(1000, "Closed")
    }
}
