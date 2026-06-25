import 'package:web_socket_channel/web_socket_channel.dart';
import 'dart:convert';

class BinanceStreamService {
  late WebSocketChannel _channel;

  // Stream getter to subscribe to live BTC/USDT price ticker
  Stream<Map<String, dynamic>> get btcTickerStream {
    _channel = WebSocketChannel.connect(
      Uri.parse('wss://stream.binance.com:9443/ws/btcusdt@ticker'),
    );
    
    return _channel.stream.map((snapshot) {
      return jsonDecode(snapshot) as Map<String, dynamic>;
    });
  }

  // Closes the socket when app is minimized or page is changed
  void closeStream() {
    _channel.sink.close();
  }
}
