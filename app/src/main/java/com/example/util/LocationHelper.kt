package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class GeocodedAddress(
    val addressLabel: String,
    val neighborhood: String,
    val latitude: Double,
    val longitude: Double
)

object LocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Pair<Double, Double>? {
        if (!hasLocationPermission(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        return try {
            val cts = CancellationTokenSource()
            val fresh = client.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cts.token
            ).await()
            if (fresh != null) {
                fresh.latitude to fresh.longitude
            } else {
                val last = client.lastLocation.await()
                last?.let { it.latitude to it.longitude }
            }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun geocodeAddress(context: Context, query: String): List<GeocodedAddress> {
        val trimmed = query.trim()
        if (trimmed.length < 3 || !Geocoder.isPresent()) return emptyList()
        val geocoder = Geocoder(context, Locale.getDefault())

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                try {
                    geocoder.getFromLocationName(trimmed, 5) { addresses ->
                        if (cont.isActive) {
                            cont.resume(addresses.mapNotNull(::toGeocodedAddress))
                        }
                    }
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(emptyList())
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                try {
                    @Suppress("DEPRECATION")
                    val results = geocoder.getFromLocationName(trimmed, 5).orEmpty()
                    results.mapNotNull(::toGeocodedAddress)
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }
    }

    suspend fun reverseGeocode(
        context: Context,
        latitude: Double,
        longitude: Double
    ): Pair<String, String>? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())

        val address: Address? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                try {
                    geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                        if (cont.isActive) cont.resume(addresses.firstOrNull())
                    }
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                try {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
                } catch (_: Exception) {
                    null
                }
            }
        }

        return address?.let {
            val street = listOfNotNull(it.subThoroughfare, it.thoroughfare)
                .joinToString(" ")
                .ifBlank { it.featureName ?: "" }
            val city = it.locality ?: it.subAdminArea ?: ""
            val label = listOf(street, city).filter { part -> part.isNotBlank() }.joinToString(", ")
                .ifBlank { String.format(Locale.US, "%.4f, %.4f", latitude, longitude) }
            val hood = it.subLocality ?: it.locality ?: "Local Neighborhood"
            label to hood
        }
    }

    private fun toGeocodedAddress(addr: Address): GeocodedAddress? {
        val street = listOfNotNull(addr.subThoroughfare, addr.thoroughfare)
            .joinToString(" ")
            .ifBlank { addr.featureName ?: "" }
        val city = addr.locality ?: addr.subAdminArea ?: ""
        val state = addr.adminArea ?: ""
        val label = listOf(street, city, state).filter { it.isNotBlank() }.joinToString(", ")
        if (label.isBlank()) return null
        val neighborhood = addr.subLocality ?: city.ifBlank { "Local Neighborhood" }
        return GeocodedAddress(
            addressLabel = label,
            neighborhood = neighborhood,
            latitude = addr.latitude,
            longitude = addr.longitude
        )
    }
}
