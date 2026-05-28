package com.example.escposprinter.escpos

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.Charset

class EscPosBuilder(private val defaultCharsetName: String = "CP850") {

    private val bos = ByteArrayOutputStream()

    fun build(): ByteArray {
        return bos.toByteArray()
    }

    fun clear(): EscPosBuilder {
        bos.reset()
        return this
    }

    private fun write(bytes: ByteArray) {
        try {
            bos.write(bytes)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun write(byte: Byte) {
        bos.write(byte.toInt())
    }

    // --- Core Commands ---

    /**
     * Initializes the printer. Clears format settings.
     * ESC @ (0x1B 0x40)
     */
    fun reset(): EscPosBuilder {
        write(byteArrayOf(0x1B, 0x40))
        return this
    }

    /**
     * Feeds 1 line.
     * LF (0x0A)
     */
    fun lineFeed(): EscPosBuilder {
        write(0x0A.toByte())
        return this
    }

    /**
     * Feeds n lines.
     * ESC d n (0x1B 0x64 n)
     */
    fun feedLines(n: Int): EscPosBuilder {
        if (n > 0) {
            write(byteArrayOf(0x1B, 0x64, n.toByte()))
        }
        return this
    }

    // --- Alignment ---

    enum class Alignment(val value: Byte) {
        LEFT(0),
        CENTER(1),
        RIGHT(2)
    }

    /**
     * Sets alignment.
     * ESC a n (0x1B 0x61 n)
     */
    fun align(alignment: Alignment): EscPosBuilder {
        write(byteArrayOf(0x1B, 0x61, alignment.value))
        return this
    }

    fun alignLeft() = align(Alignment.LEFT)
    fun alignCenter() = align(Alignment.CENTER)
    fun alignRight() = align(Alignment.RIGHT)

    // --- Text Styling ---

    /**
     * Enables or disables bold style.
     * ESC E n (0x1B 0x45 n)
     */
    fun bold(enable: Boolean): EscPosBuilder {
        write(byteArrayOf(0x1B, 0x45, if (enable) 1 else 0))
        return this
    }

    /**
     * Enables or disables underline style.
     * ESC - n (0x1B 0x2D n)
     */
    fun underline(enable: Boolean): EscPosBuilder {
        write(byteArrayOf(0x1B, 0x2D, if (enable) 1 else 0))
        return this
    }

    enum class FontSize(val value: Byte) {
        NORMAL(0x00),
        DOUBLE_HEIGHT(0x01),
        DOUBLE_WIDTH(0x10),
        LARGE(0x11), // Double height + double width
        HUGE(0x22)   // Triple height + triple width
    }

    /**
     * Sets text size.
     * GS ! n (0x1D 0x21 n)
     */
    fun size(size: FontSize): EscPosBuilder {
        write(byteArrayOf(0x1D, 0x21, size.value))
        return this
    }

    // --- Text Printing ---

    /**
     * Writes raw text using the active code page.
     */
    fun text(text: String, charsetName: String = defaultCharsetName): EscPosBuilder {
        try {
            val bytes = text.toByteArray(Charset.forName(charsetName))
            write(bytes)
        } catch (e: Exception) {
            // Fallback to UTF-8/Default
            write(text.toByteArray(Charsets.UTF_8))
        }
        return this
    }

    /**
     * Writes text and appends a line feed.
     */
    fun textLine(text: String = "", charsetName: String = defaultCharsetName): EscPosBuilder {
        text(text, charsetName)
        lineFeed()
        return this
    }

    // --- Layout Elements ---

    /**
     * Prints a divider line filled with the specified character.
     * Standard character widths:
     * - 58mm: 32 chars
     * - 80mm: 48 chars
     */
    fun dividerLine(char: Char = '-', paperWidthMm: Int = 58): EscPosBuilder {
        val totalChars = if (paperWidthMm == 80) 48 else 32
        textLine(char.toString().repeat(totalChars))
        return this
    }

    /**
     * Prints two columns of text aligned to the left and right margins.
     * Useful for totals, tax details, and item price displays.
     */
    fun twoColumns(
        left: String,
        right: String,
        paperWidthMm: Int = 58,
        charsetName: String = defaultCharsetName
    ): EscPosBuilder {
        val totalChars = if (paperWidthMm == 80) 48 else 32
        val leftLen = left.length
        val rightLen = right.length
        
        val line = when {
            leftLen + rightLen >= totalChars -> {
                // If it overflows, print left, line feed, then right aligned
                left + "\n" + " ".repeat(totalChars - rightLen) + right
            }
            else -> {
                val spaceCount = totalChars - leftLen - rightLen
                left + " ".repeat(spaceCount) + right
            }
        }
        textLine(line, charsetName)
        return this
    }

    // --- Advanced Features (QR Code & Barcode) ---

    /**
     * Prints a QR code using standard Epson GS ( k commands.
     * @param content QR code content
     * @param size QR module size (dot width) 1 to 16. Default is 6.
     */
    fun qrCode(content: String, size: Int = 6): EscPosBuilder {
        try {
            val dataBytes = content.toByteArray(Charsets.UTF_8)
            val length = dataBytes.size + 3
            val pL = (length % 256).toByte()
            val pH = (length / 256).toByte()

            // 1. Model 2 configuration (GS ( k 4 0 49 65 50 0)
            write(byteArrayOf(0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00))
            
            // 2. Set dot module size (GS ( k 3 0 49 67 size)
            write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, size.toByte()))
            
            // 3. Set error correction level L (GS ( k 3 0 49 68 48)
            write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x44, 0x48.toByte())) // 48 is 'L' error correction (approx 7%)
            
