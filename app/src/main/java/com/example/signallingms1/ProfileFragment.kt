package com.example.signallingms1

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
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

class ProfileFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    private lateinit var nameEt: EditText
    private lateinit var phoneEt: EditText
    private lateinit var emailEt: EditText
    private lateinit var addressEt: EditText
    private lateinit var imageView: ImageView
    private lateinit var uploadBtn: Button
    private lateinit var saveBtn: Button
    private lateinit var voipCallBtn: Button
    private lateinit var logoutBtn: Button
    private lateinit var progressBar: ProgressBar

    private var imageUri: Uri? = null

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            imageUri = result.data?.data
            Glide.with(this)
                .load(imageUri)
                .placeholder(R.drawable.ic_person)
                .into(imageView)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid
        database = FirebaseDatabase.getInstance().getReference("Buyers")

        nameEt = view.findViewById(R.id.etName)
        phoneEt = view.findViewById(R.id.etPhone)
        emailEt = view.findViewById(R.id.etEmail)
        addressEt = view.findViewById(R.id.etAddress)
        imageView = view.findViewById(R.id.profileImage)
        uploadBtn = view.findViewById(R.id.btnUpload)
        saveBtn = view.findViewById(R.id.btnSave)
        voipCallBtn = view.findViewById(R.id.btnVoIPCall)
        logoutBtn = view.findViewById(R.id.btnLogout)
        progressBar = view.findViewById(R.id.profileProgress)

        if (uid == null) {
            Toast.makeText(context, "User not found", Toast.LENGTH_SHORT).show()
            requireActivity().finish()
            return
        }

        loadUserData(uid)

        uploadBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            imagePickerLauncher.launch(intent)
        }

        saveBtn.setOnClickListener { saveProfile(uid) }

        voipCallBtn.setOnClickListener {
            val intent = Intent(requireContext(), VoIPCallActivity::class.java)
            startActivity(intent)
        }

        logoutBtn.setOnClickListener {
            auth.signOut()
            requireActivity().finish()
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

                    Glide.with(this@ProfileFragment)
                        .load(user.photoUrl ?: R.drawable.ic_person)
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .into(imageView)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Failed to load profile", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun saveProfile(uid: String) {
        val newName = nameEt.text.toString().trim()
        val newPhone = phoneEt.text.toString().trim()
        val newAddress = addressEt.text.toString().trim()

        if (newName.isEmpty()) {
            Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
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
            .context(requireContext().applicationContext)
            .s3Client(s3Client)
            .build()

        val fileName = "buyer_profile_images/$uid-${System.currentTimeMillis()}.jpg"
        val file = FileUtil.from(requireContext(), uri)

        val uploadObserver = transferUtility.upload(
            S3Config.BUCKET_NAME,
            fileName,
            file,
            CannedAccessControlList.PublicRead
        )

        uploadObserver.setTransferListener(object : TransferListener {
            override fun onStateChanged(id: Int, state: TransferState?) {
                if (state == TransferState.COMPLETED) {
                    val imageUrl = S3Config.getImageUrl(fileName)
                    callback(imageUrl)
                } else if (state == TransferState.FAILED) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(context, "Upload failed!", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {}
            override fun onError(id: Int, ex: Exception?) {
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Error: ${ex?.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateDatabase(uid: String, updates: Map<String, Any>) {
        database.child(uid).updateChildren(updates).addOnCompleteListener {
            progressBar.visibility = View.GONE
            if (it.isSuccessful) {
                Toast.makeText(context, "Profile updated!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Update failed!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

