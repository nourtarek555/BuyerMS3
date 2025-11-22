package com.example.signallingms1

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class ShopAdapter(
    private val shops: MutableList<Seller>,
    private val onShopClick: (Seller) -> Unit
) : RecyclerView.Adapter<ShopAdapter.ShopViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShopViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shop, parent, false)
        return ShopViewHolder(view)
    }

    override fun onBindViewHolder(holder: ShopViewHolder, position: Int) {
        holder.bind(shops[position])
    }

    override fun getItemCount(): Int = shops.size

    fun updateShops(newShops: List<Seller>) {
        shops.clear()
        shops.addAll(newShops)
        notifyDataSetChanged()
    }

    inner class ShopViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val shopImage: ImageView = itemView.findViewById(R.id.ivShopImage)
        private val shopName: TextView = itemView.findViewById(R.id.tvShopName)
        private val shopAddress: TextView = itemView.findViewById(R.id.tvShopAddress)

        fun bind(seller: Seller) {
            shopName.text = seller.shopName.ifEmpty { seller.name }
            shopAddress.text = seller.address

            Glide.with(itemView.context)
                .load(seller.photoUrl)
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .into(shopImage)

            itemView.setOnClickListener {
                onShopClick(seller)
            }
        }
    }
}
