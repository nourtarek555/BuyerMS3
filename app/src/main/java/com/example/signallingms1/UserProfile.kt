package com.example.signallingms1

data class UserProfile(
    var uid: String = "",
    var name: String = "",
    var phone: String = "",
    var email: String = "",
    var address: String="",
    var appType: String="",
    var photoUrl: String=""
)
