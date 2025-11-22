package com.example.signallingms1

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class OrdersFragment : Fragment() {

    private lateinit var rvOrders: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var orderAdapter: OrderAdapter
    private val orders = mutableListOf<Order>()
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().getReference("Orders")
    
    // Track previous order statuses for notifications
    private val previousOrderStatuses = mutableMapOf<String, String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_orders, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            if (!isAdded || context == null) return

            rvOrders = view.findViewById(R.id.rvOrders)
            progressBar = view.findViewById(R.id.progressBar)
            tvEmpty = view.findViewById(R.id.tvEmpty)

            orderAdapter = OrderAdapter(orders) { order ->
                // Navigate to order details
                navigateToOrderDetails(order)
            }

            rvOrders.layoutManager = LinearLayoutManager(requireContext())
            rvOrders.adapter = orderAdapter

            loadOrders()
        } catch (e: Exception) {
            android.util.Log.e("OrdersFragment", "Error in onViewCreated: ${e.message}", e)
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isAdded && view != null) {
            loadOrders()
        }
    }

    private fun loadOrders() {
        val buyerId = auth.currentUser?.uid
        if (buyerId == null) {
            if (isAdded && context != null) {
                Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (!isAdded || context == null || view == null) return

        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        // Query orders where buyerId matches current user
        database.orderByChild("buyerId")
            .equalTo(buyerId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded || context == null || view == null) return

                    progressBar.visibility = View.GONE
                    orders.clear()

                    if (!snapshot.exists()) {
                        tvEmpty.visibility = View.VISIBLE
                        rvOrders.visibility = View.GONE
                        Log.d("OrdersFragment", "No orders found for buyer: $buyerId")
                        return
                    }

                    for (orderSnapshot in snapshot.children) {
                        try {
                            val orderKey = orderSnapshot.key ?: "unknown"
                            
                            // Manual mapping to handle Firebase structure
                            val buyerIdValue = orderSnapshot.child("buyerId").getValue(String::class.java) ?: ""
                            val sellerId = orderSnapshot.child("sellerId").getValue(String::class.java) ?: ""
                            val totalPrice = orderSnapshot.child("totalPrice").getValue(Any::class.java)?.let {
                                when (it) {
                                    is Double -> it
                                    is Long -> it.toDouble()
                                    is Number -> it.toDouble()
                                    is String -> it.toDoubleOrNull() ?: 0.0
                                    else -> 0.0
                                }
                            } ?: 0.0
                            val status = orderSnapshot.child("status").getValue(String::class.java) ?: "pending"
                            val timestamp = orderSnapshot.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()
                            val buyerName = orderSnapshot.child("buyerName").getValue(String::class.java) ?: ""
                            val buyerAddress = orderSnapshot.child("buyerAddress").getValue(String::class.java) ?: ""
                            val sellerName = orderSnapshot.child("sellerName").getValue(String::class.java) ?: ""
                            val deliveryType = orderSnapshot.child("deliveryType").getValue(String::class.java) ?: "delivery"
                            val deliveryPrice = orderSnapshot.child("deliveryPrice").getValue(Any::class.java)?.let {
                                when (it) {
                                    is Double -> it
                                    is Long -> it.toDouble()
                                    is Number -> it.toDouble()
                                    is String -> it.toDoubleOrNull() ?: 0.0
                                    else -> 0.0
                                }
                            } ?: 0.0

                            // Parse items
                            val itemsMap = HashMap<String, CartItem>()
                            val itemsSnapshot = orderSnapshot.child("items")
                            for (itemSnapshot in itemsSnapshot.children) {
                                try {
                                    val productId = itemSnapshot.key ?: ""
                                    val cartItem = itemSnapshot.getValue(CartItem::class.java)
                                    if (cartItem != null) {
                                        cartItem.productId = productId
                                        itemsMap[productId] = cartItem
                                    } else {
                                        // Manual mapping for CartItem
                                        val itemProductId = itemSnapshot.child("productId").getValue(String::class.java) ?: productId
                                        val itemSellerId = itemSnapshot.child("sellerId").getValue(String::class.java) ?: sellerId
                                        val itemProductName = itemSnapshot.child("productName").getValue(String::class.java) ?: ""
                                        val itemPrice = itemSnapshot.child("price").getValue(Any::class.java)?.let {
                                            when (it) {
                                                is Double -> it
                                                is Long -> it.toDouble()
                                                is Number -> it.toDouble()
                                                is String -> it.toDoubleOrNull() ?: 0.0
                                                else -> 0.0
                                            }
                                        } ?: 0.0
                                        val itemQuantity = itemSnapshot.child("quantity").getValue(Any::class.java)?.let {
                                            when (it) {
                                                is Int -> it
                                                is Long -> it.toInt()
                                                is Number -> it.toInt()
                                                is String -> it.toIntOrNull() ?: 0
                                                else -> 0
                                            }
                                        } ?: 0
                                        val itemImageUrl = itemSnapshot.child("imageUrl").getValue(String::class.java) ?: ""
                                        val itemMaxStock = itemSnapshot.child("maxStock").getValue(Int::class.java) ?: 0

                                        itemsMap[itemProductId] = CartItem(
                                            productId = itemProductId,
                                            sellerId = itemSellerId,
                                            productName = itemProductName,
                                            price = itemPrice,
                                            quantity = itemQuantity,
                                            imageUrl = itemImageUrl,
                                            maxStock = itemMaxStock
                                        )
                                    }
                                } catch (e: Exception) {
                                    Log.e("OrdersFragment", "Error parsing item: ${e.message}", e)
                                }
                            }

                            val order = Order(
                                orderId = orderKey,
                                buyerId = buyerIdValue,
                                sellerId = sellerId,
                                items = itemsMap,
                                totalPrice = totalPrice,
                                status = status,
                                timestamp = timestamp,
                                buyerName = buyerName,
                                buyerAddress = buyerAddress,
                                sellerName = sellerName,
                                deliveryType = deliveryType,
                                deliveryPrice = deliveryPrice
                            )
                            
                            // Check for status changes and send notifications
                            // Only notify for "ready" (pickup) or "delivering" (delivery) statuses
                            val previousStatus = previousOrderStatuses[orderKey]
                            val currentStatus = status.lowercase()
                            
                            // Only send notifications for ready or delivering status
                            if ((currentStatus == "ready" || currentStatus == "delivering")) {
                                if (previousStatus != null && previousStatus != status) {
                                    // Status changed to ready or delivering - send notification
                                    if (context != null && isAdded) {
                                        NotificationHelper.notifyOrderStatusChange(
                                            context = requireContext(),
                                            order = order,
                                            previousStatus = previousStatus
                                        )
                                        Log.d("OrdersFragment", "Order status changed from '$previousStatus' to '$status' for order $orderKey")
                                    }
                                } else if (previousStatus == null && currentStatus in listOf("ready", "delivering")) {
                                    // First time seeing this order with ready/delivering status
                                    if (context != null && isAdded) {
                                        NotificationHelper.notifyOrderStatusChange(
                                            context = requireContext(),
                                            order = order,
                                            previousStatus = null
                                        )
                                        Log.d("OrdersFragment", "First notification for order $orderKey with status $status")
                                    }
                                }
                            }
                            
                            // Update tracked status
                            previousOrderStatuses[orderKey] = status

                            orders.add(order)
                        } catch (e: Exception) {
                            Log.e("OrdersFragment", "Error parsing order: ${e.message}", e)
                        }
                    }

                    // Sort by timestamp (newest first)
                    orders.sortByDescending { it.timestamp }

                    if (orders.isEmpty()) {
                        tvEmpty.visibility = View.VISIBLE
                        rvOrders.visibility = View.GONE
                    } else {
                        tvEmpty.visibility = View.GONE
                        rvOrders.visibility = View.VISIBLE
                        orderAdapter.updateOrders(ArrayList(orders))
                        Log.d("OrdersFragment", "Loaded ${orders.size} orders")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    if (!isAdded || context == null || view == null) return
                    progressBar.visibility = View.GONE
                    tvEmpty.visibility = View.VISIBLE
                    Toast.makeText(context, "Failed to load orders: ${error.message}", Toast.LENGTH_SHORT).show()
                    Log.e("OrdersFragment", "Error loading orders: ${error.message}")
                }
            })
    }

    private fun navigateToOrderDetails(order: Order) {
        if (!isAdded || activity == null) return

        try {
            val orderDetailsFragment = OrderDetailsFragment().apply {
                arguments = Bundle().apply {
                    // Pass orderId instead of full order to avoid serialization issues
                    putString("orderId", order.orderId)
                    // Also pass order as backup
                    putSerializable("order", order)
                }
            }

            // Navigate using parent activity's fragment container
            (activity as? HomeActivity)?.let { homeActivity ->
                val fragmentContainer = homeActivity.findViewById<androidx.fragment.app.FragmentContainerView>(R.id.fragmentContainer)
                if (fragmentContainer != null) {
                    fragmentContainer.visibility = View.VISIBLE
                    homeActivity.findViewById<ViewPager2>(R.id.viewPager).visibility = View.GONE

                    homeActivity.supportFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, orderDetailsFragment, "order_details_fragment")
                        .addToBackStack("orders")
                        .commitAllowingStateLoss()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("OrdersFragment", "Error navigating to order details: ${e.message}", e)
            if (isAdded && context != null) {
                Toast.makeText(context, "Error opening order details", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

