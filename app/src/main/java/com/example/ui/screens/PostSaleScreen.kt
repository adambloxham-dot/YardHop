package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.AiDraftUiState
import com.example.data.model.DEFAULT_SALE_CATEGORIES
import com.example.ui.components.AiListingDraftCard
import com.example.ui.components.SalePhotoImage
import com.example.util.LocationHelper
import com.example.util.PhotoHelper
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PostSaleScreen(
    initialLocation: Pair<Double, Double>,
    isSubmitting: Boolean,
    draftState: AiDraftUiState,
    onAutoDraftWithAi: (photos: List<String>, title: String, notes: String, neighborhood: String) -> Unit,
    onBack: () -> Unit,
    onUpdateUserLocation: (Double, Double) -> Unit,
    onPublishSale: (
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
    ) -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var addressQuery by remember { mutableStateOf("") }
    var addressLabel by remember { mutableStateOf("") }
    var neighborhood by remember { mutableStateOf("") }
    var latitude by remember { mutableDoubleStateOf(initialLocation.first) }
    var longitude by remember { mutableDoubleStateOf(initialLocation.second) }
    var locationPinned by remember { mutableStateOf(false) }
    var lastGeocodedQuery by remember { mutableStateOf("") }
    var dateLabel by remember { mutableStateOf("This Saturday") }
    var hoursLabel by remember { mutableStateOf("8:00 AM - 2:00 PM") }
    var priceRangeLabel by remember { mutableStateOf("$2 - $50") }
    var validationError by remember { mutableStateOf<String?>(null) }
    var isResolvingLocation by remember { mutableStateOf(false) }
    var isProcessingPhotos by remember { mutableStateOf(false) }

    val selectedCategories = remember { mutableStateListOf<String>() }
    val photoDataList = remember { mutableStateListOf<String>() }

    LaunchedEffect(draftState) {
        if (draftState is AiDraftUiState.Success) {
            val draft = draftState.draft
            title = draft.title
            description = draft.description
            priceRangeLabel = draft.priceRangeLabel
            if (draft.categories.isNotEmpty()) {
                selectedCategories.clear()
                selectedCategories.addAll(draft.categories)
            }
            validationError = null
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 3)
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            isProcessingPhotos = true
            for (uri in uris) {
                if (photoDataList.size >= 3) break
                val encoded = PhotoHelper.encodePickedPhotoToDataUri(context, uri)
                if (encoded != null) {
                    photoDataList.add(encoded)
                }
            }
            isProcessingPhotos = false
        }
    }

    fun fetchGpsAndFillAddress() {
        scope.launch {
            isResolvingLocation = true
            val coords = LocationHelper.getCurrentLocation(context)
            if (coords != null) {
                latitude = coords.first
                longitude = coords.second
                onUpdateUserLocation(coords.first, coords.second)
                val resolved = LocationHelper.reverseGeocode(context, coords.first, coords.second)
                if (resolved != null) {
                    addressLabel = resolved.first
                    neighborhood = resolved.second
                    addressQuery = resolved.first
                } else {
                    addressLabel = String.format(Locale.US, "GPS Pin (%.4f, %.4f)", coords.first, coords.second)
                    neighborhood = "Local Neighborhood"
                    addressQuery = addressLabel
                }
                lastGeocodedQuery = addressQuery.trim()
                locationPinned = true
                validationError = null
            } else {
                validationError = "Could not read GPS location. Enter an address below instead."
            }
            isResolvingLocation = false
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchGpsAndFillAddress()
        } else {
            validationError = "Location permission denied. You can still type a street address."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Post a Yard Sale", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Photos Section (Zero-Permission Android Photo Picker)
                Text(
                    text = "Photos (${photoDataList.size}/3)",
                    style = MaterialTheme.typography.titleMedium
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (photoDataList.size < 3) {
                        item {
                            Surface(
                                modifier = Modifier
                                    .size(112.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable(enabled = !isProcessingPhotos) {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                    .testTag("pick_photos_button"),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    if (isProcessingPhotos) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.AddAPhoto,
                                            contentDescription = "Add yard sale photos",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Add Photos",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    itemsIndexed(photoDataList) { index, dataUri ->
                        Box(modifier = Modifier.size(112.dp)) {
                            SalePhotoImage(
                                photoData = dataUri,
                                contentDescription = "Selected yard sale photo ${index + 1}",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(MaterialTheme.shapes.medium)
                            )
                            IconButton(
                                onClick = { photoDataList.removeAt(index) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Remove photo",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Gemini Multimodal Listing Auto-Draft Card
                AiListingDraftCard(
                    draftState = draftState,
                    hasPhotosOrNotes = photoDataList.isNotEmpty() || title.isNotBlank() || description.isNotBlank(),
                    onAutoDraftClick = {
                        onAutoDraftWithAi(
                            photoDataList.toList(),
                            title,
                            description,
                            neighborhood
                        )
                    }
                )

                // 2. Listing Basics
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Sale Headline *") },
                    placeholder = { Text("e.g., Moving Sale: Mid-Century Furniture & Vinyl") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("post_title_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("What are you selling? *") },
                    placeholder = { Text("List highlights, condition, early-bird policy, cash/Venmo...") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("post_description_input")
                )

                // 3. Geolocation & Address Lookup
                Text(
                    text = "Sale Location & Map Pin",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            if (LocationHelper.hasLocationPermission(context)) {
                                fetchGpsAndFillAddress()
                            } else {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    )
                                )
                            }
                        },
                        enabled = !isResolvingLocation,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("use_gps_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isResolvingLocation) "Locating..." else "Use My GPS")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = addressQuery,
                        onValueChange = {
                            addressQuery = it
                            addressLabel = it
                        },
                        label = { Text("Street Address or Cross-Streets *") },
                        placeholder = { Text("e.g., 742 Maple Ave, San Francisco") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("post_address_input")
                    )
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                isResolvingLocation = true
                                val matches = LocationHelper.geocodeAddress(context, addressQuery)
                                if (matches.isNotEmpty()) {
                                    val first = matches.first()
                                    addressLabel = first.addressLabel
                                    addressQuery = first.addressLabel
                                    neighborhood = first.neighborhood
                                    latitude = first.latitude
                                    longitude = first.longitude
                                } else if (neighborhood.isBlank()) {
                                    neighborhood = "Local Neighborhood"
                                }
                                lastGeocodedQuery = addressQuery.trim()
                                locationPinned = true
                                validationError = null
                                isResolvingLocation = false
                            }
                        },
                        modifier = Modifier.height(54.dp)
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = "Pin address on map")
                    }
                }

                if (locationPinned || addressLabel.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "Map Pin at %.4f, %.4f%s",
                                    latitude,
                                    longitude,
                                    if (neighborhood.isNotBlank()) " · $neighborhood" else ""
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                // 4. Date, Hours & Price Range
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = dateLabel,
                        onValueChange = { dateLabel = it },
                        label = { Text("Date") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = hoursLabel,
                        onValueChange = { hoursLabel = it },
                        label = { Text("Hours") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = priceRangeLabel,
                    onValueChange = { priceRangeLabel = it },
                    label = { Text("Typical Price Range (optional)") },
                    placeholder = { Text("e.g., $1 - $60") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 5. Categories
                Text(
                    text = "Categories",
                    style = MaterialTheme.typography.titleMedium
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DEFAULT_SALE_CATEGORIES.forEach { cat ->
                        val selected = cat in selectedCategories
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (selected) selectedCategories.remove(cat)
                                else if (selectedCategories.size < 8) selectedCategories.add(cat)
                            },
                            label = { Text(cat) }
                        )
                    }
                }

                if (!validationError.isNullOrBlank()) {
                    Text(
                        text = validationError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Button(
                    onClick = {
                        val trimmedTitle = title.trim()
                        val trimmedDesc = description.trim()
                        val trimmedAddr = addressLabel.ifBlank { addressQuery }.trim()
                        when {
                            trimmedTitle.length < 3 -> validationError = "Please enter a headline (at least 3 characters)."
                            trimmedDesc.isBlank() -> validationError = "Please describe what items you are selling."
                            trimmedAddr.length < 3 -> validationError = "Please enter an address or tap Use My GPS."
                            else -> {
                                validationError = null
                                scope.launch {
                                    var finalAddr = trimmedAddr
                                    var finalHood = neighborhood
                                    var finalLat = latitude
                                    var finalLng = longitude

                                    // Auto-geocode typed address if the user didn't tap Search/GPS yet
                                    if (!locationPinned || trimmedAddr != lastGeocodedQuery) {
                                        isResolvingLocation = true
                                        val matches = LocationHelper.geocodeAddress(context, trimmedAddr)
                                        if (matches.isNotEmpty()) {
                                            val first = matches.first()
                                            finalAddr = first.addressLabel
                                            finalHood = first.neighborhood
                                            finalLat = first.latitude
                                            finalLng = first.longitude
                                            addressLabel = finalAddr
                                            addressQuery = finalAddr
                                            neighborhood = finalHood
                                            latitude = finalLat
                                            longitude = finalLng
                                            lastGeocodedQuery = finalAddr
                                        }
                                        locationPinned = true
                                        isResolvingLocation = false
                                    }

                                    onPublishSale(
                                        trimmedTitle,
                                        trimmedDesc,
                                        finalAddr,
                                        finalHood.ifBlank { "Local Neighborhood" },
                                        finalLat,
                                        finalLng,
                                        dateLabel.ifBlank { "This Weekend" },
                                        hoursLabel.ifBlank { "8:00 AM - 2:00 PM" },
                                        selectedCategories.toList(),
                                        priceRangeLabel,
                                        photoDataList.toList()
                                    )
                                }
                            }
                        }
                    },
                    enabled = !isSubmitting && !isProcessingPhotos && !isResolvingLocation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("publish_sale_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (isSubmitting || isResolvingLocation) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Publish Yard Sale",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
