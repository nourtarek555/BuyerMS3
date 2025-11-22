package com.example.signallingms1

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class CartAdapter(
    private val cartItems: MutableList<CartItem>,
    private val onQuantityChanged: (String, String, Int) -> Unit,  // productId, sellerId, quantity
    private val onItemRemoved: (String) -> Unit
) : RecyclerView.Adapter<CartAdapter.CartViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cart, parent, false)
        return CartViewHolder(view)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        holder.bind(cartItems[position])
    }

    override fun getItemCount(): Int = cartItems.size

    fun updateCartItems(newItems: List<CartItem>) {
        cartItems.clear()
        cartItems.addAll(newItems)
        notifyDataSetChanged()
    }

    inner class CartViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val productImage: ImageView = itemView.findViewById(R.id.ivProductImage)
        private val productName: TextView = itemView.findViewById(R.id.tvProductName)
        private val productPrice: TextView = itemView.findViewById(R.id.tvProductPrice)
        private val quantityText: TextView = itemView.findViewById(R.id.tvQuantity)
        private val totalPrice: TextView = itemView.findViewById(R.id.tvTotalPrice)
        private val btnDecrease: Button = itemView.findViewById(R.id.btnDecrease)
        private val btnIncrease: Button = itemView.findViewById(R.id.btnIncrease)

        fun bind(cartItem: CartItem) {
            productName.text = cartItem.productName
            productPrice.text = "$${String.format("%.2f", cartItem.price)} each"
            quantityText.text = cartItem.quantity.toString()
            totalPrice.text = "$${String.format("%.2f", cartItem.getTotalPrice())}"

            Glide.with(itemView.context)
                .load(cartItem.imageUrl)
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .into(productImage)

            // Disable increase button if at max stock (handle old items without maxStock)
            val maxStock = if (cartItem.maxStock > 0) cartItem.maxStock else cartItem.quantity
            btnIncrease.isEnabled = cartItem.quantity < maxStock

            btnDecrease.setOnClickListener {
                val newQuantity = cartItem.quantity - 1
                if (newQuantity <= 0) {
                    onItemRemoved(cartItem.productId)
                } else {
                    onQuantityChanged(cartItem.productId, cartItem.sellerId, newQuantity)
                }
            }

            btnIncrease.setOnClickListener {
                val newQuantity = cartItem.quantity + 1
                // Check will be done in CartFragment after fetching from database
                onQuantityChanged(cartItem.productId, cartItem.sellerId, newQuantity)
            }
        }
    }
}

