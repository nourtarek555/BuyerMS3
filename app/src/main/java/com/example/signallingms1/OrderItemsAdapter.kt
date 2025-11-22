package com.example.signallingms1

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class OrderItemsAdapter(
    private val items: MutableList<CartItem>
) : RecyclerView.Adapter<OrderItemsAdapter.OrderItemViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order_product, parent, false)
        return OrderItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderItemViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<CartItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    inner class OrderItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val productImage: ImageView = itemView.findViewById(R.id.ivProductImage)
        private val productName: TextView = itemView.findViewById(R.id.tvProductName)
        private val productPrice: TextView = itemView.findViewById(R.id.tvProductPrice)
        private val productQuantity: TextView = itemView.findViewById(R.id.tvQuantity)
        private val itemTotal: TextView = itemView.findViewById(R.id.tvItemTotal)

        fun bind(cartItem: CartItem) {
            productName.text = cartItem.productName
            productPrice.text = "$${String.format("%.2f", cartItem.price)} each"
            productQuantity.text = "Qty: ${cartItem.quantity}"
            itemTotal.text = "$${String.format("%.2f", cartItem.getTotalPrice())}"

            Glide.with(itemView.context)
                .load(cartItem.imageUrl)
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .into(productImage)
        }
    }
}

