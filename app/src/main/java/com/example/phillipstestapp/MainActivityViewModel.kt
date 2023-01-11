package com.example.phillipstestapp

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketException
import java.util.*
import kotlin.experimental.xor

class MainActivityViewModel : ViewModel() {

    private lateinit var socket: Socket

    sealed interface SocketConnectionState {
        object Initial : SocketConnectionState
        object Connected : SocketConnectionState
        object Disconnected : SocketConnectionState
        class Error(val message: String) : SocketConnectionState
    }


    private val _connectionStateFlow =
        MutableStateFlow<SocketConnectionState>(SocketConnectionState.Initial)
    val connectionStateFlow = _connectionStateFlow.asStateFlow()

    private fun setConnectionFlowState(state: SocketConnectionState) {
        viewModelScope.launch {
            _connectionStateFlow.emit(state)
        }
    }


    suspend fun connect(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                socket = getSocket()
                Log.e(TAG, "connect: socket connected at ${socket.localSocketAddress}")
                socket.keepAlive = true
                if (socket.isConnected) {
                    Log.e(TAG, "connect: socket connected")
                    setConnectionFlowState(SocketConnectionState.Connected)
                } else {
                    Log.e(TAG, "connect: socket disconnected")
                    setConnectionFlowState(SocketConnectionState.Disconnected)
                }

                return@withContext socket.isConnected
            } catch (e: java.lang.Exception) {
                setConnectionFlowState(SocketConnectionState.Error(e.message ?: ""))
                tryConnect()
            }
            return@withContext false

        }
    }


    private suspend fun checkIfSocketIsConnectedAndInitialized() =
        withContext(Dispatchers.IO) { this@MainActivityViewModel::socket.isInitialized && socket.isConnected }

    fun getDataWithChecksum(byteArray: ByteArray): ByteArray {
        return writeCheckSum(input = byteArray)
    }


    private fun writeCheckSum(input: ByteArray): ByteArray {
        var checksum = 0.toByte()
        val byteList = arrayListOf<Byte>()
        for (b in input) {
            checksum = checksum.xor(b)
            byteList.add(b)
        }
        byteList.add(checksum)
        return byteList.toByteArray()
    }

    suspend fun write(bytesWithCheckSum: ByteArray) {
        withContext(Dispatchers.IO) {
            try {
                if (checkIfSocketIsConnectedAndInitialized()) {
                    socket.getOutputStream().write(bytesWithCheckSum)
                } else {
                    Log.e(TAG, "write: socket not connected")
                }
            } catch (e: SocketException) {
                Log.e(TAG, "write: ", e)
                setConnectionFlowState(SocketConnectionState.Error(e.message ?: ""))
                tryConnect()
            }
        }
    }

    private fun getSocket() = Socket(getIPAddress(true), 5555)

    private fun tryConnect() {
        if (this::socket.isInitialized) {
            socket.close()
            socket = getSocket()
            viewModelScope.launch {
                connect()
            }
        }
    }

    private fun getIPAddress(useIPv4: Boolean): String? {
        try {
            val interfaces: List<NetworkInterface> =
                Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs: List<InetAddress> = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress
                        //boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
                        val isIPv4 = sAddr.indexOf(':') < 0
                        if (useIPv4) {
                            if (isIPv4) return sAddr
                        } else {
                            if (!isIPv4) {
                                val delim = sAddr.indexOf('%') // drop ip6 zone suffix
                                return if (delim < 0) sAddr.uppercase(Locale.getDefault()) else sAddr.substring(
                                    0,
                                    delim
                                ).uppercase(
                                    Locale.getDefault()
                                )
                            }
                        }
                    }
                }
            }
        } catch (ignored: Exception) {
        } // for now eat exceptions
        return ""
    }


//    suspend fun read() {
//        withContext(Dispatchers.IO) {
//            if (socket.isConnected && socket.isClosed.not()) socket.getInputStream()?.use {
//                it.bufferedReader().readLine()
//            }
//        }
//    }

    override fun onCleared() {
        viewModelScope.launch(Dispatchers.IO) { socket.close() }
        super.onCleared()
    }


    companion object {
        private const val TAG = "MainActivityViewModel"
    }

}
