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

class TradeFragment : Fragment() {

    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private var currentSymbol = "btcusdt"
    private var currentPrice = 65000.0

    private lateinit var tvLastPrice: TextView
    private lateinit var tvPriceChange: TextView
    private lateinit var tvTickerDetails: TextView
    private lateinit var tvBalanceStats: TextView
    private lateinit var tvOrderSummary: TextView
    private lateinit var tvPositionContent: TextView

    private lateinit var etPrice: EditText
    private lateinit var etQuantity: EditText
    private lateinit var sbLeverage: SeekBar
    private lateinit var btnLeverage: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_trade, container, false)

        initViews(view)
        setupSpinners(view)
        setupListeners(view)
        startFundingTimer()
        connectBinanceWebSocket()

        return view
    }

    private fun initViews(view: View) {
        tvLastPrice = view.findViewById(R.id.tvLastPrice)
        tvPriceChange = view.findViewById(R.id.tvPriceChange)
        tvTickerDetails = view.findViewById(R.id.tvTickerDetails)
        tvBalanceStats = view.findViewById(R.id.tvBalanceStats)
        tvOrderSummary = view.findViewById(R.id.tvOrderSummary)
        tvPositionContent = view.findViewById(R.id.tvPositionContent)
        etPrice = view.findViewById(R.id.etPrice)
        etQuantity = view.findViewById(R.id.etQuantity)
        sbLeverage = view.findViewById(R.id.sbLeverage)
        btnLeverage = view.findViewById(R.id.btnLeverage)
    }

    private fun setupSpinners(view: View) {
        val spCoinSelect = view.findViewById<Spinner>(R.id.spCoinSelect)
        val spMarginMode = view.findViewById<Spinner>(R.id.spMarginMode)
        val spPositionMode = view.findViewById<Spinner>(R.id.spPositionMode)
        val spOrderType = view.findViewById<Spinner>(R.id.spOrderType)
        val spBottomTabs = view.findViewById<Spinner>(R.id.spBottomTabs)

        spCoinSelect.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, arrayOf("BTCUSDT", "ETHUSDT", "SOLUSDT"))
        spMarginMode.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, arrayOf("Cross Margin", "Isolated Margin"))
        spPositionMode.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, arrayOf("One-Way Mode", "Hedge Mode"))
        spOrderType.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, arrayOf("Limit Order", "Market Order", "Stop Limit", "Trailing Stop"))
        spBottomTabs.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, arrayOf("Open Positions", "Open Orders", "Order History", "Trade History"))

        spCoinSelect.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                val coins = arrayOf("btcusdt", "ethusdt", "solusdt")
                currentSymbol = coins[position]
                connectBinanceWebSocket()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupListeners(view: View) {
        val btnPriceMinus = view.findViewById<Button>(R.id.btnPriceMinus)
        val btnPricePlus = view.findViewById<Button>(R.id.btnPricePlus)
        val btnLastPrice = view.findViewById<Button>(R.id.btnLastPrice)
        val btn25 = view.findViewById<Button>(R.id.btn25)
        val btn50 = view.findViewById<Button>(R.id.btn50)
        val btn75 = view.findViewById<Button>(R.id.btn75)
        val btn100 = view.findViewById<Button>(R.id.btn100)

        btnPriceMinus.setOnClickListener { adjustPrice(-10.0) }
        btnPricePlus.setOnClickListener { adjustPrice(10.0) }
        btnLastPrice.setOnClickListener { etPrice.setText("%.2f".format(currentPrice)) }

        btn25.setOnClickListener { etQuantity.setText("2500") }
        btn50.setOnClickListener { etQuantity.setText("5000") }
        btn75.setOnClickListener { etQuantity.setText("7500") }
        btn100.setOnClickListener { etQuantity.setText("10000") }

        sbLeverage.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val lev = progress.coerceAtLeast(1)
                btnLeverage.text = "${lev}x"
                updateCalculations()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateCalculations() }
            override fun afterTextChanged(s: Editable?) {}
        }
        etPrice.addTextChangedListener(watcher)
        etQuantity.addTextChangedListener(watcher)
    }

    private fun adjustPrice(delta: Double) {
        val p = etPrice.text.toString().toDoubleOrNull() ?: currentPrice
        etPrice.setText("%.2f".format((p + delta).coerceAtLeast(0.0)))
    }

    private fun updateCalculations() {
        val price = etPrice.text.toString().toDoubleOrNull() ?: currentPrice
        val qty = etQuantity.text.toString().toDoubleOrNull() ?: 0.0
        val lev = sbLeverage.progress.coerceAtLeast(1)

        val reqMargin = if (lev > 0) qty / lev else 0.0
        val estFee = qty * 0.0005
        val liqPrice = if (lev > 0) price * (1.0 - (1.0 / lev)) else 0.0

        tvOrderSummary.text = "Req Margin: %.2f USDT | Est Fee: %.2f USDT\nEst Liq Price: %.2f USDT | Est Bankruptcy: %.2f\nRisk/Reward: 1:2.5 | Max Loss: %.2f USDT"
            .format(reqMargin, estFee, liqPrice, liqPrice * 0.98, reqMargin)
    }

    private fun startFundingTimer() {
        object : CountDownTimer(28800000, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {}
        }.start()
    }

    private fun connectBinanceWebSocket() {
        webSocket?.close(1000, "Switching stream")
        val request = Request.Builder()
            .url("wss://stream.binance.com:9443/ws/${currentSymbol}@ticker")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                activity?.runOnUiThread {
                    try {
                        val json = JSONObject(text)
                        if (json.has("c")) {
                            currentPrice = json.getDouble("c")
                            val change = json.getDouble("P")
                            val high = json.getDouble("h")
                            val low = json.getDouble("l")
                            val vol = json.getDouble("q")

                            tvLastPrice.text = "%.2f".format(currentPrice)
                            tvPriceChange.text = "%+.2f%%".format(change)
                            tvPriceChange.setTextColor(resources.getColor(if (change >= 0) android.R.color.holo_green_light else android.R.color.holo_red_light))

                            tvTickerDetails.text = "Mark: %.2f | Index: %.2f\n24h H: %.2f | L: %.2f | Vol: %.2fM\nFunding: 0.0100%% in 07:12:45 | OI: 450M"
                                .format(currentPrice, currentPrice + 2, high, low, vol / 1000000)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        webSocket?.close(1000, "Fragment Closed")
    }
}
