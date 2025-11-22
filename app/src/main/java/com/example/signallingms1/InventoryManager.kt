package com.example.signallingms1

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener

/**
 * InventoryManager - Handles stock validation and inventory updates
 * Provides client-side and server-side quantity checks
 */
object InventoryManager {
    
    private const val TAG = "InventoryManager"
    private val database = FirebaseDatabase.getInstance()
    
    /**
     * Validates that cart items have sufficient stock
     * @return Pair<Boolean, String> - (isValid, errorMessage)
     */
    fun validateCartStock(
        cartItems: List<CartItem>,
        onComplete: (isValid: Boolean, errors: List<String>) -> Unit
    ) {
        if (cartItems.isEmpty()) {
            onComplete(true, emptyList())
            return
        }
        
        val errors = mutableListOf<String>()
        val itemsToValidate = cartItems.groupBy { it.sellerId }
        var completedValidations = 0
        val totalValidations = itemsToValidate.size
        
        if (totalValidations == 0) {
            onComplete(true, emptyList())
            return
        }
        
        itemsToValidate.forEach { (sellerId, items) ->
            // Try "Products" (capital P) first, then "products"
            val productsRef = database.getReference("Seller").child(sellerId).child("Products")
            
            productsRef.get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    validateSellerItems(snapshot, items, errors) {
                        completedValidations++
                        if (completedValidations == totalValidations) {
                            onComplete(errors.isEmpty(), errors)
                        }
                    }
                } else {
                    // Try lowercase "products"
                    val productsRefLower = database.getReference("Seller").child(sellerId).child("products")
                    productsRefLower.get().addOnSuccessListener { snapshot2 ->
                        validateSellerItems(snapshot2, items, errors) {
                            completedValidations++
                            if (completedValidations == totalValidations) {
                                onComplete(errors.isEmpty(), errors)
                            }
                        }
                    }.addOnFailureListener { error ->
                        Log.e(TAG, "Failed to validate stock for seller $sellerId: ${error.message}")
                        errors.add("Failed to validate stock for seller. Please try again.")
                        completedValidations++
                        if (completedValidations == totalValidations) {
                            onComplete(false, errors)
                        }
                    }
                }
            }.addOnFailureListener { error ->
                Log.e(TAG, "Failed to validate stock for seller $sellerId: ${error.message}")
                errors.add("Failed to validate stock for seller. Please try again.")
                completedValidations++
                if (completedValidations == totalValidations) {
                    onComplete(false, errors)
                }
            }
        }
    }
    
    private fun validateSellerItems(
        productsSnapshot: DataSnapshot,
        items: List<CartItem>,
        errors: MutableList<String>,
        onComplete: () -> Unit
    ) {
        items.forEach { cartItem ->
            val productSnapshot = productsSnapshot.child(cartItem.productId)
            if (productSnapshot.exists()) {
                val stockValue = productSnapshot.child("stock").getValue(Any::class.java)
                val currentStock = when (stockValue) {
                    is Int -> stockValue
                    is Long -> stockValue.toInt()
                    is String -> stockValue.toIntOrNull() ?: 0
                    is Number -> stockValue.toInt()
                    else -> 0
                }
                
                if (currentStock < cartItem.quantity) {
                    val productName = cartItem.productName
                    if (currentStock == 0) {
                        errors.add("$productName is out of stock. Available: 0, Requested: ${cartItem.quantity}")
                    } else {
                        errors.add("$productName: Only $currentStock available, but ${cartItem.quantity} requested")
                    }
                }
            } else {
                errors.add("${cartItem.productName} is no longer available")
            }
        }
        onComplete()
    }
    
    /**
     * Decrements stock when adding item to cart
     * Uses Firebase transactions to ensure atomic updates
     * @return Boolean - true if update succeeded
     */
    fun decrementStockOnAddToCart(
        productId: String,
        sellerId: String,
        quantity: Int,
        onComplete: (success: Boolean, newStock: Int, errorMessage: String?) -> Unit
    ) {
        if (quantity <= 0) {
            onComplete(false, 0, "Invalid quantity")
            return
        }
        
        // Try "Products" (capital P) first
        val productRef = database.getReference("Seller").child(sellerId).child("Products").child(productId)
        
        productRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val stockSnapshot = currentData.child("stock")
                val existingStock = when (val value = stockSnapshot.getValue(Any::class.java)) {
                    is Int -> value
                    is Long -> value.toInt()
                    is String -> value.toString().toIntOrNull() ?: 0
                    is Number -> value.toInt()
                    else -> 0
                }
                
                // Check if sufficient stock
                if (existingStock >= quantity) {
                    val newStock = existingStock - quantity
                    stockSnapshot.value = newStock
                    return Transaction.success(currentData)
                } else {
                    return Transaction.abort()
                }
            }
            
            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (error != null || !committed) {
                    // Try lowercase "products" as fallback
                    val productRefLower = database.getReference("Seller").child(sellerId).child("products").child(productId)
                    productRefLower.runTransaction(object : Transaction.Handler {
                        override fun doTransaction(currentData: MutableData): Transaction.Result {
                            val stockSnapshot = currentData.child("stock")
                            val existingStock = when (val value = stockSnapshot.getValue(Any::class.java)) {
                                is Int -> value
                                is Long -> value.toInt()
                                is String -> value.toString().toIntOrNull() ?: 0
                                is Number -> value.toInt()
                                else -> 0
                            }
                            
                            if (existingStock >= quantity) {
                                val newStock = existingStock - quantity
                                stockSnapshot.value = newStock
                                return Transaction.success(currentData)
                            } else {
                                return Transaction.abort()
                            }
                        }
                        
                        override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                            if (error != null || !committed) {
                                val currentStock = currentData?.child("stock")?.getValue(Int::class.java) ?: 0
                                val errorMsg = if (currentStock < quantity) {
                                    "Insufficient stock. Available: $currentStock, Requested: $quantity"
                                } else {
                                    "Failed to update stock: ${error?.message}"
                                }
                                Log.e(TAG, "Failed to decrement stock: $errorMsg")
                                onComplete(false, currentStock, errorMsg)
                            } else {
                                val newStock = currentData?.child("stock")?.getValue(Int::class.java) ?: 0
                                Log.d(TAG, "Successfully decremented stock for product $productId: new stock = $newStock")
                                onComplete(true, newStock, null)
                            }
                        }
                    })
                } else {
                    val newStock = currentData?.child("stock")?.getValue(Int::class.java) ?: 0
                    Log.d(TAG, "Successfully decremented stock for product $productId: new stock = $newStock")
                    onComplete(true, newStock, null)
                }
            }
        })
    }
    
    /**
     * Restores stock when removing item from cart
     * Uses Firebase transactions to ensure atomic updates
     */
    fun restoreStockOnRemoveFromCart(
        productId: String,
        sellerId: String,
        quantity: Int,
        onComplete: (success: Boolean, newStock: Int) -> Unit
    ) {
        if (quantity <= 0) {
            onComplete(true, 0)
            return
        }
        
        // Try "Products" (capital P) first
        val productRef = database.getReference("Seller").child(sellerId).child("Products").child(productId)
        
        productRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val stockSnapshot = currentData.child("stock")
                val existingStock = when (val value = stockSnapshot.getValue(Any::class.java)) {
                    is Int -> value
                    is Long -> value.toInt()
                    is String -> value.toString().toIntOrNull() ?: 0
                    is Number -> value.toInt()
                    else -> 0
                }
                
                val newStock = existingStock + quantity
                stockSnapshot.value = newStock
                return Transaction.success(currentData)
            }
            
            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (error != null || !committed) {
                    // Try lowercase "products" as fallback
                    val productRefLower = database.getReference("Seller").child(sellerId).child("products").child(productId)
                    productRefLower.runTransaction(object : Transaction.Handler {
                        override fun doTransaction(currentData: MutableData): Transaction.Result {
                            val stockSnapshot = currentData.child("stock")
                            val existingStock = when (val value = stockSnapshot.getValue(Any::class.java)) {
                                is Int -> value
                                is Long -> value.toInt()
                                is String -> value.toString().toIntOrNull() ?: 0
                                is Number -> value.toInt()
                                else -> 0
                            }
                            
                            val newStock = existingStock + quantity
                            stockSnapshot.value = newStock
                            return Transaction.success(currentData)
                        }
                        
                        override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                            if (error != null || !committed) {
                                Log.e(TAG, "Failed to restore stock for product $productId: ${error?.message}")
                                onComplete(false, 0)
                            } else {
                                val newStock = currentData?.child("stock")?.getValue(Int::class.java) ?: 0
                                Log.d(TAG, "Successfully restored stock for product $productId: new stock = $newStock")
                                onComplete(true, newStock)
                            }
                        }
                    })
                } else {
                    val newStock = currentData?.child("stock")?.getValue(Int::class.java) ?: 0
                    Log.d(TAG, "Successfully restored stock for product $productId: new stock = $newStock")
                    onComplete(true, newStock)
                }
            }
        })
    }
    
    /**
     * Decrements inventory for order items (legacy - kept for backward compatibility)
     * NOTE: This should NOT be called if stock was already decremented on add to cart
     * Uses Firebase transactions to ensure atomic updates
     * @return Boolean - true if all updates succeeded
     */
    fun decrementInventory(
        orderItems: Map<String, CartItem>,
        sellerId: String,
        onComplete: (success: Boolean, errors: List<String>) -> Unit
    ) {
        if (orderItems.isEmpty()) {
            onComplete(true, emptyList())
            return
        }
        
        val errors = mutableListOf<String>()
        val updates = mutableListOf<Pair<String, Int>>() // productId -> quantity to decrement
        var completedUpdates = 0
        val totalUpdates = orderItems.size
        
        // Try "Products" (capital P) first
        val productsRef = database.getReference("Seller").child(sellerId).child("Products")
        
        productsRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                performDecrements(productsRef, orderItems, snapshot, errors) { success ->
                    onComplete(success, errors)
                }
            } else {
                // Try lowercase "products"
                val productsRefLower = database.getReference("Seller").child(sellerId).child("products")
                productsRefLower.get().addOnSuccessListener { snapshot2 ->
                    performDecrements(productsRefLower, orderItems, snapshot2, errors) { success ->
                        onComplete(success, errors)
                    }
                }.addOnFailureListener { error ->
                    Log.e(TAG, "Failed to decrement inventory: ${error.message}")
                    onComplete(false, listOf("Failed to update inventory: ${error.message}"))
                }
            }
        }.addOnFailureListener { error ->
            Log.e(TAG, "Failed to fetch products for inventory update: ${error.message}")
            onComplete(false, listOf("Failed to update inventory: ${error.message}"))
        }
    }
    
    private fun performDecrements(
        productsRef: DatabaseReference,
        orderItems: Map<String, CartItem>,
        snapshot: DataSnapshot,
        errors: MutableList<String>,
        onComplete: (success: Boolean) -> Unit
    ) {
        var hasErrors = false
        var completedUpdates = 0
        
        orderItems.forEach { (productId, cartItem) ->
            val productRef = productsRef.child(productId)
            val productSnapshot = snapshot.child(productId)
            
            if (productSnapshot.exists()) {
                val stockValue = productSnapshot.child("stock").getValue(Any::class.java)
                val currentStock = when (stockValue) {
                    is Int -> stockValue
                    is Long -> stockValue.toInt()
                    is String -> stockValue.toIntOrNull() ?: 0
                    is Number -> stockValue.toInt()
                    else -> 0
                }
                
                if (currentStock >= cartItem.quantity) {
                    // Use transaction for atomic update
                    productRef.runTransaction(object : Transaction.Handler {
                        override fun doTransaction(currentData: MutableData): Transaction.Result {
                            val stockSnapshot = currentData.child("stock")
                            val existingStock = when (val value = stockSnapshot.getValue(Any::class.java)) {
                                is Int -> value
                                is Long -> value.toInt()
                                is String -> value.toString().toIntOrNull() ?: 0
                                is Number -> value.toInt()
                                else -> 0
                            }
                            
                            // Double-check stock is still sufficient
                            if (existingStock >= cartItem.quantity) {
                                stockSnapshot.value = existingStock - cartItem.quantity
                                return Transaction.success(currentData)
                            } else {
                                return Transaction.abort()
                            }
                        }
                        
                        override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                            if (error != null || !committed) {
                                Log.e(TAG, "Failed to update stock for product $productId: ${error?.message}")
                                errors.add("Failed to update inventory for ${cartItem.productName}")
                                hasErrors = true
                            } else {
                                Log.d(TAG, "Successfully decremented stock for product $productId: ${currentData?.child("stock")?.value}")
                            }
                            
                            completedUpdates++
                            if (completedUpdates == orderItems.size) {
                                onComplete(!hasErrors)
                            }
                        }
                    })
                } else {
                    errors.add("${cartItem.productName}: Insufficient stock (available: $currentStock, requested: ${cartItem.quantity})")
                    hasErrors = true
                    completedUpdates++
                    if (completedUpdates == orderItems.size) {
                        onComplete(false)
                    }
                }
            } else {
                errors.add("${cartItem.productName} no longer exists")
                hasErrors = true
                completedUpdates++
                if (completedUpdates == orderItems.size) {
                    onComplete(false)
                }
            }
        }
    }
}

