package com.example.signallingms1

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import com.google.gson.Gson

class ShopsFragment : Fragment() {

    private lateinit var rvShops: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var shopAdapter: ShopAdapter
    private val shops = mutableListOf<Seller>()
    private val database: DatabaseReference = FirebaseDatabase.getInstance().getReference("Seller")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_shops, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            rvShops = view.findViewById(R.id.rvShops)
            progressBar = view.findViewById(R.id.progressBar)
            tvEmpty = view.findViewById(R.id.tvEmpty)

            // Initialize ShopAdapter. When a shop is clicked, set a fragment result.
            shopAdapter = ShopAdapter(mutableListOf()) { seller ->
                // Use the Fragment Result API to pass data back to the parent.
                setFragmentResult("shopSelection", bundleOf("selectedSeller" to Gson().toJson(seller)))
            }

            if (context != null) {
                rvShops.layoutManager = GridLayoutManager(requireContext(), 2)
                rvShops.adapter = shopAdapter
                rvShops.setHasFixedSize(false)

                Log.d("ShopsFragment", "RecyclerView initialized")

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

        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                
                progressBar.visibility = View.GONE
                shops.clear()

                if (!snapshot.exists()) {
                    tvEmpty.visibility = View.VISIBLE
                    rvShops.visibility = View.GONE
                    return
                }

                for (sellerSnapshot in snapshot.children) {
                    val sellerKey = sellerSnapshot.key ?: continue
                    val seller = sellerSnapshot.getValue(Seller::class.java)?.copy(uid = sellerKey)
                    if (seller != null) {
                        shops.add(seller)
                    }
                }

                if (shops.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                    rvShops.visibility = View.GONE
                } else {
                    tvEmpty.visibility = View.GONE
                    rvShops.visibility = View.VISIBLE
                    shopAdapter.updateShops(shops)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (!isAdded) return
                progressBar.visibility = View.GONE
                tvEmpty.visibility = View.VISIBLE
                Toast.makeText(context, "Failed to load shops: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
    
    companion object {
        const val REQUEST_KEY = "shopSelection"
        const val BUNDLE_KEY = "selectedSeller"
    }
}
