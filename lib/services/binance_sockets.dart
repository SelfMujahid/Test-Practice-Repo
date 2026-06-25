import 'package:web_socket_channel/web_socket_channel.dart';
import 'dart:convert';

class BinanceSocketsService {
  // Ultra-optimized global stream for all tickers
  Stream<List<dynamic>> connectAllMarkets() {
    final channel = WebSocketChannel.connect(
      Uri.parse('wss://stream.binance.com:9443/ws/!ticker@arr'),
    );
    return channel.stream.map((snapshot) {
      final parsed = jsonDecode(snapshot);
      if (parsed is List) {
        return parsed;
      }
      return [];
    });
  }
}
