import 'package:flutter/material.dart';
import '../services/binance_sockets.dart';

class FutureTradingScreen extends StatefulWidget {
  const FutureTradingScreen({super.key});
  @override
  State<FutureTradingScreen> createState() => _FutureTradingScreenState();
}

class _FutureTradingScreenState extends State<FutureTradingScreen> {
  final _socketsService = BinanceSocketsService();
  
  // Terminal Option States
  String _tradeType = "FUTURE"; 
  String _marginMode = "ISOLATED"; // CROSS or ISOLATED
  String _orderType = "MARKET"; // MARKET or LIMIT
  double _leverage = 20.0;
  
  // Text Controllers for dynamic calculations
  final TextEditingController _priceController = TextEditingController(text: "92500");
  final TextEditingController _amountController = TextEditingController(text: "0.05");
  final TextEditingController _tpController = TextEditingController();
  final TextEditingController _slController = TextEditingController();

  List<dynamic> _bids = [];
  List<dynamic> _asks = [];
  double _currentMarketPrice = 92500.0;
  
  // Real active position simulator state mapping
  bool _hasActivePosition = false;
  String _positionSide = "LONG";
  double _positionEntryPrice = 0.0;
  double _positionAmount = 0.0;
  double _positionLeverage = 20.0;

  @override
  void initState() {
    super.initState();
    _priceController.addListener(_rebuildOnInput);
    _amountController.addListener(_rebuildOnInput);
  }

  void _rebuildOnInput() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _priceController.dispose();
    _amountController.dispose();
    _tpController.dispose();
    _slController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // Math Parameters Computation Engine
    double inputPrice = double.tryParse(_priceController.text) ?? _currentMarketPrice;
    if (_orderType == "MARKET") inputPrice = _currentMarketPrice;
    double inputAmount = double.tryParse(_amountController.text) ?? 0.0;
    
    double totalNotionalSize = inputPrice * inputAmount;
    double marginCostRequired = _leverage > 0 ? (totalNotionalSize / _leverage) : 0.0;
    
    // Liquidation Price calculation mockup matrix logic
    double liquidationPrice = _positionSide == "LONG" 
        ? inputPrice * (1 - (1 / _leverage) + 0.004)
        : inputPrice * (1 + (1 / _leverage) - 0.004);

