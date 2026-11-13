package com.example

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.YardHopAiViewModel
import com.example.ai.configureAppCheck
import com.example.auth.attemptAutoSignIn
import com.example.auth.authStateFlow
import com.example.data.model.haversineMiles
import com.example.data.repository.YardHopRepository
import com.example.ui.components.AiTreasureFinderSheet
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.HopListScreen
import com.example.ui.screens.MapScreen
import com.example.ui.screens.NearbyAlertsSheet
import com.example.ui.screens.PostSaleScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SaleDetailScreen
import com.example.ui.screens.SellerTrustSheet
import com.example.ui.theme.YardHopTheme
import com.example.ui.viewmodel.UiState
import com.example.ui.viewmodel.YardHopViewModel
import com.example.util.LocationHelper
import com.example.util.NearbyNotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

enum class YardHopTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    Discover("Discover", Icons.Filled.Explore, Icons.Outlined.Explore),
    Map("Map", Icons.Filled.Map, Icons.Outlined.Map),
    HopList("Hop List", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder),
    Profile("Profile", Icons.Filled.Person, Icons.Outlined.Person)
}

class MainActivity : ComponentActivity() {

    private var deepLinkSaleId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configure App Check with launch intent before any UI, ViewModels, or Firebase operations
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        NearbyNotificationHelper.ensureChannel(this)
        deepLinkSaleId = intent?.getStringExtra(NearbyNotificationHelper.EXTRA_SALE_ID)

