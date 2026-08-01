package com.practice.cryptoapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.practice.cryptoapp.TradeViewModel

@Composable
fun TradeScreen(viewModel: TradeViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    // Screen par wapas aate hi connection ensure karein
    LaunchedEffect(Unit) {
        viewModel.ensureActiveConnection()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E11))
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Header (Pair Name & Live Price)
        HeaderSection(state)

        // 2. Margin & Leverage Controls
        MarginLeverageSection(state, viewModel)

        // 3. Order Input Fields
        OrderFormSection(state, viewModel)

        // 4. Required Margin & Liq Price Summary
        OrderSummaryPanel(state)

        // 5. Buy & Sell Action Buttons
        ActionButtonsSection()

        // 6. Bottom Positions Section
        BottomTabsSection()
    }
}

@Composable
fun HeaderSection(state: com.practice.cryptoapp.TradeUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181A20)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${state.symbol} Perpetual",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                val isGreen = state.priceChangePercent >= 0
                Text(
                    text = "$%.2f (%+.2f%%)".format(state.price, state.priceChangePercent),
                    color = if (isGreen) Color(0xFF0ECB81) else Color(0xFFF6465D),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "24h High: $%.2f | 24h Low: $%.2f | Vol: %.1fB"
                    .format(state.high24h, state.low24h, state.volume24h),
                color = Color(0xFF848E9C),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun MarginLeverageSection(state: com.practice.cryptoapp.TradeUiState, viewModel: TradeViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181A20)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CustomModeChip(
                    text = if (state.isCrossMargin) "Cross" else "Isolated",
                    onClick = { viewModel.onMarginTypeChanged(!state.isCrossMargin) },
                    modifier = Modifier.weight(1f)
                )
                CustomModeChip(
                    text = "${state.leverage}x",
                    color = Color(0xFFF0B90B),
                    onClick = {},
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = state.leverage.toFloat(),
                onValueChange = { viewModel.onLeverageChanged(it.toInt()) },
                valueRange = 1f..125f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFF0B90B),
                    activeTrackColor = Color(0xFFF0B90B)
                )
            )
            Text(
                text = "Avail Balance: $%.2f USDT".format(state.availableBalance),
                color = Color(0xFF848E9C),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun OrderFormSection(state: com.practice.cryptoapp.TradeUiState, viewModel: TradeViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181A20)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SmallIconButton(text = "-") { viewModel.adjustPrice(-10.0) }
                OutlinedTextField(
                    value = state.inputPrice,
                    onValueChange = { viewModel.onPriceInputChanged(it) },
                    label = { Text("Price (USDT)", fontSize = 10.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFF0B90B),
                        unfocusedBorderColor = Color(0xFF2B313A)
                    )
                )
                SmallIconButton(text = "+") { viewModel.adjustPrice(10.0) }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = state.inputQuantity,
                onValueChange = { viewModel.onQuantityInputChanged(it) },
                label = { Text("Amount / Quantity (USDT)", fontSize = 10.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFF0B90B),
                    unfocusedBorderColor = Color(0xFF2B313A)
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(25, 50, 75, 100).forEach { pct ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF2B313A), RoundedCornerShape(4.dp))
                            .clickable { viewModel.setPercentageQuantity(pct.toDouble()) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "$pct%", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun OrderSummaryPanel(state: com.practice.cryptoapp.TradeUiState) {
    val qty = state.inputQuantity.toDoubleOrNull() ?: 0.0
    val reqMargin = if (state.leverage > 0) qty / state.leverage else 0.0
    val price = state.inputPrice.toDoubleOrNull() ?: state.price
    val estLiq = if (state.leverage > 0) price * (1.0 - (1.0 / state.leverage)) else 0.0

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181A20)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Req Margin: $%.2f USDT".format(reqMargin), color = Color(0xFF848E9C), fontSize = 11.sp)
            Text("Est Liq Price: $%.2f USDT".format(estLiq), color = Color(0xFF848E9C), fontSize = 11.sp)
            Text("Est Trading Fee: $%.2f USDT".format(qty * 0.0005), color = Color(0xFF848E9C), fontSize = 11.sp)
        }
    }
}

@Composable
fun ActionButtonsSection() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = {},
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0ECB81)),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.weight(1f).height(42.dp)
        ) {
            Text("Buy / Long", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = {},
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF6465D)),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.weight(1f).height(42.dp)
        ) {
            Text("Sell / Short", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun BottomTabsSection() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181A20)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Open Positions (0)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text("No Active Margin Positions", color = Color(0xFF848E9C), fontSize = 11.sp)
        }
    }
}

@Composable
fun CustomModeChip(text: String, color: Color = Color.White, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFF2B313A), RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SmallIconButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(Color(0xFF2B313A), RoundedCornerShape(4.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
