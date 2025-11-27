
// Defines the package this class belongs to.
package com.example.signallingms1

// Imports the Serializable interface to allow objects of this class to be passed between components.
import java.io.Serializable

/**
 * Represents an item in the user's shopping cart.
 * This data class holds all the necessary information about a product that has been added to the cart,
 * including its ID, quantity, and price. It implements Serializable to allow CartItem objects
 * to be passed between activities or fragments, such as from a product list to the cart screen.
 *
 * @param productId The unique identifier of the product.
 * @param sellerId The unique identifier of the seller of the product.
 * @param productName The name of the product.
 * @param price The price of a single unit of the product.
 * @param quantity The number of units of this product in the cart.
 * @param imageUrl The URL of the product's image.
 * @param maxStock The maximum available stock of the product at the time it was added to the cart. This is used to prevent ordering more than what is available.
 */
data class CartItem(
    // The unique ID of the product.
    var productId: String = "",
    // The ID of the seller who owns the product.
    var sellerId: String = "",
    // The name of the product.
    var productName: String = "",
    // The price for a single unit of the product.
    var price: Double = 0.0,
    // The quantity of this item in the cart. Defaults to 1.
    var quantity: Int = 1,
    // The URL for the product's image.
    var imageUrl: String = "",
    // The available stock for the product when it was added to the cart.
    var maxStock: Int = 0
) : Serializable { // Makes the class serializable, allowing it to be passed in intents.

    /**
     * Calculates the total price for this cart item.
     * This is done by multiplying the price of a single unit by the quantity of units in the cart.
     *
     * @return The total price for this cart item as a Double.
     */
    fun getTotalPrice(): Double = price * quantity

    /**
     * Checks if the quantity of this item in the cart can be increased.
     * The quantity can be increased only if it is less than the maximum available stock.
     *
     * @return True if the quantity can be increased, false otherwise.
     */
    fun canIncreaseQuantity(): Boolean {
        // Returns true if the current quantity is less than the max stock.
        return quantity < maxStock
    }
}
