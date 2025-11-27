
// Defines the package this class belongs to.
package com.example.signallingms1

// Imports the Serializable interface to allow objects of this class to be passed between components.
import java.io.Serializable
// Imports the HashMap class to store order items.
import java.util.HashMap

/**
 * Represents a customer's order in the application.
 * This data class holds all the necessary information about an order,
 * including the items, prices, buyer and seller details, and its current status.
 * It implements Serializable to allow Order objects to be passed between activities or fragments.
 */
data class Order(
    // The unique identifier for the order. Default is an empty string.
    var orderId: String = "",
    // The unique identifier of the buyer who placed the order.
    var buyerId: String = "",
    // The unique identifier of the seller fulfilling the order.
    var sellerId: String = "",
    // A map of items included in the order, where the key is the product ID and the value is the CartItem object.
    var items: HashMap<String, CartItem> = HashMap(),
    // The total price of all items in the order, excluding delivery fees.
    var totalPrice: Double = 0.0,
    // The current status of the order. It can be one of several predefined states like "pending", "accepted", "delivered", etc.
    var status: String = "pending", // Possible statuses: pending, accepted, rejected, preparing, ready, delivering, delivered, cancelled
    // The timestamp when the order was created, in milliseconds since the epoch.
    var timestamp: Long = System.currentTimeMillis(),
    // The name of the buyer.
    var buyerName: String = "",
    // The delivery address of the buyer.
    var buyerAddress: String = "",
    // The name of the seller.
    var sellerName: String = "",
    // Specifies whether the order is for "delivery" or "pickup".
    var deliveryType: String = "delivery",
    // The calculated fee for delivery, if applicable.
    var deliveryPrice: Double = 0.0
) : Serializable { // Makes the class serializable, so it can be passed in intents.

    /**
     * Safely updates the order's status after validating the transition.
     * This function uses the OrderStateValidator to ensure that status changes are logical
     * (e.g., an order can't go from "delivered" back to "pending").
     *
     * @param newStatus The desired new status for the order.
     * @return A ValidationResult object which indicates if the transition was successful
     *         and provides an error message if it was not.
     */
    fun updateStatus(newStatus: String): OrderStateValidator.ValidationResult {
        // Validate the requested status transition using a dedicated validator class.
        val validationResult = OrderStateValidator.validateTransition(this.status, newStatus)
        // If the validation was successful, update the order's status.
        if (validationResult.isValid) {
            // Set the new status.
            this.status = newStatus
        }
        // Return the result of the validation.
        return validationResult
    }
}
