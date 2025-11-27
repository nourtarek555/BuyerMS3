
// Defines the package this class belongs to.
package com.example.signallingms1

// Android-specific imports for managing preferences.
import android.content.Context
import android.content.SharedPreferences
// Firebase imports for database interactions.
import com.google.firebase.database.FirebaseDatabase
// Gson import for JSON serialization and deserialization.
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Manages the user's shopping cart.
 * This object is a singleton, meaning there is only one instance of it throughout the app.
 * It handles adding, removing, and updating items in the cart, and persists the cart data
 * locally using SharedPreferences. It also interacts with Firebase to manage product inventory (stock).
 */
object CartManager {
    // A constant for the name of the SharedPreferences file.
    private const val PREFS_NAME = "cart_prefs"
    // A constant for the key used to store cart items in SharedPreferences.
    private const val KEY_CART_ITEMS = "cart_items"
    // An instance of Gson for converting cart data to and from JSON format.
    private val gson = Gson()

    /**
     * Retrieves the SharedPreferences instance for the cart.
     *
     * @param context The application context.
     * @return The SharedPreferences instance.
     */
    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Adds a product to the cart with stock validation and inventory update.
     * It first decrements the stock in Firebase. If successful, it adds the item to the local cart.
     *
     * @param context The application context.
     * @param product The product to add to the cart.
     * @param quantity The quantity of the product to add.
     * @param onComplete A callback that returns the result: success (boolean), a message (string), and the new stock level (Int?).
     */
    fun addToCart(
        context: Context,
        product: Product,
        quantity: Int = 1,
        onComplete: (Boolean, String, Int?) -> Unit
    ) {
        // Get current cart items and product stock.
        val cartItems = getCartItems(context).toMutableMap()
        val cartItem = cartItems[product.productId]
        val currentStock = product.getDisplayStock()

        // Calculate how many items can actually be added based on current stock.
        val quantityToAdd = if (cartItem != null) {
            val newQuantity = cartItem.quantity + quantity
            if (newQuantity > currentStock) {
                val available = currentStock - cartItem.quantity
                if (available > 0) available else 0
            } else {
                quantity
            }
        } else {
            if (quantity > currentStock) 0 else quantity
        }

        // If we can't add any items, inform the user.
        if (quantityToAdd <= 0) {
            val message = if (cartItem != null) {
                "Not enough stock. Maximum available: $currentStock (already have ${cartItem.quantity} in cart)"
            } else {
                "Not enough stock. Maximum available: $currentStock"
            }
            onComplete(false, message, currentStock)
            return
        }

        // Attempt to decrement the stock in Firebase first.
        InventoryManager.decrementStockOnAddToCart(
            productId = product.productId,
            sellerId = product.sellerId,
            quantity = quantityToAdd
        ) { success, newStock, errorMessage ->
            if (success) {
                // If stock was decremented successfully, add the item to the local cart.
                val finalQuantity = (cartItem?.quantity ?: 0) + quantityToAdd

                cartItems[product.productId] = CartItem(
                    productId = product.productId,
                    sellerId = product.sellerId,
                    productName = product.getDisplayName(),
                    price = product.getDisplayPrice(),
                    quantity = finalQuantity,
                    imageUrl = product.getDisplayImageUrl(),
                    maxStock = newStock
                )
                saveCartItems(context, cartItems)

                val message = if (quantityToAdd < quantity) {
                    "Only $quantityToAdd more available. Added $quantityToAdd to cart."
                } else {
                    "Added $quantityToAdd to cart"
                }
                onComplete(true, message, newStock)
            } else {
                // If stock update failed, inform the user.
                val message = errorMessage ?: "Failed to update inventory. Please try again."
                onComplete(false, message, currentStock)
            }
        }
    }

    /**
     * Removes an item from the cart and restores its stock in Firebase.
     *
     * @param context The application context.
     * @param productId The ID of the product to remove.
     * @param onComplete An optional callback that reports the success of the stock restoration.
     */
    fun removeFromCart(
        context: Context,
        productId: String,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val cartItems = getCartItems(context).toMutableMap()
        val removedItem = cartItems[productId]

        if (removedItem != null) {
            // Restore the stock in Firebase.
            InventoryManager.restoreStockOnRemoveFromCart(
                productId = productId,
                sellerId = removedItem.sellerId,
                quantity = removedItem.quantity
            ) { success, newStock ->
                android.util.Log.d("CartManager", "Stock restore for $productId: success=$success, newStock=$newStock")
                onComplete?.invoke(success)
            }
        }

        // Remove the item from the local cart and save.
        cartItems.remove(productId)
        saveCartItems(context, cartItems)
    }

