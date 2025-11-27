
// Defines the package this class belongs to.
package com.example.signallingms1

/**
 * Represents a seller in the application.
 * This data class holds all the necessary information about a seller,
 * including their personal details, shop information, and the products they offer.
 */
data class Seller(
    // The unique identifier for the seller, typically the user ID from the authentication system.
    var uid: String = "",
    // The name of the seller.
    var name: String = "",
    // The seller's phone number.
    var phone: String = "",
    // The seller's email address.
    var email: String = "",
    // The physical address of the seller or their shop.
    var address: String = "",
    // The type of application user, which is hardcoded to "Seller" for this class.
    var appType: String = "Seller",
    // The URL of the seller's profile photo or shop logo.
    var photoUrl: String = "",
    // The name of the seller's shop.
    var shopName: String = "",
    // A map of products offered by the seller, where the key is the product ID and the value is the Product object.
    var products: MutableMap<String, Product> = mutableMapOf()
)
