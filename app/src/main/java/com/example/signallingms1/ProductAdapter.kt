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

class ProductAdapter(
    private val products: MutableList<Product>,
    private val onAddToCart: (Product, Int) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_product, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(products[position])
    }

    override fun getItemCount(): Int = products.size

    fun updateProducts(newProducts: List<Product>) {
        products.clear()
        products.addAll(newProducts)
        notifyDataSetChanged()
    }

    inner class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val productImage: ImageView = itemView.findViewById(R.id.ivProductImage)
        private val productName: TextView = itemView.findViewById(R.id.tvProductName)
        private val productPrice: TextView = itemView.findViewById(R.id.tvProductPrice)
        private val productQuantity: TextView = itemView.findViewById(R.id.tvProductQuantity)
        private val quantityText: TextView = itemView.findViewById(R.id.tvQuantity)
        private val btnDecrease: Button = itemView.findViewById(R.id.btnDecrease)
        private val btnIncrease: Button = itemView.findViewById(R.id.btnIncrease)
        private val btnAddToCart: Button = itemView.findViewById(R.id.btnAddToCart)

        private var currentQuantity = 0
        private var currentProduct: Product? = null

        fun bind(product: Product) {
            // Store the current product to avoid issues with ViewHolder reuse
            currentProduct = product
            
            // Reset quantity when binding a new product
            currentQuantity = 0
            
            productName.text = product.getDisplayName()
            productPrice.text = "$${String.format("%.2f", product.getDisplayPrice())}"
            productQuantity.text = "Stock: ${product.getDisplayStock()}"
            quantityText.text = currentQuantity.toString()

            Glide.with(itemView.context)
                .load(product.getDisplayImageUrl())
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .into(productImage)

            btnDecrease.setOnClickListener {
                if (currentQuantity > 0) {
                    currentQuantity--
                    quantityText.text = currentQuantity.toString()
                }
            }

            btnIncrease.setOnClickListener {
                val product = currentProduct ?: return@setOnClickListener
                val availableStock = product.getDisplayStock()
                if (currentQuantity < availableStock) {
                    currentQuantity++
                    quantityText.text = currentQuantity.toString()
                } else {
                    Toast.makeText(itemView.context, "Not enough stock. Available: $availableStock", Toast.LENGTH_SHORT).show()
                }
            }

            btnAddToCart.setOnClickListener {
                val product = currentProduct ?: return@setOnClickListener
                if (currentQuantity > 0) {
                    onAddToCart(product, currentQuantity)
                    Toast.makeText(itemView.context, "Added $currentQuantity to cart", Toast.LENGTH_SHORT).show()
                    currentQuantity = 0
                    quantityText.text = "0"
                } else {
                    Toast.makeText(itemView.context, "Select quantity", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
