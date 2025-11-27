
// Defines the package this class belongs to.
package com.example.signallingms1

// Imports for Android UI components and libraries.
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

/**
 * This adapter is responsible for displaying a list of shops in a RecyclerView.
 * It takes a list of Seller objects (each representing a shop) and creates a view for each one.
 * It also handles click events on individual shops.
 *
 * @param shops A mutable list of Seller objects to be displayed.
 * @param onShopClick A lambda function to be invoked when a shop item is clicked. It passes the clicked Seller object.
 */
class ShopAdapter(
    // The list of shops (sellers) that the adapter will display.
    private val shops: MutableList<Seller>,
    // A callback function that is triggered when a user taps on a shop.
    private val onShopClick: (Seller) -> Unit
) : RecyclerView.Adapter<ShopAdapter.ShopViewHolder>() {

    /**
     * Called when RecyclerView needs a new [ShopViewHolder] to represent a shop item.
     * This is where the layout for each item is inflated from its XML definition.
     *
     * @param parent The ViewGroup into which the new View will be added.
     * @param viewType The view type of the new View.
     * @return A new ShopViewHolder that holds the View for a single shop item.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShopViewHolder {
        // Inflate the XML layout file (item_shop.xml) for a single shop item.
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shop, parent, false)
        // Create and return a new instance of ShopViewHolder.
        return ShopViewHolder(view)
    }

    /**
     * Called by RecyclerView to display the data at a specific position.
     * This method fetches the appropriate shop data and uses it to populate the ViewHolder's views.
     *
     * @param holder The ShopViewHolder that should be updated.
     * @param position The position of the item within the adapter's data set.
     */
    override fun onBindViewHolder(holder: ShopViewHolder, position: Int) {
        // Get the shop data for the current position and bind it to the ViewHolder.
        holder.bind(shops[position])
    }

    /**
     * Returns the total number of shops in the data set held by the adapter.
     *
     * @return The total number of shops.
     */
    override fun getItemCount(): Int = shops.size

    /**
     * Updates the list of shops with a new list and refreshes the RecyclerView.
     *
     * @param newShops The new list of Seller objects to display.
     */
    fun updateShops(newShops: List<Seller>) {
        // Clear the current list of shops.
        shops.clear()
        // Add all the new shops to the list.
        shops.addAll(newShops)
        // Notify the adapter that the data has changed, so the RecyclerView can be redrawn.
        notifyDataSetChanged()
    }

    /**
     * A ViewHolder describes a shop item view and metadata about its place within the RecyclerView.
     *
     * @param itemView The view for a single shop item, inflated in onCreateViewHolder.
     */
    inner class ShopViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // ImageView to display the shop's logo or primary image.
        private val shopImage: ImageView = itemView.findViewById(R.id.ivShopImage)
        // TextView to display the name of the shop.
        private val shopName: TextView = itemView.findViewById(R.id.tvShopName)
        // TextView to display the address of the shop.
        private val shopAddress: TextView = itemView.findViewById(R.id.tvShopAddress)

        /**
         * Binds the data from a Seller object to the views in the ViewHolder.
         *
         * @param seller The Seller object containing the shop's data.
         */
        fun bind(seller: Seller) {
            // Set the shop name. If the shop name is empty, use the seller's name as a fallback.
            shopName.text = seller.shopName.ifEmpty { seller.name }
            // Set the shop address.
            shopAddress.text = seller.address

            // Use the Glide library to load the shop's image from a URL.
            Glide.with(itemView.context)
                .load(seller.photoUrl) // The URL of the image to load.
                .placeholder(R.drawable.ic_person) // A placeholder image to show while the actual image is loading.
                .error(R.drawable.ic_person) // An image to show if the URL is invalid or loading fails.
                .into(shopImage) // The ImageView to load the image into.

            // Set an OnClickListener for the entire item view.
            itemView.setOnClickListener {
                // When the item is clicked, invoke the onShopClick callback, passing the current seller object.
                onShopClick(seller)
            }
        }
    }
}