    return Scaffold(
      backgroundColor: const Color(0xFFF5F6F9), // Premium Milk White Template Base
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        automaticallyImplyLeading: false,
        title: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: ["SPOT", "FUTURE", "BIT"].map((mode) {
            final bool isSel = _tradeType == mode;
            return GestureDetector(
              onTap: () => setState(() => _tradeType = mode),
              child: Container(
                margin: const EdgeInsets.symmetric(horizontal: 6),
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
                decoration: BoxDecoration(color: isSel ? const Color(0xFFF0B90B) : Colors.transparent, borderRadius: BorderRadius.circular(12)),
                child: Text(mode, style: TextStyle(color: isSel ? Colors.black : Colors.black54, fontWeight: FontWeight.bold, fontSize: 13)),
              ),
            );
          }).toList(),
        ),
        actions: [
          IconButton(icon: const Icon(Icons.candlestick_chart, color: Colors.black87), onPressed: () {})
        ],
      ),
      body: SafeArea(
        child: StreamBuilder<Map<String, dynamic>>(
          stream: _socketsService.connectExchangeStreams(),
          builder: (context, snapshot) {
            if (snapshot.hasData) {
              final streamData = snapshot.data!;
              if (streamData['stream'] == 'btcusdt@depth5') {
                _bids = streamData['data']['bids'] ?? [];
                _asks = streamData['data']['asks'] ?? [];
              } else if (streamData['stream'] == 'btcusdt@ticker') {
                _currentMarketPrice = double.tryParse(streamData['data']['c'] ?? '92500') ?? 92500.0;
              }
            }

            // Real-time floating PNL calculation engine
            double unrealizedPnlPercent = 0.0;
            double unrealizedPnlUsdt = 0.0;
            if (_hasActivePosition) {
              double priceDiff = _currentMarketPrice - _positionEntryPrice;
              if (_positionSide == "SHORT") priceDiff = _positionEntryPrice - _currentMarketPrice;
              unrealizedPnlPercent = (priceDiff / _positionEntryPrice) * _positionLeverage * 100;
              unrealizedPnlUsdt = (_positionEntryPrice * _positionAmount) * (unrealizedPnlPercent / 100) / _positionLeverage;
            }

            return Column(
              children: [
                Expanded(
                  child: SingleChildScrollView(
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        // Left Panel: Real Professional Dynamic OrderBook
                        Expanded(
                          flex: 4,
                          child: Container(
                            color: Colors.white,
                            padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 8),
                            child: Column(
                              children: [
                                const Text("Asks (Sellers)", style: TextStyle(color: Colors.red, fontSize: 11, fontWeight: FontWeight.bold)),
                                const SizedBox(height: 4),
                                SizedBox(
                                  height: 140,
                                  child: ListView.builder(
                                    itemCount: _asks.length > 7 ? 7 : _asks.length,
                                    itemBuilder: (c, i) => _buildOrderbookRow(_asks[i][0], _asks[i][1], Colors.red.withOpacity(0.06), Colors.red),
                                  ),
                                ),
                                Container(
                                  padding: const EdgeInsets.symmetric(vertical: 8),
                                  child: Text(
                                    _currentMarketPrice.toStringAsFixed(1),
                                    style: TextStyle(color: _asks.isNotEmpty ? Colors.red : Colors.green, fontSize: 14, fontWeight: FontWeight.black, fontFamily: 'monospace'),
                                  ),
                                ),
                                const Text("Bids (Buyers)", style: TextStyle(color: Colors.green, fontSize: 11, fontWeight: FontWeight.bold)),
                                const SizedBox(height: 4),
                                SizedBox(
                                  height: 140,
                                  child: ListView.builder(
                                    itemCount: _bids.length > 7 ? 7 : _bids.length,
                                    itemBuilder: (c, i) => _buildOrderbookRow(_bids[i][0], _bids[i][1], Colors.green.withOpacity(0.06), Colors.green),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                        
                        // Right Panel: Trading Controls Terminal
                        Expanded(
                          flex: 6,
                          child: Padding(
                            padding: const EdgeInsets.all(10.0),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.stretch,
                              children: [
                                // Cross/Isolated selector grid switch
                                Row(
                                  children: ["CROSS", "ISOLATED"].map((mode) {
                                    final bool isSel = _marginMode == mode;
                                    return Expanded(
                                      child: GestureDetector(
                                        onTap: () => setState(() => _marginMode = mode),
                                        child: Container(
                                          margin: const EdgeInsets.symmetric(horizontal: 2),
                                          padding: const EdgeInsets.symmetric(vertical: 6),
                                          decoration: BoxDecoration(color: isSel ? Colors.black : Colors.white, borderRadius: BorderRadius.circular(6), border: Border.all(color: Colors.black12)),
                                          alignment: Alignment.center,
                                          child: Text(mode, style: TextStyle(color: isSel ? Colors.white : Colors.black87, fontWeight: FontWeight.bold, fontSize: 10)),
                                        ),
                                      ),
                                    );
                                  }).toList(),
                                ),
                                const SizedBox(height: 8),
                                
                                // Leverage Slider Dashboard Node
                                Container(
                                  padding: const EdgeInsets.all(6),
                                  decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(8)),
                                  child: Column(
                                    children: [
                                      Row(
                                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                        children: [
                                          const Text("Leverage Slider", style: TextStyle(fontSize: 11, color: Colors.black54)),
                                          Text("${_leverage.toInt()}x", style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.black)),
                                        ],
                                      ),
                                      Slider(
                                        value: _leverage,
                                        min: 1,
                                        max: 125,
                                        activeColor: const Color(0xFFF0B90B),
                                        inactiveColor: Colors.black12,
                                        onChanged: (val) => setState(() => _leverage = val),
                                      ),
                                    ],
                                  ),
                                ),
                                const SizedBox(height: 8),

                                // Order Placement Config Type (Limit / Market)
                                Row(
                                  children: ["MARKET", "LIMIT"].map((type) {
                                    final bool isSel = _orderType == type;
                                    return Expanded(
                                      child: GestureDetector(
                                        onTap: () => setState(() => _orderType = type),
                                        child: Container(
                                          margin: const EdgeInsets.symmetric(horizontal: 2),
                                          padding: const EdgeInsets.symmetric(vertical: 6),
                                          decoration: BoxDecoration(color: isSel ? const Color(0xFF1E2329) : Colors.white, borderRadius: BorderRadius.circular(6), border: Border.all(color: Colors.black12)),
                                          alignment: Alignment.center,
                                          child: Text(type, style: TextStyle(color: isSel ? Colors.white : Colors.black87, fontWeight: FontWeight.bold, fontSize: 10)),
                                        ),
                                      ),
                                    );
                                  }).toList(),
                                ),
                                const SizedBox(height: 8),

                                if (_orderType == "LIMIT") ...[
                                  TextField(
                                    controller: _priceController,
                                    decoration: InputDecoration(hintText: "Trigger Price (USDT)", filled: true, fillColor: Colors.white, contentPadding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8), border: OutlineInputBorder(borderRadius: BorderRadius.circular(8), borderSide: BorderSide.none)),
                                    keyboardType: TextInputType.number,
                                    style: const TextStyle(fontSize: 13, fontFamily: 'monospace'),
                                  ),
                                  const SizedBox(height: 8),
                                ],

                                TextField(
                                  controller: _amountController,
                                  decoration: InputDecoration(hintText: "Amount (BTC)", filled: true, fillColor: Colors.white, contentPadding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8), border: OutlineInputBorder(borderRadius: BorderRadius.circular(8), borderSide: BorderSide.none)),
                                  keyboardType: TextInputType.number,
                                  style: const TextStyle(fontSize: 13, fontFamily: 'monospace'),
                                ),
                                const SizedBox(height: 8),

                                // Protection TP / SL fields grid
                                Row(
                                  children: [
                                    Expanded(
                                      child: TextField(
                                        controller: _tpController,
                                        decoration: InputDecoration(hintText: "Take Profit", filled: true, fillColor: Colors.white, contentPadding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8), border: OutlineInputBorder(borderRadius: BorderRadius.circular(6), borderSide: BorderSide.none)),
                                        keyboardType: TextInputType.number,
                                        style: const TextStyle(fontSize: 11),
                                      ),
                                    ),
                                    const SizedBox(width: 4),
                                    Expanded(
                                      child: TextField(
                                        controller: _slController,
                                        decoration: InputDecoration(hintText: "Stop Loss", filled: true, fillColor: Colors.white, contentPadding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8), border: OutlineInputBorder(borderRadius: BorderRadius.circular(6), borderSide: BorderSide.none)),
                                        keyboardType: TextInputType.number,
                                        style: const TextStyle(fontSize: 11),
                                      ),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 10),

                                // Computational Dashboard Stats Panel
                                Container(
                                  padding: const EdgeInsets.all(8),
                                  decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(8)),
                                  child: Column(
                                    children: [
                                      _buildMetricsRow("Cost (Margin Required)", "\$${marginCostRequired.toStringAsFixed(2)}"),
                                      const SizedBox(height: 4),
                                      _buildMetricsRow("Est. Liq Price", "\$${liquidationPrice.toStringAsFixed(1)}"),
                                    ],
                                  ),
                                ),
                                const SizedBox(height: 10),

                                // Transaction Buttons
                                Row(
                                  children: [
                                    Expanded(
                                      child: ElevatedButton(
                                        style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFF2EBD85), padding: const EdgeInsets.symmetric(vertical: 12), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8))),
                                        onPressed: () {
                                          setState(() {
                                            _hasActivePosition = true;
                                            _positionSide = "LONG";
                                            _positionEntryPrice = _currentMarketPrice;
                                            _positionAmount = inputAmount;
                                            _positionLeverage = _leverage;
                                          });
                                        },
                                        child: const Text("Buy / Long", style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 13)),
                                      ),
                                    ),
                                    const SizedBox(width: 6),
                                    Expanded(
                                      child: ElevatedButton(
                                        style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFFDF294A), padding: const EdgeInsets.symmetric(vertical: 12), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8))),
                                        onPressed: () {
                                          setState(() {
                                            _hasActivePosition = true;
                                            _positionSide = "SHORT";
                                            _positionEntryPrice = _currentMarketPrice;
                                            _positionAmount = inputAmount;
                                            _positionLeverage = _leverage;
                                          });
                                        },
                                        child: const Text("Sell / Short", style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 13)),
                                      ),
                                    ),
                                  ],
                                )
                              ],
                            ),
                          ),
                        )
                      ],
                    ),
                  ),
                ),

                // Bottom Panel: Active Margin Real Positions Console Matrix
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(12),
                  decoration: const BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.only(topLeft: Radius.circular(16), topRight: Radius.circular(16)),
                    boxShadow: [BoxShadow(color: Colors.black12, blurRadius: 6)],
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text("Positions Console", style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14, color: Colors.black87)),
                      const SizedBox(height: 8),
                      !_hasActivePosition
                          ? const Center(child: Padding(padding: EdgeInsets.symmetric(vertical: 16), child: Text("No Active Positions Running", style: TextStyle(color: Colors.black38, fontSize: 12))))
                          : Row(
                              mainAxisAlignment: MainAxisAlignment.spaceBetween,
                              children: [
                                Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Row(
                                      children: [
                                        Container(
                                          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                          decoration: BoxDecoration(color: _positionSide == "LONG" ? Colors.green.withOpacity(0.1) : Colors.red.withOpacity(0.1), borderRadius: BorderRadius.circular(4)),
                                          child: Text("BTCUSDT $_positionSide ${_positionLeverage.toInt()}x", style: TextStyle(color: _positionSide == "LONG" ? Colors.green : Colors.red, fontSize: 11, fontWeight: FontWeight.bold)),
                                        ),
                                      ],
                                    ),
                                    const SizedBox(height: 4),
                                    Text("Size: $_positionAmount BTC", style: const TextStyle(fontSize: 11, color: Colors.black54)),
                                    Text("Entry Price: \$${_positionEntryPrice.toStringAsFixed(1)}", style: const TextStyle(fontSize: 11, color: Colors.black54, fontFamily: 'monospace')),
                                  ],
                                ),
                                Column(
                                  crossAxisAlignment: CrossAxisAlignment.end,
                                  children: [
                                    const Text("Unrealized PNL", style: TextStyle(fontSize: 10, color: Colors.black38, fontWeight: FontWeight.bold)),
                                    Text(
                                      "${unrealizedPnlPercent >= 0 ? '+' : ''}${unrealizedPnlPercent.toStringAsFixed(2)}% (\$${unrealizedPnlUsdt.toStringAsFixed(2)})",
                                      style: TextStyle(color: unrealizedPnlPercent >= 0 ? Colors.green : Colors.red, fontWeight: FontWeight.bold, fontSize: 12, fontFamily: 'monospace'),
                                    ),
                                    const SizedBox(height: 4),
                                    TextButton(
                                      style: TextButton.styleFrom(backgroundColor: Colors.black.withOpacity(0.05), padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 2), minimumSize: Size.zero, tapTargetSize: MaterialTapTargetSize.shrinkWrap),
                                      onPressed: () => setState(() => _hasActivePosition = false),
                                      child: const Text("Close", style: TextStyle(color: Colors.black87, fontSize: 11, fontWeight: FontWeight.bold)),
                                    )
                                  ],
                                )
                              ],
                            ),
                    ],
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }

  Widget _buildOrderbookRow(String price, String amount, Color bgColor, Color textColor) {
    double p = double.tryParse(price) ?? 0.0;
    double a = double.tryParse(amount) ?? 0.0;
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 3, horizontal: 4),
      margin: const EdgeInsets.symmetric(vertical: 1),
      decoration: BoxDecoration(color: bgColor, borderRadius: BorderRadius.circular(4)),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(p.toStringAsFixed(1), style: TextStyle(color: textColor, fontSize: 10, fontFamily: 'monospace', fontWeight: FontWeight.bold)),
          Text(a.toStringAsFixed(3), style: const TextStyle(color: Colors.black54, fontSize: 10, fontFamily: 'monospace')),
        ],
      ),
    );
  }

  Widget _buildMetricsRow(String label, String value) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(label, style: const TextStyle(fontSize: 11, color: Colors.black45, fontWeight: FontWeight.w500)),
        Text(value, style: const TextStyle(fontSize: 11, color: Colors.black87, fontWeight: FontWeight.bold, fontFamily: 'monospace')),
      ],
    );
  }
}
