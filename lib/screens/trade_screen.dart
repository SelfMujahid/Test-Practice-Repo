import 'package:flutter/material.dart';
import '../services/binance_sockets.dart';

class TradeScreen extends StatefulWidget {
  const TradeScreen({super.key});
  @override
  State<TradeScreen> createState() => _TradeScreenState();
}

class _TradeScreenState extends State<TradeScreen> {
  final _socketsService = BinanceSocketsService();
  List<dynamic> _bids = [];
  List<dynamic> _asks = [];
  String _livePrice = "0.0";

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0B0E11),
      appBar: AppBar(
        title: const Text('BTC/USDT TERMINAL', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
        backgroundColor: const Color(0xFF12161A),
        elevation: 0,
      ),
      body: StreamBuilder<Map<String, dynamic>>(
        stream: _socketsService.connectToExchange(),
        builder: (context, snapshot) {
          if (snapshot.hasData) {
            final streamData = snapshot.data!;
            final String streamName = streamData['stream'] ?? '';
            
            if (streamName == 'btcusdt@depth5') {
              final data = streamData['data'];
              _bids = data['bids'] ?? [];
              _asks = data['asks'] ?? [];
            } else if (streamName == 'btcusdt@ticker') {
              _livePrice = double.parse(streamData['data']['c'] ?? '0.0').toStringAsFixed(2);
            }
          }

          return Row(
            children: [
              // Left Panel: Real Professional Live OrderBook
              Expanded(
                flex: 4,
                child: Container(
                  color: const Color(0xFF12161A),
                  padding: const EdgeInsets.all(8),
                  child: Column(
                    children: [
                      const Text("Asks (Sellers)", style: TextStyle(color: Colors.redAccent, fontSize: 12, fontWeight: FontWeight.bold)),
                      const SizedBox(height: 6),
                      Expanded(
                        child: ListView.builder(
                          reverse: true,
                          itemCount: _asks.length > 8 ? 8 : _asks.length,
                          itemBuilder: (context, i) => _buildOrderRow(_asks[i][0], _asks[i][1], Colors.redAccent.withOpacity(0.1), Colors.redAccent),
                        ),
                      ),
                      Padding(
                        padding: const EdgeInsets.symmetric(vertical: 10.0),
                        child: Text('\$_livePrice', style: const TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.bold, fontFamily: 'monospace')),
                      ),
                      const Text("Bids (Buyers)", style: TextStyle(color: Colors.greenAccent, fontSize: 12, fontWeight: FontWeight.bold)),
                      const SizedBox(height: 6),
                      Expanded(
                        child: ListView.builder(
                          itemCount: _bids.length > 8 ? 8 : _bids.length,
                          itemBuilder: (context, i) => _buildOrderRow(_bids[i][0], _bids[i][1], Colors.greenAccent.withOpacity(0.1), Colors.greenAccent),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              // Right Panel: Trading Core Execution Terminal
              Expanded(
                flex: 5,
                child: Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      TextField(
                        keyboardType: TextInputType.number,
                        style: const TextStyle(color: Colors.white),
                        decoration: InputDecoration(
                          labelText: 'Amount (BTC)',
                          labelStyle: TextStyle(color: Colors.white.withOpacity(0.55)),
                          filled: true,
                          fillColor: const Color(0xFF1E2329),
                          border: OutlineInputBorder(borderRadius: BorderRadius.circular(8), borderSide: BorderSide.none),
                        ),
                      ),
                      const SizedBox(height: 16),
                      ElevatedButton(
                        style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFF2EBD85), padding: const EdgeInsets.symmetric(vertical: 16)),
                        onPressed: () {},
                        child: const Text('BUY BTC', style: TextStyle(fontWeight: FontWeight.bold, color: Colors.white)),
                      ),
                      const SizedBox(height: 12),
                      ElevatedButton(
                        style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFFDF294A), padding: const EdgeInsets.symmetric(vertical: 16)),
                        onPressed: () {},
                        child: const Text('SELL BTC', style: TextStyle(fontWeight: FontWeight.bold, color: Colors.white)),
                      ),
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

  Widget _buildOrderRow(String price, String amount, Color bgColor, Color textColor) {
    double p = double.parse(price);
    double a = double.parse(amount);
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 4, horizontal: 4),
      margin: const EdgeInsets.symmetric(vertical: 1),
      decoration: BoxDecoration(color: bgColor, borderRadius: BorderRadius.circular(4)),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(p.toStringAsFixed(1), style: TextStyle(color: textColor, fontSize: 11, fontFamily: 'monospace', fontWeight: FontWeight.bold)),
          Text(a.toStringAsFixed(3), style: const TextStyle(color: Colors.white70, fontSize: 11, fontFamily: 'monospace')),
        ],
      ),
    );
  }
}
