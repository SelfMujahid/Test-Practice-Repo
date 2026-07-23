package com.practice.cryptoapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class TradeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_trade, container, false)

        val tabSpot = view.findViewById<TextView>(R.id.tabSpot)
        val tabFutures = view.findViewById<TextView>(R.id.tabFutures)
        val tabBot = view.findViewById<TextView>(R.id.tabBot)

        // Default: Futures Selected
        tabFutures.setTextColor(resources.getColor(android.R.color.holo_blue_light))

        tabSpot.setOnClickListener {
            // Switch view for Spot
        }
        tabFutures.setOnClickListener {
            // Switch view for Futures
        }
        tabBot.setOnClickListener {
            // Switch view for Bot
        }

        return view
    }
}
