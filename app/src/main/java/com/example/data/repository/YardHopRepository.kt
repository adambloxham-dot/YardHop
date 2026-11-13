package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firestore.OperationType
import com.example.data.firestore.handleFirestoreError
import com.example.data.model.SavedSaleStop
import com.example.data.model.SellerProfile
import com.example.data.model.SellerReview
import com.example.data.model.UserSettings
import com.example.data.model.YardSale
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class YardHopRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observePublicSales(): Flow<List<YardSale>> = flow {
        val uid = requireUserId()
        val path = "sales"
        emitAll(
            db.collection(path)
                .whereEqualTo("visibility", "public")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        YardSale::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeMySales(sellerId: String): Flow<List<YardSale>> = flow {
        val uid = requireUserId()
        val path = "sales"
        emitAll(
            db.collection(path)
                .whereEqualTo("sellerId", sellerId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        YardSale::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeSavedStops(userId: String): Flow<List<SavedSaleStop>> = flow {
        val uid = requireUserId()
        val path = "users/$userId/saved_sales"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("saved_sales")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SavedSaleStop::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.visited }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeUserSettings(userId: String): Flow<UserSettings?> = flow {
        val uid = requireUserId()
        val path = "users/$userId"
        emitAll(
            db.collection("users")
                .document(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserSettings::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    fun observeSellerProfile(sellerId: String): Flow<SellerProfile?> = flow {
        val uid = requireUserId()
        val path = "sellers/$sellerId"
        emitAll(
            db.collection("sellers")
                .document(sellerId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            SellerProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    fun observeAllPublicSellers(): Flow<Map<String, SellerProfile>> = flow {
        val uid = requireUserId()
        val path = "sellers"
        emitAll(
            db.collection(path)
                .whereEqualTo("visibility", "public")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SellerProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).associateBy { it.sellerId }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeSellerReviews(sellerId: String): Flow<List<SellerReview>> = flow {
        val uid = requireUserId()
        val path = "sellers/$sellerId/reviews"
        emitAll(
            db.collection("sellers")
                .document(sellerId)
                .collection("reviews")
                .whereEqualTo("visibility", "public")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SellerReview::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun ensureUserAndSellerProfiles(
        displayName: String,
        email: String?,
        avatarUrl: String?
    ): Result<Unit> {
        val uid = requireUserId()
        val safeName = displayName.trim().ifBlank { "Neighbor" }.take(80)
        return try {
            val userRef = db.collection("users").document(uid)
            val userSnap = userRef.get().await()
            if (!userSnap.exists()) {
                val userPayload = buildMap<String, Any> {
                    put("userId", uid)
                    put("displayName", safeName)
                    put("alertsEnabled", true)
                    put("alertRadiusMiles", 10L)
                    if (!email.isNullOrBlank()) put("email", email.take(200))
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                userRef.set(userPayload).await()
            }

            val sellerRef = db.collection("sellers").document(uid)
            val sellerSnap = sellerRef.get().await()
            if (!sellerSnap.exists()) {
                val sellerPayload = buildMap<String, Any> {
                    put("sellerId", uid)
                    put("displayName", safeName)
                    put("neighborhood", "Local Neighborhood")
                    put("bio", "Friendly neighborhood seller on YardHop.")
                    if (!avatarUrl.isNullOrBlank()) put("avatarUrl", avatarUrl.take(500))
                    put("ratingSum", 0L)
                    put("ratingCount", 0L)
                    put("visibility", "public")
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                sellerRef.set(sellerPayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun updateUserAlertPreferences(
        alertsEnabled: Boolean,
        alertRadiusMiles: Int,
        homeLat: Double? = null,
        homeLng: Double? = null
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val payload = buildMap<String, Any> {
            put("alertsEnabled", alertsEnabled)
            put("alertRadiusMiles", alertRadiusMiles.coerceIn(1, 100).toLong())
            if (homeLat != null && homeLng != null) {
                put("homeLat", homeLat)
                put("homeLng", homeLng)
            }
            put("updatedAt", FieldValue.serverTimestamp())
        }
        return try {
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateSellerProfile(
        displayName: String,
        neighborhood: String,
        bio: String
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("sellers").document(uid)
        val payload = mapOf(
            "displayName" to displayName.trim().ifBlank { "Neighbor" }.take(80),
            "neighborhood" to neighborhood.trim().take(80),
            "bio" to bio.trim().take(300),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun createSale(
        sellerName: String,
        sellerPhotoUrl: String?,
        title: String,
        description: String,
        addressLabel: String,
        neighborhood: String,
        latitude: Double,
        longitude: Double,
        dateLabel: String,
        hoursLabel: String,
        categories: List<String>,
        priceRangeLabel: String,
        photoDataList: List<String>
    ): Result<String> {
        val uid = requireUserId()
        val docRef = db.collection("sales").document()
        val payload = buildMap<String, Any> {
            put("sellerId", uid)
            put("sellerName", sellerName.trim().ifBlank { "Neighbor" }.take(80))
            if (!sellerPhotoUrl.isNullOrBlank()) put("sellerPhotoUrl", sellerPhotoUrl.take(500))
            put("title", title.trim().take(100))
            put("description", description.trim().take(1000))
            put("addressLabel", addressLabel.trim().take(180))
            if (neighborhood.isNotBlank()) put("neighborhood", neighborhood.trim().take(80))
            put("latitude", latitude)
            put("longitude", longitude)
            put("dateLabel", dateLabel.trim().take(80))
            put("hoursLabel", hoursLabel.trim().take(80))
            put("categories", categories.take(8).map { it.take(40) })
            if (priceRangeLabel.isNotBlank()) put("priceRangeLabel", priceRangeLabel.trim().take(50))
            put("photoDataList", photoDataList.take(3))
            put("status", "active")
            put("visibility", "public")
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun endSale(saleId: String): Result<Unit> {
        requireUserId()
        val docRef = db.collection("sales").document(saleId)
        return try {
            docRef.update(
                mapOf(
                    "status" to "ended",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteSale(saleId: String): Result<Unit> {
        requireUserId()
        val docRef = db.collection("sales").document(saleId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleSavedStop(sale: YardSale, isCurrentlySaved: Boolean): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users")
            .document(uid)
            .collection("saved_sales")
            .document(sale.id)
        return try {
            if (isCurrentlySaved) {
                docRef.delete().await()
            } else {
                val payload = mapOf(
                    "saleId" to sale.id,
                    "title" to sale.title.take(100),
                    "addressLabel" to sale.addressLabel.take(180),
                    "latitude" to sale.latitude,
                    "longitude" to sale.longitude,
                    "visited" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleStopVisited(saleId: String, visited: Boolean): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users")
            .document(uid)
            .collection("saved_sales")
            .document(saleId)
        return try {
            docRef.update("visited", visited).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun submitSellerReview(
        sellerId: String,
        reviewerName: String,
        rating: Int,
        comment: String
    ): Result<Unit> {
        val uid = requireUserId()
        require(uid != sellerId) { "Sellers cannot rate their own profile." }
        val clampedRating = rating.coerceIn(1, 5).toLong()
        val sellerRef = db.collection("sellers").document(sellerId)
        val reviewRef = sellerRef.collection("reviews").document(uid)

        return try {
            db.runTransaction { transaction ->
                val sellerSnap = transaction.get(sellerRef)
                if (!sellerSnap.exists()) {
                    throw IllegalStateException("Seller profile not found.")
                }
                val currentSum = sellerSnap.getLong("ratingSum") ?: 0L
                val currentCount = sellerSnap.getLong("ratingCount") ?: 0L

                val existingReviewSnap = transaction.get(reviewRef)
                if (existingReviewSnap.exists()) {
                    val oldRating = existingReviewSnap.getLong("rating") ?: 0L
                    val newSum = (currentSum - oldRating + clampedRating).coerceAtLeast(0L)
                    transaction.update(
                        reviewRef,
                        mapOf(
                            "reviewerName" to reviewerName.trim().ifBlank { "Shopper" }.take(80),
                            "rating" to clampedRating,
                            "comment" to comment.trim().take(400),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    transaction.update(
                        sellerRef,
                        mapOf(
                            "ratingSum" to newSum,
                            "ratingCount" to currentCount,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    val reviewPayload = mapOf(
                        "sellerId" to sellerId,
                        "reviewerId" to uid,
                        "reviewerName" to reviewerName.trim().ifBlank { "Shopper" }.take(80),
                        "rating" to clampedRating,
                        "comment" to comment.trim().take(400),
                        "visibility" to "public",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    transaction.set(reviewRef, reviewPayload)
                    transaction.update(
                        sellerRef,
                        mapOf(
                            "ratingSum" to currentSum + clampedRating,
                            "ratingCount" to currentCount + 1L,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, reviewRef.path)
            Result.failure(e)
        }
    }

    private fun handleFlowError(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }
}
