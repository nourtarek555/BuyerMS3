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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*

class ShopsFragment : Fragment() {

    private lateinit var rvShops: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var shopAdapter: ShopAdapter
    private val shops = mutableListOf<Seller>()
    private val database: DatabaseReference = FirebaseDatabase.getInstance().getReference("Seller")

    // Callback for shop click
    var onShopSelected: ((Seller) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_shops, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            // Set up callback with parent activity if it's HomeActivity
            (activity as? HomeActivity)?.setShopSelectionCallback(this)

            rvShops = view.findViewById(R.id.rvShops)
            progressBar = view.findViewById(R.id.progressBar)
            tvEmpty = view.findViewById(R.id.tvEmpty)

            // Initialize ShopAdapter with empty list
            shopAdapter = ShopAdapter(mutableListOf()) { seller ->
                onShopSelected?.invoke(seller)
            }

            if (context != null) {
                rvShops.layoutManager = GridLayoutManager(requireContext(), 2)
                rvShops.adapter = shopAdapter
                rvShops.setHasFixedSize(false)

                Log.d("ShopsFragment", "RecyclerView initialized with empty adapter")

                loadShops()
            }
        } catch (e: Exception) {
            Log.e("ShopsFragment", "Error in onViewCreated: ${e.message}", e)
            e.printStackTrace()
        }
    }

    private fun loadShops() {
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        Log.d("ShopsFragment", "Loading shops from database")

        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded || context == null || view == null) return
                
                progressBar.visibility = View.GONE
                shops.clear()

                if (!snapshot.exists()) {
                    tvEmpty.visibility = View.VISIBLE
                    rvShops.visibility = View.GONE
                    Log.w("ShopsFragment", "No shops found")
                    return
                }

                for (sellerSnapshot in snapshot.children) {
                    val sellerKey = sellerSnapshot.key ?: "unknown"

                    // Always use manual mapping to avoid deserialization issues with Products node and string values
                    val name = sellerSnapshot.child("name").getValue(String::class.java) ?: ""
                    val address = sellerSnapshot.child("address").getValue(String::class.java) ?: ""
                    val phone = sellerSnapshot.child("phone").getValue(String::class.java) ?: ""
                    val email = sellerSnapshot.child("email").getValue(String::class.java) ?: ""
                    val photoUrl = sellerSnapshot.child("photoUrl").getValue(String::class.java) ?: ""
                    val appType = sellerSnapshot.child("appType").getValue(String::class.java) ?: "Seller"
                    val shopName = sellerSnapshot.child("shopName").getValue(String::class.java) ?: ""

                    val seller = Seller(
                        uid = sellerKey,
                        name = name,
                        phone = phone,
                        email = email,
                        address = address,
                        appType = appType,
                        photoUrl = photoUrl,
                        shopName = shopName
                    )

                    // Fetch nested products - check "Products" (capital P) first, then "products"
                    val productsMap = mutableMapOf<String, Product>()
                    var productsSnapshot = sellerSnapshot.child("Products")
                    if (!productsSnapshot.exists()) {
                        productsSnapshot = sellerSnapshot.child("products")
                    }
                    
                    for (productSnapshot in productsSnapshot.children) {
                        try {
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
                                else -> 0
                            }
                            
                            val photoUrl = productSnapshot.child("photoUrl").getValue(String::class.java)
                                ?: productSnapshot.child("imageUrl").getValue(String::class.java) ?: ""
                            
                            val product = Product(
                                productId = productSnapshot.key ?: "",
                                sellerId = seller.uid,
                                name = name,
                                price = price,
                                stock = stock,
                                imageUrl = photoUrl
                            )
                            productsMap[product.productId] = product
                        } catch (e: Exception) {
                            Log.e("ShopsFragment", "Error processing product: ${e.message}", e)
                            // Skip this product if there's an error
                        }
                    }
                    seller.products = productsMap

                    shops.add(seller)
                }

                if (!isAdded) return

                if (shops.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                    rvShops.visibility = View.GONE
                } else {
                    tvEmpty.visibility = View.GONE
                    rvShops.visibility = View.VISIBLE
                    shopAdapter.updateShops(ArrayList(shops))
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (!isAdded || context == null || view == null) return
                progressBar.visibility = View.GONE
                tvEmpty.visibility = View.VISIBLE
                Toast.makeText(context, "Failed to load shops: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
