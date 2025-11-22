package com.example.signallingms1

import android.util.Log
import kotlin.random.Random

/**
 * DeliveryFeeCalculator - Calculates delivery fees dynamically based on various factors
 * 
 * Factors considered:
 * - Distance estimation from seller to buyer address
 * - Order amount (free delivery over threshold)
 * - Address similarity and zone analysis
 * - Order size/weight factor
 * - Dynamic variability and availability factors
 * - Random variation for realistic pricing
 */
object DeliveryFeeCalculator {
    
    private const val TAG = "DeliveryFeeCalculator"
    
    // Default pricing configuration
    private const val DEFAULT_BASE_FEE = 4.0 // Base delivery fee
    private const val DEFAULT_FREE_DELIVERY_THRESHOLD = 50.0 // Free delivery over $50
    private const val DEFAULT_DISTANCE_RATE = 0.8 // $0.80 per estimated km
    private const val DEFAULT_MIN_FEE = 3.0 // Minimum delivery fee (ensures never zero)
    private const val DEFAULT_MAX_FEE = 20.0 // Maximum delivery fee
    
    // Variability factors
    private const val VARIABILITY_PERCENTAGE = 0.15 // ±15% random variation
    private const val AVAILABILITY_FACTOR_MIN = 0.9 // 90-110% multiplier based on availability
    private const val AVAILABILITY_FACTOR_MAX = 1.1
    
    /**
     * Configuration for delivery fee calculation
     */
    data class DeliveryConfig(
        val baseFee: Double = DEFAULT_BASE_FEE,
        val freeDeliveryThreshold: Double = DEFAULT_FREE_DELIVERY_THRESHOLD,
        val distanceRate: Double = DEFAULT_DISTANCE_RATE, // Per km
        val minFee: Double = DEFAULT_MIN_FEE,
        val maxFee: Double = DEFAULT_MAX_FEE,
        val fixedFee: Double? = null // If set, use fixed fee only (overrides other calculations)
    )
    
    /**
     * Calculates delivery fee based on location (simplified pricing structure)
     * 
     * Pricing:
     * - Rehab/Fifth Settlement (inside Egypt): $10
     * - Other places in Egypt: $30
     * - Outside Egypt: $50
     * 
     * @param orderAmount Total price of items in the order (excluding delivery)
     * @param sellerAddress Seller's address
     * @param buyerAddress Buyer's delivery address
     * @param config Optional configuration (uses defaults if not provided)
     * @return Calculated delivery fee
     */
    fun calculateDeliveryFee(
        orderAmount: Double,
        sellerAddress: String,
        buyerAddress: String,
        config: DeliveryConfig = DeliveryConfig()
    ): Double {
        // If fixed fee is configured, use it
        if (config.fixedFee != null) {
            Log.d(TAG, "Using fixed delivery fee: ${config.fixedFee}")
            return config.fixedFee.coerceIn(config.minFee, config.maxFee)
        }
        
        // Determine delivery fee based on location (always calculate, ignore free delivery threshold for now)
        val fee = determineLocationBasedFee(buyerAddress)
        
        Log.d(TAG, "Location-based delivery fee calculation:")
        Log.d(TAG, "  - Order amount: $$orderAmount")
        Log.d(TAG, "  - Seller address: '$sellerAddress'")
        Log.d(TAG, "  - Buyer address: '$buyerAddress'")
        Log.d(TAG, "  - Calculated delivery fee: $$fee")
        
        // Note: Free delivery threshold disabled - always charge location-based fee
        // Uncomment below if you want free delivery over threshold:
        // if (orderAmount >= config.freeDeliveryThreshold) {
        //     Log.d(TAG, "Free delivery: Order amount ($orderAmount) >= threshold (${config.freeDeliveryThreshold})")
        //     return 0.0
        // }
        
        return fee
    }
    
