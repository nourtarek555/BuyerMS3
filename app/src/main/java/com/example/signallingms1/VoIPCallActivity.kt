package com.example.signallingms1

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import kotlinx.coroutines.*
import android.os.Handler
import android.os.Looper

class VoIPCallActivity : AppCompatActivity() {

    private lateinit var etIpAddress: EditText
    private lateinit var etPort: EditText
    private lateinit var btnConnect: Button
    private lateinit var btnDisconnect: Button
    private lateinit var btnMute: Button
    private lateinit var btnEndCall: Button
    private lateinit var tvConnectionStatus: TextView
    private lateinit var llCallControls: android.view.View
    private lateinit var progressBar: ProgressBar

    private var socket: Socket? = null
    private var outputWriter: PrintWriter? = null
    private var inputReader: BufferedReader? = null
    private var isConnected = false
    private var connectionJob: Job? = null
    private val handler = Handler(Looper.getMainLooper())
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Permission request code
    private val PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voip_call)

        initializeViews()
        setupClickListeners()
        
        // Request necessary permissions
        requestPermissions()
    }

    private fun initializeViews() {
        etIpAddress = findViewById(R.id.etIpAddress)
        etPort = findViewById(R.id.etPort)
        btnConnect = findViewById(R.id.btnConnect)
        btnDisconnect = findViewById(R.id.btnDisconnect)
        btnMute = findViewById(R.id.btnMute)
        btnEndCall = findViewById(R.id.btnEndCall)
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)
        llCallControls = findViewById(R.id.llCallControls)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupClickListeners() {
        btnConnect.setOnClickListener {
            connectToServer()
        }

        btnDisconnect.setOnClickListener {
            disconnectFromServer()
        }

        btnMute.setOnClickListener {
            toggleMute()
        }

        btnEndCall.setOnClickListener {
            endCall()
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        
        // Request RECORD_AUDIO permission for VoIP calls
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        // Request MODIFY_AUDIO_SETTINGS for audio routing
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.MODIFY_AUDIO_SETTINGS)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.MODIFY_AUDIO_SETTINGS)
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (!allGranted) {
                Toast.makeText(
                    this,
                    "Audio permissions are required for VoIP calls",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun connectToServer() {
        val ipAddress = etIpAddress.text.toString().trim()
        val portText = etPort.text.toString().trim()

        // Validate inputs
        if (ipAddress.isEmpty()) {
            etIpAddress.error = "Please enter IP address"
            etIpAddress.requestFocus()
            return
        }

        if (portText.isEmpty()) {
            etPort.error = "Please enter port number"
            etPort.requestFocus()
            return
        }

        val port = try {
            portText.toInt()
        } catch (e: NumberFormatException) {
            etPort.error = "Invalid port number"
            etPort.requestFocus()
            return
        }

        if (port < 1 || port > 65535) {
            etPort.error = "Port must be between 1 and 65535"
            etPort.requestFocus()
            return
        }

        // Disable inputs and show progress
        setConnectionInProgress(true)
        updateConnectionStatus("Connecting to $ipAddress:$port...")

        // Attempt connection in background
        connectionJob = coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    socket = Socket(ipAddress, port)
                    outputWriter = PrintWriter(socket?.getOutputStream(), true)
                    inputReader = BufferedReader(InputStreamReader(socket?.getInputStream()))

                    // Mark as connected
                    isConnected = true

                    // Update UI on main thread
                    handler.post {
                        setConnectionInProgress(false)
                        updateConnectionStatus("Connected to $ipAddress:$port")
                        showCallControls(true)
                        Toast.makeText(this@VoIPCallActivity, "Connected successfully!", Toast.LENGTH_SHORT).show()
                    }

                    // Start listening for incoming messages
                    listenForMessages()
                }
            } catch (e: IOException) {
                e.printStackTrace()
                handler.post {
                    setConnectionInProgress(false)
                    updateConnectionStatus("Connection failed: ${e.message}")
                    showCallControls(false)
                    Toast.makeText(this@VoIPCallActivity, "Failed to connect: ${e.message}", Toast.LENGTH_LONG).show()
                    isConnected = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                handler.post {
                    setConnectionInProgress(false)
                    updateConnectionStatus("Connection error: ${e.message}")
                    showCallControls(false)
                    Toast.makeText(this@VoIPCallActivity, "Connection error: ${e.message}", Toast.LENGTH_LONG).show()
                    isConnected = false
                }
            }
        }
    }

    private fun listenForMessages() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                while (isConnected && socket?.isClosed == false) {
                    val message = inputReader?.readLine()
                    if (message != null) {
                        handler.post {
                            // Handle incoming message
                            handleIncomingMessage(message)
                        }
                    } else {
                        // Connection closed
                        break
                    }
                }
            } catch (e: IOException) {
                if (isConnected) {
                    handler.post {
                        updateConnectionStatus("Connection lost")
                        disconnectFromServer()
                        Toast.makeText(this@VoIPCallActivity, "Connection lost", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun handleIncomingMessage(message: String) {
        // Handle incoming signaling messages
        // This can be extended to handle call control messages
        if (message.startsWith("CALL:")) {
            // Handle incoming call
        } else if (message.startsWith("ACCEPT:")) {
            // Handle call accepted
        } else if (message.startsWith("REJECT:")) {
            // Handle call rejected
        } else if (message.startsWith("END:")) {
            // Handle call ended
            endCall()
        }
    }

    private fun disconnectFromServer() {
        isConnected = false
        connectionJob?.cancel()

        coroutineScope.launch(Dispatchers.IO) {
            try {
                outputWriter?.close()
                inputReader?.close()
                socket?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        socket = null
        outputWriter = null
        inputReader = null

        handler.post {
            updateConnectionStatus("Disconnected")
            showCallControls(false)
            Toast.makeText(this, "Disconnected from server", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleMute() {
        // Implement mute functionality
        // This would control audio input/output
        val isMuted = btnMute.isSelected
        btnMute.isSelected = !isMuted
        btnMute.text = if (isMuted) "MUTE" else "UNMUTE"
        Toast.makeText(this, if (isMuted) "Unmuted" else "Muted", Toast.LENGTH_SHORT).show()
    }

    private fun endCall() {
        // Send end call message
        coroutineScope.launch(Dispatchers.IO) {
            try {
                outputWriter?.println("END_CALL")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        disconnectFromServer()
    }

    private fun setConnectionInProgress(inProgress: Boolean) {
        progressBar.visibility = if (inProgress) android.view.View.VISIBLE else android.view.View.GONE
        btnConnect.isEnabled = !inProgress
        etIpAddress.isEnabled = !inProgress
        etPort.isEnabled = !inProgress
    }

    private fun showCallControls(show: Boolean) {
        btnConnect.visibility = if (show) android.view.View.GONE else android.view.View.VISIBLE
        btnDisconnect.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        llCallControls.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun updateConnectionStatus(status: String) {
        tvConnectionStatus.text = "Status: $status"
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectFromServer()
        coroutineScope.cancel()
    }

    override fun onBackPressed() {
        if (isConnected) {
            Toast.makeText(this, "Please disconnect before exiting", Toast.LENGTH_SHORT).show()
        } else {
            super.onBackPressed()
        }
    }
}
