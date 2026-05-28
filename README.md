# ESC/POS Bluetooth Printer Manager

A native Android application designed to manage, configure, and test ESC/POS thermal printers over Bluetooth. Built using modern Android development practices: **Kotlin**, **Jetpack Compose**, and **Material Design 3**.

---

## Features

- **Bluetooth Device Scanning**: Scan and locate nearby Bluetooth devices.
- **Connection Management**: Pair and connect to Bluetooth thermal printers.
- **ESC/POS Command Builder**: Construct and send raw ESC/POS commands (text formatting, alignment, barcodes, QR codes, feed, cut, etc.).
- **System-Wide Print Service**: Integrates with Android's Print Framework to allow printing from other apps (like Chrome or Gmail) directly to your Bluetooth thermal printer.
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

## Project Structure

The project follows the standard Android Gradle multi-module structure, with all primary logic contained in the `:app` module.

```text
esc_pos_bluetooth_app/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/escposprinter/
│   │   │   │   ├── bluetooth/      # Bluetooth device discovery and socket management
│   │   │   │   ├── escpos/         # ESC/POS command generation logic
│   │   │   │   ├── service/        # BluetoothPrintService (Android PrintService implementation)
│   │   │   │   ├── ui/             # Jetpack Compose Screens, Components, and Themes
│   │   │   │   └── MainActivity.kt # Main entry point and permission handling
│   │   │   ├── res/                # XML resources, drawables, and layouts
│   │   │   └── AndroidManifest.xml # App manifest, permissions, and service declarations
│   │   └── test/                   # Unit and Instrumentation tests
│   └── build.gradle                # Module-level build configuration
├── build.gradle                    # Project-level build configuration
└── settings.gradle                 # Project settings and module inclusion
```

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

### 2. Building & Running with Android Studio (Development)

1. **Open Android Studio**.
2. Select **File > Open** or **Import Project**.
3. Choose the root folder of the project (`esc_pos_bluetooth_app`) and click **OK**.
4. Wait for Android Studio to sync the project with Gradle.
5. Connect your physical Android device via USB and ensure **USB Debugging** is enabled.
6. Click the green **Run** button to deploy the `debug` build.

---

## Building a Production-Grade APK

To generate a production-ready APK, you must create a signed Release build.

### 1. Configure Signing (Optional but recommended for automation)
Create a `keystore.properties` file in the project root with your certificate details:
```properties
storeFile=/path/to/your/keystore.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

### 2. Generating via Android Studio
1. Go to **Build > Generate Signed Bundle / APK...**
2. Select **APK** and click **Next**.
3. Choose your existing keystore (or create a new one).
4. Select the **release** build variant.
5. Choose the destination folder and click **Finish**.

### 3. Generating via Command Line
Run the following command in the project root:

#### Windows
```powershell
.\gradlew.bat assembleRelease
```

#### macOS / Linux
```bash
./gradlew assembleRelease
```

The unsigned release APK will be generated at:
`app/build/outputs/apk/release/app-release-unsigned.apk`

*Note: For a true production deployment, you must sign the APK using `apksigner` or configure the `signingConfigs` in `app/build.gradle`.*

### 4. Code Shrinking & Obfuscation
The project is configured with R8 (ProGuard) support. To enable obfuscation and resource shrinking for production, update `app/build.gradle`:
```gradle
buildTypes {
    release {
        minifyEnabled true  // Enabled for production
        shrinkResources true
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
    }
}
```

---

## Permissions & Bluetooth Setup

To discover and print to Bluetooth devices, the application requests the following permissions at runtime:

- **Android 12 (API 31) and higher**:
  - `Manifest.permission.BLUETOOTH_SCAN`
  - `Manifest.permission.BLUETOOTH_CONNECT`
- **Android 11 (API 30) and lower**:
  - `Manifest.permission.ACCESS_FINE_LOCATION`

Upon launching the app, you will be prompted to grant these permissions. If denied, Bluetooth functionalities will be disabled.

---

## Testing & Printing

1. **Enable Bluetooth** on your Android device.
2. **Turn on** your Bluetooth thermal printer.
3. Open the **ESC/POS Bluetooth Printer Manager** app.
4. Scan for devices, select your printer, and connect.
5. **System Print Integration**: Once your printer is configured in the app, you can go to any other app (e.g., Chrome), select "Print", and choose your Bluetooth printer from the list of available printers.
