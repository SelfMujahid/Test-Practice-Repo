package com.practice.cryptoapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import org.json.JSONArray
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CryptoAdapter
    private val activityScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.cryptoRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        adapter = CryptoAdapter(emptyList())
        recyclerView.adapter = adapter

        // Real-time loop to fetch 300+ live crypto prices from Binance
        startRealTimeUpdates()
    }

    private fun startRealTimeUpdates() {
        activityScope.launch {
            while (isActive) {
                val liveData = fetchCryptoPrices()
                if (liveData.isNotEmpty()) {
                    adapter.updateData(liveData)
                }
                // Updates every 3 seconds
                delay(3000) 
            }
        }
    }

    private suspend fun fetchCryptoPrices(): List<CryptoItem> = withContext(Dispatchers.IO) {
        val cryptoList = mutableListOf<CryptoItem>()
        try {
            // Public Binance Ticker Price Endpoint (Returns 300+ USDT pairs)
            val jsonString = URL("https://api.binance.com/api/v3/ticker/price").readText()
            val jsonArray = JSONArray(jsonString)

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val symbol = obj.getString("symbol")
                val price = obj.getString("price")

                // Filter USDT pairs for clean rendering
                if (symbol.endsWith("USDT")) {
                    val cleanSymbol = symbol.replace("USDT", " / USDT")
                    val formattedPrice = price.toDoubleOrNull()?.let {
                        String.format("%.4f", it)
                    } ?: price

                    cryptoList.add(CryptoItem(cleanSymbol, formattedPrice))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext cryptoList
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }
}
