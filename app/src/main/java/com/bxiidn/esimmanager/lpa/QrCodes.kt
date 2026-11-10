package com.bxiidn.esimmanager.lpa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrCodes {

    private fun newReader(): MultiFormatReader = MultiFormatReader().apply {
        setHints(
            mapOf<DecodeHintType, Any>(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
            ),
        )
    }

    private fun decode(reader: MultiFormatReader, source: LuminanceSource): String? {
        val normal = try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (e: Exception) {
            null
        } finally {
            reader.reset()
        }
        if (normal != null) return normal
        // QR terbalik warna (putih di atas hitam).
        return try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source.invert()))).text
        } catch (e: Exception) {
            null
        } finally {
            reader.reset()
        }
    }

    /** Membuat QR dari teks (mis. string LPA) untuk dipindai perangkat lain. */
    fun encode(text: String, size: Int = 720): Bitmap? {
        if (text.isBlank()) return null
        return try {
            val hints = mapOf<EncodeHintType, Any>(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 2,
                EncodeHintType.CHARACTER_SET to "UTF-8",
            )
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
            val width = matrix.width
            val height = matrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                }
            }
            Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            null
        }
    }

    /** Membaca QR dari berkas gambar (mis. tangkapan layar email penyedia eSIM). */
    fun decodeFromImage(context: Context, uri: Uri): String? {
        return try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val largest = maxOf(info.size.width, info.size.height)
                if (largest > 2000) decoder.setTargetSampleSize(largest / 1500 + 1)
            }
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            bitmap.recycle()
            decode(newReader(), RGBLuminanceSource(width, height, pixels))
        } catch (e: Exception) {
            null
        }
    }

    /** Analyzer CameraX: mendekode QR dari bidang luminans (Y) setiap frame. */
    class Analyzer(private val onDecoded: (String) -> Unit) : ImageAnalysis.Analyzer {
        private val reader = newReader()

        override fun analyze(image: ImageProxy) {
            try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val rowStride = plane.rowStride
                val width = image.width
                val height = image.height
                val data = ByteArray(width * height)
                buffer.rewind()
                if (rowStride == width) {
                    buffer.get(data, 0, minOf(buffer.remaining(), data.size))
                } else {
                    for (row in 0 until height) {
                        val position = row * rowStride
                        if (position >= buffer.limit()) break
                        buffer.position(position)
                        buffer.get(data, row * width, minOf(width, buffer.remaining()))
                    }
                }
                val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
                val text = decode(reader, source)
                if (!text.isNullOrBlank()) onDecoded(text)
            } catch (e: Exception) {
                // Frame dilewati; frame berikutnya dicoba lagi.
            } finally {
                image.close()
            }
        }
    }
}
