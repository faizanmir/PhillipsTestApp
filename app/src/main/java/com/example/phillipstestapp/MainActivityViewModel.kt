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
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.experimental.xor

class MainActivityViewModel : ViewModel() {

    private var socket: Socket = Socket()

    sealed interface SocketConnectionState {
        object Initial : SocketConnectionState
        object Connected :SocketConnectionState
        object Disconnected  :SocketConnectionState
        class Error (val message:String) :SocketConnectionState
    }


    init {
        socket.keepAlive =  true
    }

    private val _connectionStateFlow = MutableStateFlow<SocketConnectionState>(SocketConnectionState.Initial)
    val connectionStateFlow =  _connectionStateFlow.asStateFlow()

    private fun setConnectionFlowState (state: SocketConnectionState) {
        viewModelScope.launch {
            _connectionStateFlow.emit(state)
        }
    }


    suspend fun connect(ipAddress: String): Boolean {
        return withContext(Dispatchers.IO) {
            assert(ipAddress.isNotEmpty())
            try {
                socket.connect(InetSocketAddress(ipAddress, 5555), 2)
            } catch (e : java.lang.Exception) {
                setConnectionFlowState(SocketConnectionState.Error(e.message ?: ""))
            }
            socket.onConnectionChangeListener{ connected ->
                if (connected) {
                    Log.e(TAG, "connect: socket connected", )
                    setConnectionFlowState(SocketConnectionState.Connected)
                } else {
                    Log.e(TAG, "connect: socket disconnected", )
                    setConnectionFlowState(SocketConnectionState.Disconnected)
                }
            }

            socket.isConnected
        }
    }




    private suspend fun checkIfSocketIsConnected() = withContext(Dispatchers.IO){ socket?.isConnected}

    fun getData(byteArray: ByteArray): ByteArray {
        return writeCheckSum(input = byteArray)
    }


    private fun writeCheckSum(input: ByteArray): ByteArray {
        var checksum = 0.toByte()
        val byteList = arrayListOf<Byte>()
        for (b in input) {
            checksum = checksum.xor(b)
            byteList.add(b)
        }
        return byteList.toByteArray()
    }

    suspend fun write(bytesWithCheckSum: ByteArray) {
        withContext(Dispatchers.IO) {
            if (checkIfSocketIsConnected() == true) {
                socket.getOutputStream()?.write(bytesWithCheckSum)
            }
        }
    }


    suspend fun read() {
        withContext(Dispatchers.IO) {
            socket.getInputStream()?.use {
                it.bufferedReader().readLine()
            }
        }
    }

    override fun onCleared() {
        viewModelScope.launch(Dispatchers.IO) { socket.close() }
        super.onCleared()
    }


    companion object {
        private const val TAG = "MainActivityViewModel"
    }

}

private suspend fun Socket.onConnectionChangeListener(onConnectionStatusChanged:(connected:Boolean)->Unit) {
    var state: Boolean
    while (true) {
        state  = isConnected
        delay(1000)
        if (isConnected != state) {
            onConnectionStatusChanged(isConnected)
        }
    }
}
