package com.example.signallingms1

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class OrderAdapter(
    private val orders: MutableList<Order>,
    private val onOrderClick: (Order) -> Unit
) : RecyclerView.Adapter<OrderAdapter.OrderViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order, parent, false)
        return OrderViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(orders[position])
    }

    override fun getItemCount(): Int = orders.size

    fun updateOrders(newOrders: List<Order>) {
        orders.clear()
        orders.addAll(newOrders)
        notifyDataSetChanged()
    }

    inner class OrderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvOrderId: TextView = itemView.findViewById(R.id.tvOrderId)
        private val tvSellerName: TextView = itemView.findViewById(R.id.tvSellerName)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvTotalPrice: TextView = itemView.findViewById(R.id.tvTotalPrice)
        private val tvOrderDate: TextView = itemView.findViewById(R.id.tvOrderDate)
        private val tvItemCount: TextView = itemView.findViewById(R.id.tvItemCount)

        fun bind(order: Order) {
            // Store order in tag for delivery type access
            itemView.tag = order
            
            // Order ID (shortened)
            val shortOrderId = if (order.orderId.length > 12) {
                order.orderId.substring(0, 12) + "..."
            } else {
                order.orderId
            }
            tvOrderId.text = "Order #$shortOrderId"

            // Seller name
            tvSellerName.text = order.sellerName.ifEmpty { "Unknown Seller" }

            // Status with color coding - pass order for delivery type context
            tvStatus.text = getStatusDisplayText(order.status, order.deliveryType)
            tvStatus.setTextColor(getStatusColor(order.status))

            // Total price
            tvTotalPrice.text = "$${String.format("%.2f", order.totalPrice)}"

            // Order date
            val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            tvOrderDate.text = dateFormat.format(Date(order.timestamp))

            // Item count
            val itemCount = order.items.values.sumOf { it.quantity }
            tvItemCount.text = "$itemCount item${if (itemCount != 1) "s" else ""}"

            // Click listener
            itemView.setOnClickListener {
                onOrderClick(order)
            }
        }

        private fun getStatusDisplayText(status: String, deliveryType: String = ""): String {
            val deliveryTypeLower = deliveryType.lowercase()
            
            return when (status.lowercase()) {
                "pending" -> "Waiting for Seller Approval"
                "accepted" -> "Accepted"
                "rejected" -> "Rejected"
                "ready" -> if (deliveryTypeLower == "delivery") {
                    "Being Delivered"
                } else {
                    "Ready for Pickup"
                }
                "preparing" -> "Preparing"
                "delivering" -> if (deliveryTypeLower == "pickup") {
                    "Ready for Pickup"
                } else {
                    "Out for Delivery"
                }
                "delivered" -> "Delivered"
                "cancelled" -> "Cancelled"
                else -> status.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
        }

        private fun getStatusColor(status: String): Int {
            return when (status.lowercase()) {
                "pending" -> ContextCompat.getColor(itemView.context, android.R.color.holo_orange_dark)
                "accepted" -> ContextCompat.getColor(itemView.context, android.R.color.holo_blue_dark)
                "rejected" -> ContextCompat.getColor(itemView.context, android.R.color.holo_red_dark)
                "ready" -> ContextCompat.getColor(itemView.context, android.R.color.holo_green_dark)
                "preparing" -> ContextCompat.getColor(itemView.context, android.R.color.holo_blue_light)
                "delivering" -> ContextCompat.getColor(itemView.context, android.R.color.holo_purple)
                "delivered" -> ContextCompat.getColor(itemView.context, android.R.color.holo_green_dark)
                "cancelled" -> ContextCompat.getColor(itemView.context, android.R.color.darker_gray)
                else -> ContextCompat.getColor(itemView.context, android.R.color.black)
            }
        }
    }
}

