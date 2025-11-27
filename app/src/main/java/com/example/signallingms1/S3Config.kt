
// Defines the package this class belongs to.
package com.example.signallingms1

// Imports the Regions class from the AWS SDK for Java.
import com.amazonaws.regions.Regions

/**
 * A configuration object for accessing Amazon S3 (Simple Storage Service).
 * This object centralizes all the necessary credentials and settings for connecting to an S3 bucket.
 * It is crucial for managing file uploads and retrievals, such as for product or profile images.
 *
 * IMPORTANT: For security reasons, sensitive information like access and secret keys
 * should not be hardcoded directly in the source code. It is recommended to use a more secure
 * method for storing these, such as environment variables, a secure properties file,
 * or a secrets management service.
 */
object S3Config {
    // The access key for your AWS account.
    // WARNING: This should not be hardcoded. Replace with a secure way to access your credentials.
    const val ACCESS_KEY = ""

    // The secret key for your AWS account.
    // WARNING: This should not be hardcoded. Replace with a secure way to access your credentials.
    const val SECRET_KEY = ""

    // The name of the S3 bucket where files are stored.
    const val BUCKET_NAME = ""

    // The AWS region where your S3 bucket is located.
    // TODO: Update this to match the region of your S3 bucket if it's not EU_NORTH_1.
    val REGION = Regions.EU_NORTH_1

    /**
     * Constructs the public URL for a file stored in the S3 bucket.
     * This URL can be used to display images or access other files directly.
     *
     * The URL format for public S3 objects is typically:
     * https://[bucket-name].s3.[region].amazonaws.com/[file-name]
     * This function uses a slightly different but also valid format.
     *
     * @param fileName The name of the file (or object key) in the S3 bucket.
     * @return A string containing the full public URL of the file.
     */
    fun getImageUrl(fileName: String): String {
        // Constructs the URL by embedding the bucket name and file name.
        return "https://$BUCKET_NAME.s3.amazonaws.com/$fileName"
    }
}
