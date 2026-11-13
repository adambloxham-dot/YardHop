package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.NearbyAlertItem
import com.example.data.model.SavedSaleStop
import com.example.data.model.SellerProfile
import com.example.data.model.SellerReview
import com.example.data.model.UserSettings
import com.example.data.model.YardSale
import com.example.data.repository.YardHopRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class SaleWithDistance(
    val sale: YardSale,
    val distanceMiles: Double,
    val sellerProfile: SellerProfile?
)

@OptIn(ExperimentalCoroutinesApi::class)
class YardHopViewModel(
    private val repository: YardHopRepository,
    val currentUserId: String,
    private val initialDisplayName: String,
    private val initialEmail: String?,
    private val initialPhotoUrl: String?
) : ViewModel() {

    private val _userLocation = MutableStateFlow(DEFAULT_LAT to DEFAULT_LNG)
    val userLocation: StateFlow<Pair<Double, Double>> = _userLocation.asStateFlow()

    private val _hasLiveGps = MutableStateFlow(false)
    val hasLiveGps: StateFlow<Boolean> = _hasLiveGps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _showActiveOnly = MutableStateFlow(true)
    val showActiveOnly: StateFlow<Boolean> = _showActiveOnly.asStateFlow()

    private val _maxDistanceMiles = MutableStateFlow(25)
    val maxDistanceMiles: StateFlow<Int> = _maxDistanceMiles.asStateFlow()

    private val _selectedSaleId = MutableStateFlow<String?>(null)
    val selectedSaleId: StateFlow<String?> = _selectedSaleId.asStateFlow()

    private val _focusedMapSaleId = MutableStateFlow<String?>(null)
    val focusedMapSaleId: StateFlow<String?> = _focusedMapSaleId.asStateFlow()

    private val _inspectedSellerId = MutableStateFlow<String?>(null)
    val inspectedSellerId: StateFlow<String?> = _inspectedSellerId.asStateFlow()

    private val _nearbyAlerts = MutableStateFlow<List<NearbyAlertItem>>(emptyList())
    val nearbyAlerts: StateFlow<List<NearbyAlertItem>> = _nearbyAlerts.asStateFlow()

    private val _newAlertEvents = MutableSharedFlow<NearbyAlertItem>(extraBufferCapacity = 8)
    val newAlertEvents = _newAlertEvents.asSharedFlow()

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarMessages = _snackbarMessages.asSharedFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val seenSaleIds = mutableSetOf<String>()
    private var initialSnapshotProcessed = false

    val publicSalesState: StateFlow<UiState<List<YardSale>>> = repository.observePublicSales()
        .map<List<YardSale>, UiState<List<YardSale>>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestore(error) ?: error
            Log.w(TAG, "Error observing public sales", root)
            emit(UiState.Error(root.message ?: "Failed to load yard sales"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val mySalesState: StateFlow<UiState<List<YardSale>>> = repository.observeMySales(currentUserId)
        .map<List<YardSale>, UiState<List<YardSale>>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestore(error) ?: error
            Log.w(TAG, "Error observing user sales", root)
            emit(UiState.Error(root.message ?: "Failed to load your listings"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val savedStops: StateFlow<List<SavedSaleStop>> = repository.observeSavedStops(currentUserId)
        .catch { error ->
            val root = unwrapFirestore(error) ?: error
            Log.w(TAG, "Error observing saved stops", root)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val userSettings: StateFlow<UserSettings?> = repository.observeUserSettings(currentUserId)
        .catch { error ->
            val root = unwrapFirestore(error) ?: error
            Log.w(TAG, "Error observing user settings", root)
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    val mySellerProfile: StateFlow<SellerProfile?> = repository.observeSellerProfile(currentUserId)
        .catch { error ->
            val root = unwrapFirestore(error) ?: error
            Log.w(TAG, "Error observing seller profile", root)
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    val publicSellersMap: StateFlow<Map<String, SellerProfile>> = repository.observeAllPublicSellers()
        .catch { error ->
            val root = unwrapFirestore(error) ?: error
            Log.w(TAG, "Error observing sellers", root)
            emit(emptyMap())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyMap()
        )

    val inspectedSellerProfile: StateFlow<SellerProfile?> = _inspectedSellerId
        .flatMapLatest { sellerId ->
            if (sellerId == null) flowOf(null)
            else repository.observeSellerProfile(sellerId).catch { emit(null) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    val inspectedSellerReviews: StateFlow<List<SellerReview>> = _inspectedSellerId
        .flatMapLatest { sellerId ->
            if (sellerId == null) flowOf(emptyList())
            else repository.observeSellerReviews(sellerId).catch { emit(emptyList()) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val filteredSales: StateFlow<UiState<List<SaleWithDistance>>> = combine(
        publicSalesState,
        userLocation,
        searchQuery,
        selectedCategory,
        showActiveOnly,
        maxDistanceMiles,
        publicSellersMap
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val salesUiState = values[0] as UiState<List<YardSale>>
        @Suppress("UNCHECKED_CAST")
        val loc = values[1] as Pair<Double, Double>
        val query = (values[2] as String).trim().lowercase()
        val category = values[3] as String?
        val activeOnly = values[4] as Boolean
        val maxMiles = values[5] as Int
        @Suppress("UNCHECKED_CAST")
        val sellers = values[6] as Map<String, SellerProfile>

        when (salesUiState) {
            is UiState.Loading -> UiState.Loading
            is UiState.Error -> UiState.Error(salesUiState.message)
            is UiState.Success -> {
                val mapped = salesUiState.data
                    .asSequence()
                    .filter { sale -> !activeOnly || sale.isActive }
                    .filter { sale ->
                        category == null || sale.categories.any {
                            it.equals(category, ignoreCase = true)
                        }
                    }
                    .filter { sale ->
                        if (query.isEmpty()) true
                        else {
                            sale.title.lowercase().contains(query) ||
                                sale.description.lowercase().contains(query) ||
                                sale.addressLabel.lowercase().contains(query) ||
                                sale.neighborhood.lowercase().contains(query) ||
                                sale.categories.any { it.lowercase().contains(query) }
                        }
                    }
                    .map { sale ->
                        val dist = sale.distanceMilesFrom(loc.first, loc.second)
                        SaleWithDistance(
                            sale = sale,
                            distanceMiles = dist,
                            sellerProfile = sellers[sale.sellerId]
                        )
                    }
                    .filter { maxMiles >= 100 || it.distanceMiles <= maxMiles }
                    .sortedWith(compareBy<SaleWithDistance> { !it.sale.isActive }.thenBy { it.distanceMiles })
                    .toList()
                UiState.Success(mapped)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading
    )

    // Map displays all active public sales (never hidden by distance, search, or category filters)
    val mapSalesState: StateFlow<UiState<List<SaleWithDistance>>> = combine(
        publicSalesState,
        userLocation,
        publicSellersMap
    ) { salesUiState, loc, sellers ->
        when (salesUiState) {
            is UiState.Loading -> UiState.Loading
            is UiState.Error -> UiState.Error(salesUiState.message)
            is UiState.Success -> {
                val mapped = salesUiState.data
                    .asSequence()
                    .filter { it.isActive }
                    .map { sale ->
                        SaleWithDistance(
                            sale = sale,
                            distanceMiles = sale.distanceMilesFrom(loc.first, loc.second),
                            sellerProfile = sellers[sale.sellerId]
                        )
                    }
                    .sortedBy { it.distanceMiles }
                    .toList()
                UiState.Success(mapped)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading
    )

    init {
        viewModelScope.launch {
            repository.ensureUserAndSellerProfiles(
                displayName = initialDisplayName,
                email = initialEmail,
                avatarUrl = initialPhotoUrl
            )
        }

        // Monitor public sales in real time to detect newly posted nearby sales
        viewModelScope.launch {
            combine(
                publicSalesState,
                userSettings,
                userLocation
            ) { salesState, settings, loc ->
                Triple(salesState, settings, loc)
            }.collect { (salesState, settings, loc) ->
                if (salesState !is UiState.Success) return@collect
                val sales = salesState.data
                if (!initialSnapshotProcessed) {
                    seenSaleIds.addAll(sales.map { it.id })
                    initialSnapshotProcessed = true
                    // Populate initial nearby digest silently without firing notifications
                    val radius = (settings?.alertRadiusMiles ?: 10L).toDouble()
                    val digest = sales
                        .filter { it.isActive && it.sellerId != currentUserId }
                        .mapNotNull { sale ->
                            val d = sale.distanceMilesFrom(loc.first, loc.second)
                            if (d <= radius) {
                                NearbyAlertItem(
                                    saleId = sale.id,
                                    title = sale.title,
                                    addressLabel = sale.addressLabel,
                                    distanceMiles = d
                                )
                            } else null
                        }
                        .sortedBy { it.distanceMiles }
                    _nearbyAlerts.value = digest
                    return@collect
                }

                val alertsEnabled = settings?.alertsEnabled ?: true
                val radiusMiles = (settings?.alertRadiusMiles ?: 10L).toDouble()

                for (sale in sales) {
                    if (sale.id.isNotBlank() && sale.id !in seenSaleIds) {
                        seenSaleIds.add(sale.id)
                        if (sale.isActive && sale.sellerId != currentUserId) {
                            val dist = sale.distanceMilesFrom(loc.first, loc.second)
                            if (alertsEnabled && dist <= radiusMiles) {
                                val alert = NearbyAlertItem(
                                    saleId = sale.id,
                                    title = sale.title,
                                    addressLabel = sale.addressLabel,
                                    distanceMiles = dist
                                )
                                _nearbyAlerts.value = (listOf(alert) + _nearbyAlerts.value).distinctBy { it.saleId }
                                _newAlertEvents.tryEmit(alert)
                            }
                        }
                    }
                }
            }
        }
    }

    fun updateUserLocation(lat: Double, lng: Double, fromGps: Boolean = true) {
        _userLocation.value = lat to lng
        if (fromGps) _hasLiveGps.value = true
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: String?) {
        _selectedCategory.value = if (_selectedCategory.value == category) null else category
    }

    fun setShowActiveOnly(activeOnly: Boolean) {
        _showActiveOnly.value = activeOnly
    }

    fun setMaxDistanceMiles(miles: Int) {
        _maxDistanceMiles.value = miles
    }

    fun openSaleDetail(saleId: String?) {
        _selectedSaleId.value = saleId
    }

    fun focusSaleOnMap(saleId: String?) {
        _focusedMapSaleId.value = saleId
    }

    fun consumeFocusedMapSale() {
        _focusedMapSaleId.value = null
    }

    fun openSellerProfile(sellerId: String?) {
        _inspectedSellerId.value = sellerId
    }

    fun createSale(
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
        photoDataList: List<String>,
        onSuccess: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val sellerName = mySellerProfile.value?.displayName?.ifBlank { initialDisplayName }
                ?: initialDisplayName.ifBlank { "Neighbor" }
            val result = repository.createSale(
                sellerName = sellerName,
                sellerPhotoUrl = initialPhotoUrl,
                title = title,
                description = description,
                addressLabel = addressLabel,
                neighborhood = neighborhood,
                latitude = latitude,
                longitude = longitude,
                dateLabel = dateLabel,
                hoursLabel = hoursLabel,
                categories = categories,
                priceRangeLabel = priceRangeLabel,
                photoDataList = photoDataList
            )
            _isSubmitting.value = false
            result.onSuccess { id ->
                seenSaleIds.add(id)
                _searchQuery.value = ""
                _selectedCategory.value = null
                _focusedMapSaleId.value = id
                _snackbarMessages.tryEmit("Yard sale posted live on the map!")
                onSuccess(id)
            }.onFailure { err ->
                _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to post yard sale")
            }
        }
    }

    fun endSale(saleId: String) {
        viewModelScope.launch {
            repository.endSale(saleId)
                .onSuccess { _snackbarMessages.tryEmit("Sale marked as ended") }
                .onFailure { _snackbarMessages.tryEmit(it.localizedMessage ?: "Could not end sale") }
        }
    }

    fun deleteSale(saleId: String) {
        viewModelScope.launch {
            repository.deleteSale(saleId)
                .onSuccess {
                    if (_selectedSaleId.value == saleId) _selectedSaleId.value = null
                    _snackbarMessages.tryEmit("Listing removed")
                }
                .onFailure { _snackbarMessages.tryEmit(it.localizedMessage ?: "Could not delete sale") }
        }
    }

    fun toggleSavedStop(sale: YardSale) {
        viewModelScope.launch {
            val isSaved = savedStops.value.any { it.saleId == sale.id }
            repository.toggleSavedStop(sale, isSaved)
                .onSuccess {
                    _snackbarMessages.tryEmit(
                        if (isSaved) "Removed from Hop List" else "Added to your Hop List"
                    )
                }
                .onFailure { _snackbarMessages.tryEmit("Could not update Hop List") }
        }
    }

    fun toggleStopVisited(saleId: String, visited: Boolean) {
        viewModelScope.launch {
            repository.toggleStopVisited(saleId, visited)
        }
    }

    fun updateAlertPreferences(alertsEnabled: Boolean, radiusMiles: Int) {
        viewModelScope.launch {
            val loc = if (_hasLiveGps.value) _userLocation.value else null
            repository.updateUserAlertPreferences(
                alertsEnabled = alertsEnabled,
                alertRadiusMiles = radiusMiles,
                homeLat = loc?.first,
                homeLng = loc?.second
            ).onSuccess {
                _snackbarMessages.tryEmit(
                    if (alertsEnabled) "Nearby alerts set to $radiusMiles mi"
                    else "Nearby alerts paused"
                )
            }.onFailure {
                _snackbarMessages.tryEmit("Could not update alert settings")
            }
        }
    }

    fun updateSellerProfile(displayName: String, neighborhood: String, bio: String) {
        viewModelScope.launch {
            _isSubmitting.value = true
            repository.updateSellerProfile(displayName, neighborhood, bio)
                .onSuccess { _snackbarMessages.tryEmit("Seller profile updated") }
                .onFailure { _snackbarMessages.tryEmit("Failed to update profile") }
            _isSubmitting.value = false
        }
    }

    fun submitSellerReview(
        sellerId: String,
        rating: Int,
        comment: String,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val reviewerName = mySellerProfile.value?.displayName?.ifBlank { initialDisplayName }
                ?: initialDisplayName.ifBlank { "Neighbor" }
            repository.submitSellerReview(
                sellerId = sellerId,
                reviewerName = reviewerName,
                rating = rating,
                comment = comment
            ).onSuccess {
                _snackbarMessages.tryEmit("Thanks! Rating published.")
                onComplete()
            }.onFailure { err ->
                _snackbarMessages.tryEmit(err.localizedMessage ?: "Could not submit rating")
            }
            _isSubmitting.value = false
        }
    }

    private fun unwrapFirestore(error: Throwable): FirebaseFirestoreException? {
        val fs = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (fs == null && error is CancellationException) throw error
        return fs
    }

    private companion object {
        const val TAG = "YardHopVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
        const val DEFAULT_LAT = 37.7749
        const val DEFAULT_LNG = -122.4194
    }
}
