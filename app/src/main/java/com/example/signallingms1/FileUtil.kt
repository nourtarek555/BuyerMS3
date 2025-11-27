
// Defines the package this class belongs to.
package com.example.signallingms1

// Imports for Android framework classes.
import android.content.Context
import android.net.Uri
// Imports for Java file handling classes.
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * A utility object for handling file-related operations.
 * This object provides helper methods to work with files, such as converting a Uri to a File object.
 */
object FileUtil {

    /**
     * Creates a temporary File object from a given content Uri.
     * This is useful when you need to work with a file path but only have a Uri,
     * for example, when picking an image from the gallery. The file is created in the app's cache directory.
     *
     * @param context The application context, used to access the content resolver and cache directory.
     * @param uri The content Uri of the file to be converted.
     * @return A File object representing the content of the Uri.
     */
    fun from(context: Context, uri: Uri): File {
        // Get an InputStream from the content Uri using the ContentResolver.
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)

        // Create a new file in the app's cache directory.
        // A unique filename is generated using the current timestamp to avoid collisions.
        val file = File(context.cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")

        // Create an OutputStream to write data into the newly created file.
        val outputStream = FileOutputStream(file)

        // Copy the data from the InputStream to the OutputStream.
        // The 'copyTo' extension function handles the buffering and writing process.
        inputStream?.copyTo(outputStream)

        // Close the OutputStream to ensure all data is written and resources are released.
        outputStream.close()
        // Close the InputStream to release its resources.
        inputStream?.close()

        // Return the newly created File object.
        return file
    }
}
