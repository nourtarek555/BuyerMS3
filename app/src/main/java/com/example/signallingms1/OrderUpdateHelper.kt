package com.example.signallingms1

import android.util.Log
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

/**
 * OrderUpdateHelper - Utility functions for updating order status with validation
 * 
 * This helper ensures all order status updates go through validation to maintain
 * transaction integrity and enforce proper state transitions.
 */
object OrderUpdateHelper {
    
    private const val TAG = "OrderUpdateHelper"
    
    /**
     * Safely updates an order's status in Firebase with validation
     * 
     * This method:
     * 1. Validates the state transition is allowed
     * 2. Updates the order locally
     * 3. Saves to Firebase
     * 
     * @param orderId The ID of the order to update
     * @param currentStatus The current status of the order
     * @param newStatus The new status to transition to
     * @param onSuccess Callback when update succeeds
     * @param onFailure Callback when update fails (with error message)
     */
    fun updateOrderStatus(
        orderId: String,
        currentStatus: String,
        newStatus: String,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        // Validate the transition
        val validationResult = OrderStateValidator.validateTransition(currentStatus, newStatus)
        
        if (!validationResult.isValid) {
            val errorMessage = validationResult.errorMessage ?: "Invalid state transition"
            Log.e(TAG, "Cannot update order $orderId: $errorMessage")
            onFailure(errorMessage)
            return
        }
        
        // Update in Firebase
        val database = FirebaseDatabase.getInstance()
        val orderRef = database.getReference("Orders").child(orderId)
        
        orderRef.child("status").setValue(newStatus)
            .addOnSuccessListener {
                Log.d(TAG, "Order $orderId status updated from '$currentStatus' to '$newStatus'")
                onSuccess()
            }
            .addOnFailureListener { exception ->
                val errorMessage = "Failed to update order status: ${exception.message}"
                Log.e(TAG, errorMessage, exception)
                onFailure(errorMessage)
            }
    }
    
    /**
     * Updates order status using an Order object
     * 
     * @param order The order to update
     * @param newStatus The new status to transition to
     * @param onSuccess Callback when update succeeds
     * @param onFailure Callback when update fails (with error message)
     */
    fun updateOrderStatus(
        order: Order,
        newStatus: String,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        updateOrderStatus(
            orderId = order.orderId,
            currentStatus = order.status,
            newStatus = newStatus,
            onSuccess = {
                // Update local order status after successful Firebase update
                order.status = newStatus
                onSuccess()
            },
            onFailure = onFailure
        )
    }
    
    /**
     * Gets valid next states for an order based on its current status
     * 
     * @param currentStatus The current order status
     * @return List of valid next states
     */
    fun getValidNextStates(currentStatus: String): List<String> {
        return OrderStateValidator.getValidNextStates(currentStatus)
    }
}

