package com.practice.cryptoapp

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

    private val allMasterList = mutableListOf<CryptoItem>() 
    private val filteredList = mutableListOf<CryptoItem>()  
    private val displayedList = mutableListOf<CryptoItem>() 
    private val symbolMap = HashMap<String, CryptoItem>()

    private var currentPage = 1
    private var currentFilter = "ALL" 
    private var isDarkMode = false

    private lateinit var btnPrevious: Button
    private lateinit var btnNext: Button
    private lateinit var tvPageCount: TextView
    private lateinit var etSearch: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Logo with 3 lines Click Listener
        val logoContainer = findViewById<FrameLayout>(R.id.logoMenuContainer)
        logoContainer.setOnClickListener { showSettingsMenu(it) }

        // 2. Views Setup
        btnPrevious = findViewById(R.id.btnPrevious)
        btnNext = findViewById(R.id.btnNext)
        tvPageCount = findViewById(R.id.tvPageCount)
        etSearch = findViewById(R.id.etSearch)

        recyclerView = findViewById(R.id.cryptoRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = CryptoAdapter(emptyList())
        recyclerView.adapter = adapter

        setupPaginationListeners()
        setupSearchAndFilters()

        // 3. Fetch All Binance Coins
        fetchAllBinanceCoins()
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

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentPage = 1
                applyFiltersAndSearch()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnSearchIcon.setOnClickListener { 
            currentPage = 1
            applyFiltersAndSearch() 
        }

        btnAll.setOnClickListener {
            currentFilter = "ALL"
            currentPage = 1
            applyFiltersAndSearch()
        }
        btnGainers.setOnClickListener {
            currentFilter = "GAINERS"
            currentPage = 1
            applyFiltersAndSearch()
        }
        btnLosers.setOnClickListener {
            currentFilter = "LOSERS"
            currentPage = 1
            applyFiltersAndSearch()
        }
    }

    private fun applyFiltersAndSearch() {
        val query = etSearch.text.toString().trim().lowercase()

        var temp = if (query.isEmpty()) {
            allMasterList.toMutableList()
        } else {
            allMasterList.filter {
                it.name.lowercase().contains(query) || it.symbol.lowercase().contains(query)
            }.toMutableList()
        }

        when (currentFilter) {
            "GAINERS" -> temp.sortByDescending { it.change24h }
            "LOSERS" -> temp.sortBy { it.change24h }
            "ALL" -> temp.sortBy { it.rank }
        }

        filteredList.clear()
        filteredList.addAll(temp)

        renderCurrentPage()
    }

    private fun setupPaginationListeners() {
        btnNext.setOnClickListener {
            val totalPages = getTotalPages()
            if (currentPage < totalPages) {
                currentPage++
                renderCurrentPage()
            }
        }

        btnPrevious.setOnClickListener {
            if (currentPage > 1) {
                currentPage--
                renderCurrentPage()
            }
        }
    }

    private fun getTotalPages(): Int {
        if (filteredList.size <= 20) return 1
        val remainingItems = filteredList.size - 20
        return 1 + kotlin.math.ceil(remainingItems.toDouble() / 50.0).toInt()
    }

    private fun renderCurrentPage() {
        displayedList.clear()

        val startIndex: Int
        val endIndex: Int

        if (currentPage == 1) {
            startIndex = 0
            endIndex = minOf(20, filteredList.size)
        } else {
            startIndex = 20 + (currentPage - 2) * 50
            endIndex = minOf(startIndex + 50, filteredList.size)
        }

        if (startIndex < filteredList.size) {
            displayedList.addAll(filteredList.subList(startIndex, endIndex))
        }

        adapter.updateData(displayedList.toList())

        val totalPages = getTotalPages()
        tvPageCount.text = "Page $currentPage of $totalPages (${displayedList.size} coins)"
        btnPrevious.isEnabled = currentPage > 1
        btnNext.isEnabled = currentPage < totalPages

        connectBinanceWebSocket()
    }

    private fun fetchAllBinanceCoins() {
        activityScope.launch(Dispatchers.IO) {
            try {
                // Fetch All Tickers via Binance API
                val url = "https://api.binance.com/api/v3/ticker/24hr"
                val jsonString = URL(url).readText()
                val jsonArray = JSONArray(jsonString)

                val tempList = mutableListOf<CryptoItem>()
                var rankCounter = 1

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val symbol = obj.getString("symbol")

                    // Filter only USDT pairs for clean pricing
                    if (symbol.endsWith("USDT")) {
                        val baseSymbol = symbol.replace("USDT", "")
                        val price = obj.optDouble("lastPrice", 0.0)
                        val c24h = obj.optDouble("priceChangePercent", 0.0)

                        val item = CryptoItem(
                            rank = rankCounter++,
                            name = baseSymbol,
                            symbol = baseSymbol,
                            price = "%.2f".format(price),
                            logoUrl = "https://assets.coincap.io/assets/icons/${baseSymbol.lowercase()}@2x.png",
                            change1h = 0.0,
                            change24h = c24h,
                            change7d = 0.0,
                            rawPrice = price,
                            priceState = PriceState.NEUTRAL
                        )
                        tempList.add(item)
                        symbolMap[symbol] = item
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
        webSocket?.close(1000, "Switching Page")

        if (displayedList.isEmpty()) return

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