    /**
     * Determines delivery fee based on buyer's location
     * 
     * @param buyerAddress Buyer's delivery address
     * @return Delivery fee: $10 for Rehab/Fifth Settlement, $30 for other Egypt, $50 for outside Egypt
     */
    private fun determineLocationBasedFee(buyerAddress: String): Double {
        Log.d(TAG, "determineLocationBasedFee called with address: '$buyerAddress'")
        
        if (buyerAddress.isBlank()) {
            // Default to Egypt (non-Rehab) if address is blank
            Log.d(TAG, "Blank address - defaulting to Egypt (non-Rehab) fee: $30")
            return 30.0
        }
        
        val normalizedAddress = buyerAddress.lowercase().trim()
        Log.d(TAG, "Normalized address: '$normalizedAddress'")
        
        // Check for Rehab/Fifth Settlement keywords
        val rehabKeywords = listOf(
            "rehab", 
            "fifth settlement", 
            "5th settlement",
            "new cairo",
            "cairo",// Often includes Fifth Settlement
            "madinet nasr", // Sometimes associated
            "nasr city" // Nasr City is near Fifth Settlement
        )
        
        val matchedRehabKeyword = rehabKeywords.find { keyword ->
            normalizedAddress.contains(keyword, ignoreCase = true)
        }
        val isRehabArea = matchedRehabKeyword != null
        
        if (isRehabArea) {
            Log.d(TAG, "Location detected: Rehab/Fifth Settlement (matched: '$matchedRehabKeyword') - Fee: $10")
            return 10.0
        }
        
        // Check if address is in Egypt
        val egyptKeywords = listOf(
            "alexandria",
            "giza",
            "luxor",
            "aswan",
            "port said",
            "suez",
            "mansoura",
            "tanta",
            "ismailia",
            "assiut",
            "zagazig",
            "damanhur",
            "minya",
            "beni suef",
            "qena",
            "sohag",
            "hurghada",
            "sharm el sheikh",
            "dahab",
            "nile",
            "delta",
            "sinai",
            "maadi",
            "zamalek",
            "heliopolis",
            "dokki",
            "mohandessin",
            "nasr city",
            "6th october",
            "sheikh zayed"
        )
        
        val matchedEgyptKeyword = egyptKeywords.find { keyword ->
            normalizedAddress.contains(keyword, ignoreCase = true)
        }
        val isInEgypt = matchedEgyptKeyword != null
        
        val fee = when {
            isInEgypt -> {
                Log.d(TAG, "Location detected: Egypt (other areas) (matched: '$matchedEgyptKeyword') - Fee: $30")
                30.0
            }
            else -> {
                Log.d(TAG, "Location detected: Outside Egypt - Fee: $50")
                50.0
            }
        }
        
        Log.d(TAG, "Final fee determined: $fee")
        return fee
    }
    
    /**
     * Estimates distance between two addresses using intelligent address parsing
     * Always returns a meaningful distance value based on address analysis
     * 
     * @param sellerAddress Seller's address
     * @param buyerAddress Buyer's delivery address
     * @return Estimated distance in kilometers (always > 0)
     */
    private fun estimateDistanceFromAddresses(sellerAddress: String, buyerAddress: String): Double {
        if (sellerAddress.isBlank() || buyerAddress.isBlank()) {
            // If addresses are blank, return a default medium distance
            return 8.0 + Random.nextDouble(0.0, 5.0) // 8-13 km
        }
        
        // Normalize addresses for comparison
        val sellerNormalized = normalizeAddress(sellerAddress)
        val buyerNormalized = normalizeAddress(buyerAddress)
        
        // Extract location components
        val sellerCity = extractCity(sellerNormalized)
        val buyerCity = extractCity(buyerNormalized)
        val sellerStreet = extractStreet(sellerNormalized)
        val buyerStreet = extractStreet(buyerNormalized)
        val sellerZip = extractZipCode(sellerNormalized)
        val buyerZip = extractZipCode(buyerNormalized)
        
        // Calculate similarity score (0.0 = same location, 1.0 = very different)
        var similarityScore = 0.0
        
        // City comparison (40% weight)
        if (sellerCity.isNotEmpty() && buyerCity.isNotEmpty()) {
            if (sellerCity.equals(buyerCity, ignoreCase = true)) {
                similarityScore += 0.0  // Same city
            } else {
                similarityScore += 0.4  // Different city
            }
        } else {
            similarityScore += 0.3  // Unknown city
        }
        
        // Zip code comparison (30% weight)
        if (sellerZip.isNotEmpty() && buyerZip.isNotEmpty()) {
            if (sellerZip == buyerZip) {
                similarityScore += 0.0  // Same zip = very close
            } else {
                // Calculate numeric difference in zip codes
                val sellerZipInt = sellerZip.toIntOrNull() ?: 0
                val buyerZipInt = buyerZip.toIntOrNull() ?: 0
                val zipDiff = Math.abs(sellerZipInt - buyerZipInt)
                similarityScore += (zipDiff / 10000.0).coerceIn(0.0, 0.3)
            }
        } else {
            similarityScore += 0.2
        }
        
        // Street comparison (20% weight)
        val streetSimilarity = calculateStringSimilarity(sellerStreet, buyerStreet)
        similarityScore += (1 - streetSimilarity) * 0.2
        
        // Address length/format analysis (10% weight)
        val addressLengthDiff = Math.abs(sellerNormalized.length - buyerNormalized.length)
        similarityScore += (addressLengthDiff / 100.0).coerceIn(0.0, 0.1)
        
        // Convert similarity score to distance estimate
        // similarityScore 0.0-0.2: Same area (1-5 km)
        // similarityScore 0.2-0.5: Nearby area (5-10 km)
        // similarityScore 0.5-0.7: Different area (10-20 km)
        // similarityScore 0.7-1.0: Far area (20-35 km)
        val baseDistance = when {
            similarityScore <= 0.2 -> 3.0 + Random.nextDouble(0.0, 2.0)  // 3-5 km
            similarityScore <= 0.5 -> 7.0 + Random.nextDouble(0.0, 3.0)  // 7-10 km
            similarityScore <= 0.7 -> 15.0 + Random.nextDouble(0.0, 5.0) // 15-20 km
            else -> 25.0 + Random.nextDouble(0.0, 10.0)                  // 25-35 km
        }
        
        // Add some random variation (±20%)
        val variation = Random.nextDouble(0.9, 1.1)
        return (baseDistance * variation).coerceAtLeast(1.0) // Minimum 1 km
    }
    
