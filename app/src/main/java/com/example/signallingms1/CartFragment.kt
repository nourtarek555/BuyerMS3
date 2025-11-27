
// Defines the package this class belongs to.
package com.example.signallingms1

// Android and Java imports for UI, data handling, and system functions.
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

/**
 * A Fragment that displays the contents of the user's shopping cart.
 * This screen allows the user to view the items they have added, adjust quantities,
 * remove items, select a delivery or pickup option, and finally, place an order.
 * It interacts heavily with CartManager to manage the cart state and with Firebase
 * for placing orders and fetching real-time data like stock and addresses.
 */
class CartFragment : Fragment() {

    // --- UI View Variables ---
    private lateinit var rvCart: RecyclerView // The list that displays cart items.
    private lateinit var tvTotalPrice: TextView // Displays the subtotal of all items.
    private lateinit var tvDeliveryFee: TextView // Displays the calculated delivery fee.
    private lateinit var tvGrandTotal: TextView // Displays the final total (subtotal + delivery).
    private lateinit var llDeliveryFee: View // The layout container for the delivery fee, can be hidden.
    private lateinit var tvEmpty: TextView // A message shown when the cart is empty.
    private lateinit var btnClearCart: Button // Button to remove all items from the cart.
    private lateinit var btnPlaceOrder: Button // Button to finalize the purchase and create orders.
    private lateinit var rgDeliveryType: RadioGroup // Group of radio buttons for choosing delivery or pickup.
    private lateinit var rbDelivery: RadioButton // The radio button for selecting delivery.
    private lateinit var rbPickup: RadioButton // The radio button for selecting pickup.

    // --- Adapter and Data Variables ---
    private lateinit var cartAdapter: CartAdapter // The adapter for the RecyclerView.
    private val cartItems = mutableListOf<CartItem>() // The local list of cart items.

    // --- Firebase and Address Variables ---
    private val auth = FirebaseAuth.getInstance() // Firebase Authentication instance.
    private val database = FirebaseDatabase.getInstance() // Firebase Realtime Database instance.
    private var buyerAddress: String = "" // The current user's (buyer's) address.
    private var sellerAddresses = mutableMapOf<String, String>() // A map to store seller addresses, keyed by sellerId.

    /**
     * Called to have the fragment instantiate its user interface view.
     * This is where the layout for the fragment is inflated.
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout XML file for this fragment.
        return inflater.inflate(R.layout.fragment_cart, container, false)
    }

    /**
     * Called immediately after onCreateView() has returned, but before any saved state has been restored in to the view.
     * This is where UI elements are initialized and listeners are set up.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Find and assign all the UI views from the layout.
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

        // Initialize the CartAdapter with callbacks for item interactions.
        cartAdapter = CartAdapter(cartItems,
            onQuantityChanged = { productId, sellerId, quantity ->
                // When a user changes an item's quantity, fetch the latest stock info before updating.
                fetchAndUpdateQuantity(productId, sellerId, quantity)
            },
            onItemRemoved = { productId ->
                // When a user wants to remove an item, show a confirmation dialog first.
                showRemoveItemConfirmationDialog(productId)
            }
        )

        // Set up the RecyclerView with a vertical layout manager and the adapter.
        rvCart.layoutManager = LinearLayoutManager(requireContext())
        rvCart.adapter = cartAdapter

        // Set a click listener for the "Clear Cart" button.
        btnClearCart.setOnClickListener {
            // Use CartManager to clear the cart and then refresh the UI.
            CartManager.clearCart(requireContext()) { success ->
                if (isAdded && context != null) { // Check if fragment is still active.
                    refreshCart()
                    Toast.makeText(context, "Cart cleared", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Set a click listener for the "Place Order" button.
        btnPlaceOrder.setOnClickListener {
            placeOrder()
        }

        // Add a listener to the delivery type radio group.
        rgDeliveryType.setOnCheckedChangeListener { _, _ ->
            // When the user switches between delivery and pickup, recalculate the total price.
            updateDeliveryFeeAndTotal()
        }

        // Post the initial cart refresh to the view's message queue to ensure all views are initialized.
        view.post { refreshCart() }
    }

    /**
     * Called when the fragment is visible to the user.
     * Refreshes the cart to ensure it displays the most up-to-date information.
     */
    override fun onResume() {
        super.onResume()
        // A check to ensure the view is available before refreshing.
        if (isAdded && view != null) {
            view?.post { refreshCart() }
        }
    }