            // 4. Store barcode data in buffer (GS ( k pL pH 49 80 48 data)
            write(byteArrayOf(0x1D, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30))
            write(dataBytes)
            
            // 5. Print QR from buffer (GS ( k 3 0 49 81 48)
            write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30))
            
            // Add spacing after QR code
            lineFeed()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return this
    }

    /**
     * Prints a 1D barcode in CODE128 format.
     * @param content Barcode content (alphanumeric)
     * @param height Barcode height in dots (1 to 255). Default 80.
     * @param width Barcode width multiplier (2 to 6). Default 3.
     */
    fun barcodeCODE128(content: String, height: Int = 80, width: Int = 3): EscPosBuilder {
        try {
            // CODE128 requires a prefix indicating character set. We use '{B' (0x7B, 0x42) for subset B
            val prefix = byteArrayOf(0x7B, 0x42)
            val barcodeBytes = prefix + content.toByteArray(Charsets.US_ASCII)
            val length = barcodeBytes.size

            // 1. Set Height (GS h height)
            write(byteArrayOf(0x1D, 0x68, height.toByte()))
            
            // 2. Set Width (GS w width)
            write(byteArrayOf(0x1D, 0x77, width.toByte()))
            
            // 3. Set text position below barcode (GS H 2)
            write(byteArrayOf(0x1D, 0x48, 2.toByte()))
            
            // 4. Print barcode: Format 2 (GS k m n d1...dn)
            // m = 73 (CODE128), n = length
            write(byteArrayOf(0x1D, 0x6B, 73.toByte(), length.toByte()))
            write(barcodeBytes)
            
            // Add a line feed
            lineFeed()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return this
    }

    // --- Paper Cut ---

    /**
     * Commands the printer to feed paper and cut.
     * GS V m (0x1D 0x56 m)
     * m = 1 (Partial cut), 0 (Full cut)
     * m = 66 (0x42) is Feed paper and perform partial cut (standard on modern printers).
     */
    fun cut(partial: Boolean = true): EscPosBuilder {
        // We feed 3 lines before cutting to ensure print content is fully out
        feedLines(3)
        if (partial) {
            write(byteArrayOf(0x1D, 0x56, 0x42.toByte(), 0x00.toByte()))
        } else {
            write(byteArrayOf(0x1D, 0x56, 0x00.toByte()))
        }
        return this
    }

    // --- Raw Byte Operations ---

    /**
     * Appends a custom raw byte array. Useful for testing unmapped ESC/POS commands.
     */
    fun raw(bytes: ByteArray): EscPosBuilder {
        write(bytes)
        return this
    }
}
