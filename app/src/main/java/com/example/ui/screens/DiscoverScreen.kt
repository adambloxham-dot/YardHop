package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.DEFAULT_SALE_CATEGORIES
import com.example.data.model.NearbyAlertItem
import com.example.data.model.SavedSaleStop
import com.example.data.model.YardSale
import com.example.ui.components.YardSaleCard
import com.example.ui.viewmodel.SaleWithDistance
import com.example.ui.viewmodel.UiState
import java.util.Locale

@Composable
fun DiscoverScreen(
    salesState: UiState<List<SaleWithDistance>>,
    savedStops: List<SavedSaleStop>,
    searchQuery: String,
    selectedCategory: String?,
    maxDistanceMiles: Int,
    hasLiveGps: Boolean,
    nearbyAlerts: List<NearbyAlertItem>,
    onSearchChange: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onDistanceChange: (Int) -> Unit,
    onRefreshLocation: () -> Unit,
    onOpenAlertsSheet: () -> Unit,
    onOpenTreasureFinder: () -> Unit,
    onOpenSaleDetail: (String) -> Unit,
    onToggleSave: (YardSale) -> Unit,
    onSellerClick: (String) -> Unit,
    onPostSaleClick: () -> Unit
) {
    val savedIds = savedStops.map { it.saleId }.toSet()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
        ) {
            // Top Editorial Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "YardHop",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Row(
                        modifier = Modifier
                            .clickable(onClick = onRefreshLocation)
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MyLocation,
                            contentDescription = "Refresh GPS location",
                            tint = if (hasLiveGps) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (hasLiveGps) "Live GPS active · Within ${if (maxDistanceMiles >= 100) "All" else "$maxDistanceMiles mi"}"
                            else "Tap to use GPS location",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onOpenAlertsSheet,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("nearby_alerts_button")
                ) {
                    BadgedBox(
                        badge = {
                            if (nearbyAlerts.isNotEmpty()) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(text = nearbyAlerts.size.coerceAtMost(9).toString())
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = "Nearby sale alerts",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            // Live Nearby Alert Strip
            AnimatedVisibility(visible = nearbyAlerts.isNotEmpty()) {
                val latest = nearbyAlerts.first()
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .clickable { onOpenSaleDetail(latest.saleId) }
                        .testTag("live_alert_banner"),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Nearby: ${latest.title}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "%.1f mi away · %s",
                                    latest.distanceMiles,
                                    latest.addressLabel
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        TextButton(onClick = onOpenAlertsSheet) {
                            Text("All")
                        }
                    }
                }
            }

            // Search Field + Gemini AI Treasure Finder Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Search vinyl, furniture...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("discover_search_input")
                )
                FilledTonalButton(
                    onClick = onOpenTreasureFinder,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("ai_treasure_finder_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = "AI Treasure Finder",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("AI Match")
                }
            }

            // Radius & Category Filter Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val radiusOptions = listOf(5 to "5 mi", 10 to "10 mi", 25 to "25 mi", 100 to "Any dist")
                radiusOptions.forEach { (miles, label) ->
                    FilterChip(
                        selected = maxDistanceMiles == miles,
                        onClick = { onDistanceChange(miles) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
                DEFAULT_SALE_CATEGORIES.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { onCategorySelected(cat) },
                        label = { Text(cat) }
                    )
                }
            }

            // Sales Feed
            when (salesState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = salesState.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is UiState.Success -> {
                    val sales = salesState.data
                    if (sales.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_empty_sales),
                                contentDescription = "No yard sales found nearby",
                                modifier = Modifier.size(180.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No sales in this radius yet",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Try expanding your distance filter or be the first neighbor to list a weekend sale.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onPostSaleClick,
                                modifier = Modifier.testTag("empty_post_sale_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("Post a Yard Sale")
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 20.dp,
                                end = 20.dp,
                                top = 8.dp,
                                bottom = 104.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(sales, key = { it.sale.id }) { item ->
                                YardSaleCard(
                                    item = item,
                                    isSaved = item.sale.id in savedIds,
                                    onCardClick = { onOpenSaleDetail(item.sale.id) },
                                    onToggleSave = { onToggleSave(item.sale) },
                                    onSellerClick = { onSellerClick(item.sale.sellerId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