    /**
     * Refreshes the entire cart view.
     * It loads items from CartManager, updates the UI to show or hide the empty cart message,
     * recalculates all prices, and fetches necessary addresses for delivery fee calculation.
     */
    private fun refreshCart() {
        // Safety check to prevent crashes if the fragment is not attached to a context.
        if (!isAdded || context == null || view == null) return

        try {
            // Load the current list of items from the CartManager.
            val items = CartManager.getCartItemsList(requireContext())
            cartItems.clear()
            cartItems.addAll(items)

            // Check if the cart is empty.
            if (cartItems.isEmpty()) {
                // If empty, show the "empty cart" message and hide the list and order buttons.
                tvEmpty.visibility = View.VISIBLE
                rvCart.visibility = View.GONE
                rgDeliveryType.visibility = View.GONE
                llDeliveryFee.visibility = View.GONE
                btnPlaceOrder.isEnabled = false
                tvGrandTotal.text = "$0.00"
            } else {
                // If not empty, show the list and hide the empty message.
                tvEmpty.visibility = View.GONE
                rvCart.visibility = View.VISIBLE
                rgDeliveryType.visibility = View.VISIBLE
                btnPlaceOrder.isEnabled = true
            }

            // Update the subtotal price display.
            val total = CartManager.getTotalPrice(requireContext())
            tvTotalPrice.text = "$${String.format("%.2f", total)}"

            // Tell the adapter to update its data, which refreshes the RecyclerView.
            cartAdapter.updateCartItems(ArrayList(cartItems))

            // Asynchronously load buyer and seller addresses to calculate the delivery fee.
            loadAddressesForDeliveryFee()
        } catch (e: Exception) {
            // Log any errors that occur during the refresh process.
            android.util.Log.e("CartFragment", "Error refreshing cart: ${e.message}", e)
        }
    }

