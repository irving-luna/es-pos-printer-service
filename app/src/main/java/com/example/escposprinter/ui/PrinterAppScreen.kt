package com.example.escposprinter.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.escposprinter.bluetooth.BluetoothPrinterManager
import com.example.escposprinter.bluetooth.ConnectionStatus
import com.example.escposprinter.escpos.EscPosBuilder
import kotlinx.coroutines.launch
import java.util.*

// Sealed class representing interactive receipt elements
sealed class ReceiptElement {
    data class Text(
        val text: String,
        val alignment: EscPosBuilder.Alignment = EscPosBuilder.Alignment.LEFT,
        val bold: Boolean = false,
        val size: EscPosBuilder.FontSize = EscPosBuilder.FontSize.NORMAL
    ) : ReceiptElement()

    data class TwoColumn(val left: String, val right: String) : ReceiptElement()
    data class Divider(val char: Char = '-') : ReceiptElement()
    data class QrCode(val content: String) : ReceiptElement()
    data class Barcode(val content: String) : ReceiptElement()
    object Cut : ReceiptElement()
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun PrinterAppScreen(
    printerManager: BluetoothPrinterManager,
    onRequestPermissions: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val hasPermission = printerManager.hasBluetoothPermission()
    val isBtSupported = printerManager.isBluetoothSupported()
    val isBtEnabled = printerManager.isBluetoothEnabled()

    val connectionStatus by printerManager.connectionStatus.collectAsState()
    val connectedDeviceName by printerManager.connectedDeviceName.collectAsState()
    val discoveredDevices by printerManager.discoveredDevices.collectAsState()
    val isScanning by printerManager.isScanning.collectAsState()

    var pairedDevices by remember { mutableStateOf(emptyList<BluetoothDevice>()) }
    var selectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    var paperWidthMm by remember { mutableStateOf(printerManager.getPaperWidth()) }
    var charsetEncoding by remember { mutableStateOf(printerManager.getCharsetEncoding()) }

    // Load paired devices and initial selected device
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            pairedDevices = printerManager.getPairedDevices()
            val savedAddress = printerManager.getSelectedPrinterAddress()
            if (savedAddress != null) {
                selectedDevice = pairedDevices.find { it.address == savedAddress }
                // Proactively connect to the saved printer on app launch if not already connected
                if (selectedDevice != null && printerManager.connectionStatus.value == ConnectionStatus.DISCONNECTED) {
                    coroutineScope.launch {
                        printerManager.connect(selectedDevice!!)
                    }
                }
            }
        }
    }

    // Stop discovery when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            printerManager.stopDiscovery()
        }
    }

    // Refresh paired devices when scanning finishes or connection changes
    LaunchedEffect(connectionStatus, isScanning) {
        if (hasPermission) {
            pairedDevices = printerManager.getPairedDevices()
        }
    }

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Quick Test", "Interactive Builder", "Raw Hex Console")

    // Interactive Receipt state
    val interactiveElements = remember { 
        mutableStateListOf<ReceiptElement>(
            ReceiptElement.Text("Veterinary Clinic", EscPosBuilder.Alignment.CENTER, true, EscPosBuilder.FontSize.LARGE),
            ReceiptElement.Text("Order Receipt #1042", EscPosBuilder.Alignment.CENTER, false, EscPosBuilder.FontSize.NORMAL),
            ReceiptElement.Divider('-'),
            ReceiptElement.Text("Patient: Luna (Cat)", EscPosBuilder.Alignment.LEFT, true),
            ReceiptElement.Text("Owner: Irvin R.", EscPosBuilder.Alignment.LEFT, false),
            ReceiptElement.Divider('.'),
            ReceiptElement.TwoColumn("Rabies Vaccine", "$25.00"),
            ReceiptElement.TwoColumn("General Checkup", "$40.00"),
            ReceiptElement.TwoColumn("Deworming Pill", "$12.50"),
            ReceiptElement.Divider('-'),
            ReceiptElement.TwoColumn("Subtotal", "$77.50"),
            ReceiptElement.TwoColumn("Tax (16%)", "$12.40"),
            ReceiptElement.TwoColumn("Total Paid", "$89.90"),
            ReceiptElement.Divider('='),
            ReceiptElement.Text("Thank you for your trust!", EscPosBuilder.Alignment.CENTER, false),
            ReceiptElement.QrCode("https://example.com/clinic/Luna"),
            ReceiptElement.Cut
        )
    }

    // HEX Editor state
    var hexInputText by remember { 
        mutableStateOf("1B 40\n1B 61 01\n1D 21 11\n48 45 4C 4C 4F\n0A 0A\n1D 56 42 00") 
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow, // Simulated print head icon
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            "ThermoPrint ESC/POS",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    IconButton(onClick = { 
                        if (hasPermission) {
                            pairedDevices = printerManager.getPairedDevices()
                            if (!isScanning) printerManager.startDiscovery()
                        } else onRequestPermissions()
                    }) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.secondary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Scan for devices",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Permission & Bluetooth Checks banner
            if (!isBtSupported) {
                ErrorMessageCard(message = "Bluetooth is not supported on this device.")
            } else if (!isBtEnabled) {
                ErrorMessageCard(message = "Bluetooth is disabled. Please enable Bluetooth.")
            } else if (!hasPermission) {
                PermissionRequestCard(onRequest = onRequestPermissions)
            }

            // Connection Status Banner
            ConnectionStatusCard(
                status = connectionStatus,
                connectedDeviceName = connectedDeviceName,
                onDisconnect = {
                    coroutineScope.launch { printerManager.disconnect() }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Printer Selector (if disconnected or error)
            if (connectionStatus == ConnectionStatus.DISCONNECTED || connectionStatus == ConnectionStatus.ERROR) {
                DeviceSelectionSection(
                    pairedDevices = pairedDevices,
                    discoveredDevices = discoveredDevices,
                    selectedDevice = selectedDevice,
                    onDeviceSelect = { selectedDevice = it },
                    onConnectClick = { dev ->
                        coroutineScope.launch {
                            printerManager.stopDiscovery()
                            if (dev.bondState == BluetoothDevice.BOND_NONE) {
                                printerManager.pairDevice(dev)
                            } else {
                                printerManager.connect(dev)
                            }
                        }
                    },
                    isConnecting = connectionStatus == ConnectionStatus.CONNECTING,
                    isScanning = isScanning
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Paper settings bar
            PaperSettingsRow(
                paperWidth = paperWidthMm,
                encoding = charsetEncoding,
                onWidthChange = { 
                    paperWidthMm = it
                    printerManager.savePaperWidth(it)
                },
                onEncodingChange = { 
                    charsetEncoding = it
                    printerManager.saveCharsetEncoding(it)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Main Tab Selection
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tab Content Frame
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> QuickTestTab(
                        printerManager = printerManager,
                        paperWidth = paperWidthMm,
                        encoding = charsetEncoding,
                        isConnected = connectionStatus == ConnectionStatus.CONNECTED
                    )
                    1 -> InteractiveBuilderTab(
                        elements = interactiveElements,
                        printerManager = printerManager,
                        paperWidth = paperWidthMm,
                        encoding = charsetEncoding,
                        isConnected = connectionStatus == ConnectionStatus.CONNECTED
                    )
                    2 -> RawHexConsoleTab(
                        inputText = hexInputText,
                        onTextChange = { hexInputText = it },
                        printerManager = printerManager,
                        isConnected = connectionStatus == ConnectionStatus.CONNECTED
                    )
                }
            }
        }
    }
}

// Subcomponents definitions

@Composable
fun ErrorMessageCard(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 14.sp)
        }
    }
}

