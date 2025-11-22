package com.example.signallingms1

import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.PropertyName

@IgnoreExtraProperties
data class Product(
    var productId: String = "",
    var sellerId: String = "",
    @get:PropertyName("name")
    @set:PropertyName("name")
    var name: String = "",
    @get:PropertyName("price")
    @set:PropertyName("price")
    var price: Double = 0.0,
    @get:PropertyName("stock")
    @set:PropertyName("stock")
    var stock: Int = 0,
    @get:PropertyName("photoUrl")
    @set:PropertyName("photoUrl")
    var imageUrl: String = "",
    var description: String = ""
) {
    // Helper function to get the correct name
    fun getDisplayName(): String = name.ifEmpty { "" }
    
    // Helper function to get the correct price
    fun getDisplayPrice(): Double = price
    
    // Helper function to get the correct stock/quantity
    fun getDisplayStock(): Int = stock
    
    // Helper function to get the correct image URL
    fun getDisplayImageUrl(): String = imageUrl
}

