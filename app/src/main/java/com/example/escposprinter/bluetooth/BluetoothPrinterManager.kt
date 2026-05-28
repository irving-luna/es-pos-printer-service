package com.example.escposprinter.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

class BluetoothPrinterManager private constructor(context: Context) {

    private val context = context.applicationContext
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    companion object {
        @Volatile
        private var INSTANCE: BluetoothPrinterManager? = null

        fun getInstance(context: Context): BluetoothPrinterManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BluetoothPrinterManager(context).also { INSTANCE = it }
            }
        }
    }

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val sharedPreferences = context.getSharedPreferences("printer_prefs", Context.MODE_PRIVATE)
    private val connectionMutex = Mutex()
    private val printMutex = Mutex()

    private var isReceiverRegistered = false

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        val currentList = _discoveredDevices.value
                        if (!currentList.any { d -> d.address == it.address }) {
                            _discoveredDevices.value = currentList + it
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    _isScanning.value = true
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isScanning.value = false
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                    if (state == BluetoothDevice.BOND_BONDED) {
                        // Refresh lists or notify
                        _discoveredDevices.value = _discoveredDevices.value.filter { it.address != device?.address }
                    }
                }
            }
        }
    }

    private val PRINTER_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        if (!hasBluetoothPermission() || bluetoothAdapter == null) return
        
        if (!isReceiverRegistered) {
            // Register for broadcasts when a device is discovered.
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            }
            context.registerReceiver(bluetoothReceiver, filter)
            isReceiverRegistered = true
        }
        
        _discoveredDevices.value = emptyList()
        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }
        bluetoothAdapter.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter.cancelDiscovery()
        }
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(bluetoothReceiver)
            } catch (e: IllegalArgumentException) {
                // Receiver not registered
            }
            isReceiverRegistered = false
        }
        _isScanning.value = false
    }

    @SuppressLint("MissingPermission")
    fun pairDevice(device: BluetoothDevice): Boolean {
        if (!hasBluetoothPermission()) return false
        return device.createBond()
    }

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

    fun saveSelectedPrinterAddress(address: String?) {
        sharedPreferences.edit().putString("selected_printer_address", address).apply()
    }

    fun getSelectedPrinterAddress(): String? {
        return sharedPreferences.getString("selected_printer_address", null)
    }

    fun savePaperWidth(width: Int) {
        sharedPreferences.edit().putInt("paper_width", width).apply()
    }

    fun getPaperWidth(): Int {
        return sharedPreferences.getInt("paper_width", 58)
    }

    fun saveCharsetEncoding(encoding: String) {
        sharedPreferences.edit().putString("charset_encoding", encoding).apply()
    }

    fun getCharsetEncoding(): String {
        return sharedPreferences.getString("charset_encoding", "CP850") ?: "CP850"
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        connectionMutex.withLock {
            if (!hasBluetoothPermission()) {
                _connectionStatus.value = ConnectionStatus.ERROR
                return@withLock false
            }

            val currentSocket = bluetoothSocket
            if (_connectionStatus.value == ConnectionStatus.CONNECTED && 
                currentSocket != null && 
                currentSocket.isConnected && 
                currentSocket.remoteDevice?.address == device.address) {
                return@withLock true
            }

            _connectionStatus.value = ConnectionStatus.CONNECTING
            closeSocket()

            try {
                Log.d("BluetoothPrinterManager", "Attempting to connect to ${device.address}")
                bluetoothSocket = try {
                    device.createRfcommSocketToServiceRecord(PRINTER_UUID)
                } catch (e: Exception) {
                    Log.w("BluetoothPrinterManager", "Secure socket failed, trying insecure")
                    device.createInsecureRfcommSocketToServiceRecord(PRINTER_UUID)
                }
                
                bluetoothAdapter?.cancelDiscovery()
                
                bluetoothSocket?.connect()
                outputStream = bluetoothSocket?.outputStream
                
                val deviceName = (try { device.name } catch (e: SecurityException) { null }) ?: device.address
                _connectedDeviceName.value = deviceName
                _connectionStatus.value = ConnectionStatus.CONNECTED
                
                // Persist the successfully connected printer address
                saveSelectedPrinterAddress(device.address)

                Log.d("BluetoothPrinterManager", "Successfully connected to ${device.address}")
                return@withLock true
            } catch (e: Exception) {
                Log.e("BluetoothPrinterManager", "Connection failed to ${device.address}", e)
                closeSocket()
                _connectionStatus.value = ConnectionStatus.ERROR
                return@withLock false
            }
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
        printMutex.withLock {
            val stream = outputStream
            if (_connectionStatus.value != ConnectionStatus.CONNECTED || stream == null) {
                return@withLock false
            }
            try {
                stream.write(bytes)
                stream.flush()
                return@withLock true
            } catch (e: IOException) {
                e.printStackTrace()
                _connectionStatus.value = ConnectionStatus.ERROR
                closeSocket()
                return@withLock false
            }
        }
    }
}
