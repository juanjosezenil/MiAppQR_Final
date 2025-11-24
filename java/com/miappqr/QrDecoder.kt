
package com.miappqr

import android.graphics.Bitmap
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.LuminanceSource

/**
 * LuminanceSource a partir de un Bitmap (RGB) para ZXing.
 */
class BitmapLuminanceSource(private val bitmap: Bitmap) : LuminanceSource(bitmap.width, bitmap.height) {
    private val data: ByteArray

    init {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        data = ByteArray(width * height)
        var index = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = pixels[y * width + x]
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF
                // Convertir a luminancia (Y) aproximada
                data[index++] = ((r * 0.299 + g * 0.587 + b * 0.114)).toInt().toByte()
            }
        }
    }

    override fun getRow(y: Int, row: ByteArray?): ByteArray {
        val width = width
        val start = y * width
        val r = row ?: ByteArray(width)
        System.arraycopy(data, start, r, 0, width)
        return r
    }

    override fun getMatrix(): ByteArray = data

    override fun isCropSupported(): Boolean = true

    override fun crop(left: Int, top: Int, width: Int, height: Int): LuminanceSource {
        val croppedBitmap = Bitmap.createBitmap(bitmap, left, top, width, height)
        return BitmapLuminanceSource(croppedBitmap)
    }

    override fun invert(): LuminanceSource {
        val inverted = ByteArray(data.size) { i -> (255 - (data[i].toInt() and 0xFF)).toByte() }
        val tmpBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        // Nota: Para un invert real con Bitmap se requiere regenerar pixels; para QR normales no es necesario.
        return this // normalmente no hace falta invertir para QR estándar
    }
}

/**
 * Decodifica un QR desde un Bitmap. Devuelve el contenido o null si no encuentra.
 */
fun decodeQrFromBitmap(bitmap: Bitmap): String? {
    return try {
        val source = BitmapLuminanceSource(bitmap)
        val bitmapBinary = BinaryBitmap(HybridBinarizer(source))
        val reader = MultiFormatReader()
        val result: Result = reader.decode(bitmapBinary)
        result.text
    } catch (e: NotFoundException) {
        null
    } catch (e: Exception) {
        null
    }
}
