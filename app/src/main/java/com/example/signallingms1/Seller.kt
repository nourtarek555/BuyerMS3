package com.example.signallingms1

data class Seller(
    var uid: String = "",
    var name: String = "",
    var phone: String = "",
    var email: String = "",
    var address: String = "",
    var appType: String = "Seller",
    var photoUrl: String = "",
    var shopName: String = "",
    var products: MutableMap<String, Product> = mutableMapOf()
)
