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
        holder.tvRank.text = "#${item.rank}"
        holder.tvSymbol.text = item.symbol
        holder.tvPrice.text = "$${item.price}"
    }

    override fun getItemCount(): Int = cryptoList.size

    fun updateData(newList: List<CryptoItem>) {
        cryptoList = newList
        notifyDataSetChanged()
    }
}
