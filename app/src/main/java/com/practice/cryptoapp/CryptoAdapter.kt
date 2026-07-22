package com.practice.cryptoapp

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class CryptoAdapter(private var list: List<CryptoItem>) :
    RecyclerView.Adapter<CryptoAdapter.CryptoViewHolder>() {

    class CryptoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvRank: TextView = itemView.findViewById(R.id.tvRank)
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvSymbol: TextView = itemView.findViewById(R.id.tvSymbol)
        val tvPrice: TextView = itemView.findViewById(R.id.tvPrice)
        val tvChange: TextView = itemView.findViewById(R.id.tvChange)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CryptoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_crypto, parent, false)
        return CryptoViewHolder(view)
    }

    override fun onBindViewHolder(holder: CryptoViewHolder, position: Int) {
        val item = list[position]

        holder.tvRank.text = item.rank.toString()
        holder.tvName.text = item.name
        holder.tvSymbol.text = item.symbol
        holder.tvPrice.text = "$${item.price}"

        // Set 24h Change Badge Color & Text
        val isUp = item.change24h >= 0
        holder.tvChange.text = String.format(if (isUp) "▲ +%.2f%%" else "▼ %.2f%%", abs(item.change24h))
        holder.tvChange.setTextColor(ContextCompat.getColor(holder.itemView.context, if (isUp) R.color.green_up else R.color.red_down))
        holder.tvChange.setBackgroundResource(if (isUp) R.drawable.bg_change_up else R.drawable.bg_change_down)
    }

    // Partial Refresh for Smooth Flash Animations
    override fun onBindViewHolder(holder: CryptoViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            val direction = payloads[0] as String
            val item = list[position]

            holder.tvPrice.text = "$${item.price}"

            // 0.5 Sec Flash Animation (Green/Red -> Black)
            val startColor = if (direction == "UP") Color.parseColor("#00E676") else Color.parseColor("#FF1744")
            val endColor = Color.parseColor("#111111") // Default Black

            val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), startColor, endColor)
            colorAnimation.duration = 500 // 0.5 Seconds Flash
            colorAnimation.addUpdateListener { animator ->
                holder.tvPrice.setTextColor(animator.animatedValue as Int)
            }
            colorAnimation.start()
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount(): Int = list.size

    fun updateData(newList: List<CryptoItem>) {
        this.list = newList
        notifyDataSetChanged()
    }
}
