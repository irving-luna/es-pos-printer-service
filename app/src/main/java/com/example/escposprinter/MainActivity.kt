package com.example.escposprinter

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.escposprinter.bluetooth.BluetoothPrinterManager
import com.example.escposprinter.ui.PrinterAppScreen
import com.example.escposprinter.ui.theme.EscPosPrinterTheme

class MainActivity : ComponentActivity() {

    private lateinit var printerManager: BluetoothPrinterManager

    // Permission launcher for runtime requests
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "Permissions granted successfully", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permissions denied. Bluetooth printing requires these permissions.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        printerManager = BluetoothPrinterManager(this)

        setContent {
            EscPosPrinterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PrinterAppScreen(
                        printerManager = printerManager,
                        onRequestPermissions = { requestAppPermissions() }
                    )
                }
            }
        }

        // Proactively request permissions on launch if not granted
        if (!printerManager.hasBluetoothPermission()) {
            requestAppPermissions()
        }
    }

    private fun requestAppPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
}
