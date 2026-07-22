package com.practice.cryptoapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CryptoAdapter(private var cryptoList: List<CryptoItem>) :
    RecyclerView.Adapter<CryptoAdapter.CryptoViewHolder>() {

    class CryptoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvRank: TextView = itemView.findViewById(R.id.tvRank)
        val tvSymbol: TextView = itemView.findViewById(R.id.tvSymbol)
        val tvPrice: TextView = itemView.findViewById(R.id.tvPrice)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CryptoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_crypto, parent, false)
        return CryptoViewHolder(view)
    }

    override fun onBindViewHolder(holder: CryptoViewHolder, position: Int) {
    val item = cryptoList[position]
    
    // 1. '# ' Hash Symbol Remove Kar Diya
    holder.tvRank.text = item.rank.toString()
    holder.tvSymbol.text = item.symbol
    holder.tvPrice.text = "$${item.price}"

    // 2. Price Change Dynamic Color Logic (Green for UP, Red for DOWN, Black for NO CHANGE)
    when (item.priceState) {
        PriceState.UP -> holder.tvPrice.setTextColor(android.graphics.Color.parseColor("#0ECB81"))   // Green
        PriceState.DOWN -> holder.tvPrice.setTextColor(android.graphics.Color.parseColor("#F6465D")) // Red
        PriceState.NEUTRAL -> holder.tvPrice.setTextColor(android.graphics.Color.parseColor("#1E2329")) // Dark Black/Gray
    }
}


    override fun getItemCount(): Int = cryptoList.size

    fun updateData(newList: List<CryptoItem>) {
        cryptoList = newList
        notifyDataSetChanged()
    }
}
