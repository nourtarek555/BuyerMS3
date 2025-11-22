package com.example.signallingms1

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.*

class OrderDetailsFragment : Fragment() {

    private lateinit var btnBack: ImageButton
    private lateinit var tvOrderId: TextView
    private lateinit var tvSellerName: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvOrderDate: TextView
    private lateinit var tvTotalPrice: TextView
    private lateinit var tvBuyerAddress: TextView
    private lateinit var tvDeliveryType: TextView
    private lateinit var rvOrderItems: RecyclerView
    private lateinit var orderItemsAdapter: OrderItemsAdapter
    
    private var order: Order? = null
    private val database = FirebaseDatabase.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_order_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            if (!isAdded || context == null) return

            btnBack = view.findViewById(R.id.btnBack)
            tvOrderId = view.findViewById(R.id.tvOrderId)
            tvSellerName = view.findViewById(R.id.tvSellerName)
            tvStatus = view.findViewById(R.id.tvStatus)
            tvOrderDate = view.findViewById(R.id.tvOrderDate)
            tvTotalPrice = view.findViewById(R.id.tvTotalPrice)
            tvBuyerAddress = view.findViewById(R.id.tvBuyerAddress)
            tvDeliveryType = view.findViewById(R.id.tvDeliveryType)
            rvOrderItems = view.findViewById(R.id.rvOrderItems)

            // Get order from arguments - try orderId first, then order object
            val orderId = arguments?.getString("orderId")
            if (orderId != null) {
                loadOrderDetails(orderId)
            } else {
                // Try to get order object directly
                order = arguments?.getSerializable("order") as? Order
                android.util.Log.d("OrderDetailsFragment", "Received order: ${order?.orderId}, items count: ${order?.items?.size}")
                if (order != null && order!!.items.isNotEmpty()) {
                    displayOrderDetails(order!!)
                } else if (order != null && order!!.orderId.isNotEmpty()) {
                    // If order exists but items are empty, fetch from Firebase
                    loadOrderDetails(order!!.orderId)
                } else {
                    android.util.Log.e("OrderDetailsFragment", "Order is null or empty!")
                    if (isAdded && context != null) {
                        Toast.makeText(context, "Order not found", Toast.LENGTH_SHORT).show()
                    }
                    if (isAdded && activity != null) {
                        requireActivity().onBackPressed()
                    }
                }
            }

            orderItemsAdapter = OrderItemsAdapter(mutableListOf())
            rvOrderItems.layoutManager = LinearLayoutManager(requireContext())
            rvOrderItems.adapter = orderItemsAdapter

            btnBack.setOnClickListener {
                if (isAdded && activity != null) {
                    // Pop back stack - HomeActivity's back handler will manage visibility
                    requireActivity().supportFragmentManager.popBackStack()
                }
            }
            
