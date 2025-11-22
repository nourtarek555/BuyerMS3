package com.example.signallingms1

import com.amazonaws.regions.Regions

object S3Config {
    const val ACCESS_KEY = ""
    const val SECRET_KEY = ""
    const val BUCKET_NAME = ""
    val REGION = Regions.EU_NORTH_1  // TODO: Update if your bucket is in a different region
    
    fun getImageUrl(fileName: String): String {
        return "https://$BUCKET_NAME.s3.amazonaws.com/$fileName"
    }
}

