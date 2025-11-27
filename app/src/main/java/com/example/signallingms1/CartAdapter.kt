
// Package declaration for the application.
package com.example.signallingms1

// Imports for Android UI components and libraries.
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

/**
 * This adapter is responsible for displaying the items in the user's shopping cart.
 * It handles the creation of view holders, binding data to the views, and user interactions
 * such as changing the quantity of an item or removing it from the cart.
 *
 * @param cartItems A mutable list of CartItem objects to be displayed.
 * @param onQuantityChanged A lambda function to be invoked when the quantity of an item is changed.
 * @param onItemRemoved A lambda function to be invoked when an item is removed from the cart.
 */
class CartAdapter(
    // A mutable list of items in the cart.
    private val cartItems: MutableList<CartItem>,
    // Callback for when the quantity of an item changes. It receives productId, sellerId, and the new quantity.
    private val onQuantityChanged: (String, String, Int) -> Unit,
    // Callback for when an item is removed. It receives the productId.
    private val onItemRemoved: (String) -> Unit
) : RecyclerView.Adapter<CartAdapter.CartViewHolder>() {

    /**
     * Called when RecyclerView needs a new [CartViewHolder] of the given type to represent an item.
     *
     * @param parent The ViewGroup into which the new View will be added after it is bound to an adapter position.
     * @param viewType The view type of the new View.
     * @return A new CartViewHolder that holds a View of the given view type.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        // Inflate the layout for a single cart item.
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cart, parent, false)
        // Return a new ViewHolder instance.
        return CartViewHolder(view)
    }

    /**
     * Called by RecyclerView to display the data at the specified position.
     *
     * @param holder The ViewHolder which should be updated to represent the contents of the item at the given position in the data set.
     * @param position The position of the item within the adapter's data set.
     */
    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        // Bind the data from the cart item at the given position to the ViewHolder.
        holder.bind(cartItems[position])
    }

    /**
     * Returns the total number of items in the data set held by the adapter.
     *
     * @return The total number of items in this adapter.
     */
    override fun getItemCount(): Int = cartItems.size

    /**
     * Updates the list of cart items and notifies the adapter to refresh the view.
     *
     * @param newItems The new list of cart items.
     */
    fun updateCartItems(newItems: List<CartItem>) {
        // Clear the existing items.
        cartItems.clear()
        // Add all the new items.
        cartItems.addAll(newItems)
        // Notify the adapter that the data set has changed.
        notifyDataSetChanged()
    }

    /**
     * A ViewHolder describes an item view and metadata about its place within the RecyclerView.
     *
     * @param itemView The view for a single cart item.
     */
    inner class CartViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // ImageView for displaying the product image.
        private val productImage: ImageView = itemView.findViewById(R.id.ivProductImage)
        // TextView for displaying the product name.
        private val productName: TextView = itemView.findViewById(R.id.tvProductName)
        // TextView for displaying the product price.
        private val productPrice: TextView = itemView.findViewById(R.id.tvProductPrice)
        // TextView for displaying the quantity of the product.
        private val quantityText: TextView = itemView.findViewById(R.id.tvQuantity)
        // TextView for displaying the total price of the item (price * quantity).
        private val totalPrice: TextView = itemView.findViewById(R.id.tvTotalPrice)
        // Button to decrease the quantity of the item.
        private val btnDecrease: Button = itemView.findViewById(R.id.btnDecrease)
        // Button to increase the quantity of the item.
        private val btnIncrease: Button = itemView.findViewById(R.id.btnIncrease)

        /**
         * Binds the data from a CartItem object to the views in the ViewHolder.
         *
         * @param cartItem The cart item to bind.
         */
        fun bind(cartItem: CartItem) {
            // Set the product name.
            productName.text = cartItem.productName
            // Set the product price, formatted to two decimal places.
            productPrice.text = "$${String.format("%.2f", cartItem.price)} each"
            // Set the quantity.
            quantityText.text = cartItem.quantity.toString()
            // Set the total price, formatted to two decimal places.
            totalPrice.text = "$${String.format("%.2f", cartItem.getTotalPrice())}"

            // Load the product image using Glide.
            Glide.with(itemView.context)
                .load(cartItem.imageUrl)
                .placeholder(R.drawable.ic_person) // Placeholder image while loading.
                .error(R.drawable.ic_person) // Image to show if loading fails.
                .into(productImage)

            // Disable the increase button if the quantity reaches the maximum stock.
            // Handles older items that might not have a maxStock value.
            val maxStock = if (cartItem.maxStock > 0) cartItem.maxStock else cartItem.quantity
            btnIncrease.isEnabled = cartItem.quantity < maxStock

            // Set a click listener for the decrease button.
            btnDecrease.setOnClickListener {
                // Calculate the new quantity.
                val newQuantity = cartItem.quantity - 1
                // If the new quantity is zero or less, remove the item.
                if (newQuantity <= 0) {
                    onItemRemoved(cartItem.productId)
                } else {
                    // Otherwise, update the quantity.
                    onQuantityChanged(cartItem.productId, cartItem.sellerId, newQuantity)
                }
            }

            // Set a click listener for the increase button.
            btnIncrease.setOnClickListener {
                // Calculate the new quantity.
                val newQuantity = cartItem.quantity + 1
                // The check for max stock will be done in the CartFragment after fetching from the database.
                onQuantityChanged(cartItem.productId, cartItem.sellerId, newQuantity)
            }
        }
    }
}
