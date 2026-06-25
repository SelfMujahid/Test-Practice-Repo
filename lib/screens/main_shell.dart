import 'package:flutter/material.dart';
import 'dashboard.dart';

class MainShell extends StatefulWidget {
  const MainShell({super.key});

  @override
  State<MainShell> createState() => _MainShellState();
}

class _MainShellState extends State<MainShell> {
  int _selectedIndex = 0;
  
  final List<Widget> _screens = [
    const DashboardScreen(),
    const Scaffold(body: Center(child: Text('Trading Desk Console Connected', style: TextStyle(color: Colors.greenAccent, fontWeight: FontWeight.bold)))),
    const Scaffold(body: Center(child: Text('Market Analytics Node Online', style: TextStyle(color: Color(0xFFF0B90B), fontWeight: FontWeight.bold)))),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(index: _selectedIndex, children: _screens),
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: _selectedIndex,
        backgroundColor: const Color(0xFF12161A),
        selectedItemColor: const Color(0xFFF0B90B),
        unselectedItemColor: Colors.white30,
        selectedLabelStyle: const TextStyle(fontWeight: FontWeight.bold, fontSize: 12),
        onTap: (index) => setState(() => _selectedIndex = index),
        items: const [
          BottomNavigationBarItem(icon: Icon(Icons.home_filled), label: 'Home'),
          BottomNavigationBarItem(icon: Icon(Icons.insert_chart_outlined), label: 'Trading'),
          BottomNavigationBarItem(icon: Icon(Icons.analytics_outlined), label: 'Analytics'),
        ],
      ),
    );
  }
}
