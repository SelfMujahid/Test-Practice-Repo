package com.practice.cryptoapp

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
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

    private val allMasterList = mutableListOf<CryptoItem>() // Up to 1,000 coins
    private val filteredList = mutableListOf<CryptoItem>()  // Active filter
    private val displayedList = mutableListOf<CryptoItem>() // Page view
    private val symbolMap = HashMap<String, CryptoItem>()

    private var currentFilter = "ALL" // ALL, GAINERS, LOSERS
    private var currentLimit = 20
    private var isDarkMode = false

    private lateinit var btnPrevious: Button
    private lateinit var btnNext: Button
    private lateinit var tvPageCount: TextView
    private lateinit var etSearch: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Menu Trigger (3 Lines & Logo)
        val menuBtn = findViewById<ImageView>(R.id.imgMenuIcon)
        val logoBtn = findViewById<ImageView>(R.id.imgAppLogo)
        val openMenuListener = View.OnClickListener { showSettingsMenu(it) }
        menuBtn.setOnClickListener(openMenuListener)
        logoBtn.setOnClickListener(openMenuListener)

        // 2. Views Setup
        btnPrevious = findViewById(R.id.btnPrevious)
        btnNext = findViewById(R.id.btnNext)
        tvPageCount = findViewById(R.id.tvPageCount)
        etSearch = findViewById(R.id.etSearch)

        recyclerView = findViewById(R.id.cryptoRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = CryptoAdapter(emptyList())
        recyclerView.adapter = adapter

        // 3. Setup Listeners
        setupPaginationListeners()
        setupSearchAndFilters()

        // 4. Fetch 1,000 Coins
        fetchTop1000Coins()
    }

    private fun showSettingsMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menu.add("Liquidations")
        popup.menu.add("Wallet Transactions")
        popup.menu.add("Bubbles")
        popup.menu.add(if (isDarkMode) "☀️ Light Mode" else "🌙 Dark Mode")

        popup.setOnMenuItemClickListener { item ->
            when (item.title.toString()) {
                "☀️ Light Mode", "🌙 Dark Mode" -> {
                    isDarkMode = !isDarkMode
                    AppCompatDelegate.setDefaultNightMode(
                        if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                    )
                }
                else -> Toast.makeText(this, "Selected: ${item.title}", Toast.LENGTH_SHORT).show()
            }
            true
        }
        popup.show()
    }

    private fun setupSearchAndFilters() {
        val btnSearchIcon = findViewById<ImageView>(R.id.btnSearchIcon)
        val btnAll = findViewById<Button>(R.id.btnAll)
        val btnGainers = findViewById<Button>(R.id.btnGainers)
        val btnLosers = findViewById<Button>(R.id.btnLosers)

        // Instant Real-time Search
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFiltersAndSearch()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnSearchIcon.setOnClickListener { applyFiltersAndSearch() }

        // Filter Buttons
        btnAll.setOnClickListener {
            currentFilter = "ALL"
            applyFiltersAndSearch()
        }
        btnGainers.setOnClickListener {
            currentFilter = "GAINERS"
            applyFiltersAndSearch()
        }
        btnLosers.setOnClickListener {
            currentFilter = "LOSERS"
            applyFiltersAndSearch()
        }
    }

    private fun applyFiltersAndSearch() {
        val query = etSearch.text.toString().trim().lowercase()

        // 1. Filter by Search Query
        var temp = if (query.isEmpty()) {
            allMasterList.toMutableList()
        } else {
            allMasterList.filter {
                it.name.lowercase().contains(query) || it.symbol.lowercase().contains(query)
            }.toMutableList()
        }

        // 2. Filter by Category (Gainers / Losers)
        when (currentFilter) {
            "GAINERS" -> temp.sortByDescending { it.change24h }
            "LOSERS" -> temp.sortBy { it.change24h }
            "ALL" -> temp.sortBy { it.rank }
        }

        filteredList.clear()
        filteredList.addAll(temp)

        // Reset limit to 20 on search/filter change
        currentLimit = 20
        updateListDisplay()
    }

    private fun setupPaginationListeners() {
        btnNext.setOnClickListener {
            if (currentLimit < filteredList.size) {
                currentLimit += 50
                if (currentLimit > filteredList.size) currentLimit = filteredList.size
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
        displayedList.addAll(filteredList.take(currentLimit))
        adapter.updateData(displayedList.toList())

        tvPageCount.text = "Showing ${displayedList.size} of ${filteredList.size}"
        btnPrevious.isEnabled = currentLimit > 20
        btnNext.isEnabled = currentLimit < filteredList.size

        connectBinanceWebSocket()
    }

    private fun fetchTop1000Coins() {
        activityScope.launch(Dispatchers.IO) {
            try {
                val tempList = mutableListOf<CryptoItem>()

                // Fetch 4 pages * 250 = 1,000 Coins
                for (page in 1..4) {
                    val url = "https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=250&page=$page&sparkline=false&price_change_percentage=1h,24h,7d"
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
                    applyFiltersAndSearch()
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun connectBinanceWebSocket() {
        webSocket?.close(1000, "Switching List")

        if (displayedList.isEmpty()) return

        val streams = displayedList.take(50).joinToString("/") { "${it.symbol.lowercase()}usdt@ticker" }
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