    /**
     * Updates the quantity of an item in the cart and adjusts Firebase inventory accordingly.
     *
     * @param context The application context.
     * @param productId The ID of the product to update.
     * @param quantity The new quantity.
     * @param currentStock The current known stock of the item.
     * @param onComplete A callback with the result of the operation.
     */
    fun updateQuantity(
        context: Context,
        productId: String,
        quantity: Int,
        currentStock: Int? = null,
        onComplete: (Boolean, String, Int?) -> Unit
    ) {
        // If quantity is zero or less, remove the item.
        if (quantity <= 0) {
            removeFromCart(context, productId, onComplete = { success ->
                onComplete(success, "Item removed from cart", null)
            })
            return
        }

        val cartItems = getCartItems(context).toMutableMap()
        val existingItem = cartItems[productId]
        if (existingItem != null) {
            val quantityDiff = quantity - existingItem.quantity

            if (quantityDiff == 0) {
                onComplete(true, "Quantity unchanged", currentStock)
                return
            }

            // Fetch the latest stock from Firebase to ensure accuracy.
            val productsRef = FirebaseDatabase.getInstance()
                .getReference("Seller").child(existingItem.sellerId).child("Products").child(productId)

            productsRef.get().addOnSuccessListener { snapshot ->
                val stockValue = snapshot.child("stock").value
                val dbStock = when (stockValue) {
                    is Number -> stockValue.toInt()
                    else -> currentStock ?: existingItem.maxStock
                }

                // Check for sufficient stock if increasing quantity.
                if (quantityDiff > 0 && quantity > dbStock) {
                    onComplete(false, "Not enough stock. Maximum available: $dbStock", dbStock)
                    return@addOnSuccessListener
                }

                // Update inventory based on the quantity change.
                if (quantityDiff > 0) { // Increasing quantity
                    InventoryManager.decrementStockOnAddToCart(productId, existingItem.sellerId, quantityDiff) { success, newStock, error ->
                        if (success) {
                            existingItem.quantity = quantity
                            existingItem.maxStock = newStock
                            saveCartItems(context, cartItems)
                            onComplete(true, "Quantity updated", newStock)
                        } else {
                            onComplete(false, error ?: "Inventory update failed", dbStock)
                        }
                    }
                } else { // Decreasing quantity
                    InventoryManager.restoreStockOnRemoveFromCart(productId, existingItem.sellerId, -quantityDiff) { success, newStock ->
                        if (success) {
                            existingItem.quantity = quantity
                            existingItem.maxStock = newStock
                            saveCartItems(context, cartItems)
                            onComplete(true, "Quantity updated", newStock)
                        } else {
                            onComplete(false, "Inventory update failed", dbStock)
                        }
                    }
                }
            }.addOnFailureListener {
                // Handle failure to fetch stock from Firebase.
                onComplete(false, "Could not verify stock. Please try again.", currentStock)
            }
        } else {
            onComplete(false, "Item not found in cart", null)
        }
    }

    /**
     * Retrieves all items from the cart.
     *
     * @param context The application context.
     * @return A map of cart items, with product ID as the key.
     */
    fun getCartItems(context: Context): Map<String, CartItem> {
        val json = getSharedPreferences(context).getString(KEY_CART_ITEMS, null)
        return if (json != null) {
            val type = object : TypeToken<Map<String, CartItem>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } else {
            emptyMap()
        }
    }

    /**
     * Retrieves all items from the cart as a list.
     *
     * @param context The application context.
     * @return A list of CartItem objects.
     */
    fun getCartItemsList(context: Context): List<CartItem> {
        return getCartItems(context).values.toList()
    }

    /**
     * Calculates the total price of all items in the cart.
     *
     * @param context The application context.
     * @return The total price.
     */
    fun getTotalPrice(context: Context): Double {
        return getCartItemsList(context).sumOf { it.getTotalPrice() }
    }

    /**
     * Clears all items from the cart.
     *
     * @param context The application context.
     * @param restoreStock Whether to restore the stock in Firebase. Should be false after a successful order.
     * @param onComplete An optional callback to indicate when the process is complete.
     */
    fun clearCart(context: Context, restoreStock: Boolean = true, onComplete: ((Boolean) -> Unit)? = null) {
        val cartItems = getCartItems(context)

        if (cartItems.isEmpty()) {
            getSharedPreferences(context).edit().remove(KEY_CART_ITEMS).apply()
            onComplete?.invoke(true)
            return
        }

        if (!restoreStock) {
            getSharedPreferences(context).edit().remove(KEY_CART_ITEMS).apply()
            onComplete?.invoke(true)
            return
        }

        // Restore stock for all items before clearing.
        var completedRestores = 0
        val totalItems = cartItems.size
        var allSuccess = true

        cartItems.forEach { (_, cartItem) ->
            InventoryManager.restoreStockOnRemoveFromCart(
                cartItem.productId,
                cartItem.sellerId,
                cartItem.quantity
            ) { success, _ ->
                if (!success) allSuccess = false
                completedRestores++
                if (completedRestores == totalItems) {
                    getSharedPreferences(context).edit().remove(KEY_CART_ITEMS).apply()
                    onComplete?.invoke(allSuccess)
                }
            }
        }
    }

    /**
     * Gets the total number of individual items in the cart.
     *
     * @param context The application context.
     * @return The total item count.
     */
    fun getCartItemCount(context: Context): Int {
        return getCartItemsList(context).sumOf { it.quantity }
    }

    /**
     * Saves the current cart items to SharedPreferences.
     *
     * @param context The application context.
     * @param items The map of cart items to save.
     */
    private fun saveCartItems(context: Context, items: Map<String, CartItem>) {
        val json = gson.toJson(items)
        getSharedPreferences(context).edit().putString(KEY_CART_ITEMS, json).apply()
    }
}
