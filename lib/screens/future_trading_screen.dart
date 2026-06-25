import 'package:flutter/material.dart';
import '../services/binance_sockets.dart';

class FutureTradingScreen extends StatefulWidget {
  const FutureTradingScreen({super.key});

  @override
  State<FutureTradingScreen> createState() => _FutureTradingScreenState();
}

class _FutureTradingScreenState extends State<FutureTradingScreen> {
  final _socketsService = BinanceSocketsService();
  
  // Dynamic App State Machine Options
  String _topActiveTab = "FUTURES";       // FUTURES, SPOT, BOT
  String _marginMode = "ISOLATED";        // CROSS, ISOLATED
  String _executionSide = "OPEN";         // OPEN, CLOSE
  String _orderType = "MARKET";           // MARKET, LIMIT
  double _leverageValue = 20.0;           // Slider 1x to 125x
  double _orderbookFilterSize = 1.0;      // 0.1, 1, 10, 100
  bool _tpSlChecked = false;
  String _bottomTab = "POSITIONS";        // POSITIONS, ORDERS, HISTORY

  // Live WebSocket State Arrays
  double _liveCryptoPrice = 92480.0;
  double _24hChangePct = 3.14;
  List<dynamic> _orderBookAsks = [];
  List<dynamic> _orderBookBids = [];

  // Precision Input Entry Handlers
  final TextEditingController _priceInputController = TextEditingController(text: "92480.0");
  final TextEditingController _amountInputController = TextEditingController(text: "0.025");
  final TextEditingController _tpController = TextEditingController();
  final TextEditingController _slController = TextEditingController();

  // Active Account State Monitors
  bool _hasRunningPosition = true;
  double _positionEntryPrice = 91950.0;
  double _positionAmountSize = 0.085;
  String _positionSideType = "LONG";

  @override
  void initState() {
    super.initState();
    _priceInputController.addListener(_recomputeUIMetrics);
    _amountInputController.addListener(_recomputeUIMetrics);
  }

  void _recomputeUIMetrics() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _priceInputController.dispose();
    _amountInputController.dispose();
    _tpController.dispose();
    _slController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // Advanced Computational Vector Formulations
    double enteredPriceValue = double.tryParse(_priceInputController.text) ?? _liveCryptoPrice;
    if (_orderType == "MARKET") enteredPriceValue = _liveCryptoPrice;
    double enteredAmountValue = double.tryParse(_amountInputController.text) ?? 0.0;

    double dynamicNotionalValue = enteredPriceValue * enteredAmountValue;
    double realMarginRequired = _leverageValue > 0 ? (dynamicNotionalValue / _leverageValue) : 0.0;
    
