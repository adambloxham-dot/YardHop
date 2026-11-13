package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@IgnoreExtraProperties
data class YardSale(
    @DocumentId val id: String = "",
    val sellerId: String = "",
    val sellerName: String = "",
    val sellerPhotoUrl: String? = null,
    val title: String = "",
    val description: String = "",
    val addressLabel: String = "",
    val neighborhood: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val dateLabel: String = "",
    val hoursLabel: String = "",
    val categories: List<String> = emptyList(),
    val priceRangeLabel: String = "",
    val photoDataList: List<String> = emptyList(),
    val status: String = "active",
    val visibility: String = "public",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isActive: Boolean get() = status == "active"

    fun distanceMilesFrom(userLat: Double, userLng: Double): Double {
        return haversineMiles(userLat, userLng, latitude, longitude)
    }
}

@IgnoreExtraProperties
data class SellerProfile(
    @DocumentId val id: String = "",
    val sellerId: String = "",
    val displayName: String = "",
    val neighborhood: String = "",
    val bio: String = "",
    val avatarUrl: String? = null,
    val ratingSum: Long = 0L,
    val ratingCount: Long = 0L,
    val visibility: String = "public",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val averageRating: Double
        get() = if (ratingCount > 0L) ratingSum.toDouble() / ratingCount.toDouble() else 0.0
}

@IgnoreExtraProperties
data class SellerReview(
    @DocumentId val id: String = "",
    val sellerId: String = "",
    val reviewerId: String = "",
    val reviewerName: String = "",
    val rating: Long = 5L,
    val comment: String = "",
    val visibility: String = "public",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserSettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String? = null,
    val displayName: String = "",
    val alertsEnabled: Boolean = true,
    val alertRadiusMiles: Long = 10L,
    val homeLat: Double? = null,
    val homeLng: Double? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SavedSaleStop(
    @DocumentId val id: String = "",
    val saleId: String = "",
    val title: String = "",
    val addressLabel: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val visited: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class NearbyAlertItem(
    val saleId: String,
    val title: String,
    val addressLabel: String,
    val distanceMiles: Double,
    val receivedAtMillis: Long = System.currentTimeMillis()
)

val DEFAULT_SALE_CATEGORIES = listOf(
    "Furniture",
    "Vintage",
    "Electronics",
    "Clothing",
    "Books & Vinyl",
    "Kids & Toys",
    "Garden & Tools",
    "Collectibles"
)

fun haversineMiles(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadiusMiles = 3958.8
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusMiles * c
}
