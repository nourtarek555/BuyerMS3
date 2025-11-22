package com.example.signallingms1

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object CartManager {
    private const val PREFS_NAME = "cart_prefs"
    private const val KEY_CART_ITEMS = "cart_items"
    private val gson = Gson()
    
    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Adds a product to cart with stock validation and inventory update
     * Decrements Firebase stock when adding to cart
     * @param onComplete Callback with (success, message, newStock)
     */
    fun addToCart(
        context: Context, 
        product: Product, 
        quantity: Int = 1,
        onComplete: (Boolean, String, Int?) -> Unit
    ) {
        val cartItems = getCartItems(context).toMutableMap()
        val cartItem = cartItems[product.productId]
        val currentStock = product.getDisplayStock()
        
        // Calculate how much to add
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
        
        // Check if we can add anything
        if (quantityToAdd <= 0) {
            val message = if (cartItem != null) {
                "Not enough stock. Maximum available: $currentStock (already have ${cartItem.quantity} in cart)"
            } else {
                "Not enough stock. Maximum available: $currentStock"
            }
            onComplete(false, message, currentStock)
            return
        }
        
        // Decrement stock in Firebase first
        InventoryManager.decrementStockOnAddToCart(
            productId = product.productId,
            sellerId = product.sellerId,
            quantity = quantityToAdd
        ) { success, newStock, errorMessage ->
            if (success) {
                // Stock decremented successfully, now add to cart
                val finalQuantity = if (cartItem != null) {
                    cartItem.quantity + quantityToAdd
                } else {
                    quantityToAdd
                }
                
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
                // Failed to decrement stock
                val message = errorMessage ?: "Failed to update inventory. Please try again."
                onComplete(false, message, currentStock)
            }
        }
    }
    
    /**
     * Legacy synchronous version - kept for backward compatibility
     * NOTE: This does NOT update Firebase inventory. Use the async version instead.
     */
    @Deprecated("Use addToCart with callback instead")
    fun addToCartSync(context: Context, product: Product, quantity: Int = 1): Pair<Boolean, String> {
        val cartItems = getCartItems(context).toMutableMap()
        val cartItem = cartItems[product.productId]
        val currentStock = product.getDisplayStock()
        
        if (cartItem != null) {
            val newQuantity = cartItem.quantity + quantity
            if (newQuantity > currentStock) {
                val available = currentStock - cartItem.quantity
                return if (available > 0) {
                    val finalQuantity = cartItem.quantity + available
                    cartItems[product.productId] = CartItem(
                        productId = cartItem.productId,
                        sellerId = cartItem.sellerId,
                        productName = cartItem.productName,
                        price = cartItem.price,
                        quantity = finalQuantity,
                        imageUrl = cartItem.imageUrl,
                        maxStock = currentStock
                    )
                    saveCartItems(context, cartItems)
                    Pair(true, "Only $available more available. Added $available to cart.")
                } else {
                    Pair(false, "Not enough stock. Maximum available: $currentStock (already have ${cartItem.quantity} in cart)")
                }
            } else {
                cartItems[product.productId] = CartItem(
                    productId = cartItem.productId,
                    sellerId = cartItem.sellerId,
                    productName = cartItem.productName,
                    price = cartItem.price,
                    quantity = newQuantity,
                    imageUrl = cartItem.imageUrl,
                    maxStock = currentStock
                )
                saveCartItems(context, cartItems)
                return Pair(true, "Added $quantity to cart")
            }
        } else {
            if (quantity > currentStock) {
                return Pair(false, "Not enough stock. Maximum available: $currentStock")
            }
            
            cartItems[product.productId] = CartItem(
                productId = product.productId,
                sellerId = product.sellerId,
                productName = product.getDisplayName(),
                price = product.getDisplayPrice(),
                quantity = quantity,
                imageUrl = product.getDisplayImageUrl(),
                maxStock = currentStock
            )
            saveCartItems(context, cartItems)
            return Pair(true, "Added $quantity to cart")
        }
    }
    
    fun removeFromCart(
        context: Context, 
        productId: String,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val cartItems = getCartItems(context).toMutableMap()
        val removedItem = cartItems[productId]
        
        if (removedItem != null) {
            // Restore stock in Firebase
            InventoryManager.restoreStockOnRemoveFromCart(
                productId = productId,
                sellerId = removedItem.sellerId,
                quantity = removedItem.quantity
            ) { success, newStock ->
                android.util.Log.d("CartManager", "Stock restore for $productId: success=$success, newStock=$newStock")
                onComplete?.invoke(success)
            }
        }
        
        cartItems.remove(productId)
        saveCartItems(context, cartItems)
    }
    
    /**
     * Updates quantity of an item in cart with stock validation and inventory update
     * @param onComplete Callback with (success, message, newStock)
     */
    fun updateQuantity(
        context: Context, 
        productId: String, 
        quantity: Int, 
        currentStock: Int? = null,
        onComplete: (Boolean, String, Int?) -> Unit
    ) {
        if (quantity <= 0) {
            removeFromCart(context, productId, onComplete = { success ->
                onComplete(success, "Item removed from cart", null)
            })
            return
        }
        
        val cartItems = getCartItems(context).toMutableMap()
        val existingItem = cartItems[productId]
        if (existingItem != null) {
            val oldQuantity = existingItem.quantity
            val quantityDiff = quantity - oldQuantity
            
            if (quantityDiff == 0) {
                onComplete(true, "Quantity unchanged", currentStock)
                return
            }
            
            // Fetch current stock from Firebase
            val productsRef = FirebaseDatabase.getInstance()
                .getReference("Seller").child(existingItem.sellerId).child("Products").child(productId)
            
            productsRef.get().addOnSuccessListener { snapshot ->
                val stockValue = if (snapshot.exists()) {
                    snapshot.child("stock").getValue(Any::class.java)
                } else {
                    // Try lowercase "products"
                    null
                }
                
                val dbStock = when (stockValue) {
                    is Int -> stockValue
                    is Long -> stockValue.toInt()
                    is String -> stockValue.toIntOrNull() ?: 0
                    is Number -> stockValue.toInt()
                    else -> currentStock ?: existingItem.maxStock
                }
                
                // If increasing quantity, check if enough stock
                if (quantityDiff > 0 && quantity > dbStock) {
                    onComplete(false, "Not enough stock. Maximum available: $dbStock", dbStock)
                    return@addOnSuccessListener
                }
                
                // Update inventory based on quantity difference
                if (quantityDiff > 0) {
                    // Need to decrement more stock
                    InventoryManager.decrementStockOnAddToCart(
                        productId = productId,
                        sellerId = existingItem.sellerId,
                        quantity = quantityDiff
                    ) { success, newStock, errorMessage ->
                        if (success) {
                            cartItems[productId] = CartItem(
                                productId = existingItem.productId,
                                sellerId = existingItem.sellerId,
                                productName = existingItem.productName,
                                price = existingItem.price,
                                quantity = quantity,
                                imageUrl = existingItem.imageUrl,
                                maxStock = newStock
                            )
                            saveCartItems(context, cartItems)
                            onComplete(true, "Quantity updated", newStock)
                        } else {
                            onComplete(false, errorMessage ?: "Failed to update inventory", dbStock)
                        }
                    }
                } else {
                    // Decreasing quantity - restore stock
                    val restoreQuantity = -quantityDiff
                    InventoryManager.restoreStockOnRemoveFromCart(
                        productId = productId,
                        sellerId = existingItem.sellerId,
                        quantity = restoreQuantity
                    ) { success, newStock ->
                        if (success) {
                            cartItems[productId] = CartItem(
                                productId = existingItem.productId,
                                sellerId = existingItem.sellerId,
                                productName = existingItem.productName,
                                price = existingItem.price,
                                quantity = quantity,
                                imageUrl = existingItem.imageUrl,
                                maxStock = newStock
                            )
                            saveCartItems(context, cartItems)
                            onComplete(true, "Quantity updated", newStock)
                        } else {
                            onComplete(false, "Failed to update inventory", dbStock)
                        }
                    }
                }
            }.addOnFailureListener {
                // Fallback to provided currentStock
                val maxStock = currentStock ?: existingItem.maxStock
                if (quantity > maxStock) {
                    onComplete(false, "Not enough stock. Maximum available: $maxStock", maxStock)
                } else {
                    cartItems[productId] = CartItem(
                        productId = existingItem.productId,
                        sellerId = existingItem.sellerId,
                        productName = existingItem.productName,
                        price = existingItem.price,
                        quantity = quantity,
                        imageUrl = existingItem.imageUrl,
                        maxStock = maxStock
                    )
                    saveCartItems(context, cartItems)
                    onComplete(true, "Quantity updated", maxStock)
                }
            }
        } else {
            onComplete(false, "Item not found in cart", null)
        }
    }
    
    /**
     * Legacy synchronous version - kept for backward compatibility
     */
    @Deprecated("Use updateQuantity with callback instead")
    fun updateQuantitySync(context: Context, productId: String, quantity: Int, currentStock: Int? = null): Pair<Boolean, String> {
        if (quantity <= 0) {
            removeFromCart(context, productId)
            return Pair(true, "Item removed from cart")
        }
        
        val cartItems = getCartItems(context).toMutableMap()
        val existingItem = cartItems[productId]
        if (existingItem != null) {
            val maxStock = currentStock ?: (if (existingItem.maxStock > 0) existingItem.maxStock else existingItem.quantity)
            
            if (quantity > maxStock) {
                return Pair(false, "Not enough stock. Maximum available: $maxStock")
            }
            
            cartItems[productId] = CartItem(
                productId = existingItem.productId,
                sellerId = existingItem.sellerId,
                productName = existingItem.productName,
                price = existingItem.price,
                quantity = quantity,
                imageUrl = existingItem.imageUrl,
                maxStock = maxStock
            )
            saveCartItems(context, cartItems)
            return Pair(true, "Quantity updated")
        }
        return Pair(false, "Item not found in cart")
    }
    
    fun getCartItems(context: Context): Map<String, CartItem> {
        val json = getSharedPreferences(context).getString(KEY_CART_ITEMS, null)
        return if (json != null) {
            val type = object : TypeToken<Map<String, CartItem>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } else {
            emptyMap()
        }
    }
    
    fun getCartItemsList(context: Context): List<CartItem> {
        return getCartItems(context).values.toList()
    }
    
    fun getTotalPrice(context: Context): Double {
        return getCartItemsList(context).sumOf { it.getTotalPrice() }
    }
    
    /**
     * Clears the cart
     * @param restoreStock If true, restores stock to Firebase (for manual cart clearing).
     *                     If false, doesn't restore stock (for after successful order placement).
     * @param onComplete Callback with success status
     */
    fun clearCart(context: Context, restoreStock: Boolean = true, onComplete: ((Boolean) -> Unit)? = null) {
        val cartItems = getCartItems(context)
        
        if (cartItems.isEmpty()) {
            getSharedPreferences(context).edit().remove(KEY_CART_ITEMS).apply()
            onComplete?.invoke(true)
            return
        }
        
        if (!restoreStock) {
            // Don't restore stock - just clear the cart (e.g., after successful order placement)
            android.util.Log.d("CartManager", "Clearing cart without restoring stock (order placed)")
            getSharedPreferences(context).edit().remove(KEY_CART_ITEMS).apply()
            onComplete?.invoke(true)
            return
        }
        
        // Restore stock for all items before clearing (manual cart clearing)
        var completedRestores = 0
        val totalItems = cartItems.size
        var allSuccess = true
        
        cartItems.forEach { (productId, cartItem) ->
            InventoryManager.restoreStockOnRemoveFromCart(
                productId = productId,
                sellerId = cartItem.sellerId,
                quantity = cartItem.quantity
            ) { success, newStock ->
                if (!success) {
                    allSuccess = false
                    android.util.Log.w("CartManager", "Failed to restore stock for $productId")
                }
                
                completedRestores++
                if (completedRestores == totalItems) {
                    // Clear cart regardless of restore success
                    getSharedPreferences(context).edit().remove(KEY_CART_ITEMS).apply()
                    onComplete?.invoke(allSuccess)
                }
            }
        }
    }
    
    fun getCartItemCount(context: Context): Int {
        return getCartItemsList(context).sumOf { it.quantity }
    }
    
    private fun saveCartItems(context: Context, items: Map<String, CartItem>) {
        val json = gson.toJson(items)
        getSharedPreferences(context).edit().putString(KEY_CART_ITEMS, json).apply()
    }
}

