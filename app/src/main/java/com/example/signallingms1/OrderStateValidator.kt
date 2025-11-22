package com.example.signallingms1

/**
 * OrderStateValidator - Enforces transaction integrity by validating order state transitions
 * 
 * Order State Sequence:
 * 1. pending (Waiting for Seller Approval)
 * 2. accepted (after seller approval)
 * 3. [preparing OR delivering OR ready] - depending on delivery/pickup type
 *    - preparing -> ready (for pickup orders)
 *    - preparing -> delivering (for delivery orders)
 *    - delivering -> delivered (for delivery orders)
 *    - ready -> delivered (for pickup orders)
 * 4. delivered (final state)
 * 
 * Terminal states: delivered, rejected, cancelled
 */
object OrderStateValidator {
    
    /**
     * Valid order states
     */
    enum class OrderState(val value: String) {
        PENDING("pending"),
        ACCEPTED("accepted"),
        REJECTED("rejected"),
        PREPARING("preparing"),
        DELIVERING("delivering"),
        READY("ready"),
        DELIVERED("delivered"),
        CANCELLED("cancelled")
    }
    
    /**
     * Validates if a state transition is allowed
     * @param currentStatus Current order status
     * @param newStatus New order status to transition to
     * @return ValidationResult with isValid flag and error message if invalid
     */
    fun validateTransition(currentStatus: String, newStatus: String): ValidationResult {
        val current = currentStatus.lowercase()
        val new = newStatus.lowercase()
        
        // Same state transition is always valid (idempotent)
        if (current == new) {
            return ValidationResult(true, null)
        }
        
        // Terminal states cannot be changed (except back to accepted for special cases)
        if (isTerminalState(current) && current != OrderState.ACCEPTED.value) {
            return ValidationResult(
                false,
                "Cannot transition from terminal state '$currentStatus'. Order is already completed."
            )
        }
        
        // Validate based on current state
        return when (current) {
            OrderState.PENDING.value -> {
                // From pending, can only go to accepted, rejected, or cancelled
                when (new) {
                    OrderState.ACCEPTED.value,
                    OrderState.REJECTED.value,
                    OrderState.CANCELLED.value -> ValidationResult(true, null)
                    else -> ValidationResult(
                        false,
                        "Invalid transition from 'Pending' to '$newStatus'. Can only transition to: Accepted, Rejected, or Cancelled."
                    )
                }
            }
            
            OrderState.ACCEPTED.value -> {
                // From accepted, can go to preparing, delivering, ready, or cancelled
                // This allows flexibility for delivery/pickup options
                when (new) {
                    OrderState.PREPARING.value,
                    OrderState.DELIVERING.value,
                    OrderState.READY.value,
                    OrderState.CANCELLED.value -> ValidationResult(true, null)
                    else -> ValidationResult(
                        false,
                        "Invalid transition from 'Accepted' to '$newStatus'. Can only transition to: Preparing, Delivering, Ready for Pickup, or Cancelled."
                    )
                }
            }
            
            OrderState.PREPARING.value -> {
                // From preparing, can go to delivering (for delivery), ready (for pickup), or cancelled
                when (new) {
                    OrderState.DELIVERING.value,
                    OrderState.READY.value,
                    OrderState.CANCELLED.value -> ValidationResult(true, null)
                    else -> ValidationResult(
                        false,
                        "Invalid transition from 'Preparing' to '$newStatus'. Can only transition to: Delivering (for delivery), Ready for Pickup, or Cancelled."
                    )
                }
            }
            
            OrderState.DELIVERING.value -> {
                // From delivering, can only go to delivered or cancelled
                when (new) {
                    OrderState.DELIVERED.value,
                    OrderState.CANCELLED.value -> ValidationResult(true, null)
                    else -> ValidationResult(
                        false,
                        "Invalid transition from 'Delivering' to '$newStatus'. Can only transition to: Delivered or Cancelled."
                    )
                }
            }
            
            OrderState.READY.value -> {
                // From ready (pickup), can only go to delivered or cancelled
                when (new) {
                    OrderState.DELIVERED.value,
                    OrderState.CANCELLED.value -> ValidationResult(true, null)
                    else -> ValidationResult(
                        false,
                        "Invalid transition from 'Ready for Pickup' to '$newStatus'. Can only transition to: Delivered or Cancelled."
                    )
                }
            }
            
            OrderState.REJECTED.value,
            OrderState.CANCELLED.value,
            OrderState.DELIVERED.value -> {
                // Terminal states - cannot transition
                ValidationResult(
                    false,
                    "Cannot transition from terminal state '$currentStatus' to '$newStatus'."
                )
            }
            
            else -> {
                // Unknown current state
                ValidationResult(
                    false,
                    "Unknown order state '$currentStatus'. Cannot validate transition."
                )
            }
        }
    }
    
    /**
     * Checks if a state is terminal (final state)
     */
    fun isTerminalState(status: String): Boolean {
        return when (status.lowercase()) {
            OrderState.DELIVERED.value,
            OrderState.REJECTED.value,
            OrderState.CANCELLED.value -> true
            else -> false
        }
    }
    
    /**
     * Gets all valid next states for a given current state
     */
    fun getValidNextStates(currentStatus: String): List<String> {
        val current = currentStatus.lowercase()
        return when (current) {
            OrderState.PENDING.value -> listOf(
                OrderState.ACCEPTED.value,
                OrderState.REJECTED.value,
                OrderState.CANCELLED.value
            )
            OrderState.ACCEPTED.value -> listOf(
                OrderState.PREPARING.value,
                OrderState.DELIVERING.value,
                OrderState.READY.value,
                OrderState.CANCELLED.value
            )
            OrderState.PREPARING.value -> listOf(
                OrderState.DELIVERING.value,
                OrderState.READY.value,
                OrderState.CANCELLED.value
            )
            OrderState.DELIVERING.value -> listOf(
                OrderState.DELIVERED.value,
                OrderState.CANCELLED.value
            )
            OrderState.READY.value -> listOf(
                OrderState.DELIVERED.value,
                OrderState.CANCELLED.value
            )
            else -> emptyList() // Terminal states have no valid next states
        }
    }
    
    /**
     * Result of validation
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String?
    )
}

