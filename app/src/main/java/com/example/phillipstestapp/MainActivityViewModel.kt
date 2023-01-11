package com.example.phillipstestapp

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.Socket
import java.net.SocketException
import kotlin.experimental.xor

class MainActivityViewModel : ViewModel() {

    private lateinit var socket: Socket
    private var ip: String? = null

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
                    socket = Socket(InetAddress.getLoopbackAddress(), 5555)
                    Log.e(TAG, "connect: socket connected at ${socket.localSocketAddress}", )
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


    private suspend fun checkIfSocketIsConnected() =
        withContext(Dispatchers.IO) { socket.isConnected }

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
                if (checkIfSocketIsConnected()) {
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

    private fun tryConnect() {
        if (this::socket.isInitialized) {
            socket.close()
            socket = Socket(InetAddress.getLocalHost(), 5555)
            viewModelScope.launch {
                connect()
            }
        }
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
