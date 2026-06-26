package com.crypto.exchangeapp

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import okhttp3.*
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit

class FutureTradingActivity : AppCompatActivity() {

    private var tvRawData: TextView? = null
    private var webSocket: WebSocket? = null
    private val selectedSymbol = "btcusdt" // lowercase standard for streams
    private lateinit var client: OkHttpClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // UI Setup - Sirf ek bada text box poori screen par raw data dikhane ke liye
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setBackgroundColor(Color.parseColor("#161A20"))
            setPadding(30, 50, 30, 50)
        }

        tvRawData = TextView(this).apply {
            text = "Connecting to Binance Stream..."
            textSize = 13f // Chota size taaki microsecond ka poora data fit aaye
            setTextColor(Color.WHITE)
            gravity = Gravity.LEFT
        }

        rootLayout.addView(tvRawData)
        setContentView(rootLayout)

        // Network Setup
        client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()

        startTargetedWebSocket()
    }

    private fun startTargetedWebSocket() {
        webSocket?.close(1000, "Reset")

        // Binance Futures Stream URL
        val wsUrl = "wss://fstream.binance.com/ws/${selectedSymbol}@ticker"

        val request = Request.Builder()
            .url(wsUrl)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                lifecycleScope.launch(Dispatchers.Main) {
                    tvRawData?.text = "Tunnel Open! Waiting for raw data packets..."
                    tvRawData?.setTextColor(Color.GREEN)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Bina kisi try-catch parsing ke, direct string ko screen par fenkna
                lifecycleScope.launch(Dispatchers.Main) {
                    tvRawData?.text = text
                    tvRawData?.setTextColor(Color.WHITE)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                lifecycleScope.launch(Dispatchers.Main) {
                    tvRawData?.text = "Connection Failed: ${t.localizedMessage}\nRetrying..."
                    tvRawData?.setTextColor(Color.RED)
                    delay(3000)
                    startTargetedWebSocket()
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocket?.close(1000, "Exit")
    }
}
