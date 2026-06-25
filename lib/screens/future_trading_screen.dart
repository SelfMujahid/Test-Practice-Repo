import 'package:flutter/material.dart';
import '../services/binance_sockets.dart';

class FutureTradingScreen extends StatefulWidget {
  const FutureTradingScreen({super.key});
  @override
  State<FutureTradingScreen> createState() => _FutureTradingScreenState();
}

class _FutureTradingScreenState extends State<FutureTradingScreen> {
  final _socketsService = BinanceSocketsService();
  String _tradeType = "FUTURE";
  List<dynamic> _bids = [];
  List<dynamic> _asks = [];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF5F6F9),
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        title: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: ["SPOT", "FUTURE", "BIT"].map((mode) {
            final bool isSel = _tradeType == mode;
            return GestureDetector(
              onTap: () => setState(() => _tradeType = mode),
              child: Container(
                margin: const EdgeInsets.symmetric(horizontal: 8),
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
      body: StreamBuilder<Map<String, dynamic>>(
        stream: _socketsService.connectExchangeStreams(),
        builder: (context, snapshot) {
          if (snapshot.hasData) {
            final streamData = snapshot.data!;
            if (streamData['stream'] == 'btcusdt@depth5') {
              _bids = streamData['data']['bids'] ?? [];
              _asks = streamData['data']['asks'] ?? [];
            }
          }

          return Row(
            children: [
              // Left Orderbook
              Expanded(
                flex: 4,
                child: Container(
                  color: Colors.white,
                  padding: const EdgeInsets.all(6),
                  child: Column(
                    children: [
                      const Text("Asks", style: TextStyle(color: Colors.red, fontSize: 11, fontWeight: FontWeight.bold)),
                      Expanded(child: ListView.builder(itemCount: _asks.length, itemBuilder: (c, i) => Text(_asks[i][0], style: const TextStyle(color: Colors.red, fontSize: 11, fontFamily: 'monospace')))),
                      const Divider(),
                      const Text("Bids", style: TextStyle(color: Colors.green, fontSize: 11, fontWeight: FontWeight.bold)),
                      Expanded(child: ListView.builder(itemCount: _bids.length, itemBuilder: (c, i) => Text(_bids[i][0], style: const TextStyle(color: Colors.green, fontSize: 11, fontFamily: 'monospace')))),
                    ],
                  ),
                ),
              ),
              
              // Right Form
              Expanded(
                flex: 6,
                child: Padding(
                  padding: const EdgeInsets.all(12.0),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Container(padding: const EdgeInsets.all(6), color: Colors.black.withOpacity(0.08), child: const Text("Cross 20x", style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: Colors.black))),
                          Container(padding: const EdgeInsets.all(6), color: Colors.black.withOpacity(0.08), child: const Text("Limit Order", style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: Colors.black))),
                        ],
                      ),
                      const SizedBox(height: 20),
                      TextField(
                        decoration: InputDecoration(hintText: "Price (USDT)", filled: true, fillColor: Colors.white, border: OutlineInputBorder(borderRadius: BorderRadius.circular(8), borderSide: BorderSide.none)),
                        keyboardType: TextInputType.number,
                      ),
                      const SizedBox(height: 10),
                      TextField(
                        decoration: InputDecoration(hintText: "Amount (BTC)", filled: true, fillColor: Colors.white, border: OutlineInputBorder(borderRadius: BorderRadius.circular(8), borderSide: BorderSide.none)),
                        keyboardType: TextInputType.number,
                      ),
                      const SizedBox(height: 20),
                      ElevatedButton(style: ElevatedButton.styleFrom(backgroundColor: Colors.green), onPressed: () {}, child: const Text("Buy / Long", style: TextStyle(color: Colors.white))),
                      const SizedBox(height: 5),
                      ElevatedButton(style: ElevatedButton.styleFrom(backgroundColor: Colors.red), onPressed: () {}, child: const Text("Sell / Short", style: TextStyle(color: Colors.white))),
                    ],
                  ),
                ),
              )
            ],
          );
        },
      ),
    );
  }
}