@Composable
fun PermissionRequestCard(onRequest: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Bluetooth permission is required to scan & connect to thermal printers.",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Grant Bluetooth Permission", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
fun ConnectionStatusCard(
    status: ConnectionStatus,
    connectedDeviceName: String?,
    onDisconnect: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ), label = "alpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Status indicator LED dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            when (status) {
                                ConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.primary
                                ConnectionStatus.CONNECTING -> MaterialTheme.colorScheme.secondary.copy(
                                    alpha = alpha
                                )
                                ConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
                                ConnectionStatus.DISCONNECTED -> Color.Gray
                            }
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = when (status) {
                            ConnectionStatus.CONNECTED -> "CONNECTED"
                            ConnectionStatus.CONNECTING -> "CONNECTING..."
                            ConnectionStatus.DISCONNECTED -> "DISCONNECTED"
                            ConnectionStatus.ERROR -> "CONNECTION ERROR"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = when (status) {
                            ConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.primary
                            ConnectionStatus.CONNECTING -> MaterialTheme.colorScheme.secondary
                            ConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
                            ConnectionStatus.DISCONNECTED -> Color.Gray
                        }
                    )
                    if (status == ConnectionStatus.CONNECTED && connectedDeviceName != null) {
                        Text(
                            text = connectedDeviceName,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (status == ConnectionStatus.CONNECTED) {
                OutlinedButton(
                    onClick = onDisconnect,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Disconnect", fontSize = 12.sp)
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceSelectionSection(
    pairedDevices: List<BluetoothDevice>,
    discoveredDevices: List<BluetoothDevice>,
    selectedDevice: BluetoothDevice?,
    onDeviceSelect: (BluetoothDevice) -> Unit,
    onConnectClick: (BluetoothDevice) -> Unit,
    isConnecting: Boolean,
    isScanning: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Bluetooth Printers",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isScanning) {
                    Text(
                        "Scanning...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (pairedDevices.isEmpty() && discoveredDevices.isEmpty()) {
                Text(
                    "No devices found. Tap the search icon to scan or pair in system settings.",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    if (pairedDevices.isNotEmpty()) {
                        item {
                            Text(
                                "PAIRED DEVICES",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(pairedDevices) { device ->
                            DeviceRow(
                                device = device,
                                isSelected = selectedDevice?.address == device.address,
                                onSelect = { onDeviceSelect(device) }
                            )
                        }
                    }

                    if (discoveredDevices.isNotEmpty()) {
                        item {
                            Text(
                                "DISCOVERED DEVICES",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(discoveredDevices) { device ->
                            DeviceRow(
                                device = device,
                                isSelected = selectedDevice?.address == device.address,
                                onSelect = { onDeviceSelect(device) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { selectedDevice?.let { onConnectClick(it) } },
                    enabled = selectedDevice != null && !isConnecting,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        val isPaired = selectedDevice?.bondState == BluetoothDevice.BOND_BONDED
                        Icon(
                            imageVector = if (isPaired) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (isPaired) "Connect Selected Printer" else "Pair and Connect Printer",
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceRow(
    device: BluetoothDevice,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
            .clickable { onSelect() }
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(device.name ?: "Unknown Device", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(device.address, fontSize = 11.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun PaperSettingsRow(
    paperWidth: Int,
    encoding: String,
    onWidthChange: (Int) -> Unit,
    onEncodingChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Paper Size", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
            Row {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onWidthChange(58) }) {
                    RadioButton(selected = paperWidth == 58, onClick = { onWidthChange(58) })
                    Text("58mm", fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onWidthChange(80) }) {
                    RadioButton(selected = paperWidth == 80, onClick = { onWidthChange(80) })
                    Text("80mm", fontSize = 13.sp)
                }
            }
        }

        Column {
            Text("Encoding", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
            Row {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onEncodingChange("CP850") }) {
                    RadioButton(selected = encoding == "CP850", onClick = { onEncodingChange("CP850") })
                    Text("CP850", fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onEncodingChange("UTF-8") }) {
                    RadioButton(selected = encoding == "UTF-8", onClick = { onEncodingChange("UTF-8") })
                    Text("UTF-8", fontSize = 13.sp)
                }
            }
        }
    }
}

// Tab 1: Quick Test Actions
@Composable
fun QuickTestTab(
    printerManager: BluetoothPrinterManager,
    paperWidth: Int,
    encoding: String,
    isConnected: Boolean
) {
    val coroutineScope = rememberCoroutineScope()

    val testReceiptBlock = {
        val builder = EscPosBuilder(encoding)
            .reset()
            .alignCenter()
            .bold(true)
            .size(EscPosBuilder.FontSize.LARGE)
            .textLine("VET AGENDA CLINIC")
            .size(EscPosBuilder.FontSize.NORMAL)
            .bold(false)
            .textLine("Dr. Irving R. (DVM)")
            .textLine("Av. de las Mascotas 302")
            .textLine("Phone: 555-123-4567")
            .dividerLine('-', paperWidth)
            .alignLeft()
            .bold(true)
            .textLine("PATIENT: Luna (Cat - 2 years)")
            .bold(false)
            .textLine("Owner: Irvin R.")
            .textLine("Check-in: 2026-05-27 18:30")
            .dividerLine('.', paperWidth)
            .twoColumns("Rabies Vaccination", "$25.00", paperWidth, encoding)
            .twoColumns("Deworming Treatment", "$15.00", paperWidth, encoding)
            .twoColumns("Feline Leucosis Shot", "$35.00", paperWidth, encoding)
            .twoColumns("Consultation Fee", "$40.00", paperWidth, encoding)
            .dividerLine('-', paperWidth)
            .twoColumns("Subtotal", "$115.00", paperWidth, encoding)
            .twoColumns("Tax (16%)", "$18.40", paperWidth, encoding)
            .bold(true)
            .twoColumns("Total Paid", "$133.40", paperWidth, encoding)
            .bold(false)
            .dividerLine('=', paperWidth)
            .alignCenter()
            .textLine("Appointment: 2026-06-27 (Booster)")
            .textLine("Scan to check medical file:")
            .qrCode("https://example.com/clinic/Luna/history")
            .barcodeCODE128("Luna-1042")
            .textLine("Luna-1042")
            .cut()
            .build()

        coroutineScope.launch {
            printerManager.printBytes(builder)
        }
    }

    val testFormattingBlock = {
        val builder = EscPosBuilder(encoding)
            .reset()
            .alignCenter()
            .textLine("FORMATTING TESTING")
            .dividerLine('-', paperWidth)
            .alignLeft()
            .textLine("Left aligned text")
            .alignCenter()
            .textLine("Center aligned text")
            .alignRight()
            .textLine("Right aligned text")
            .dividerLine('.', paperWidth)
            .alignLeft()
            .bold(true)
            .textLine("Bold Text On")
            .bold(false)
            .textLine("Bold Text Off")
            .underline(true)
            .textLine("Underlined Text On")
            .underline(false)
            .textLine("Underlined Text Off")
            .dividerLine('.', paperWidth)
            .size(EscPosBuilder.FontSize.DOUBLE_HEIGHT)
            .textLine("DOUBLE HEIGHT")
            .size(EscPosBuilder.FontSize.DOUBLE_WIDTH)
            .textLine("DOUBLE WIDTH")
            .size(EscPosBuilder.FontSize.LARGE)
            .textLine("LARGE FONT")
            .size(EscPosBuilder.FontSize.NORMAL)
            .textLine("Normal font size")
            .cut()
            .build()

        coroutineScope.launch {
            printerManager.printBytes(builder)
        }
    }

    val testBarcodesBlock = {
        val builder = EscPosBuilder(encoding)
            .reset()
            .alignCenter()
            .bold(true)
            .textLine("BARCODE & QR CODES")
            .bold(false)
            .dividerLine('-', paperWidth)
            .textLine("Epson QR Code (Size 6):")
            .qrCode("https://google.com")
            .dividerLine('.', paperWidth)
            .textLine("CODE128 Barcode (Width 3, Height 80):")
            .barcodeCODE128("123456789012")
            .textLine("123456789012")
            .cut()
            .build()

        coroutineScope.launch {
            printerManager.printBytes(builder)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Quick Print Presets",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "Send pre-built test templates immediately to your printer to verify formatting, layout widths, and graphic capabilities.",
            fontSize = 12.sp,
            color = Color.LightGray
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { testReceiptBlock() },
            enabled = isConnected,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .height(60.dp)
        ) {
            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Print Veterinary Sale Receipt", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
        }

        Button(
            onClick = { testFormattingBlock() },
            enabled = isConnected,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .height(52.dp)
        ) {
            Icon(imageVector = Icons.Default.Info, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Print Text Formatting Specs", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
        }

        Button(
            onClick = { testBarcodesBlock() },
            enabled = isConnected,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .height(52.dp)
        ) {
            Icon(imageVector = Icons.Default.Build, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Print Barcode & QR Code Demo", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
        }

        if (!isConnected) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Note: Connect to a Bluetooth printer above to enable printing.",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// Tab 2: Interactive Designer with Virtual Receipt Preview
@Composable
fun InteractiveBuilderTab(
    elements: MutableList<ReceiptElement>,
    printerManager: BluetoothPrinterManager,
    paperWidth: Int,
    encoding: String,
    isConnected: Boolean
) {
    val coroutineScope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Virtual Receipt Preview", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Row {
                IconButton(onClick = { elements.clear() }) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear", tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Element", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Virtual Receipt Canvas (Styling mimics physical thermal paper roll)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFE2E8F0)) // Slate gray surrounding layout
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            val listState = rememberLazyListState()
            // Physical paper look card
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        // Top Jagged Cut simulation
                        val path = Path()
                        val peakSize = 8.dp.toPx()
                        var currentX = 0f
                        path.moveTo(0f, 0f)
                        while (currentX < size.width) {
                            path.lineTo(currentX + peakSize / 2, peakSize)
                            path.lineTo(currentX + peakSize, 0f)
                            currentX += peakSize
                        }
                        path.lineTo(size.width, 0f)
                        path.close()
                        drawPath(path, Color(0xFFE2E8F0))
                    },
                colors = CardDefaults.cardColors(containerColor = Color.White), // Thermal receipt is white!
                shape = RoundedCornerShape(0.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 12.dp, horizontal = 16.dp)
                ) {
                    items(elements) { element ->
                        ReceiptElementRow(element = element, paperWidth = paperWidth)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                // Build receipt bytes from elements
                val builder = EscPosBuilder(encoding).reset()
                elements.forEach { elem ->
                    when (elem) {
                        is ReceiptElement.Text -> {
                            builder.align(elem.alignment)
                                   .bold(elem.bold)
                                   .size(elem.size)
                                   .textLine(elem.text)
                        }
                        is ReceiptElement.Divider -> {
                            builder.alignLeft()
                                   .bold(false)
                                   .size(EscPosBuilder.FontSize.NORMAL)
                                   .dividerLine(elem.char, paperWidth)
                        }
                        is ReceiptElement.TwoColumn -> {
                            builder.alignLeft()
                                   .bold(false)
                                   .size(EscPosBuilder.FontSize.NORMAL)
                                   .twoColumns(elem.left, elem.right, paperWidth, encoding)
                        }
                        is ReceiptElement.QrCode -> {
                            builder.alignCenter()
                                   .qrCode(elem.content)
                        }
                        is ReceiptElement.Barcode -> {
                            builder.alignCenter()
                                   .barcodeCODE128(elem.content)
                        }
                        is ReceiptElement.Cut -> {
                            builder.cut()
                        }
                    }
                }
                val job = builder.build()
                coroutineScope.launch {
                    printerManager.printBytes(job)
                }
            },
            enabled = isConnected && elements.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.Send, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Print Virtual Receipt", color = MaterialTheme.colorScheme.onPrimary)
        }
    }

    // Add Element Dialog
    if (showAddDialog) {
        AddElementDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { elem ->
                elements.add(elem)
                showAddDialog = false
            }
        )
    }
}

// Receipt Rendering inside the virtual roll preview
@Composable
fun ReceiptElementRow(element: ReceiptElement, paperWidth: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        val alignment = when (element) {
            is ReceiptElement.Text -> when (element.alignment) {
                EscPosBuilder.Alignment.LEFT -> Alignment.CenterStart
                EscPosBuilder.Alignment.CENTER -> Alignment.Center
                EscPosBuilder.Alignment.RIGHT -> Alignment.CenterEnd
            }
            is ReceiptElement.QrCode, is ReceiptElement.Barcode -> Alignment.Center
            else -> Alignment.CenterStart
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = alignment
        ) {
            when (element) {
                is ReceiptElement.Text -> {
                    Text(
                        text = element.text,
                        color = Color.Black,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (element.bold) FontWeight.Bold else FontWeight.Normal,
                        fontSize = when (element.size) {
                            EscPosBuilder.FontSize.NORMAL -> 11.sp
                            EscPosBuilder.FontSize.DOUBLE_HEIGHT -> 15.sp
                            EscPosBuilder.FontSize.DOUBLE_WIDTH -> 15.sp
                            EscPosBuilder.FontSize.LARGE -> 20.sp
                            EscPosBuilder.FontSize.HUGE -> 26.sp
                        },
                        textAlign = when (element.alignment) {
                            EscPosBuilder.Alignment.LEFT -> TextAlign.Left
                            EscPosBuilder.Alignment.CENTER -> TextAlign.Center
                            EscPosBuilder.Alignment.RIGHT -> TextAlign.Right
                        }
                    )
                }
                is ReceiptElement.Divider -> {
                    val lineLen = if (paperWidth == 80) 48 else 32
                    Text(
                        text = element.char.toString().repeat(lineLen),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color.DarkGray
                    )
                }
                is ReceiptElement.TwoColumn -> {
                    val lineLen = if (paperWidth == 80) 48 else 32
                    val leftText = element.left
                    val rightText = element.right
                    val spaceCount = maxOf(1, lineLen - leftText.length - rightText.length)
                    Text(
                        text = leftText + " ".repeat(spaceCount) + rightText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color.Black
                    )
                }
                is ReceiptElement.QrCode -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Simulated QR code drawing
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .border(2.dp, Color.Black)
                                .padding(4.dp)
                        ) {
                            // Checkered center look
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.85f))
                            )
                        }
                        Text(element.content, fontSize = 8.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                    }
                }
                is ReceiptElement.Barcode -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Simulated Barcode lines
                        Row(
                            modifier = Modifier
                                .width(120.dp)
                                .height(30.dp)
                                .padding(vertical = 2.dp)
                        ) {
                            for (i in 0..15) {
                                Box(
                                    modifier = Modifier
                                        .weight(if (i % 3 == 0) 2f else 1f)
                                        .fillMaxHeight()
                                        .background(if (i % 2 == 0) Color.Black else Color.Transparent)
                                )
                            }
                        }
                        Text(element.content, fontSize = 9.sp, color = Color.Black, fontFamily = FontFamily.Monospace)
                    }
                }
                is ReceiptElement.Cut -> {
                    // Draw a dashed scissor divider
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(14.dp))
                        Text(
                            text = " - - - - - - - - - - - - - - - - - - ",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color.LightGray
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddElementDialog(onDismiss: () -> Unit, onAdd: (ReceiptElement) -> Unit) {
    var selectedType by remember { mutableStateOf(0) } // 0: Text, 1: 2-Col, 2: Divider, 3: QR, 4: Barcode, 5: Cut
    val types = listOf("Text", "Two-Column", "Divider", "QR Code", "Barcode", "Cut Paper")

    // Input States
    var textInput by remember { mutableStateOf("") }
    var textBold by remember { mutableStateOf(false) }
    var textAlignment by remember { mutableStateOf(EscPosBuilder.Alignment.LEFT) }
    var textSize by remember { mutableStateOf(EscPosBuilder.FontSize.NORMAL) }

    var col1Input by remember { mutableStateOf("") }
    var col2Input by remember { mutableStateOf("") }

    var dividerChar by remember { mutableStateOf('-') }
    var graphicContentInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Receipt Element", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                ScrollableTabRow(
                    selectedTabIndex = selectedType,
                    edgePadding = 0.dp,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    types.forEachIndexed { index, type ->
                        Tab(selected = selectedType == index, onClick = { selectedType = index }, text = { Text(type, fontSize = 12.sp) })
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedType) {
                    0 -> { // Text
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            label = { Text("Text content") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = textBold, onCheckedChange = { textBold = it })
                            Text("Bold Font", fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Alignment", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Row {
                            listOf(
                                EscPosBuilder.Alignment.LEFT to "Left",
                                EscPosBuilder.Alignment.CENTER to "Center",
                                EscPosBuilder.Alignment.RIGHT to "Right"
                            ).forEach { (align, label) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable { textAlignment = align }
                                        .padding(end = 12.dp)
                                ) {
                                    RadioButton(selected = textAlignment == align, onClick = { textAlignment = align })
                                    Text(label, fontSize = 13.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Font Size", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Row {
                            listOf(
                                EscPosBuilder.FontSize.NORMAL to "Normal",
                                EscPosBuilder.FontSize.DOUBLE_HEIGHT to "Height x2",
                                EscPosBuilder.FontSize.LARGE to "Large"
                            ).forEach { (size, label) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable { textSize = size }
                                        .padding(end = 12.dp)
                                ) {
                                    RadioButton(selected = textSize == size, onClick = { textSize = size })
                                    Text(label, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    1 -> { // 2-Col
                        OutlinedTextField(value = col1Input, onValueChange = { col1Input = it }, label = { Text("Left column text") }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = col2Input, onValueChange = { col2Input = it }, label = { Text("Right column text") }, modifier = Modifier.fillMaxWidth())
                    }
                    2 -> { // Divider
                        Text("Select divider line character:")
                        Row {
                            listOf('-', '.', '=', '*').forEach { char ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable { dividerChar = char }
                                        .padding(end = 16.dp)
                                ) {
                                    RadioButton(selected = dividerChar == char, onClick = { dividerChar = char })
                                    Text(char.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    3, 4 -> { // QR, Barcode
                        OutlinedTextField(
                            value = graphicContentInput,
                            onValueChange = { graphicContentInput = it },
                            label = { Text(if (selectedType == 3) "QR Code URL/Data" else "Barcode digits (CODE128)") },
                            keyboardOptions = KeyboardOptions(keyboardType = if (selectedType == 4) KeyboardType.Number else KeyboardType.Text),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    5 -> { // Cut
                        Text("Add physical cut command. This feeds the receipt out and partial cuts the paper roll.", fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val element = when (selectedType) {
                        0 -> ReceiptElement.Text(textInput, textAlignment, textBold, textSize)
                        1 -> ReceiptElement.TwoColumn(col1Input, col2Input)
                        2 -> ReceiptElement.Divider(dividerChar)
                        3 -> ReceiptElement.QrCode(graphicContentInput)
                        4 -> ReceiptElement.Barcode(graphicContentInput)
                        else -> ReceiptElement.Cut
                    }
                    onAdd(element)
                }
            ) {
                Text("Add", color = MaterialTheme.colorScheme.onPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Tab 3: Raw Hex Console
@Composable
fun RawHexConsoleTab(
    inputText: String,
    onTextChange: (String) -> Unit,
    printerManager: BluetoothPrinterManager,
    isConnected: Boolean
) {
    val coroutineScope = rememberCoroutineScope()

    // Helper to parse hex string into bytes safely
    fun parseHexToBytes(hexString: String): ByteArray {
        val cleanHex = hexString.replace("[^0-9a-fA-F]".toRegex(), "")
        val result = ByteArray(cleanHex.length / 2)
        try {
            for (i in result.indices) {
                val index = i * 2
                val item = cleanHex.substring(index, index + 2)
                result[i] = item.toInt(16).toByte()
            }
        } catch (e: Exception) {
            // return empty on error
        }
        return result
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("ESC/POS Hex Command Editor", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(
            "Input instructions in hexadecimal format. Spaces and line feeds are ignored during conversion.",
            fontSize = 11.sp,
            color = Color.LightGray
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = inputText,
            onValueChange = onTextChange,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            textStyle = MaterialTheme.typography.labelSmall,
            placeholder = { Text("e.g. 1B 40 (Reset printer)", color = Color.Gray, fontFamily = FontFamily.Monospace) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onTextChange("") },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.weight(1f)
            ) {
                Text("Clear", color = MaterialTheme.colorScheme.onSurface)
            }

            Button(
                onClick = {
                    val bytes = parseHexToBytes(inputText)
                    if (bytes.isNotEmpty()) {
                        coroutineScope.launch {
                            printerManager.printBytes(bytes)
                        }
                    }
                },
                enabled = isConnected,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(2.5f)
            ) {
                Icon(imageVector = Icons.Default.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Send Bytes to Printer", color = MaterialTheme.colorScheme.onPrimary)
            }
        }

        if (!isConnected) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Must connect to printer first.",
                color = MaterialTheme.colorScheme.error,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