        setContent {
            YardHopTheme {
                YardHopRoot(
                    deepLinkSaleId = deepLinkSaleId,
                    onConsumeDeepLink = { deepLinkSaleId = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(NearbyNotificationHelper.EXTRA_SALE_ID)?.let { saleId ->
            deepLinkSaleId = saleId
        }
    }
}

@Composable
fun YardHopRoot(
    deepLinkSaleId: String?,
    onConsumeDeepLink: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = CredentialManager.create(context),
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedYardHopApp(
                user = user,
                deepLinkSaleId = deepLinkSaleId,
                onConsumeDeepLink = onConsumeDeepLink
            )
        }
    }
}

@Composable
private fun AuthenticatedYardHopApp(
    user: FirebaseUser,
    deepLinkSaleId: String?,
    onConsumeDeepLink: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val viewModel: YardHopViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                YardHopViewModel(
                    repository = YardHopRepository(db),
                    currentUserId = user.uid,
                    initialDisplayName = user.displayName ?: "Neighbor",
                    initialEmail = user.email,
                    initialPhotoUrl = user.photoUrl?.toString()
                )
            }
        }
    )

    val aiViewModel: YardHopAiViewModel = viewModel(key = "ai_${user.uid}")

    val filteredSalesState by viewModel.filteredSales.collectAsStateWithLifecycle()
    val mapSalesState by viewModel.mapSalesState.collectAsStateWithLifecycle()
    val publicSalesState by viewModel.publicSalesState.collectAsStateWithLifecycle()
    val mySalesState by viewModel.mySalesState.collectAsStateWithLifecycle()
    val savedStops by viewModel.savedStops.collectAsStateWithLifecycle()
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
    val mySellerProfile by viewModel.mySellerProfile.collectAsStateWithLifecycle()
    val publicSellersMap by viewModel.publicSellersMap.collectAsStateWithLifecycle()
    val userLocation by viewModel.userLocation.collectAsStateWithLifecycle()
    val hasLiveGps by viewModel.hasLiveGps.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val maxDistanceMiles by viewModel.maxDistanceMiles.collectAsStateWithLifecycle()
    val nearbyAlerts by viewModel.nearbyAlerts.collectAsStateWithLifecycle()
    val selectedSaleId by viewModel.selectedSaleId.collectAsStateWithLifecycle()
    val focusedMapSaleId by viewModel.focusedMapSaleId.collectAsStateWithLifecycle()
    val inspectedSellerId by viewModel.inspectedSellerId.collectAsStateWithLifecycle()
    val inspectedSellerProfile by viewModel.inspectedSellerProfile.collectAsStateWithLifecycle()
    val inspectedSellerReviews by viewModel.inspectedSellerReviews.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()

    val aiDraftState by aiViewModel.draftState.collectAsStateWithLifecycle()
    val aiScoutState by aiViewModel.scoutState.collectAsStateWithLifecycle()
    val aiTreasureMatchState by aiViewModel.treasureMatchState.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(YardHopTab.Discover) }
    var showPostSale by remember { mutableStateOf(false) }
    var showAlertsSheet by remember { mutableStateOf(false) }
    var showTreasureFinderSheet by remember { mutableStateOf(false) }

    fun refreshLocationNow() {
        scope.launch {
            LocationHelper.getCurrentLocation(context)?.let { (lat, lng) ->
                viewModel.updateUserLocation(lat, lng, fromGps = true)
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            refreshLocationNow()
        }
    }

    LaunchedEffect(Unit) {
        if (LocationHelper.hasLocationPermission(context)) {
            refreshLocationNow()
        }
    }

    LaunchedEffect(deepLinkSaleId) {
        if (!deepLinkSaleId.isNullOrBlank()) {
            viewModel.openSaleDetail(deepLinkSaleId)
            onConsumeDeepLink()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.snackbarMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.newAlertEvents.collect { alert ->
            NearbyNotificationHelper.postNearbySaleNotification(
                context = context,
                saleId = alert.saleId,
                title = alert.title,
                addressLabel = alert.addressLabel,
                distanceMiles = alert.distanceMiles
            )
            snackbarHostState.showSnackbar("New yard sale nearby: ${alert.title}")
        }
    }

    val allPublicSales = (publicSalesState as? UiState.Success)?.data.orEmpty()
    val activeDetailSale = selectedSaleId?.let { id -> allPublicSales.find { it.id == id } }

    when {
        showPostSale -> {
            PostSaleScreen(
                initialLocation = userLocation,
                isSubmitting = isSubmitting,
                draftState = aiDraftState,
                onAutoDraftWithAi = aiViewModel::draftListingFromPhotosAndNotes,
                onBack = {
                    aiViewModel.resetDraftState()
                    showPostSale = false
                },
                onUpdateUserLocation = { lat, lng -> viewModel.updateUserLocation(lat, lng, fromGps = true) },
                onPublishSale = { title, desc, addr, hood, lat, lng, date, hours, cats, price, photos ->
                    viewModel.createSale(
                        title = title,
                        description = desc,
                        addressLabel = addr,
                        neighborhood = hood,
                        latitude = lat,
                        longitude = lng,
                        dateLabel = date,
                        hoursLabel = hours,
                        categories = cats,
                        priceRangeLabel = price,
                        photoDataList = photos,
                        onSuccess = { newId ->
                            aiViewModel.resetDraftState()
                            showPostSale = false
                            viewModel.focusSaleOnMap(newId)
                            currentTab = YardHopTab.Map
                            viewModel.openSaleDetail(newId)
                        }
                    )
                }
            )
        }

        activeDetailSale != null -> {
            val isSaved = savedStops.any { it.saleId == activeDetailSale.id }
            val dist = haversineMiles(
                userLocation.first,
                userLocation.second,
                activeDetailSale.latitude,
                activeDetailSale.longitude
            )
            LaunchedEffect(activeDetailSale.id) {
                aiViewModel.ensureScoutContext(activeDetailSale.id)
            }
            SaleDetailScreen(
                sale = activeDetailSale,
                sellerProfile = publicSellersMap[activeDetailSale.sellerId],
                distanceMiles = dist,
                currentUserId = user.uid,
                isSaved = isSaved,
                scoutState = aiScoutState,
                onAskScout = { question ->
                    aiViewModel.askSaleScout(activeDetailSale, dist, question)
                },
                onViewOnMap = {
                    viewModel.focusSaleOnMap(activeDetailSale.id)
                    viewModel.openSaleDetail(null)
                    currentTab = YardHopTab.Map
                },
                onBack = { viewModel.openSaleDetail(null) },
                onToggleSave = { viewModel.toggleSavedStop(activeDetailSale) },
                onViewSeller = { sellerId -> viewModel.openSellerProfile(sellerId) },
                onEndSale = { viewModel.endSale(it) },
                onDeleteSale = { viewModel.deleteSale(it) }
            )
        }

        else -> {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavRail = maxWidth >= 840.dp

                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    floatingActionButton = {
                        if (currentTab != YardHopTab.Profile) {
                            ExtendedFloatingActionButton(
                                onClick = { showPostSale = true },
                                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                text = { Text("Post Sale") },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.testTag("fab_post_sale")
                            )
                        }
                    },
                    bottomBar = {
                        if (!useNavRail) {
                            NavigationBar(
                                windowInsets = WindowInsets.navigationBars,
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                YardHopTab.entries.forEach { tab ->
                                    val selected = currentTab == tab
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { currentTab = tab },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        },
                                        label = { Text(tab.label) },
                                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (useNavRail) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                YardHopTab.entries.forEach { tab ->
                                    val selected = currentTab == tab
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { currentTab = tab },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        },
                                        label = { Text(tab.label) }
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            when (currentTab) {
                                YardHopTab.Discover -> DiscoverScreen(
                                    salesState = filteredSalesState,
                                    savedStops = savedStops,
                                    searchQuery = searchQuery,
                                    selectedCategory = selectedCategory,
                                    maxDistanceMiles = maxDistanceMiles,
                                    hasLiveGps = hasLiveGps,
                                    nearbyAlerts = nearbyAlerts,
                                    onSearchChange = viewModel::setSearchQuery,
                                    onCategorySelected = viewModel::selectCategory,
                                    onDistanceChange = viewModel::setMaxDistanceMiles,
                                    onRefreshLocation = {
                                        if (LocationHelper.hasLocationPermission(context)) {
                                            refreshLocationNow()
                                        } else {
                                            locationPermissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                                    Manifest.permission.ACCESS_FINE_LOCATION
                                                )
                                            )
                                        }
                                    },
                                    onOpenAlertsSheet = { showAlertsSheet = true },
                                    onOpenTreasureFinder = {
                                        aiViewModel.resetTreasureMatchState()
                                        showTreasureFinderSheet = true
                                    },
                                    onOpenSaleDetail = { viewModel.openSaleDetail(it) },
                                    onToggleSave = viewModel::toggleSavedStop,
                                    onSellerClick = { viewModel.openSellerProfile(it) },
                                    onPostSaleClick = {
                                        aiViewModel.resetDraftState()
                                        showPostSale = true
                                    }
                                )

                                YardHopTab.Map -> MapScreen(
                                    salesState = mapSalesState,
                                    savedStops = savedStops,
                                    userLocation = userLocation,
                                    currentUserId = user.uid,
                                    focusedSaleId = focusedMapSaleId,
                                    alertRadiusMiles = (userSettings?.alertRadiusMiles ?: 10L).toInt(),
                                    onConsumeFocusedSale = viewModel::consumeFocusedMapSale,
                                    onRefreshLocation = {
                                        if (LocationHelper.hasLocationPermission(context)) {
                                            refreshLocationNow()
                                        } else {
                                            locationPermissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                                    Manifest.permission.ACCESS_FINE_LOCATION
                                                )
                                            )
                                        }
                                    },
                                    onOpenSaleDetail = { viewModel.openSaleDetail(it) }
                                )

                                YardHopTab.HopList -> HopListScreen(
                                    savedStops = savedStops,
                                    allSales = allPublicSales,
                                    userLocation = userLocation,
                                    onToggleVisited = viewModel::toggleStopVisited,
                                    onRemoveStop = viewModel::toggleSavedStop,
                                    onOpenSaleDetail = { viewModel.openSaleDetail(it) }
                                )

                                YardHopTab.Profile -> ProfileScreen(
                                    currentUserEmail = user.email,
                                    sellerProfile = mySellerProfile,
                                    userSettings = userSettings,
                                    mySalesState = mySalesState,
                                    isSubmitting = isSubmitting,
                                    onUpdateSellerProfile = viewModel::updateSellerProfile,
                                    onUpdateAlertPreferences = viewModel::updateAlertPreferences,
                                    onOpenSaleDetail = { viewModel.openSaleDetail(it) },
                                    onViewMyReviews = { viewModel.openSellerProfile(user.uid) },
                                    onPostSaleClick = { showPostSale = true }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (inspectedSellerId != null && inspectedSellerProfile != null) {
        SellerTrustSheet(
            sellerProfile = inspectedSellerProfile,
            reviews = inspectedSellerReviews,
            currentUserId = user.uid,
            isSubmitting = isSubmitting,
            onDismiss = { viewModel.openSellerProfile(null) },
            onSubmitReview = { rating, comment ->
                viewModel.submitSellerReview(
                    sellerId = inspectedSellerId!!,
                    rating = rating,
                    comment = comment,
                    onComplete = {}
                )
            }
        )
    }

    if (showAlertsSheet) {
        NearbyAlertsSheet(
            alerts = nearbyAlerts,
            onDismiss = { showAlertsSheet = false },
            onSelectAlert = { saleId ->
                showAlertsSheet = false
                viewModel.openSaleDetail(saleId)
            }
        )
    }

    if (showTreasureFinderSheet) {
        val candidateSales = (mapSalesState as? UiState.Success)?.data.orEmpty()
        AiTreasureFinderSheet(
            matchState = aiTreasureMatchState,
            onDismiss = { showTreasureFinderSheet = false },
            onFindMatches = { wish ->
                aiViewModel.findTreasureMatches(wish, candidateSales)
            },
            onSelectSale = { saleId ->
                showTreasureFinderSheet = false
                viewModel.openSaleDetail(saleId)
            }
        )
    }
}
