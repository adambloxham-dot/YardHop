package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object PhotoHelper {

    private const val MAX_DIMENSION_PX = 640
    private const val JPEG_QUALITY = 65
    private const val DATA_URI_PREFIX = "data:image/jpeg;base64,"

    private val decodedCache = LruCache<String, ImageBitmap>(48)

    suspend fun encodePickedPhotoToDataUri(context: Context, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            try {
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOpts)
                }
                if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return@withContext null

                var sampleSize = 1
                val maxSide = max(boundsOpts.outWidth, boundsOpts.outHeight)
                while (maxSide / (sampleSize * 2) >= MAX_DIMENSION_PX) {
                    sampleSize *= 2
                }

                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val sampled = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOpts)
                } ?: return@withContext null

                val scaled = scaleBitmapIfNeeded(sampled, MAX_DIMENSION_PX)
                if (scaled !== sampled) sampled.recycle()

                val output = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
                scaled.recycle()

                val base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
                if (base64.length > 180_000) return@withContext null
                DATA_URI_PREFIX + base64
            } catch (_: Exception) {
                null
            }
        }

    fun decodeDataUriToImageBitmap(dataUri: String): ImageBitmap? {
        if (!dataUri.startsWith("data:image")) return null
        decodedCache.get(dataUri)?.let { return it }

        val bitmap = decodeDataUriToBitmap(dataUri) ?: return null
        val imageBitmap = bitmap.asImageBitmap()
        decodedCache.put(dataUri, imageBitmap)
        return imageBitmap
    }

    fun decodeDataUriToBitmap(dataUri: String): Bitmap? {
        if (!dataUri.startsWith("data:image")) return null
        return try {
            val commaIndex = dataUri.indexOf(',')
            if (commaIndex == -1) return null
            val b64 = dataUri.substring(commaIndex + 1)
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val currentMax = max(width, height)
        if (currentMax <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / currentMax.toFloat()
        val targetW = (width * ratio).roundToInt().coerceAtLeast(1)
        val targetH = (height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }
}