    /**
     * Calculates zone factor based on address similarity
     * Returns a multiplier (0.7-1.3x) for fee adjustment
     */
    private fun calculateZoneFactor(sellerAddress: String, buyerAddress: String): Double {
        if (sellerAddress.isBlank() || buyerAddress.isBlank()) {
            return 1.0 + Random.nextDouble(-0.1, 0.1) // Small random variation
        }
        
        val sellerCity = extractCity(normalizeAddress(sellerAddress))
        val buyerCity = extractCity(normalizeAddress(buyerAddress))
        
        return when {
            sellerCity.isNotEmpty() && buyerCity.isNotEmpty() && 
            sellerCity.equals(buyerCity, ignoreCase = true) -> {
                0.75 + Random.nextDouble(0.0, 0.1) // Same city: 25% discount
            }
            sellerCity.isNotEmpty() && buyerCity.isNotEmpty() -> {
                1.15 + Random.nextDouble(0.0, 0.15) // Different city: 15-30% premium
            }
            else -> {
                1.0 + Random.nextDouble(-0.1, 0.1) // Unknown: no change
            }
        }
    }
    
    /**
     * Normalizes address string for better comparison
     */
    private fun normalizeAddress(address: String): String {
        return address.trim()
            .replace(Regex("\\s+"), " ") // Multiple spaces to single space
            .replace(Regex("[^a-zA-Z0-9\\s,.-]"), "") // Remove special chars except common ones
            .lowercase()
    }
    
    /**
     * Extracts city/area name from address string
     */
    private fun extractCity(address: String): String {
        val parts = address.split(",").map { it.trim() }
        
        // Common address formats:
        // "123 Main St, City, State ZIP"
        // "Street, City"
        // "City, Country"
        return when {
            parts.size >= 2 -> parts[parts.size - 2].takeWhile { !it.isDigit() }.trim()
            parts.isNotEmpty() -> parts[0].takeWhile { !it.isDigit() }.trim()
            else -> ""
        }.replace(Regex("\\d+"), "").trim() // Remove any remaining digits
    }
    
    /**
     * Extracts street/road name from address
     */
    private fun extractStreet(address: String): String {
        val parts = address.split(",").map { it.trim() }
        return if (parts.isNotEmpty()) {
            parts[0].lowercase()
        } else {
            address.lowercase()
        }
    }
    
    /**
     * Extracts ZIP/postal code from address
     */
    private fun extractZipCode(address: String): String {
        // Look for 5-digit or 5+4 digit ZIP codes
        val zipRegex = Regex("\\b\\d{5}(?:-\\d{4})?\\b")
        return zipRegex.find(address)?.value ?: ""
    }
    
    /**
     * Calculates string similarity using simple character matching
     * Returns value between 0.0 (completely different) and 1.0 (identical)
     */
    private fun calculateStringSimilarity(str1: String, str2: String): Double {
        if (str1 == str2) return 1.0
        if (str1.isEmpty() || str2.isEmpty()) return 0.0
        
        val longer = if (str1.length > str2.length) str1 else str2
        val shorter = if (str1.length > str2.length) str2 else str1
        
        if (longer.isEmpty()) return 1.0
        
        // Simple similarity: count common characters
        var matches = 0
        var maxLen = Math.max(str1.length, str2.length)
        
        for (i in shorter.indices) {
            if (i < longer.length && shorter[i] == longer[i]) {
                matches++
            }
        }
        
        // Also check for common words
        val words1 = str1.split(Regex("\\s+")).toSet()
        val words2 = str2.split(Regex("\\s+")).toSet()
        val commonWords = words1.intersect(words2).size
        val totalWords = words1.union(words2).size
        
        val charSimilarity = matches.toDouble() / maxLen
        val wordSimilarity = if (totalWords > 0) commonWords.toDouble() / totalWords else 0.0
        
        return (charSimilarity * 0.5 + wordSimilarity * 0.5).coerceIn(0.0, 1.0)
    }
    
    /**
     * Calculate distance using Haversine formula (if coordinates are available)
     * 
     * @param lat1 Latitude of first point
     * @param lon1 Longitude of first point
     * @param lat2 Latitude of second point
     * @param lon2 Longitude of second point
     * @return Distance in kilometers
     */
    fun calculateDistanceFromCoordinates(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val earthRadiusKm = 6371.0 // Earth radius in kilometers
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        
        return earthRadiusKm * c
    }
}

