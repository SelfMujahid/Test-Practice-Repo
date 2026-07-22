package com.practice.cryptoapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.*
import org.json.JSONArray

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CryptoAdapter
    
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    
    // Quick lookups ke liye Map
    private val cryptoMap = HashMap<String, CryptoItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.cryptoRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = CryptoAdapter(emptyList())
        recyclerView.adapter = adapter

        // Binance WebSocket Tunnel Open Karein
        connectWebSocketTunnel()
    }

    private fun connectWebSocketTunnel() {
        // Binance miniTicker Stream URL (All market tickers live)
        val request = Request.Builder()
            .url("wss://stream.binance.com:9443/ws/!miniTicker@arr")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                // Jab bhi Binance se tunnel mein live event aaye
                parseAndRenderWebSocketData(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                t.printStackTrace()
            }
        })
    }

    private fun parseAndRenderWebSocketData(jsonText: String) {
        try {
            val jsonArray = JSONArray(jsonText)

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val symbol = obj.getString("s") // Symbol (e.g. BTCUSDT)
                val closePrice = obj.getString("c") // Current Close Price

                if (symbol.endsWith("USDT")) {
                    val cleanSymbol = symbol.replace("USDT", " / USDT")
                    val formattedPrice = closePrice.toDoubleOrNull()?.let {
                        String.format("%.4f", it)
                    } ?: closePrice

                    cryptoMap[cleanSymbol] = CryptoItem(cleanSymbol, formattedPrice)
                }
            }

            val updatedList = cryptoMap.values.toList()

            // Main Thread par UI Screen update karein
            runOnUiThread {
                adapter.updateData(updatedList)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // App close hone par tunnel (WebSocket) disconnect kar dein
        webSocket?.close(1000, "App closed")
    }
}
