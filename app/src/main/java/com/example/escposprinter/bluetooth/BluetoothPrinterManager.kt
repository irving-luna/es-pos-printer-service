package com.example.escposprinter.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

class BluetoothPrinterManager(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName

    private val PRINTER_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun isBluetoothSupported(): Boolean {
        return bluetoothAdapter != null
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled ?: false
    }

    fun hasBluetoothPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        if (!hasBluetoothPermission() || bluetoothAdapter == null) {
            return emptyList()
        }
        return try {
            bluetoothAdapter.bondedDevices.toList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        if (!hasBluetoothPermission()) {
            _connectionStatus.value = ConnectionStatus.ERROR
            return@withContext false
        }

        _connectionStatus.value = ConnectionStatus.CONNECTING
        disconnect()

        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(PRINTER_UUID)
            bluetoothAdapter?.cancelDiscovery()
            
            bluetoothSocket?.connect()
            outputStream = bluetoothSocket?.outputStream
            
            _connectedDeviceName.value = device.name ?: device.address
            _connectionStatus.value = ConnectionStatus.CONNECTED
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            closeSocket()
            _connectionStatus.value = ConnectionStatus.ERROR
            return@withContext false
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        closeSocket()
        _connectedDeviceName.value = null
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }

    private fun closeSocket() {
        try {
            outputStream?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        outputStream = null

        try {
            bluetoothSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        bluetoothSocket = null
    }

    suspend fun printBytes(bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val stream = outputStream
        if (_connectionStatus.value != ConnectionStatus.CONNECTED || stream == null) {
            return@withContext false
        }
        try {
            stream.write(bytes)
            stream.flush()
            return@withContext true
        } catch (e: IOException) {
            e.printStackTrace()
            _connectionStatus.value = ConnectionStatus.ERROR
            closeSocket()
            return@withContext false
        }
    }
}