    /**
     * Fetches the latest stock for a product from Firebase and then updates the quantity.
     * This prevents a user from adding more items to the cart than are actually available.
     */
    private fun fetchAndUpdateQuantity(productId: String, sellerId: String, quantity: Int) {
        if (!isAdded || context == null) return

        // Get a reference to the product in the Firebase database.
        val productsRef = database.getReference("Seller").child(sellerId).child("Products").child(productId)
        productsRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                // The stock value in Firebase could be a number or a string, so handle both.
                val stockValue = snapshot.child("stock").getValue(Any::class.java)
                val currentStock = when (stockValue) {
                    is Int -> stockValue
                    is Long -> stockValue.toInt()
                    is String -> stockValue.toIntOrNull() ?: 0
                    else -> 0
                }

                // Now, call CartManager to update the quantity, providing the fresh stock data.
                CartManager.updateQuantity(requireContext(), productId, quantity, currentStock) { success, message, newStock ->
                    if (isAdded && context != null) {
                        if (!success) {
                            // If the update failed (e.g., not enough stock), show an alert.
                            showStockAlertDialog(productId, newStock ?: currentStock, quantity)
                        }
                        // Always refresh the cart to show the final state.
                        refreshCart()
                    }
                }
            } else {
                // If the product wasn't found, it might be an error or the user is offline.
                // Fallback to updating without a stock check.
                handleStockFetchFallback(productId, quantity)
            }
        }.addOnFailureListener {
            // If the database fetch fails, use the fallback.
            handleStockFetchFallback(productId, quantity)
        }
    }

    /**
     * A fallback method for when fetching live stock data from Firebase fails.
     * It attempts to update the quantity without a real-time stock check.
     * CartManager might still reject the change based on the stock info saved when the item was first added.
     */
    private fun handleStockFetchFallback(productId: String, quantity: Int) {
        if (!isAdded || context == null) return
        // Attempt to update the quantity without providing a current stock value.
        CartManager.updateQuantity(requireContext(), productId, quantity, null) { success, message, _ ->
            if (isAdded && context != null) {
                if (!success) {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
                refreshCart()
            }
        }
    }

    /**
     * Loads the buyer's address and the addresses of all unique sellers in the cart.
     * This information is required to calculate the delivery fees accurately.
     */
    private fun loadAddressesForDeliveryFee() {
        if (!isAdded || context == null || cartItems.isEmpty()) return

        val buyerId = auth.currentUser?.uid ?: return

        // 1. Fetch the buyer's address.
        database.getReference("Buyers").child(buyerId).get().addOnSuccessListener { buyerSnapshot ->
            buyerAddress = buyerSnapshot.child("address").getValue(String::class.java) ?: ""

            // 2. Get a list of unique seller IDs from the cart.
            val uniqueSellerIds = cartItems.map { it.sellerId }.distinct()
            var loadedCount = 0
            val totalSellers = uniqueSellerIds.size

            if (totalSellers == 0) {
                // If there are no sellers, just update the totals (unlikely case).
                updateDeliveryFeeAndTotal()
            } else {
                // 3. For each unique seller, fetch their address.
                uniqueSellerIds.forEach { sellerId ->
                    database.getReference("Seller").child(sellerId).get().addOnSuccessListener { sellerSnapshot ->
                        sellerAddresses[sellerId] = sellerSnapshot.child("address").getValue(String::class.java) ?: ""
                        loadedCount++
                        // 4. Once all seller addresses are loaded, update the delivery fee.
                        if (loadedCount == totalSellers) {
                            updateDeliveryFeeAndTotal()
                        }
                    }.addOnFailureListener {
                        // Even if one seller's address fails to load, continue.
                        loadedCount++
                        if (loadedCount == totalSellers) {
                            updateDeliveryFeeAndTotal()
                        }
                    }
                }
            }
        }.addOnFailureListener {
             // If buyer address fails to load, still proceed. The calculator will use a default.
            updateDeliveryFeeAndTotal()
        }
    }

    /**
     * Calculates and displays the delivery fee and the grand total.
     * This is called whenever the delivery type changes or when address data is loaded.
     */
    private fun updateDeliveryFeeAndTotal() {
        if (!isAdded || context == null || view == null || cartItems.isEmpty()) return

        val subtotal = CartManager.getTotalPrice(requireContext())
        val isDelivery = rgDeliveryType.checkedRadioButtonId == rbDelivery.id

        if (isDelivery) {
            // If delivery is selected, calculate the fee.
            var totalDeliveryFee = 0.0
            // Group items by seller, as each seller may have a different delivery fee.
            val ordersBySeller = cartItems.groupBy { it.sellerId }

            ordersBySeller.forEach { (sellerId, items) ->
                val orderSubtotal = items.sumOf { it.getTotalPrice() }
                val sellerAddress = sellerAddresses[sellerId] ?: ""

                // Use the DeliveryFeeCalculator utility to get the fee for this part of the order.
                val deliveryFee = DeliveryFeeCalculator.calculateDeliveryFee(
                    orderAmount = orderSubtotal,
                    sellerAddress = sellerAddress,
                    buyerAddress = buyerAddress
                )
                totalDeliveryFee += deliveryFee
            }

            // Update the UI to show the delivery fee and the new grand total.
            tvDeliveryFee.text = "$${String.format("%.2f", totalDeliveryFee)}"
            llDeliveryFee.visibility = View.VISIBLE
            val grandTotal = subtotal + totalDeliveryFee
            tvGrandTotal.text = "$${String.format("%.2f", grandTotal)}"
        } else {
            // If pickup is selected, hide the delivery fee and set the grand total to be the same as the subtotal.
            llDeliveryFee.visibility = View.GONE
            tvGrandTotal.text = "$${String.format("%.2f", subtotal)}"
        }
    }

    /**
     * Initiates the order placement process.
     * It groups items by seller and creates a separate order for each.
     */
    private fun placeOrder() {
        if (!isAdded || context == null) return
        val buyerId = auth.currentUser?.uid

        // Basic checks before proceeding.
        if (buyerId == null) {
            Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }
        if (cartItems.isEmpty()) {
            Toast.makeText(context, "Cart is empty", Toast.LENGTH_SHORT).show()
            return
        }

        // Disable the button to prevent multiple clicks while processing.
        btnPlaceOrder.isEnabled = false

        // Group items by their seller, as each will become a separate order.
        val ordersBySeller = cartItems.groupBy { it.sellerId }
        var completedOrders = 0
        val totalOrderGroups = ordersBySeller.size
        var allOrdersSuccessful = true

        // Fetch buyer's profile information (name, address) to include in the order.
        database.getReference("Buyers").child(buyerId).get().addOnSuccessListener { buyerSnapshot ->
            val buyer = buyerSnapshot.getValue(UserProfile::class.java)
            if (buyer == null) {
                showErrorDialog("Error", "Could not find your user profile.")
                btnPlaceOrder.isEnabled = true
                return@addOnSuccessListener
            }

            // Loop through each group of items for each seller.
            ordersBySeller.forEach { (sellerId, items) ->
                // Fetch seller's info.
                database.getReference("Seller").child(sellerId).get().addOnSuccessListener { sellerSnapshot ->
                    val seller = sellerSnapshot.getValue(Seller::class.java)
                    val sellerName = seller?.shopName?.ifEmpty { seller.name } ?: "Unknown Seller"

                    // Create a new unique ID for this order.
                    val orderId = database.getReference("Orders").push().key ?: return@addOnSuccessListener
                    val itemsMap = HashMap<String, CartItem>().apply { items.forEach { put(it.productId, it) } }
                    val subtotal = items.sumOf { it.getTotalPrice() }
                    val deliveryType = if (rgDeliveryType.checkedRadioButtonId == rbDelivery.id) "delivery" else "pickup"

                    // Calculate delivery fee for this specific seller's order.
                    val deliveryFee = if (deliveryType == "delivery") {
                        DeliveryFeeCalculator.calculateDeliveryFee(subtotal, seller?.address ?: "", buyer.address)
                    } else { 0.0 }

                    // Create the Order object.
                    val order = Order(
                        orderId, buyerId, sellerId, itemsMap, subtotal, "pending",
                        System.currentTimeMillis(), buyer.name, buyer.address, sellerName, deliveryType, deliveryFee
                    )

                    // Save the order to the "Orders" node in Firebase.
                    database.getReference("Orders").child(orderId).setValue(order).addOnCompleteListener { task ->
                        completedOrders++
                        if (!task.isSuccessful) {
                            allOrdersSuccessful = false
                        }

                        // After all order groups have been processed...
                        if (completedOrders == totalOrderGroups) {
                            // Re-enable the button.
                            btnPlaceOrder.isEnabled = true
                            if (allOrdersSuccessful) {
                                // If everything worked, show a confirmation dialog.
                                showOrderConfirmationDialog(order, sellerName)
                                // Clear the cart, but DO NOT restore stock because the items have been sold.
                                CartManager.clearCart(requireContext(), restoreStock = false) { refreshCart() }
                            } else {
                                showErrorDialog("Order Failed", "Some items could not be ordered. Please try again.")
                            }
                        }
                    }
                }
            }
        }
    }


    /**
     * Shows a confirmation dialog after an order is successfully placed.
     */
    private fun showOrderConfirmationDialog(order: Order, sellerName: String) {
        if (!isAdded || context == null) return

        val message = "Your order from $sellerName has been placed successfully!"

        AlertDialog.Builder(requireContext())
            .setTitle("Order Confirmed! ✓")
            .setMessage(message)
            .setPositiveButton("View Orders") { _, _ ->
                // Navigate to the Orders tab.
                (activity as? HomeActivity)?.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)?.currentItem = 3
            }
            .setNegativeButton("Continue Shopping", null)
            .show()
    }

    /**
     * Shows a generic error dialog.
     */
    private fun showErrorDialog(title: String, message: String) {
        if (!isAdded || context == null) return
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    /**
     * Shows an alert when a user tries to add more of an item than is in stock.
     */
    private fun showStockAlertDialog(productId: String, availableStock: Int, requestedQuantity: Int) {
        if (!isAdded || context == null) return
        val productName = CartManager.getCartItems(requireContext())[productId]?.productName ?: "Product"

        val message = if (availableStock <= 0) "$productName is out of stock."
                      else "Only $availableStock of $productName available."

        AlertDialog.Builder(requireContext())
            .setTitle("Stock Alert")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    /**
     * Shows a confirmation dialog before removing an item from the cart.
     */
    private fun showRemoveItemConfirmationDialog(productId: String) {
        if (!isAdded || context == null) return
        val productName = CartManager.getCartItems(requireContext())[productId]?.productName ?: "this item"

        AlertDialog.Builder(requireContext())
            .setTitle("Remove Item")
            .setMessage("Are you sure you want to remove '$productName' from your cart?")
            .setPositiveButton("Remove") { _, _ ->
                // If confirmed, remove the item using CartManager and refresh the cart.
                CartManager.removeFromCart(requireContext(), productId) { refreshCart() }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
