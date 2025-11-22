package com.example.signallingms1

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class CartFragment : Fragment() {

    private lateinit var rvCart: RecyclerView
    private lateinit var tvTotalPrice: TextView
    private lateinit var tvDeliveryFee: TextView
    private lateinit var tvGrandTotal: TextView
    private lateinit var llDeliveryFee: View
    private lateinit var tvEmpty: TextView
    private lateinit var btnClearCart: Button
    private lateinit var btnPlaceOrder: Button
    private lateinit var rgDeliveryType: RadioGroup
    private lateinit var rbDelivery: RadioButton
    private lateinit var rbPickup: RadioButton
    private lateinit var cartAdapter: CartAdapter
    private val cartItems = mutableListOf<CartItem>()
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    
    private var buyerAddress: String = ""
    private var sellerAddresses = mutableMapOf<String, String>() // sellerId -> address

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_cart, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            if (!isAdded || context == null) return

            rvCart = view.findViewById(R.id.rvCart)
            tvTotalPrice = view.findViewById(R.id.tvTotalPrice)
            tvDeliveryFee = view.findViewById(R.id.tvDeliveryFee)
            tvGrandTotal = view.findViewById(R.id.tvGrandTotal)
            llDeliveryFee = view.findViewById(R.id.llDeliveryFee)
            tvEmpty = view.findViewById(R.id.tvEmpty)
            btnClearCart = view.findViewById(R.id.btnClearCart)
            btnPlaceOrder = view.findViewById(R.id.btnPlaceOrder)
            rgDeliveryType = view.findViewById(R.id.rgDeliveryType)
            rbDelivery = view.findViewById(R.id.rbDelivery)
            rbPickup = view.findViewById(R.id.rbPickup)

            cartAdapter = CartAdapter(cartItems,
                onQuantityChanged = { productId, sellerId, quantity ->
                    if (isAdded && context != null) {
                        // Fetch current stock from Firebase before updating
                        fetchAndUpdateQuantity(productId, sellerId, quantity)
                    }
                },
                onItemRemoved = { productId ->
                    if (isAdded && context != null) {
                        showRemoveItemConfirmationDialog(productId)
                    }
                }
            )

            rvCart.layoutManager = LinearLayoutManager(requireContext())
            rvCart.adapter = cartAdapter

            btnClearCart.setOnClickListener {
                if (isAdded && context != null) {
                    CartManager.clearCart(requireContext()) { success ->
                        if (isAdded && context != null) {
                            refreshCart()
                            Toast.makeText(context, "Cart cleared", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            btnPlaceOrder.setOnClickListener {
                placeOrder()
            }
            
            // Listen to delivery type changes to recalculate delivery fee
            rgDeliveryType.setOnCheckedChangeListener { _, _ ->
                updateDeliveryFeeAndTotal()
            }

            // Delay refresh to ensure views are fully initialized
            view.post {
                refreshCart()
            }
        } catch (e: Exception) {
            android.util.Log.e("CartFragment", "Error in onViewCreated: ${e.message}", e)
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isAdded && view != null) {
            view?.post {
                refreshCart()
            }
        }
    }

    private fun refreshCart() {
        if (!isAdded || context == null || view == null) {
            android.util.Log.w("CartFragment", "Cannot refresh cart - fragment not ready")
            return
        }
        
        try {
            // Check if adapter is initialized
            if (!::cartAdapter.isInitialized) {
                android.util.Log.w("CartFragment", "Adapter not initialized yet")
                return
            }
            
            val items = CartManager.getCartItemsList(requireContext())
            android.util.Log.d("CartFragment", "Cart items count: ${items.size}")
            
            if (items.isNotEmpty()) {
                android.util.Log.d("CartFragment", "Cart items: ${items.map { "${it.productName} x${it.quantity}" }}")
            }
            
            cartItems.clear()
            cartItems.addAll(items)

            if (cartItems.isEmpty()) {
                android.util.Log.d("CartFragment", "Cart is empty, showing empty message")
                tvEmpty.visibility = View.VISIBLE
                rvCart.visibility = View.GONE
                rgDeliveryType.visibility = View.GONE
                llDeliveryFee.visibility = View.GONE
                btnPlaceOrder.isEnabled = false
                tvGrandTotal.text = "$0.00"
            } else {
                android.util.Log.d("CartFragment", "Cart has ${cartItems.size} items, showing cart")
                tvEmpty.visibility = View.GONE
                rvCart.visibility = View.VISIBLE
                rgDeliveryType.visibility = View.VISIBLE
                btnPlaceOrder.isEnabled = true
            }

            val total = CartManager.getTotalPrice(requireContext())
            android.util.Log.d("CartFragment", "Total price: $total")
            tvTotalPrice.text = "$${String.format("%.2f", total)}"
            
            // Update adapter
            cartAdapter.updateCartItems(ArrayList(cartItems))
            android.util.Log.d("CartFragment", "Adapter updated with ${cartAdapter.itemCount} items")
            
            // Load buyer and seller addresses for delivery fee calculation and update totals
            loadAddressesForDeliveryFee()
        } catch (e: Exception) {
            android.util.Log.e("CartFragment", "Error refreshing cart: ${e.message}", e)
            e.printStackTrace()
        }
    }

    private fun fetchAndUpdateQuantity(productId: String, sellerId: String, quantity: Int) {
        if (!isAdded || context == null) return
        
        // Fetch current stock from Firebase - try "Products" first, then "products"
        val productsRef = database.getReference("Seller").child(sellerId).child("Products").child(productId)
        productsRef.get().addOnSuccessListener { snapshot ->
            if (!isAdded || context == null) return@addOnSuccessListener
            
            if (snapshot.exists()) {
                try {
                    // Get stock value (can be String or Int)
                    val stockValue = snapshot.child("stock").getValue(Any::class.java)
                    val currentStock = when (stockValue) {
                        is Int -> stockValue
                        is Long -> stockValue.toInt()
                        is String -> stockValue.toIntOrNull() ?: 0
                        is Number -> stockValue.toInt()
                        else -> 0
                    }
                    
                    // Update quantity with inventory update
                    CartManager.updateQuantity(requireContext(), productId, quantity, currentStock) { success, message, newStock ->
                        if (!isAdded || context == null) return@updateQuantity
                        
                        if (!success) {
                            // Show stock alert dialog
                            showStockAlertDialog(productId, newStock ?: currentStock, quantity)
                        } else {
                            // Removed low stock warning - doesn't make sense for buyers
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                        refreshCart()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CartFragment", "Error fetching stock: ${e.message}", e)
                    handleStockFetchFallback(productId, quantity)
                }
            } else {
                // Try lowercase "products"
                val productsRefLower = database.getReference("Seller").child(sellerId).child("products").child(productId)
                productsRefLower.get().addOnSuccessListener { snapshot2 ->
                    if (!isAdded || context == null) return@addOnSuccessListener
                    try {
                        val stockValue = snapshot2.child("stock").getValue(Any::class.java)
                        val currentStock = when (stockValue) {
                            is Int -> stockValue
                            is Long -> stockValue.toInt()
                            is String -> stockValue.toIntOrNull() ?: 0
                            is Number -> stockValue.toInt()
                            else -> 0
                        }
                        CartManager.updateQuantity(requireContext(), productId, quantity, currentStock) { success, message, newStock ->
                            if (!isAdded || context == null) return@updateQuantity
                            
                            if (!success) {
                                // Show stock alert dialog
                                val cartItem = CartManager.getCartItems(requireContext())[productId]
                                val productName = cartItem?.productName ?: "Product"
                                showStockAlertDialog(productId, newStock ?: currentStock, quantity)
                            } else {
                                // Removed low stock warning - doesn't make sense for buyers
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                            refreshCart()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("CartFragment", "Error fetching stock: ${e.message}", e)
                        handleStockFetchFallback(productId, quantity)
                    }
                }.addOnFailureListener {
                    handleStockFetchFallback(productId, quantity)
                }
            }
        }.addOnFailureListener { error ->
            if (isAdded && context != null) {
                android.util.Log.e("CartFragment", "Failed to fetch stock: ${error.message}", error)
                handleStockFetchFallback(productId, quantity)
            }
        }
    }
    
    private fun handleStockFetchFallback(productId: String, quantity: Int) {
        if (!isAdded || context == null) return
        // Fallback - try to update without current stock
        CartManager.updateQuantity(requireContext(), productId, quantity, null) { success, message, newStock ->
            if (!isAdded || context == null) return@updateQuantity
            if (!success) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
            refreshCart()
        }
    }
    
    private fun loadAddressesForDeliveryFee() {
        if (!isAdded || context == null) return
        
        val buyerId = auth.currentUser?.uid ?: return
        if (cartItems.isEmpty()) return
        
        // Load buyer address - manually retrieve from snapshot for reliability
        database.getReference("Buyers").child(buyerId).get().addOnSuccessListener { buyerSnapshot ->
            if (!isAdded || context == null) return@addOnSuccessListener
            
            // Manually get address field from Firebase (more reliable than deserialization)
            buyerAddress = buyerSnapshot.child("address").getValue(String::class.java) ?: ""
            
            android.util.Log.d("CartFragment", "Loaded buyer address: '$buyerAddress'")
            
            // Load seller addresses
            val uniqueSellerIds = cartItems.map { it.sellerId }.distinct()
            var loadedCount = 0
            val totalSellers = uniqueSellerIds.size
            
            if (totalSellers == 0) {
                // No sellers, update immediately with buyer address
                updateDeliveryFeeAndTotal()
            } else {
                uniqueSellerIds.forEach { sellerId ->
                    database.getReference("Seller").child(sellerId).get().addOnSuccessListener { sellerSnapshot ->
                        if (!isAdded || context == null) return@addOnSuccessListener
                        // Manually get address field
                        sellerAddresses[sellerId] = sellerSnapshot.child("address").getValue(String::class.java) ?: ""
                        loadedCount++
                        
                        android.util.Log.d("CartFragment", "Loaded seller $sellerId address: '${sellerAddresses[sellerId]}'")
                        
                        if (loadedCount == totalSellers) {
                            // All addresses loaded, update delivery fee
                            updateDeliveryFeeAndTotal()
                        }
                    }.addOnFailureListener { error ->
                        android.util.Log.e("CartFragment", "Failed to load seller $sellerId address: ${error.message}")
                        loadedCount++
                        // Continue even if one seller fails
                        if (loadedCount == totalSellers) {
                            updateDeliveryFeeAndTotal()
                        }
                    }
                }
            }
        }.addOnFailureListener { error ->
            android.util.Log.e("CartFragment", "Failed to load buyer address: ${error.message}")
            // Still try to update with empty address
            updateDeliveryFeeAndTotal()
        }
    }
    
    private fun updateDeliveryFeeAndTotal() {
        if (!isAdded || context == null || view == null) return
        if (cartItems.isEmpty()) return
        
        val subtotal = CartManager.getTotalPrice(requireContext())
        val isDelivery = rgDeliveryType.checkedRadioButtonId == rbDelivery.id
        
        android.util.Log.d("CartFragment", "updateDeliveryFeeAndTotal - isDelivery: $isDelivery, buyerAddress: '$buyerAddress'")
        
        if (isDelivery) {
            // Calculate delivery fee for each seller's order and sum them up
            var totalDeliveryFee = 0.0
            val ordersBySeller = cartItems.groupBy { it.sellerId }
            
            ordersBySeller.forEach { (sellerId, items) ->
                val orderSubtotal = items.sumOf { it.getTotalPrice() }
                val sellerAddress = sellerAddresses[sellerId] ?: ""
                
                android.util.Log.d("CartFragment", "Calculating fee for seller $sellerId - orderAmount: $orderSubtotal, buyerAddress: '$buyerAddress'")
                
                val deliveryFee = DeliveryFeeCalculator.calculateDeliveryFee(
                    orderAmount = orderSubtotal,
                    sellerAddress = sellerAddress,
                    buyerAddress = buyerAddress
                )
                
                android.util.Log.d("CartFragment", "Delivery fee for seller $sellerId: $deliveryFee")
                totalDeliveryFee += deliveryFee
            }
            
            // Display delivery fee (even if address is empty, calculator will default to $30)
            tvDeliveryFee.text = "$${String.format("%.2f", totalDeliveryFee)}"
            llDeliveryFee.visibility = View.VISIBLE
            
            // Update grand total
            val grandTotal = subtotal + totalDeliveryFee
            tvGrandTotal.text = "$${String.format("%.2f", grandTotal)}"
            
            android.util.Log.d("CartFragment", "Final delivery fee: $totalDeliveryFee, Grand total: $grandTotal")
        } else {
            // Pickup selected - no delivery fee
            llDeliveryFee.visibility = View.GONE
            tvGrandTotal.text = "$${String.format("%.2f", subtotal)}"
        }
    }

    private fun placeOrder() {
        if (!isAdded || context == null) return
        
        val buyerId = auth.currentUser?.uid
        if (buyerId == null) {
            Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        if (cartItems.isEmpty()) {
            Toast.makeText(context, "Cart is empty", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Disable place order button to prevent multiple submissions
        btnPlaceOrder.isEnabled = false
        
        // Stock is already validated and decremented when items were added to cart
        // Just proceed with order placement
        android.util.Log.d("CartFragment", "Proceeding with order placement for ${cartItems.size} items...")
        proceedWithOrderPlacement(buyerId, cartItems)
    }
    
    private fun proceedWithOrderPlacement(buyerId: String, cartItems: List<CartItem>) {
        // Group items by seller
        val ordersBySeller = cartItems.groupBy { it.sellerId }
        var completedOrders = 0
        var failedOrders = 0
        val totalOrders = ordersBySeller.size
        val orderErrors = mutableListOf<String>()
        var confirmationShown = false
        var firstSuccessfulOrder: Order? = null
        var firstSellerName: String? = null

        // Load buyer info
        database.getReference("Buyers").child(buyerId).get().addOnSuccessListener { buyerSnapshot ->
            if (!isAdded || context == null) {
                btnPlaceOrder.isEnabled = true
                return@addOnSuccessListener
            }
            
            val buyer = buyerSnapshot.getValue(UserProfile::class.java)
            val buyerName = buyer?.name ?: "Unknown"
            val buyerAddress = buyer?.address ?: ""

            // Create orders for each seller
            ordersBySeller.forEach { (sellerId, items) ->
                // Load seller info
                database.getReference("Seller").child(sellerId).get().addOnSuccessListener { sellerSnapshot ->
                    if (!isAdded || context == null) return@addOnSuccessListener
                    
                    val seller = sellerSnapshot.getValue(Seller::class.java)
                    val sellerName = seller?.shopName?.ifEmpty { seller?.name } ?: "Unknown"

                    // Create order
                    val orderId = database.getReference("Orders").push().key ?: run {
                        failedOrders++
                        completedOrders++
                        if (completedOrders == totalOrders) {
                            handleOrderCompletion(completedOrders, failedOrders, orderErrors)
                        }
                        return@addOnSuccessListener
                    }
                    
                    val itemsMap = HashMap<String, CartItem>().apply {
                        items.forEach { item ->
                            put(item.productId, item)
                        }
                    }
                    val totalPrice = items.sumOf { it.getTotalPrice() }
                    
                    // Get selected delivery type
                    val selectedDeliveryType = if (rgDeliveryType.checkedRadioButtonId == rbDelivery.id) {
                        "delivery"
                    } else {
                        "pickup"
                    }
                    
                    // Calculate delivery fee if delivery is selected
                    val deliveryFee = if (selectedDeliveryType == "delivery") {
                        val sellerAddress = seller?.address ?: ""
                        DeliveryFeeCalculator.calculateDeliveryFee(
                            orderAmount = totalPrice,
                            sellerAddress = sellerAddress,
                            buyerAddress = buyerAddress
                        )
                    } else {
                        0.0 // No delivery fee for pickup
                    }

                    val order = Order(
                        orderId = orderId,
                        buyerId = buyerId,
                        sellerId = sellerId,
                        items = itemsMap,
                        totalPrice = totalPrice,
                        status = "pending",
                        timestamp = System.currentTimeMillis(),
                        buyerName = buyerName,
                        buyerAddress = buyerAddress,
                        sellerName = sellerName,
                        deliveryType = selectedDeliveryType,
                        deliveryPrice = deliveryFee
                    )

                    // Step 3: Save order first
                    android.util.Log.d("CartFragment", "Saving order $orderId for seller $sellerId")
                    database.getReference("Orders").child(orderId).setValue(order)
                        .addOnSuccessListener {
                            if (!isAdded || context == null) return@addOnSuccessListener
                            
                            android.util.Log.d("CartFragment", "Order $orderId saved successfully, updating inventory...")
                            
                            // Step 4: Inventory already decremented when items were added to cart
                            // No need to decrement again - just mark order as completed
                            completedOrders++
                            android.util.Log.d("CartFragment", "Order $orderId saved successfully (inventory already updated on add to cart)")
                            
                            // Store first successful order for confirmation dialog
                            if (!confirmationShown && failedOrders == 0) {
                                firstSuccessfulOrder = order
                                firstSellerName = sellerName
                                confirmationShown = true
                            }
                            
                            // Handle completion when all orders are processed
                            if (completedOrders == totalOrders) {
                                handleOrderCompletion(
                                    completedOrders, 
                                    failedOrders, 
                                    orderErrors,
                                    firstSuccessfulOrder,
                                    firstSellerName
                                )
                            }
                        }
                        .addOnFailureListener { error ->
                            if (!isAdded || context == null) return@addOnFailureListener
                            
                            completedOrders++
                            failedOrders++
                            orderErrors.add("Failed to save order for $sellerName: ${error.message}")
                            android.util.Log.e("CartFragment", "Failed to save order: ${error.message}")
                            
                            if (completedOrders == totalOrders) {
                                handleOrderCompletion(completedOrders, failedOrders, orderErrors)
                            }
                        }
                }.addOnFailureListener { error ->
                    if (!isAdded || context == null) return@addOnFailureListener
                    
                    completedOrders++
                    failedOrders++
                    orderErrors.add("Failed to load seller info: ${error.message}")
                    
                    if (completedOrders == totalOrders) {
                        handleOrderCompletion(completedOrders, failedOrders, orderErrors)
                    }
                }
            }
        }.addOnFailureListener { error ->
            if (!isAdded || context == null) {
                btnPlaceOrder.isEnabled = true
                return@addOnFailureListener
            }
            
            btnPlaceOrder.isEnabled = true
            showErrorDialog("Failed to place order", "Failed to load buyer information: ${error.message}")
            android.util.Log.e("CartFragment", "Failed to load buyer info: ${error.message}")
        }
    }
    
    private fun handleOrderCompletion(
        completedOrders: Int,
        failedOrders: Int,
        errors: List<String>,
        firstSuccessfulOrder: Order? = null,
        firstSellerName: String? = null
    ) {
        if (!isAdded || context == null) {
            btnPlaceOrder.isEnabled = true
            return
        }
        
        btnPlaceOrder.isEnabled = true
        
        if (failedOrders == 0) {
            // All orders placed successfully
            android.util.Log.d("CartFragment", "All $completedOrders orders placed successfully")
            
            // Show confirmation dialog if we have order info
            if (firstSuccessfulOrder != null && firstSellerName != null) {
                showOrderConfirmationDialog(firstSuccessfulOrder, firstSellerName)
            }
            
            // Clear cart WITHOUT restoring stock since order was successfully placed
            CartManager.clearCart(requireContext(), restoreStock = false) { success ->
                if (isAdded && context != null) {
                    refreshCart()
                }
            }
        } else {
            // Some orders failed
            val errorMessage = if (errors.size == 1) {
                errors[0]
            } else {
                "Some orders could not be completed:\n" + errors.joinToString("\n") { "• $it" }
            }
            showErrorDialog("Order Placement Partially Failed", errorMessage)
            android.util.Log.e("CartFragment", "Order placement completed with errors: $errors")
            // Don't clear cart if there were errors, let user retry
        }
    }
    
    /**
     * Shows order confirmation alert dialog
     */
    private fun showOrderConfirmationDialog(order: Order, sellerName: String) {
        if (!isAdded || context == null) return
        
        val orderIdShort = order.orderId.take(12)
        val deliveryInfo = if (order.deliveryType.lowercase() == "pickup") {
            "Pickup from $sellerName"
        } else {
            "Delivery to your address"
        }
        
        val message = """
            Order #$orderIdShort has been placed successfully!
            
            Seller: $sellerName
            Total: $${String.format("%.2f", order.totalPrice + order.deliveryPrice)}
            ${if (order.deliveryPrice > 0) "Delivery Fee: $${String.format("%.2f", order.deliveryPrice)}\n" else ""}
            Delivery Method: ${deliveryInfo}
            
            You will receive notifications when your order status updates.
        """.trimIndent()
        
        AlertDialog.Builder(requireContext())
            .setTitle("Order Confirmed! ✓")
            .setMessage(message)
            .setPositiveButton("View Orders") { _, _ ->
                // Navigate to Orders tab
                (activity as? HomeActivity)?.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)?.currentItem = 3
            }
            .setNegativeButton("Continue Shopping", null)
            .setIcon(android.R.drawable.ic_dialog_info)
            .setCancelable(false)
            .show()
    }
    
    /**
     * Shows error alert dialog
     */
    private fun showErrorDialog(title: String, message: String) {
        if (!isAdded || context == null) return
        
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }
    
    /**
     * Shows stock alert dialog when insufficient stock
     */
    private fun showStockAlertDialog(productId: String, availableStock: Int, requestedQuantity: Int) {
        if (!isAdded || context == null) return
        
        val cartItem = CartManager.getCartItems(requireContext())[productId]
        val productName = cartItem?.productName ?: "Product"
        
        val message = when {
            availableStock == 0 -> "$productName is currently out of stock. Please remove it from your cart or wait for restock."
            else -> "Insufficient stock for $productName.\n\nAvailable: $availableStock\nRequested: $requestedQuantity\n\nOnly $availableStock available."
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("Stock Alert")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setIcon(android.R.drawable.stat_notify_error)
            .show()
        
        // Removed low stock notification - doesn't make sense for buyers
    }
    
    /**
     * Shows confirmation dialog before removing item from cart
     */
    private fun showRemoveItemConfirmationDialog(productId: String) {
        if (!isAdded || context == null) return
        
        val cartItem = CartManager.getCartItems(requireContext())[productId]
        val productName = cartItem?.productName ?: "this item"
        
        AlertDialog.Builder(requireContext())
            .setTitle("Remove Item")
            .setMessage("Are you sure you want to remove '$productName' from your cart?")
            .setPositiveButton("Remove") { _, _ ->
                CartManager.removeFromCart(requireContext(), productId) { success ->
                    if (isAdded && context != null) {
                        if (success) {
                            android.util.Log.d("CartFragment", "Stock restored for product $productId")
                        } else {
                            android.util.Log.w("CartFragment", "Failed to restore stock for product $productId")
                        }
                        refreshCart()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .setIcon(android.R.drawable.ic_menu_delete)
            .show()
    }
}

