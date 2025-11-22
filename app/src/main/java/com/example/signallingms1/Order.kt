package com.example.signallingms1

import java.io.Serializable
import java.util.HashMap

data class Order(
    var orderId: String = "",
    var buyerId: String = "",
    var sellerId: String = "",
    var items: HashMap<String, CartItem> = HashMap(),
    var totalPrice: Double = 0.0,
    var status: String = "pending", // pending (Waiting for Seller Approval), accepted, rejected, preparing, delivering, ready, delivered, cancelled
    var timestamp: Long = System.currentTimeMillis(),
    var buyerName: String = "",
    var buyerAddress: String = "",
    var sellerName: String = "",
    var deliveryType: String = "delivery", // "delivery" or "pickup"
    var deliveryPrice: Double = 0.0 // Delivery fee calculated at checkout
) : Serializable {
    
    /**
     * Safely updates the order status with validation
     * @param newStatus The new status to transition to
     * @return Result indicating success or failure with error message
     */
    fun updateStatus(newStatus: String): OrderStateValidator.ValidationResult {
        val validationResult = OrderStateValidator.validateTransition(this.status, newStatus)
        if (validationResult.isValid) {
            this.status = newStatus
        }
        return validationResult
    }
}

