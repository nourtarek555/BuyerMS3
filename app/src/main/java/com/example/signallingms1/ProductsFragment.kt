package com.example.signallingms1

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ProductsFragment : Fragment() {

    private lateinit var rvProducts: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var tvShopName: TextView
    private lateinit var btnBack: ImageButton
    private lateinit var productAdapter: ProductAdapter
    private val products = mutableListOf<Product>()
    private val database = FirebaseDatabase.getInstance().getReference("Seller")
    private var currentSellerId: String? = null
    private var productsListener: ValueEventListener? = null
    private val productStockListeners = mutableMapOf<String, ValueEventListener>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_products, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            rvProducts = view.findViewById(R.id.rvProducts)
            progressBar = view.findViewById(R.id.progressBar)
            tvEmpty = view.findViewById(R.id.tvEmpty)
            tvShopName = view.findViewById(R.id.tvShopName)
            btnBack = view.findViewById(R.id.btnBack)

            productAdapter = ProductAdapter(products) { product, quantity ->
                if (isAdded && context != null) {
                    // Show loading or disable button while processing
                    CartManager.addToCart(requireContext(), product, quantity) { success, message, newStock ->
                        if (!isAdded || context == null) return@addToCart
                        
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        
                        if (success) {
                            // Update the product's stock in the local list
                            // The real-time listener will also update this, but we update immediately for responsiveness
                            val productIndex = products.indexOfFirst { it.productId == product.productId }
                            if (productIndex >= 0 && newStock != null) {
                                products[productIndex].stock = newStock
                                // Update UI on main thread
                                view?.post {
                                    if (isAdded) {
                                        productAdapter?.notifyItemChanged(productIndex)
                                    }
                                }
                            }
                            // Removed low stock warning - doesn't make sense for buyers
                        } else {
                            // Show stock alert
                            val currentStock = newStock ?: product.getDisplayStock()
                            showStockAlertDialog(product.getDisplayName(), currentStock, quantity)
                        }
                    }
                }
            }

            rvProducts.layoutManager = GridLayoutManager(context, 2)
            rvProducts.adapter = productAdapter
            rvProducts.visibility = View.VISIBLE
            rvProducts.setHasFixedSize(false)
            
            android.util.Log.d("ProductsFragment", "RecyclerView initialized, adapter set")

            // Back button functionality
            btnBack.setOnClickListener {
                if (isAdded) {
                    requireActivity().supportFragmentManager.popBackStack()
                }
            }

            // Load products if seller ID was passed via arguments
            arguments?.getString("sellerId")?.let { sellerId ->
                arguments?.getString("sellerName")?.let { sellerName ->
                    tvShopName.text = sellerName
                    loadProducts(sellerId)
                }
            } ?: run {
                // If no arguments, show empty state
                tvShopName.text = "Select a shop to view products"
                tvEmpty.visibility = View.VISIBLE
                tvEmpty.text = "No shop selected"
                rvProducts.visibility = View.GONE
                progressBar.visibility = View.GONE
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (isAdded && context != null) {
                Toast.makeText(context, "Error initializing products view: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun loadProductsForSeller(seller: Seller) {
        if (!isAdded || view == null) {
            return
        }
        
        try {
            // Remove old listeners before loading new seller
            removeProductsListener()
            removeProductStockListeners()
            
            currentSellerId = seller.uid
            tvShopName.text = seller.shopName.ifEmpty { seller.name }
            rvProducts.visibility = View.VISIBLE
            tvEmpty.visibility = View.GONE
            loadProducts(seller.uid)
        } catch (e: Exception) {
            e.printStackTrace()
            if (isAdded && context != null) {
                Toast.makeText(context, "Error loading products: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadProducts(sellerId: String) {
        if (!isAdded || context == null || view == null) {
            return
        }
        
        // Remove existing listener
        removeProductsListener()
        
        try {
            progressBar.visibility = View.VISIBLE
            tvEmpty.visibility = View.GONE
            rvProducts.visibility = View.VISIBLE

            // Try "Products" (capital P) first, then fallback to "products"
            val productsRef = database.child(sellerId).child("Products")
            
            // First load with single value event, then set up real-time listener
            productsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded || context == null || view == null) {
                        return
                    }
                    
                    try {
                        progressBar.visibility = View.GONE

                        // Check if the products node exists
                        if (!snapshot.exists()) {
                            // Try lowercase "products" as fallback
                            val productsRefLower = database.child(sellerId).child("products")
                            productsRefLower.addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(snapshot2: DataSnapshot) {
                                    if (!isAdded || context == null || view == null) return
                                    processProducts(snapshot2, sellerId)
                                    // Set up real-time listener for lowercase "products"
                                    setupRealtimeListener(sellerId, "products")
                                }
                                override fun onCancelled(error: DatabaseError) {
                                    if (!isAdded || context == null || view == null) return
                                    showError("No products available for this shop")
                                }
                            })
                            return
                        }

                        processProducts(snapshot, sellerId)
                        // Set up real-time listener after initial load
                        setupRealtimeListener(sellerId, "Products")
                    } catch (e: Exception) {
                        e.printStackTrace()
                        if (isAdded && context != null) {
                            showError("Error processing products: ${e.message}")
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    if (!isAdded || context == null || view == null) return
                    try {
                        progressBar.visibility = View.GONE
                        Toast.makeText(context, "Failed to load products: ${error.message}", Toast.LENGTH_SHORT).show()
                        showError("Failed to load products")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
            if (isAdded && context != null) {
                progressBar.visibility = View.GONE
                showError("Error loading products: ${e.message}")
            }
        }
    }
    
    private fun setupRealtimeListener(sellerId: String, productsNode: String) {
        // Remove old listener first
        removeProductsListener()
        
        val productsRef = database.child(sellerId).child(productsNode)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded || context == null || view == null) return
                
                android.util.Log.d("ProductsFragment", "Realtime update: ${snapshot.childrenCount} products")
                
                // Update stock for each product in real-time
                for (productSnapshot in snapshot.children) {
                    val productId = productSnapshot.key ?: continue
                    val stockValue = productSnapshot.child("stock").getValue(Any::class.java)
                    val newStock = when (stockValue) {
                        is Int -> stockValue
                        is Long -> stockValue.toInt()
                        is String -> stockValue.toIntOrNull() ?: 0
                        is Number -> stockValue.toInt()
                        else -> 0
                    }
                    
                    // Update the product in the list
                    val productIndex = products.indexOfFirst { it.productId == productId }
                    if (productIndex >= 0 && products[productIndex].stock != newStock) {
                        android.util.Log.d("ProductsFragment", "Updating stock for $productId: ${products[productIndex].stock} -> $newStock")
                        products[productIndex].stock = newStock
                        // Notify adapter of the change on UI thread
                        if (isAdded && view != null) {
                            view?.post {
                                if (isAdded) {
                                    productAdapter?.notifyItemChanged(productIndex)
                                }
                            }
                        }
                    }
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("ProductsFragment", "Realtime listener cancelled: ${error.message}")
            }
        }
        
        productsListener = listener
        productsRef.addValueEventListener(listener)
        android.util.Log.d("ProductsFragment", "Realtime listener set up for $sellerId/$productsNode")
    }
    
    private fun removeProductsListener() {
        productsListener?.let { listener ->
            currentSellerId?.let { sellerId ->
                // Try both "Products" and "products"
                database.child(sellerId).child("Products").removeEventListener(listener)
                database.child(sellerId).child("products").removeEventListener(listener)
            }
        }
        productsListener = null
    }
    
    private fun removeProductStockListeners() {
        productStockListeners.forEach { (productId, listener) ->
            currentSellerId?.let { sellerId ->
                database.child(sellerId).child("Products").child(productId).removeEventListener(listener)
                database.child(sellerId).child("products").child(productId).removeEventListener(listener)
            }
        }
        productStockListeners.clear()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        removeProductsListener()
        removeProductStockListeners()
    }
    
    private fun processProducts(snapshot: DataSnapshot, sellerId: String) {
        if (!isAdded || context == null || view == null) return
        
        try {
            products.clear()

            if (!snapshot.exists()) {
                showError("No products available for this shop")
                return
            }

            android.util.Log.d("ProductsFragment", "Processing ${snapshot.childrenCount} products")
            
            for (productSnapshot in snapshot.children) {
                try {
                    val productKey = productSnapshot.key ?: "unknown"
                    android.util.Log.d("ProductsFragment", "Processing product key: $productKey")
                    
                    // Always use manual mapping to handle string values for price/stock
                    val name = productSnapshot.child("name").getValue(String::class.java) ?: ""
                    
                    // Price can be Double or String
                    val priceValue = productSnapshot.child("price").getValue(Any::class.java)
                    val price = when (priceValue) {
                        is Double -> priceValue
                        is Long -> priceValue.toDouble()
                        is String -> priceValue.toDoubleOrNull() ?: 0.0
                        is Number -> priceValue.toDouble()
                        else -> 0.0
                    }
                    
                    // Stock can be Int or String
                    val stockValue = productSnapshot.child("stock").getValue(Any::class.java)
                    val stock = when (stockValue) {
                        is Int -> stockValue
                        is Long -> stockValue.toInt()
                        is String -> stockValue.toIntOrNull() ?: 0
                        is Number -> stockValue.toInt()
                        else -> productSnapshot.child("quantity").getValue(Int::class.java) ?: 0
                    }
                    
                    val photoUrl = productSnapshot.child("photoUrl").getValue(String::class.java)
                        ?: productSnapshot.child("imageUrl").getValue(String::class.java) ?: ""
                    
                    android.util.Log.d("ProductsFragment", "Manual mapping - name: $name, price: $price, stock: $stock, photoUrl: $photoUrl")
                    
                    val product = Product(
                        productId = productKey,
                        sellerId = sellerId,
                        name = name,
                        price = price,
                        stock = stock,
                        imageUrl = photoUrl
                    )
                    android.util.Log.d("ProductsFragment", "Created product: ${product.getDisplayName()}, price: ${product.getDisplayPrice()}, stock: ${product.getDisplayStock()}")
                    products.add(product)
                } catch (e: Exception) {
                    // Skip this product if mapping fails
                    android.util.Log.e("ProductsFragment", "Error processing product: ${e.message}", e)
                    e.printStackTrace()
                }
            }

            android.util.Log.d("ProductsFragment", "Total products processed: ${products.size}")
            
            if (products.isEmpty()) {
                android.util.Log.w("ProductsFragment", "No products found after processing")
                showError("No products available for this shop")
            } else {
                android.util.Log.d("ProductsFragment", "Updating adapter with ${products.size} products")
                // Ensure UI updates happen on main thread
                if (isAdded && view != null) {
                    view?.post {
                        if (isAdded && view != null) {
                            tvEmpty.visibility = View.GONE
                            rvProducts.visibility = View.VISIBLE
                            val productsCopy = ArrayList(products)
                            productAdapter.updateProducts(productsCopy)
                            android.util.Log.d("ProductsFragment", "Adapter updated. Item count: ${productAdapter.itemCount}")
                            android.util.Log.d("ProductsFragment", "RecyclerView visibility: ${rvProducts.visibility}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (isAdded && context != null) {
                showError("Error processing products: ${e.message}")
            }
        }
    }
    
    private fun showError(message: String) {
        if (!isAdded || context == null || view == null) return
        try {
            android.util.Log.d("ProductsFragment", "Showing error: $message")
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = message
            rvProducts.visibility = View.GONE
            progressBar.visibility = View.GONE
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Shows stock alert dialog when insufficient stock
     */
    private fun showStockAlertDialog(productName: String, availableStock: Int, requestedQuantity: Int) {
        if (!isAdded || context == null) return
        
        val message = when {
            availableStock == 0 -> "$productName is currently out of stock. Please try again later."
            else -> "Insufficient stock for $productName.\n\nAvailable: $availableStock\nRequested: $requestedQuantity"
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("Stock Alert")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setIcon(android.R.drawable.stat_notify_error)
            .show()
        
        // Removed low stock notification - doesn't make sense for buyers
    }
}

