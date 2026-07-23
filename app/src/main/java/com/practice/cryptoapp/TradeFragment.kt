package com.practice.cryptoapp

import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import kotlin.math.min

class TradeFragment : Fragment() {

    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private var currentSymbol = "btcusdt"

    private lateinit var tv24hHighLow: TextView
    private lateinit var tvFundingRate: TextView
    private lateinit var tvOrderBookContent: TextView
    private lateinit var tvEstLiquidation: TextView
    private lateinit var etPrice: EditText
    private lateinit var etAmount: EditText
    private lateinit var sbLeverageLine: SeekBar
    private lateinit var btnLeverage: Button

    private var isOrderBookMode = true
    private var currentPrice = 0.0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_trade, container, false)

        tv24hHighLow = view.findViewById(R.id.tv24hHighLow)
        tvFundingRate = view.findViewById(R.id.tvFundingRate)
        tvOrderBookContent = view.findViewById(R.id.tvOrderBookContent)
        tvEstLiquidation = view.findViewById(R.id.tvEstLiquidation)
        etPrice = view.findViewById(R.id.etPrice)
        etAmount = view.findViewById(R.id.etAmount)
        sbLeverageLine = view.findViewById(R.id.sbLeverageLine)
        btnLeverage = view.findViewById(R.id.btnLeverage)

        setupSpinners(view)
        setupListeners(view)
        startFundingTimer()
        connectBinanceTradeWebSocket()

        return view
    }

    private fun setupSpinners(view: View) {
        val spCoinSelect = view.findViewById<Spinner>(R.id.spCoinSelect)
        val spMarginType = view.findViewById<Spinner>(R.id.spMarginType)
        val spOrderType = view.findViewById<Spinner>(R.id.spOrderType)
        val spTradeAmountFilter = view.findViewById<Spinner>(R.id.spTradeAmountFilter)

        val coins = arrayOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "XRPUSDT")
        val marginTypes = arrayOf("Cross", "Isolated")
        val orderTypes = arrayOf("Limit Order", "Market Order")
        val amountFilters = arrayOf("All Amounts", "> $1,000", "> $10,000")

        spCoinSelect.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, coins)
        spMarginType.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, marginTypes)
        spOrderType.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, orderTypes)
        spTradeAmountFilter.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, amountFilters)

        spCoinSelect.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                currentSymbol = coins[position].lowercase()
                connectBinanceTradeWebSocket()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupListeners(view: View) {
        val tabOrderBook = view.findViewById<TextView>(R.id.tabOrderBook)
        val tabRealTrades = view.findViewById<TextView>(R.id.tabRealTrades)

        tabOrderBook.setOnClickListener {
            isOrderBookMode = true
            tabOrderBook.setTextColor(resources.getColor(android.R.color.holo_orange_light))
            tabRealTrades.setTextColor(resources.getColor(android.R.color.darker_gray))
            connectBinanceTradeWebSocket()
        }

        tabRealTrades.setOnClickListener {
            isOrderBookMode = false
            tabRealTrades.setTextColor(resources.getColor(android.R.color.holo_orange_light))
            tabOrderBook.setTextColor(resources.getColor(android.R.color.darker_gray))
            connectBinanceTradeWebSocket()
        }

        sbLeverageLine.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val lev = if (progress < 1) 1 else progress
                btnLeverage.text = "${lev}x"
                calculateLiquidation()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                calculateLiquidation()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        etPrice.addTextChangedListener(watcher)
        etAmount.addTextChangedListener(watcher)
    }

    private fun calculateLiquidation() {
        val price = etPrice.text.toString().toDoubleOrNull() ?: currentPrice
        val leverage = sbLeverageLine.progress.coerceAtLeast(1)

        if (price > 0) {
            val liqPrice = price * (1.0 - (1.0 / leverage))
            tvEstLiquidation.text = "Est. Liq Price: $%.2f USDT".format(liqPrice)
        }
    }

    private fun startFundingTimer() {
        object : CountDownTimer(28800000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val hours = (millisUntilFinished / 3600000) % 24
                val minutes = (millisUntilFinished / 60000) % 60
                val seconds = (millisUntilFinished / 1000) % 60
                tvFundingRate.text = "Funding: 0.0100%% in %02d:%02d:%02d".format(hours, minutes, seconds)
            }
            override fun onFinish() {}
        }.start()
    }

    private fun connectBinanceTradeWebSocket() {
        webSocket?.close(1000, "Switching stream")

        val streamName = if (isOrderBookMode) "${currentSymbol}@depth10@100ms" else "${currentSymbol}@trade"
        val tickerStream = "${currentSymbol}@ticker"

        val request = Request.Builder()
            .url("wss://stream.binance.com:9443/stream?streams=$streamName/$tickerStream")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                activity?.runOnUiThread {
                    parseTradeStream(text)
                }
            }
        })
    }

    private fun parseTradeStream(jsonText: String) {
        try {
            val root = JSONObject(jsonText)
            if (!root.has("data")) return
            val data = root.getJSONObject("data")

            if (data.has("h") && data.has("l")) {
                val high = data.getDouble("h")
                val low = data.getDouble("l")
                currentPrice = data.getDouble("c")
                tv24hHighLow.text = "24h H: $high | L: $low"
            }

            if (isOrderBookMode && data.has("bids")) {
                val bids = data.getJSONArray("bids")
                val asks = data.getJSONArray("asks")

                val sb = StringBuilder()
                sb.append("--- ASKS (SELL) ---\n")
                val maxAsks = min(5, asks.length())
                for (i in 0 until maxAsks) {
                    val a = asks.getJSONArray(i)
                    sb.append("%.2f  %.4f\n".format(a.getString(0).toDouble(), a.getString(1).toDouble()))
                }
                sb.append("\nPRICE: %.2f\n\n".format(currentPrice))
                sb.append("--- BIDS (BUY) ---\n")
                val maxBids = min(5, bids.length())
                for (i in 0 until maxBids) {
                    val b = bids.getJSONArray(i)
                    sb.append("%.2f  %.4f\n".format(b.getString(0).toDouble(), b.getString(1).toDouble()))
                }
                tvOrderBookContent.text = sb.toString()
            } else if (!isOrderBookMode && data.has("p")) {
                val p = data.getDouble("p")
                val q = data.getDouble("q")
                val isBuyerMaker = data.getBoolean("m")
                val side = if (isBuyerMaker) "SELL" else "BUY"

                val currentText = tvOrderBookContent.text.toString()
                val line = "[$side] $p ($q)\n"
                tvOrderBookContent.text = (line + currentText).take(300)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        webSocket?.close(1000, "Fragment Closed")
    }
}
