package com.example.phillipstestapp

import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.phillipstestapp.MainActivityViewModel.SocketConnectionState.Error
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {

    lateinit var ipAddressEditText: EditText
    lateinit var connectButton: Button
    lateinit var send: Button
    lateinit var status: TextView
    private lateinit var job: Job

    private val viewModel by viewModels<MainActivityViewModel>()

    private fun initialize() {
        connectButton = findViewById(R.id.connect_button)
        send = findViewById(R.id.send_button)
        ipAddressEditText = findViewById(R.id.ipAddress)
        status = findViewById(R.id.status)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initialize()

        job = lifecycleScope.launchWhenStarted {
            viewModel.connectionStateFlow.collectLatest {
                when (it) {
                    MainActivityViewModel.SocketConnectionState.Connected -> {
                        status.text = "Connected"
                    }
                    MainActivityViewModel.SocketConnectionState.Disconnected -> {
                        status.text = "Disconnected"
                    }
                    MainActivityViewModel.SocketConnectionState.Initial -> {
                        status.text = "Initial"
                    }
                    is Error -> {
                        status.text = it.message
                    }
                }
            }
        }

        connectButton.setOnClickListener {
            lifecycleScope.launch {
                try {
                    viewModel.connect()
                } catch (e: java.lang.AssertionError) {
                    showMessage("IP Address is empty")
                }
            }
        }

        send.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val bytesWithCheckSum =
                    viewModel.getDataWithChecksum(byteArrayOf(0x07, 0x01, 0x00, 0x44, 0xA, 0xA))
                Log.e(TAG, "onClick:send: $bytesWithCheckSum")
                viewModel.write(bytesWithCheckSum)
               // viewModel.read()
            }
        }
    }


    private fun showMessage(message: String) {
        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }


    companion object {
        private const val TAG = "MainActivity"

    }
}