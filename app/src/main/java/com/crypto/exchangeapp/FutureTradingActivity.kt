package com.crypto.exchangeapp

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import okhttp3.*
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit

class FutureTradingActivity : AppCompatActivity() {

    private var tvSymbol: TextView? = null
    private var tvPrice: TextView? = null
    private var tvChange: TextView? = null

    private var webSocket: WebSocket? = null
    private val selectedSymbol = "BTCUSDT"
    private lateinit var client: OkHttpClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#161A20"))
            setPadding(50, 50, 50, 50)
        }

        tvSymbol = TextView(this).apply {
            text = selectedSymbol
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        tvPrice = TextView(this).apply {
            text = "Starting Network..."
            textSize = 20f
            setTextColor(Color.parseColor("#848E9C"))
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 30)
        }

        tvChange = TextView(this).apply {
            text = "Waiting for Handshake..."
            textSize = 16f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
        }

        rootLayout.addView(tvSymbol)
        rootLayout.addView(tvPrice)
        rootLayout.addView(tvChange)

        setContentView(rootLayout)

        setupAuthenticNetworkEngine()
        startTargetedWebSocket(selectedSymbol)
    }

    private fun setupAuthenticNetworkEngine() {

        client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // websocket ke liye important
            .retryOnConnectionFailure(true)
            .eventListener(object : EventListener() {

                override fun dnsStart(call: Call, domainName: String) {
                    printDiagnostic("Resolving DNS...", "Connecting...")
                }

                override fun connectStart(
                    call: Call,
                    inetSocketAddress: java.net.InetSocketAddress,
                    proxy: Proxy
                ) {
                    printDiagnostic("TCP Handshake...", "TCP Active")
                }

                override fun secureConnectStart(call: Call) {
                    printDiagnostic("SSL Verification...", "SSL Active")
                }

                override fun connectEnd(
                    call: Call,
                    inetSocketAddress: java.net.InetSocketAddress,
                    proxy: Proxy,
                    protocol: Protocol?
                ) {
                    printDiagnostic("Connected!", "Handshake Success")
                }

                override fun connectFailed(
                    call: Call,
                    inetSocketAddress: java.net.InetSocketAddress,
                    proxy: Proxy,
                    protocol: Protocol?,
                    ioe: IOException
                ) {
                    printDiagnostic(
                        "Handshake Failed",
                        "Err: ${ioe.localizedMessage}"
                    )
                }
            })
            .build()
    }

    private fun printDiagnostic(priceText: String, changeText: String) {

        lifecycleScope.launch(Dispatchers.Main) {
            tvPrice?.text = priceText
            tvChange?.text = changeText
            tvChange?.setTextColor(Color.parseColor("#848E9C"))
        }
    }

    private fun startTargetedWebSocket(symbol: String) {

        webSocket?.close(1000, "Reset")

        // YAHAN SABSE BARA BUG THA
        val wsUrl =
            "wss://fstream.binance.com/ws/${symbol.lowercase()}@ticker"

        val request = Request.Builder()
            .url(wsUrl)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {

                override fun onOpen(
                    webSocket: WebSocket,
                    response: Response
                ) {

                    lifecycleScope.launch(Dispatchers.Main) {

                        tvPrice?.text = "Tunnel Connected!"
                        tvPrice?.setTextColor(
                            Color.parseColor("#0ECB81")
                        )

                        tvChange?.text = "Streaming Live..."
                    }
                }

                override fun onMessage(
                    webSocket: WebSocket,
                    text: String
                ) {

                    try {

                        val jsonObject =
                            JsonParser.parseString(text).asJsonObject

                        val price =
                            jsonObject.get("c").asString.toDouble()

                        val change =
                            jsonObject.get("P").asString.toDouble()

                        lifecycleScope.launch(Dispatchers.Main) {

                            tvPrice?.text =
                                String.format("$%.2f", price)

                            tvChange?.text =
                                String.format(
                                    "24h Change: %.2f%%",
                                    change
                                )

                            if (change >= 0) {

                                tvPrice?.setTextColor(
                                    Color.parseColor("#0ECB81")
                                )

                            } else {

                                tvPrice?.setTextColor(
                                    Color.parseColor("#F6465D")
                                )
                            }
                        }

                    } catch (e: Exception) {

                        lifecycleScope.launch(Dispatchers.Main) {

                            tvPrice?.text = "JSON Error"

                            tvChange?.text =
                                e.localizedMessage ?: "Unknown Error"

                            tvChange?.setTextColor(Color.YELLOW)
                        }
                    }
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: Response?
                ) {

                    lifecycleScope.launch(Dispatchers.Main) {

                        tvPrice?.text = "Tunnel Failure"

                        tvPrice?.setTextColor(
                            Color.parseColor("#F6465D")
                        )

                        tvChange?.text =
                            "Reason: ${t.localizedMessage}"

                        tvChange?.setTextColor(Color.YELLOW)

                        delay(5000)

                        startTargetedWebSocket(selectedSymbol)
                    }
                }
            }
        )
    }

    override fun onDestroy() {

        super.onDestroy()

        webSocket?.close(1000, "Exit")
    }
}