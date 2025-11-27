
// Defines the package this class belongs to.
package com.example.signallingms1

/**
 * Represents a user's profile in the application.
 * This data class holds all the necessary information about a user,
 * including their personal details and application-specific data.
 */
data class UserProfile(
    // The unique identifier for the user, typically the user ID from the authentication system (e.g., Firebase Auth UID).
    var uid: String = "",

    // The user's full name.
    var name: String = "",

    // The user's phone number.
    var phone: String = "",

    // The user's email address.
    var email: String = "",

    // The user's physical address. This can be a home or shipping address.
    var address: String = "",

    // The type of application the user is registered for (e.g., "Buyer" or "Seller").
    // This helps in distinguishing user roles within the app.
    var appType: String = "",

    // The URL of the user's profile picture.
    var photoUrl: String = ""
)