            // Also handle system back button
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Pop back stack - HomeActivity's back handler will manage visibility
                    requireActivity().supportFragmentManager.popBackStack()
                }
            })

        } catch (e: Exception) {
            android.util.Log.e("OrderDetailsFragment", "Error in onViewCreated: ${e.message}", e)
            e.printStackTrace()
        }
    }

    private fun loadOrderDetails(orderId: String) {
        if (!isAdded || context == null) return
        
        android.util.Log.d("OrderDetailsFragment", "Loading order details for: $orderId")
        
        database.getReference("Orders").child(orderId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded || context == null || view == null) return
                    
                    if (!snapshot.exists()) {
                        android.util.Log.e("OrderDetailsFragment", "Order not found in Firebase: $orderId")
                        if (isAdded && context != null) {
                            Toast.makeText(context, "Order not found", Toast.LENGTH_SHORT).show()
                        }
                        if (isAdded && activity != null) {
                            requireActivity().onBackPressed()
                        }
                        return
                    }
                    
                    try {
                        val orderKey = snapshot.key ?: orderId
                        
                        // Manual mapping to handle Firebase structure
                        val buyerId = snapshot.child("buyerId").getValue(String::class.java) ?: ""
                        val sellerId = snapshot.child("sellerId").getValue(String::class.java) ?: ""
                        val totalPrice = snapshot.child("totalPrice").getValue(Any::class.java)?.let {
                            when (it) {
                                is Double -> it
                                is Long -> it.toDouble()
                                is Number -> it.toDouble()
                                is String -> it.toDoubleOrNull() ?: 0.0
                                else -> 0.0
                            }
                        } ?: 0.0
                        val status = snapshot.child("status").getValue(String::class.java) ?: "pending"
                        val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()
                        val buyerName = snapshot.child("buyerName").getValue(String::class.java) ?: ""
                        val buyerAddress = snapshot.child("buyerAddress").getValue(String::class.java) ?: ""
                        val sellerName = snapshot.child("sellerName").getValue(String::class.java) ?: ""
                        val deliveryType = snapshot.child("deliveryType").getValue(String::class.java) ?: "delivery"
                        val deliveryPrice = snapshot.child("deliveryPrice").getValue(Any::class.java)?.let {
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
                        val itemsSnapshot = snapshot.child("items")
                        android.util.Log.d("OrderDetailsFragment", "Items snapshot exists: ${itemsSnapshot.exists()}, children count: ${itemsSnapshot.childrenCount}")
                        
                        for (itemSnapshot in itemsSnapshot.children) {
                            try {
                                val productId = itemSnapshot.key ?: ""
                                
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
                                android.util.Log.d("OrderDetailsFragment", "Added item: $itemProductName, qty: $itemQuantity")
                            } catch (e: Exception) {
                                android.util.Log.e("OrderDetailsFragment", "Error parsing item: ${e.message}", e)
                            }
                        }

                        val loadedOrder = Order(
                            orderId = orderKey,
                            buyerId = buyerId,
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
                        
                        android.util.Log.d("OrderDetailsFragment", "Loaded order with ${itemsMap.size} items")
                        order = loadedOrder
                        displayOrderDetails(loadedOrder)
                    } catch (e: Exception) {
                        android.util.Log.e("OrderDetailsFragment", "Error loading order: ${e.message}", e)
                        if (isAdded && context != null) {
                            Toast.makeText(context, "Error loading order: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    if (!isAdded || context == null) return
                    android.util.Log.e("OrderDetailsFragment", "Error loading order: ${error.message}")
                    if (isAdded && context != null) {
                        Toast.makeText(context, "Failed to load order: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            })
    }

    private fun displayOrderDetails(order: Order) {
        this.order = order

        // Order ID
        tvOrderId.text = "Order #${order.orderId}"

        // Seller Name
        tvSellerName.text = order.sellerName.ifEmpty { "Unknown Seller" }

        // Status
        tvStatus.text = getStatusDisplayText(order.status)
        tvStatus.setTextColor(getStatusColor(order.status))

        // Order Date
        val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
        tvOrderDate.text = dateFormat.format(Date(order.timestamp))

        // Total Price
        tvTotalPrice.text = "$${String.format("%.2f", order.totalPrice)}"

        // Delivery Type
        val deliveryTypeText = when (order.deliveryType.lowercase()) {
            "pickup" -> "Delivery Method: Pickup"
            "delivery" -> "Delivery Method: Delivery"
            else -> "Delivery Method: ${order.deliveryType.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }}"
        }
        tvDeliveryType.text = deliveryTypeText

        // Buyer Address (if available) - only show for delivery orders
        if (order.deliveryType.lowercase() == "delivery" && order.buyerAddress.isNotEmpty()) {
            tvBuyerAddress.text = "Delivery Address: ${order.buyerAddress}"
            tvBuyerAddress.visibility = View.VISIBLE
        } else {
            tvBuyerAddress.visibility = View.GONE
        }

        // Order Items
        val itemsList = order.items.values.toList()
        android.util.Log.d("OrderDetailsFragment", "Displaying order with ${itemsList.size} items")
        if (itemsList.isEmpty()) {
            android.util.Log.w("OrderDetailsFragment", "Order items map is empty or null")
            android.util.Log.d("OrderDetailsFragment", "Order items map: ${order.items}")
        }
        orderItemsAdapter.updateItems(itemsList)
    }

    private fun getStatusDisplayText(status: String): String {
        // Use order's delivery type for context-aware status display
        val deliveryType = order?.deliveryType?.lowercase() ?: ""
        
        return when (status.lowercase()) {
            "pending" -> "Waiting for Seller Approval"
            "accepted" -> "Accepted"
            "rejected" -> "Rejected"
            "ready" -> if (deliveryType == "delivery") {
                "Being Delivered"
            } else {
                "Ready for Pickup"
            }
            "preparing" -> "Preparing"
            "delivering" -> if (deliveryType == "pickup") {
                "Ready for Pickup"
            } else {
                "Out for Delivery"
            }
            "delivered" -> "Delivered"
            "cancelled" -> "Cancelled"
            else -> status.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }

    private fun getStatusColor(status: String): Int {
        return when (status.lowercase()) {
            "pending" -> androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark)
            "accepted" -> androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark)
            "rejected" -> androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
            "ready" -> androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark)
            "preparing" -> androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.holo_blue_light)
            "delivering" -> androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.holo_purple)
            "delivered" -> androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark)
            "cancelled" -> androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.darker_gray)
            else -> androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.black)
        }
    }
}

