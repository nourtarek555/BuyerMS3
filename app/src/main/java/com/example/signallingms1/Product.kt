
// Defines the package this class belongs to.
package com.example.signallingms1

// Imports Firebase annotations to customize how data is mapped to and from the database.
import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.PropertyName

/**
 * Represents a product in the application.
 * This data class holds all the necessary information about a product,
 * including its ID, seller, name, price, stock, and image.
 * The `@IgnoreExtraProperties` annotation prevents the app from crashing if the database
 * contains extra fields that are not defined in this class.
 */
@IgnoreExtraProperties
data class Product(
    // The unique identifier for the product.
    var productId: String = "",
    // The unique identifier of the seller who owns this product.
    var sellerId: String = "",

    // The name of the product.
    // The `@PropertyName` annotations ensure that this field is correctly mapped
    // to the "name" field in the Firebase database during serialization and deserialization.
    @get:PropertyName("name")
    @set:PropertyName("name")
    var name: String = "",

    // The price of the product.
    // The `@PropertyName` annotations map this to the "price" field in Firebase.
    @get:PropertyName("price")
    @set:PropertyName("price")
    var price: Double = 0.0,

    // The available stock quantity of the product.
    // The `@PropertyName` annotations map this to the "stock" field in Firebase.
    @get:PropertyName("stock")
    @set:PropertyName("stock")
    var stock: Int = 0,

    // The URL of the product's image.
    // The `@PropertyName` annotations map this to the "photoUrl" field in Firebase.
    @get:PropertyName("photoUrl")
    @set:PropertyName("photoUrl")
    var imageUrl: String = "",

    // A detailed description of the product.
    var description: String = ""
) {
    /**
     * Helper function to safely get the product's display name.
     * @return The product name, or an empty string if it's not set.
     */
    fun getDisplayName(): String = name.ifEmpty { "" }

    /**
     * Helper function to get the product's price.
     * @return The price of the product.
     */
    fun getDisplayPrice(): Double = price

    /**
     * Helper function to get the product's available stock.
     * @return The stock quantity of the product.
     */
    fun getDisplayStock(): Int = stock

    /**
     * Helper function to get the product's image URL.
     * @return The URL of the product's image.
     */
    fun getDisplayImageUrl(): String = imageUrl
}
