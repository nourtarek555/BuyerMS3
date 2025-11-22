package com.example.signallingms1

import java.io.Serializable

data class CartItem(
    var productId: String = "",
    var sellerId: String = "",
    var productName: String = "",
    var price: Double = 0.0,
    var quantity: Int = 1,
    var imageUrl: String = "",
    var maxStock: Int = 0  // Maximum available stock when added to cart
) : Serializable {
    fun getTotalPrice(): Double = price * quantity
    
    fun canIncreaseQuantity(): Boolean {
        return quantity < maxStock
    }
}