    return Scaffold(
      backgroundColor: const Color(0xFFF4F5F8), // Premium High-Contrast Milk White Base Tone
      body: SafeArea(
        child: StreamBuilder<Map<String, dynamic>>(
          stream: _socketsService.connectExchangeStreams(),
          builder: (context, snapshot) {
            if (snapshot.hasData) {
              final networkData = snapshot.data!;
              if (networkData['stream'] == 'btcusdt@depth5') {
                _orderBookAsks = networkData['data']['asks'] ?? [];
                _orderBookBids = networkData['data']['bids'] ?? [];
              } else if (networkData['stream'] == 'btcusdt@ticker') {
                final stats = networkData['data'];
                _liveCryptoPrice = double.tryParse(stats['c'] ?? '92480.0') ?? 92480.0;
                _24hChangePct = double.tryParse(stats['P'] ?? '3.14') ?? 3.14;
              }
            }

            return Column(
              children: [
                _buildTopHeaderControlDesk(),
                _buildDynamicSecondTickerSection(),
                Expanded(
                  child: SingleChildScrollView(
                    physics: const ClampingScrollPhysics(),
                    child: Column(
                      children: [
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 10.0, vertical: 6.0),
                          child: Row(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              // LEFT SECTION - DENSE TRADING PANEL CONTROL MATRIX
                              Expanded(flex: 13, child: _buildLeftTradingControlPanel(realMarginRequired, enteredAmountValue)),
                              const SizedBox(width: 10),
                              // RIGHT SECTION - SYSTEM DEPTH ORDER BOOK
                              Expanded(flex: 11, child: _buildRightOrderbookPanel()),
                            ],
                          ),
                        ),
                        _buildBottomTabConsoleSelector(),
                        _buildBottomTabWorkspaceContent(realMarginRequired),
                      ],
                    ),
                  ),
                )
              ],
            );
          },
        ),
      ),
    );
  }

  // =========================================================
  // 1. TOP BAR AREA
  // =========================================================
  Widget _buildTopHeaderControlDesk() {
    return Container(
      color: Colors.white,
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      child: Row(
        mainAxisAlignment: Main====MainAxisAlignment.spaceBetween,
        children: [
          Row(
            children: ["Futures", "Spot", "Bot"].map((mode) {
              final bool isSel = _topActiveTab == mode.toUpperCase();
              return GestureDetector(
                onTap: () => setState(() => _topActiveTab = mode.toUpperCase()),
                child: Container(
                  margin: const EdgeInsets.only(right: 12),
                  padding: const EdgeInsets.symmetric(vertical: 4, horizontal: 2),
                  decoration: BoxDecoration(
                    border: Border(bottom: BorderSide(color: isSel ? const Color(0xFFF0B90B) : Colors.transparent, width: 2)),
                  ),
                  child: Text(
                    mode,
                    style: TextStyle(color: isSel ? Colors.black : Colors.black38, fontSize: 13, fontWeight: FontWeight.bold),
                  ),
                ),
              );
            }).toList(),
          ),
          IconButton(
            icon: const Icon(Icons.candlestick_chart, color: Colors.black87, size: 20),
            onPressed: () {},
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(),
          )
        ],
      ),
    );
  }

  // =========================================================
  // 2. SECOND PRICE STATS STRIP SECTION
  // =========================================================
  Widget _buildDynamicSecondTickerSection() {
    final bool isGreen = _24hChangePct >= 0;
    return Container(
      color: const Color(0xFFEBF0F5),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Row(
            children: const [
              Text("BTC/USDT", style: TextStyle(color: Colors.black, fontWeight: FontWeight.w900, fontSize: 15)),
              Icon(Icons.arrow_drop_down, color: Colors.black54, size: 16),
            ],
          ),
          Row(
            children: [
              Text(
                _liveCryptoPrice.toStringAsFixed(1),
                style: TextStyle(color: isGreen ? const Color(0xFF0ECB81) : const Color(0xFFF6465D), fontWeight: FontWeight.w900, fontSize: 14, fontFamily: 'monospace'),
              ),
              const SizedBox(width: 8),
              Text(
                "H \$${(_liveCryptoPrice * 1.015).toStringAsFixed(0)}",
                style: const TextStyle(color: Colors.black45, fontSize: 9, fontWeight: FontWeight.bold),
              ),
              const SizedBox(width: 6),
              Text(
                "L \$${(_liveCryptoPrice * 0.982).toStringAsFixed(0)}",
                style: const TextStyle(color: Colors.black45, fontSize: 9, fontWeight: FontWeight.bold),
              ),
              const SizedBox(width: 8),
              Text(
                "${isGreen ? '+' : ''}${_24hChangePct.toStringAsFixed(2)}%",
                style: TextStyle(color: isGreen ? const Color(0xFF0ECB81) : const Color(0xFFF6465D), fontWeight: FontWeight.bold, fontSize: 10),
              )
            ],
          )
        ],
      ),
    );
  }

  // =========================================================
  // 3. LEFT PANEL: ADVANCED TRADING CORE EXECUTIVE PANEL
  // =========================================================
  Widget _buildLeftTradingControlPanel(double marginCost, double tokenVolume) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // Cross / Isolated Matrix Toggle Switches
        Row(
          children: ["CROSS", "ISOLATED"].map((mode) {
            final bool isSel = _marginMode == mode;
            return Expanded(
              child: GestureDetector(
                onTap: () => setState(() => _marginMode = mode),
                child: Container(
                  margin: const EdgeInsets.symmetric(horizontal: 1),
                  padding: const EdgeInsets.symmetric(vertical: 5),
                  decoration: BoxDecoration(
                    color: isSel ? const Color(0xFF1E2329) : Colors.white,
                    borderRadius: BorderRadius.circular(4),
                    border: Border.all(color: Colors.black.withOpacity(0.06)),
                  ),
                  alignment: Alignment.center,
                  child: Text(mode, style: TextStyle(color: isSel ? Colors.white : Colors.black87, fontSize: 10, fontWeight: FontWeight.bold)),
                ),
              ),
            );
          }).toList(),
        ),
        const SizedBox(height: 6),

        // Open / Close Processing Tabs
        Row(
          children: ["OPEN", "CLOSE"].map((side) {
            final bool isSel = _executionSide == side;
            return Expanded(
              child: GestureDetector(
                onTap: () => setState(() => _executionSide = side),
                child: Container(
                  margin: const EdgeInsets.symmetric(horizontal: 1),
                  padding: const EdgeInsets.symmetric(vertical: 5),
                  decoration: BoxDecoration(
                    color: isSel ? Colors.black : Colors.transparent,
                    borderRadius: BorderRadius.circular(4),
                    border: Border.all(color: isSel ? Colors.black : Colors.black12),
                  ),
                  alignment: Alignment.center,
                  child: Text(side, style: TextStyle(color: isSel ? Colors.white : Colors.black54, fontSize: 10, fontWeight: FontWeight.bold)),
                ),
              ),
            );
          }).toList(),
        ),
        const SizedBox(height: 8),

        // 1x -> 125x Draggable Dynamic Leverage Slider Engine
        Container(
          padding: const EdgeInsets.all(6),
          decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(6), border: Border.all(color: Colors.black.withOpacity(0.02))),
          child: Column(
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text("Leverage", style: TextStyle(fontSize: 10, color: Colors.black45, fontWeight: FontWeight.bold)),
                  Text("${_leverageValue.toInt()}x", style: const TextStyle(fontWeight: FontWeight.w900, color: Colors.black, fontSize: 11)),
                ],
              ),
              SliderTheme(
                data: SliderThemeData(
                  trackHeight: 2.0,
                  thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 5.0),
                  activeTrackColor: const Color(0xFFF0B90B),
                  inactiveTrackColor: Colors.black12,
                  thumbColor: const Color(0xFFF0B90B),
                ),
                child: Slider(
                  value: _leverageValue,
                  min: 1,
                  max: 125,
                  onChanged: (val) => setState(() => _leverageValue = val),
                ),
              )
            ],
          ),
        ),
        const SizedBox(height: 8),

        // Market / Limit Order Routing Mode Tab
        Row(
          children: ["MARKET", "LIMIT"].map((order) {
            final bool isSel = _orderType == order;
            return Expanded(
              child: GestureDetector(
                onTap: () => setState(() => _orderType = order),
                child: Container(
                  padding: const EdgeInsets.symmetric(vertical: 5),
                  decoration: BoxDecoration(
                    color: isSel ? const Color(0xFFEBF0F5) : Colors.transparent,
                    border: Border(bottom: BorderSide(color: isSel ? const Color(0xFFF0B90B) : Colors.black12, width: 1.5)),
                  ),
                  alignment: Alignment.center,
                  child: Text(order, style: TextStyle(color: isSel ? Colors.black : Colors.black38, fontSize: 9, fontWeight: FontWeight.bold)),
                ),
              ),
            );
          }).toList(),
        ),
        const SizedBox(height: 8),

        if (_orderType == "LIMIT") ...[
          _buildFormInputField(_priceInputController, "Price (USDT)"),
          const SizedBox(height: 6),
        ],

        _buildFormInputField(_amountInputController, "Amount (BTC)"),
        const SizedBox(height: 8),

        // Fractional Layout Quantity Trigger Percentage Array Shortcut Buttons
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [0.25, 0.50, 0.75, 1.00].map((fraction) {
            return GestureDetector(
              onTap: () {
                setState(() {
                  _amountInputController.text = (fraction * 0.12).toStringAsFixed(3);
                });
              },
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
                decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(4), border: Border.all(color: Colors.black12)),
                child: Text("${(fraction * 100).toInt()}%", style: const TextStyle(fontSize: 8.5, fontWeight: FontWeight.bold, color: Colors.black54)),
              ),
            );
          }).toList(),
        ),
        const SizedBox(height: 8),

        // TP/SL Dynamic Overlay Toggle Nodes
        Row(
          children: [
            SizedBox(
              height: 20,
              width: 20,
              child: Checkbox(
                value: _tpSlChecked,
                activeColor: const Color(0xFFF0B90B),
                onChanged: (val) => setState(() => _tpSlChecked = val ?? false),
              ),
            ),
            const SizedBox(width: 6),
            const Text("TP/SL Conditional Fields", style: TextStyle(fontSize: 10, color: Colors.black54, fontWeight: FontWeight.bold)),
          ],
        ),
        
        if (_tpSlChecked) ...[
          const SizedBox(height: 6),
          Row(
            children: [
              Expanded(child: _buildFormInputField(_tpController, "Take Profit")),
              const SizedBox(width: 4),
              Expanded(child: _buildFormInputField(_slController, "Stop Loss")),
            ],
          )
        ],
        const SizedBox(height: 8),

        // Available Asset Capital Monitor Row
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: const [
            Text("Avbl Balance", style: TextStyle(fontSize: 9.5, color: Colors.black38, fontWeight: FontWeight.bold)),
            Text("1,250.00 USDT", style: TextStyle(fontSize: 9.5, color: Colors.black87, fontWeight: FontWeight.w900, fontFamily: 'monospace')),
          ],
        ),
        const Divider(color: Colors.black10, height: 12),

        // Order Final Executable Output Metrics Tracker Row
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            const Text("Max Open", style: TextStyle(fontSize: 9.5, color: Colors.black38, fontWeight: FontWeight.bold)),
            Text("${(enteredAmountValue * 1.5).toStringAsFixed(3)} BTC", style: const TextStyle(fontSize: 9.5, color: Colors.black87, fontWeight: FontWeight.bold, fontFamily: 'monospace')),
          ],
        ),
        const SizedBox(height: 4),
        Center(
          child: Text(
            "Value Size: \$${dynamicNotionalValue.toStringAsFixed(1)} USDT",
            style: const TextStyle(fontSize: 9.5, color: Colors.black45, fontWeight: FontWeight.bold, fontFamily: 'monospace'),
          ),
        ),
        const SizedBox(height: 6),

        // CORE TRANSACTION FLASH TAP BUTTON CONTROLS
        ElevatedButton(
          style: ElevatedButton.styleFrom(
            backgroundColor: const Color(0xFF0ECB81),
            padding: const EdgeInsets.symmetric(vertical: 10),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(6)),
            elevation: 0,
          ),
          onPressed: () => setState(() {
            _hasRunningPosition = true;
            _positionSideType = "LONG";
            _positionEntryPrice = _liveCryptoPrice;
            _positionAmountSize = enteredAmountValue;
          }),
          child: const Text("Open Long", style: TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.w900)),
        ),
        const SizedBox(height: 4),
        ElevatedButton(
          style: ElevatedButton.styleFrom(
            backgroundColor: const Color(0xFFF6465D),
            padding: const EdgeInsets.symmetric(vertical: 10),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(6)),
            elevation: 0,
          ),
          onPressed: () => setState(() {
            _hasRunningPosition = true;
            _positionSideType = "SHORT";
            _positionEntryPrice = _liveCryptoPrice;
            _positionAmountSize = enteredAmountValue;
          }),
          child: const Text("Open Short", style: TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.w900)),
        ),
      ],
    );
  }

  Widget _buildFormInputField(TextEditingController ctrl, String placeholder) {
    return Container(
      height: 32,
      decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(6), border: Border.all(color: Colors.black.withOpacity(0.05))),
      child: TextField(
        controller: ctrl,
        keyboardType: const TextInputType.numberWithOptions(decimal: true),
        style: const TextStyle(fontSize: 11.5, fontFamily: 'monospace', fontWeight: FontWeight.bold, color: Colors.black87),
        decoration: InputDecoration(
          hintText: placeholder,
          hintStyle: const TextStyle(color: Colors.black26, fontSize: 10.5),
          contentPadding: const EdgeInsets.symmetric(horizontal: 8, vertical: 10),
          border: InputBorder.none,
        ),
      ),
    );
  }

  // =========================================================
  // 4. RIGHT PANEL: HIGH DENSITY COMPACT ORDER BOOK WINDOW
  // =========================================================
  Widget _buildRightOrderbookPanel() {
    return Container(
      decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(6)),
      padding: const EdgeInsets.all(4),
      child: Column(
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: const [
              Text("Rate / Time", style: TextStyle(fontSize: 8.5, color: Colors.black38, fontWeight: FontWeight.bold)),
              Text("0.0100% / 03:55", style: TextStyle(fontSize: 8.5, color: Colors.black87, fontWeight: FontWeight.bold, fontFamily: 'monospace')),
            ],
          ),
          const Divider(color: Colors.black10, height: 6),
          
          // SELL / ASKS ORDER ENGINE TRACKS
          Column(
            children: List.generate(5, (index) {
              double p = _liveCryptoPrice + (5 - index) * 3.5;
              double size = 0.021 + index * 0.045;
              return _buildOrderbookDepthLine(p.toStringAsFixed(1), size.toStringAsFixed(3), const Color(0xFFF6465D).withOpacity(0.06), const Color(0xFFF6465D));
            }),
          ),

          // INSTANT CENTER INTERMEDIATE TICK PRICE OVERLAY NODE
          Container(
            padding: const EdgeInsets.symmetric(vertical: 4, horizontal: 2),
            margin: const EdgeInsets.symmetric(vertical: 4),
            color: const Color(0xFFF4F5F8),
            alignment: Alignment.center,
            child: Text(
              _liveCryptoPrice.toStringAsFixed(1),
              style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w900, color: Colors.black87, fontFamily: 'monospace'),
            ),
          ),

          // BUY / BIDS ORDER ENGINE TRACKS
          Column(
            children: List.generate(5, (index) {
              double p = _liveCryptoPrice - (index + 1) * 3.5;
              double size = 0.654 - index * 0.09;
              return _buildOrderbookDepthLine(p.toStringAsFixed(1), size.toStringAsFixed(3), const Color(0xFF0ECB81).withOpacity(0.06), const Color(0xFF0ECB81));
            }),
          ),
          const SizedBox(height: 6),

          // HORIZONTAL PERCENTAGE DISTRIBUTION VISUALIZER ACCENT BAR
          Row(
            children: [
              const Text("62%", style: TextStyle(fontSize: 8, color: Color(0xFF0ECB81), fontWeight: FontWeight.bold)),
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 4.0),
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(2),
                    child: SizedBox(
                      height: 4,
                      child: Row(
                        children: [
                          Expanded(flex: 62, child: Container(color: const Color(0xFF0ECB81))),
                          Expanded(flex: 38, child: Container(color: const Color(0xFFF6465D))),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
              const Text("38%", style: TextStyle(fontSize: 8, color: Color(0xFFF6465D), fontWeight: FontWeight.bold)),
            ],
          ),
          const SizedBox(height: 4),

          // ORDERBOOK QUANTITY SPAN DEVIATION TOGGLE
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [0.1, 1, 10].map((val) {
              final bool isSel = _orderbookFilterSize == val;
              return GestureDetector(
                onTap: () => setState(() => _orderbookFilterSize = val.toDouble()),
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
                  decoration: BoxDecoration(color: isSel ? const Color(0xFFEBF0F5) : Colors.transparent, borderRadius: BorderRadius.circular(2)),
                  child: Text(val.toString(), style: TextStyle(fontSize: 8.5, color: isSel ? Colors.black : Colors.black38, fontWeight: FontWeight.bold)),
                ),
              );
            }).toList(),
          )
        ],
      ),
    );
  }

  Widget _buildOrderbookDepthLine(String prc, String vol, Color backingColor, Color textStyleColor) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 2, horizontal: 4),
      margin: const EdgeInsets.symmetric(vertical: 0.5),
      decoration: BoxDecoration(color: backingColor, borderRadius: BorderRadius.circular(2)),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(prc, style: TextStyle(color: textStyleColor, fontSize: 9.5, fontFamily: 'monospace', fontWeight: FontWeight.bold)),
          Text(vol, style: const TextStyle(color: Colors.black54, fontSize: 9.5, fontFamily: 'monospace')),
        ],
      ),
    );
  }

  // =========================================================
  // 5. BOTTOM WORKSPACE CONSOLE NAV TABS MANAGER
  // =========================================================
  Widget _buildBottomTabConsoleSelector() {
    return Container(
      color: Colors.white,
      margin: const EdgeInsets.only(top: 8),
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: ["POSITIONS", "ORDERS", "HISTORY"].map((tab) {
          final bool isSel = _bottomTab == tab;
          return GestureDetector(
            onTap: () => setState(() => _bottomTab = tab),
            child: Container(
              padding: const EdgeInsets.symmetric(vertical: 6, horizontal: 10),
              decoration: BoxDecoration(
                border: Border(bottom: BorderSide(color: isSel ? const Color(0xFFF0B90B) : Colors.transparent, width: 2)),
              ),
              child: Text(
                tab,
                style: TextStyle(color: isSel ? Colors.black : Colors.black38, fontSize: 10.5, fontWeight: FontWeight.w900),
              ),
            ),
          );
        }).toList(),
      ),
    );
  }

  Widget _buildBottomTabWorkspaceContent(double requiredCost) {
    if (_bottomTab == "POSITIONS") {
      return _buildPositionsWorkspaceCard(requiredCost);
    }
    return Container(
      padding: const EdgeInsets.all(32),
      alignment: Alignment.center,
      child: Text("No Pending Active Records in $_bottomTab Panel", style: const TextStyle(color: Colors.black26, fontSize: 11, fontWeight: FontWeight.bold)),
    );
  }

  // =========================================================
  // 6. HIGH-FIDELITY ACTIVE PORTFOLIO CARD RENDERING NODE
  // =========================================================
  Widget _buildPositionsWorkspaceCard(double derivedCost) {
    if (!_hasRunningPosition) {
      return Container(
        padding: const EdgeInsets.all(40),
        child: const Center(child: Text("Zero Running Margin Exposures", style: TextStyle(color: Colors.black26, fontSize: 11, fontWeight: FontWeight.bold))),
      );
    }

    // Dynamic floating PNL evaluations mapping loops
    double structuralDelta = _liveCryptoPrice - _positionEntryPrice;
    if (_positionSideType == "SHORT") structuralDelta = _positionEntryPrice - _liveCryptoPrice;
    double yieldPercentage = (structuralDelta / _positionEntryPrice) * _leverageValue * 100;
    double dynamicPnlUsdt = (_positionEntryPrice * _positionAmountSize) * (yieldPercentage / 100) / _leverageValue;
    final bool isUpGreen = yieldPercentage >= 0;

    return Container(
      margin: const EdgeInsets.all(10),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(10),
        boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.015), blurRadius: 6)],
      ),
      child: Column(
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2),
                    decoration: BoxDecoration(
                      color: _positionSideType == "LONG" ? const Color(0xFF0ECB81).withOpacity(0.12) : const Color(0xFFF6465D).withOpacity(0.12),
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: Text(
                      "BTCUSDT ${_positionSideType} ${_leverageValue.toInt()}x",
                      style: TextStyle(color: _positionSideType == "LONG" ? const Color(0xFF0ECB81) : const Color(0xFFF6465D), fontSize: 10, fontWeight: FontWeight.bold),
                    ),
                  ),
                ],
              ),
              GestureDetector(
                onTap: () => setState(() => _hasRunningPosition = false),
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(color: const Color(0xFFEBF0F5), borderRadius: BorderRadius.circular(4)),
                  child: const Text("Market Close", style: TextStyle(fontSize: 9.5, fontWeight: FontWeight.bold, color: Colors.black87)),
                ),
              )
            ],
          ),
          const SizedBox(height: 12),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              _buildCardAttributeNode("Size Value", "${_positionAmountSize.toStringAsFixed(3)} BTC"),
              _buildCardAttributeNode("Margin Occupied", "\$${(derivedCost + 24.50).toStringAsFixed(2)}"),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  const Text("Unrealized PNL (ROI%)", style: TextStyle(color: Colors.black38, fontSize: 9, fontWeight: FontWeight.bold)),
                  Text(
                    "${isUpGreen ? '+' : ''}${yieldPercentage.toStringAsFixed(2)}% (\$${dynamicPnlUsdt.toStringAsFixed(2)})",
                    style: TextStyle(color: isUpGreen ? const Color(0xFF0ECB81) : const Color(0xFFF6465D), fontWeight: FontWeight.w900, fontSize: 11.5, fontFamily: 'monospace'),
                  )
                ],
              )
            ],
          ),
          const Padding(padding: EdgeInsets.symmetric(vertical: 4), child: Divider(color: Color(0xFFF4F5F8))),
          Row(
            mainAxisAlignment: Main====MainAxisAlignment.spaceBetween,
            children: [
              _buildCardAttributeNode("Entry Price", "\$${_positionEntryPrice.toStringAsFixed(1)}"),
              _buildCardAttributeNode("Mark Price", "\$${_liveCryptoPrice.toStringAsFixed(1)}"),
              _buildCardAttributeNode("Est. Liq Price", "\$${(_positionEntryPrice * (_positionSideType == "LONG" ? 0.954 : 1.043)).toStringAsFixed(1)}", metricColor: const Color(0xFFF6465D)),
            ],
          )
        ],
      ),
    );
  }

  Widget _buildCardAttributeNode(String descriptor, String valueText, {Color metricColor = Colors.black87}) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(descriptor, style: const TextStyle(color: Colors.black38, fontSize: 9, fontWeight: FontWeight.bold)),
        const SizedBox(height: 1.5),
        Text(valueText, style: TextStyle(color: metricColor, fontSize: 10.5, fontWeight: FontWeight.bold, fontFamily: 'monospace')),
      ],
    );
  }
}
