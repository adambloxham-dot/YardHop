package com.example.ui.map

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

object OsmTileMath {

    fun latLngToTileCoords(lat: Double, lng: Double, zoom: Int): Pair<Double, Double> {
        val n = 2.0.pow(zoom)
        val clampedLat = lat.coerceIn(-85.0511, 85.0511)
        val latRad = Math.toRadians(clampedLat)
        val x = (lng + 180.0) / 360.0 * n
        val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
        return x to y
    }

    fun tileCoordsToLatLng(tileX: Double, tileY: Double, zoom: Int): Pair<Double, Double> {
        val n = 2.0.pow(zoom)
        val lng = tileX / n * 360.0 - 180.0
        val latRad = atan(sinh(PI * (1.0 - 2.0 * tileY / n)))
        val lat = Math.toDegrees(latRad).coerceIn(-85.0511, 85.0511)
        return lat to lng.coerceIn(-180.0, 180.0)
    }

    fun metersPerPixel(lat: Double, zoom: Int, tileSizePx: Float): Double {
        val earthCircumference = 40075016.686
        return earthCircumference * cos(Math.toRadians(lat)) / (2.0.pow(zoom) * tileSizePx)
    }
}

object OsmTileLoader {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val tileCache = LruCache<String, ImageBitmap>(140)
    private val inFlight = mutableSetOf<String>()
    private val mutex = Mutex()

    fun getCached(z: Int, x: Int, y: Int): ImageBitmap? {
        return tileCache.get("$z/$x/$y")
    }

    suspend fun fetchTile(z: Int, x: Int, y: Int): ImageBitmap? {
        val maxTile = (1 shl z)
        if (y < 0 || y >= maxTile) return null
        val wrappedX = ((x % maxTile) + maxTile) % maxTile
        val key = "$z/$wrappedX/$y"
        tileCache.get(key)?.let { return it }

        val shouldFetch = mutex.withLock {
            if (key in inFlight) false else {
                inFlight.add(key)
                true
            }
        }
        if (!shouldFetch) return null

        return try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url("https://tile.openstreetmap.org/$z/$wrappedX/$y.png")
                    .header("User-Agent", "YardHopAndroid/1.0 (neighborhood yard sale finder)")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val bytes = response.body?.bytes() ?: return@withContext null
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: return@withContext null
                    val imageBitmap = bitmap.asImageBitmap()
                    tileCache.put(key, imageBitmap)
                    imageBitmap
                }
            }
        } catch (_: Exception) {
            null
        } finally {
            mutex.withLock { inFlight.remove(key) }
        }
    }
}
