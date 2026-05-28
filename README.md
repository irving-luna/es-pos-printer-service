# ESC/POS Bluetooth Printer Manager

A native Android application designed to manage, configure, and test ESC/POS thermal printers over Bluetooth. Built using modern Android development practices: **Kotlin**, **Jetpack Compose**, and **Material Design 3**.

---

## Features

- **Bluetooth Device Scanning**: Scan and locate nearby Bluetooth devices.
- **Connection Management**: Pair and connect to Bluetooth thermal printers.
- **ESC/POS Command Builder**: Construct and send raw ESC/POS commands (text formatting, alignment, barcodes, QR codes, feed, cut, etc.).
- **Interactive Layout Designer**: Preview and design custom receipts and tickets before printing.
- **Runtime Permissions**: Modern permission-handling tailored for Android 12+ (Bluetooth Scan/Connect permissions) and backward compatibility with older Android versions (Location permissions).

---

## Tech Stack & Architecture

- **Core**: Kotlin & native Android APIs.
- **UI Framework**: Jetpack Compose (Declarative UI) with Material 3 components.
- **Minimum SDK**: API 26 (Android 8.0 Oreo)
- **Target / Compile SDK**: API 34 (Android 14)
- **Build System**: Gradle (Groovy DSL)
- **Bluetooth API**: Android BluetoothAdapter & BluetoothSocket

---

## Prerequisites

Before building or running the project, ensure you have:
1. **Android Studio**: Android Studio Koala / Ladybug or newer is highly recommended.
2. **JDK**: JDK 17 (required by Android Gradle Plugin `8.2.2`).
3. **Android Device**: A physical Android device with Bluetooth capability (Android Emulator does not support Bluetooth hardware emulation).
4. **Thermal Printer**: A physical Bluetooth ESC/POS printer (typically 58mm or 80mm roll width).

---

## Getting Started

### 1. Cloning / Accessing the Project
Ensure you have the source code locally on your workstation.

```bash
git clone <repository-url>
cd esc_pos_bluetooth_app
```

### 2. Building & Running with Android Studio (Recommended)

This is the easiest way to develop, debug, and deploy the app.

1. **Open Android Studio**.
2. Select **File > Open** or **Import Project**.
3. Choose the root folder of the project (`esc_pos_bluetooth_app`) and click **OK**.
4. Wait for Android Studio to sync the project with Gradle (this will download Gradle 8.2 and all required dependencies).
5. Connect your physical Android device via USB and ensure **USB Debugging** is enabled in the device's developer options.
6. In the run configuration dropdown at the top, select `app`.
7. Click the green **Run** button (or press `Shift + F10`) to compile, install, and launch the application on your device.

### 3. Building & Deploying via Command Line (Gradle)

If you prefer building from the terminal:

#### Windows
```powershell
# Optional: To generate the Gradle wrapper files (gradlew / gradlew.bat) if they are missing
gradle wrapper

# Build the Debug APK
.\gradlew.bat assembleDebug
```

#### macOS / Linux
```bash
# Optional: To generate the Gradle wrapper files
gradle wrapper

# Build the Debug APK
./gradlew assembleDebug
```

The compiled APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

#### Installing on a Connected Device
Make sure `adb` (Android Debug Bridge) is in your PATH, then run:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Permissions & Bluetooth Setup

To discover and print to Bluetooth devices, the application requests the following permissions at runtime:

- **Android 12 (API 31) and higher**:
  - `Manifest.permission.BLUETOOTH_SCAN` (to discover devices)
  - `Manifest.permission.BLUETOOTH_CONNECT` (to pair/connect and send print payloads)
- **Android 11 (API 30) and lower**:
  - `Manifest.permission.ACCESS_FINE_LOCATION` & `Manifest.permission.ACCESS_COARSE_LOCATION` (required by Android's classic Bluetooth discovery mechanism)

Upon launching the app, you will be prompted to grant these permissions. If denied, Bluetooth functionalities will be disabled, but you can re-request permissions using the in-app prompt.

---

## Testing & Printing

1. **Enable Bluetooth** on your Android phone/tablet.
2. **Turn on** your Bluetooth thermal printer and make sure it is in pairing mode (some printers require a default PIN like `0000` or `1234` when pairing for the first time).
3. Open the **ESC/POS Bluetooth Printer Manager** app.
4. Scan for devices, select your printer from the list, and connect.
5. Once connected, use the interactive panel to type text, customize alignment, add formatting, and click **Print Test** or **Send Custom Layout** to test the printer output.
