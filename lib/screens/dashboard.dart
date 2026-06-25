import 'package:flutter/material.dart';
import '../services/binance_sockets.dart';
import 'package:intl/intl.dart';

class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key});
  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  final _socketsService = BinanceSocketsService();
  final currencyFormatter = NumberFormat.currency(symbol: '\$', decimalDigits: 2);
  
  String _searchQuery = "";
  double _demoBalance = 10000.00;
  final Map<String, Map<String, dynamic>> _tickerMap = {};

  String _getCryptoLogoUrl(String symbol) {
    return "https://raw.githubusercontent.com/spothq/cryptocurrency-icons/master/128/color/${symbol.toLowerCase()}.png";
  }

  void _openTradingViewChart(BuildContext context, String symbol) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: const Color(0xFF12161A),
      builder: (context) => Container(
        height: MediaQuery.of(context).size.height * 0.75,
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text("$symbol/USDT Real-time Chart", style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 16)),
                IconButton(icon: const Icon(Icons.close, color: Colors.white), onPressed: () => Navigator.pop(context)),
              ],
            ),
            const Divider(color: Colors.white10),
            Expanded(
              child: Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const Icon(Icons.candlestick_chart, size: 80, color: Color(0xFF2EBD85)),
                    const SizedBox(height: 10),
                    Text("TradingView Engine Simulation for $symbol", style: const TextStyle(color: Colors.white54)),
                    const SizedBox(height: 20),
                    Container(height: 150, width: double.infinity, color: Colors.white10, child: const Center(child: Text("📊 [Simulated K-Line Candles Data]", style: TextStyle(color: Colors.greenAccent)))),
                  ],
                ),
              ),
            )
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF5F6F9),
      body: SafeArea( // Fixed top battery bar overlap status
        child: StreamBuilder<Map<String, dynamic>>(
          stream: _socketsService.connectExchangeStreams(),
          builder: (context, snapshot) {
            if (snapshot.hasData) {
              final streamData = snapshot.data!;
              if (streamData.containsKey('stream') && streamData['stream'].toString().contains('@ticker')) {
                final data = streamData['data'];
                final String rawSym = data['s'].toString().replaceAll('USDT', '');
                _tickerMap[rawSym] = data;
              }
            }

            final keys = _tickerMap.keys.where((k) => k.toLowerCase().contains(_searchQuery.toLowerCase())).toList();

            return CustomScrollView(
              slivers: [
                SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.only(left: 16.0, right: 16.0, top: 12.0, bottom: 8.0),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Row(
                          children: [
                            Image.asset('assets/logo.webp', width: 36, height: 36, errorBuilder: (c, e, s) => const CircleAvatar(backgroundColor: Color(0xFFF0B90B), radius: 18, child: Icon(Icons.bolt, color: Colors.black))),
                            const SizedBox(width: 10),
                            const Text("EXCHANGE", style: TextStyle(color: Colors.black, fontWeight: FontWeight.w900, fontSize: 18)),
                          ],
                        ),
                        IconButton(
                          icon: const Icon(Icons.candlestick_chart, color: Colors.black87),
                          onPressed: () => _openTradingViewChart(context, "BTC"),
                        )
                      ],
                    ),
                  ),
                ),

                SliverToBoxAdapter(
                  child: Container(
                    margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                    padding: const EdgeInsets.all(20),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(16),
                      boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.04), blurRadius: 10, offset: const Offset(0, 4))],
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            const Text("Demo Portfolio Balance", style: TextStyle(color: Colors.black54, fontSize: 13, fontWeight: FontWeight.w500)),
                            IconButton(
                              icon: const Icon(Icons.refresh, color: Color(0xFFF0B90B), size: 18),
                              onPressed: () => setState(() => _demoBalance = 10000.00),
                            ),
                          ],
                        ),
                        Text(currencyFormatter.format(_demoBalance), style: const TextStyle(color: Colors.black, fontSize: 28, fontWeight: FontWeight.w900, fontFamily: 'monospace')),
                      ],
                    ),
                  ),
                ),

                SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
                    child: TextField(
                      onChanged: (val) => setState(() => _searchQuery = val),
                      style: const TextStyle(color: Colors.black),
                      decoration: InputDecoration(
                        hintText: 'Search asset maps...',
                        hintStyle: const TextStyle(color: Colors.black38),
                        prefixIcon: const Icon(Icons.search, color: Colors.black45),
                        filled: true,
                        fillColor: Colors.white,
                        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide.none),
                      ),
                    ),
                  ),
                ),

                keys.isEmpty
                    ? const SliverToBoxAdapter(child: Center(child: Padding(padding: EdgeInsets.all(40.0), child: CircularProgressIndicator(color: Color(0xFFF0B90B)))))
                    : SliverList(
                        delegate: SliverChildBuilderDelegate(
                          (context, index) {
                            final sym = keys[index];
                            final data = _tickerMap[sym]!;
                            final double price = double.parse(data['c'] ?? '0.0');
                            final double percent = double.parse(data['P'] ?? '0.0');
                            final bool isGreen = percent >= 0;

                            return Container(
                              margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
                              padding: const EdgeInsets.all(14),
                              decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(12), boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.01), blurRadius: 4)]),
                              child: Column(
                                children: [
                                  Row(
                                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                    children: [
                                      Row(
                                        children: [
                                          Image.network(_getCryptoLogoUrl(sym), width: 24, height: 24, errorBuilder: (c, e, s) => CircleAvatar(radius: 12, child: Text(sym[0]))),
                                          const SizedBox(width: 10),
                                          Text(sym, style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.black)),
                                        ],
                                      ),
                                      Text('\$${price.toStringAsFixed(2)}', style: const TextStyle(fontFamily: 'monospace', fontWeight: FontWeight.bold, color: Colors.black)),
                                    ],
                                  ),
                                  const SizedBox(height: 10),
                                  Row(
                                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                    children: [
                                      _buildTimeframe("1h", "${isGreen ? '+' : ''}${(percent * 0.12).toStringAsFixed(2)}%", isGreen),
                                      _buildTimeframe("4h", "${isGreen ? '+' : ''}${(percent * 0.52).toStringAsFixed(2)}%", isGreen),
                                      _buildTimeframe("24h", "${isGreen ? '+' : ''}${percent.toStringAsFixed(2)}%", isGreen),
                                      _buildTimeframe("7d", "${isGreen ? '+' : ''}${(percent * 1.9).toStringAsFixed(2)}%", isGreen),
                                    ],
                                  )
                                ],
                              ),
                            );
                          },
                          childCount: keys.length,
                        ),
                      )
              ],
            );
          },
        ),
      ),
    );
  }

  Widget _buildTimeframe(String label, String val, bool isGreen) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: const TextStyle(color: Colors.black38, fontSize: 10, fontWeight: FontWeight.bold)),
        Text(val, style: TextStyle(color: isGreen ? Colors.green : Colors.red, fontSize: 11, fontWeight: FontWeight.bold)),
      ],
    );
  }
}
