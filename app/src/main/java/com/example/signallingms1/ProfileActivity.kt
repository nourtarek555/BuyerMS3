package com.example.signallingms1

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.CannedAccessControlList
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    private lateinit var nameEt: EditText
    private lateinit var phoneEt: EditText
    private lateinit var emailEt: EditText
    private lateinit var addressEt: EditText
    private lateinit var imageView: ImageView
    private lateinit var uploadBtn: Button
    private lateinit var saveBtn: Button
    private lateinit var logoutBtn: Button
    private lateinit var progressBar: ProgressBar

    private var imageUri: Uri? = null
    private val PICK_IMAGE_REQUEST = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid
        database = FirebaseDatabase.getInstance().getReference("Buyers")

        nameEt = findViewById(R.id.etName)
        phoneEt = findViewById(R.id.etPhone)
        emailEt = findViewById(R.id.etEmail)
        addressEt = findViewById(R.id.etAddress)
        imageView = findViewById(R.id.profileImage)
        uploadBtn = findViewById(R.id.btnUpload)
        saveBtn = findViewById(R.id.btnSave)
        logoutBtn = findViewById(R.id.btnLogout)
        progressBar = findViewById(R.id.profileProgress)

        if (uid == null) {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        loadUserData(uid)

        uploadBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }

        saveBtn.setOnClickListener { saveProfile(uid) }

        logoutBtn.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun loadUserData(uid: String) {
        progressBar.visibility = View.VISIBLE
        database.child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                progressBar.visibility = View.GONE
                val user = snapshot.getValue(UserProfile::class.java)
                if (user != null) {
                    nameEt.setText(user.name)
                    phoneEt.setText(user.phone)
                    emailEt.setText(user.email)
                    emailEt.isEnabled = false
                    addressEt.setText(user.address)

                    Glide.with(this@ProfileActivity)
                        .load(user.photoUrl ?: R.drawable.ic_person)
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .into(imageView)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@ProfileActivity, "Failed to load profile", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun saveProfile(uid: String) {
        val newName = nameEt.text.toString().trim()
        val newPhone = phoneEt.text.toString().trim()
        val newAddress = addressEt.text.toString().trim()

        if (newName.isEmpty()) {
            Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        val updates = mutableMapOf<String, Any>(
            "name" to newName,
            "phone" to newPhone,
            "address" to newAddress
        )

        if (imageUri != null) {
            Glide.with(this)
                .load(imageUri)
                .placeholder(R.drawable.ic_person)
                .into(imageView)

            uploadImageToS3(uid, imageUri!!) { imageUrl ->
                updates["photoUrl"] = imageUrl
                updateDatabase(uid, updates)
            }
        } else {
            updateDatabase(uid, updates)
        }
    }

    private fun uploadImageToS3(uid: String, uri: Uri, callback: (String) -> Unit) {
        val credentials = BasicAWSCredentials(S3Config.ACCESS_KEY, S3Config.SECRET_KEY)
        val s3Client = AmazonS3Client(credentials, com.amazonaws.regions.Region.getRegion(S3Config.REGION))
        val transferUtility = TransferUtility.builder()
            .context(applicationContext)
            .s3Client(s3Client)
            .build()

        val fileName = "buyer_profile_images/$uid-${System.currentTimeMillis()}.jpg"
        val file = FileUtil.from(this, uri)

        val uploadObserver = transferUtility.upload(
            S3Config.BUCKET_NAME,
            fileName,
            file,
            CannedAccessControlList.PublicRead // <-- Make uploaded file public
        )

        uploadObserver.setTransferListener(object : TransferListener {
            override fun onStateChanged(id: Int, state: TransferState?) {
                if (state == TransferState.COMPLETED) {
                    val imageUrl = S3Config.getImageUrl(fileName)
                    callback(imageUrl)
                } else if (state == TransferState.FAILED) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@ProfileActivity, "Upload failed!", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {}
            override fun onError(id: Int, ex: Exception?) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@ProfileActivity, "Error: ${ex?.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateDatabase(uid: String, updates: Map<String, Any>) {
        database.child(uid).updateChildren(updates).addOnCompleteListener {
            progressBar.visibility = View.GONE
            if (it.isSuccessful) {
                Toast.makeText(this, "Profile updated!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Update failed!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            imageUri = data.data
            Glide.with(this)
                .load(imageUri)
                .placeholder(R.drawable.ic_person)
                .into(imageView)
        }
    }
}

