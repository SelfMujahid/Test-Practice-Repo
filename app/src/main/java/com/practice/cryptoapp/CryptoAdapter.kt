package com.practice.cryptoapp

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.practice.cryptoapp.R

class CryptoAdapter(private var cryptoList: List<CryptoItem>) :
    RecyclerView.Adapter<CryptoAdapter.CryptoViewHolder>() {

    private val mainHandler = Handler(Looper.getMainLooper())

    inner class CryptoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvRank: TextView = itemView.findViewById(R.id.tvRank)
        val tvName: TextView = itemView.findViewById(R.id.tvCoinName)
        val tvSymbol: TextView = itemView.findViewById(R.id.tvCoinSymbol)
        val tvPrice: TextView = itemView.findViewById(R.id.tvPrice)
        val tvChange1h: TextView = itemView.findViewById(R.id.tvChange1h)
        val tvChange24h: TextView = itemView.findViewById(R.id.tvChange24h)
        val tvChange7d: TextView = itemView.findViewById(R.id.tvChange7d)
        val imgLogo: ImageView = itemView.findViewById(R.id.imgCoinLogo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CryptoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_crypto, parent, false)
        return CryptoViewHolder(view)
    }

    override fun onBindViewHolder(holder: CryptoViewHolder, position: Int) {
        val item = cryptoList[position]

        holder.tvRank.text = item.rank.toString()
        holder.tvName.text = item.name
        holder.tvSymbol.text = item.symbol
        holder.tvPrice.text = "$${item.price}"

        // Image Loading via Glide
        if (item.logoUrl.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(item.logoUrl)
                .into(holder.imgLogo)
        }

        // 1h %
        bindPercentage(holder.tvChange1h, item.change1h)

        // 24h %
        bindPercentage(holder.tvChange24h, item.change24h)

        // 7d %
        bindPercentage(holder.tvChange7d, item.change7d)

        // Flash 0.5s Animation Logic
        if (item.isFlashed) {
            val animRes = if (item.priceState == PriceState.UP) R.anim.flash_up else R.anim.flash_down
            val flashColor = if (item.priceState == PriceState.UP) "#00E676" else "#FF1744"

            holder.tvPrice.setTextColor(Color.parseColor(flashColor))
            holder.tvPrice.startAnimation(AnimationUtils.loadAnimation(holder.itemView.context, animRes))

            mainHandler.postDelayed({
                holder.tvPrice.setTextColor(Color.parseColor("#111111"))
                item.isFlashed = false
            }, 500)
        } else {
            holder.tvPrice.setTextColor(Color.parseColor("#111111"))
        }
    }

    private fun bindPercentage(textView: TextView, value: Double) {
        val isUp = value >= 0
        textView.text = if (isUp) "+%.2f%%".format(value) else "%.2f%%".format(value)
        textView.setTextColor(if (isUp) Color.parseColor("#00C853") else Color.parseColor("#FF1744"))
    }

    override fun getItemCount(): Int = cryptoList.size

    fun updateData(newList: List<CryptoItem>) {
        this.cryptoList = newList
        notifyDataSetChanged()
    }
}
