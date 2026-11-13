package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.AiStreamUiState
import com.example.data.model.SellerProfile
import com.example.data.model.YardSale
import com.example.ui.components.AiSaleScoutCard
import com.example.ui.components.SalePhotoImage
import com.example.ui.components.SellerRatingPill
import com.example.ui.components.launchTurnByTurnNavigation
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun SaleDetailScreen(
    sale: YardSale,
    sellerProfile: SellerProfile?,
    distanceMiles: Double,
    currentUserId: String,
    isSaved: Boolean,
    scoutState: AiStreamUiState,
    onAskScout: (String) -> Unit,
    onViewOnMap: () -> Unit,
    onBack: () -> Unit,
    onToggleSave: () -> Unit,
    onViewSeller: (String) -> Unit,
    onEndSale: (String) -> Unit,
    onDeleteSale: (String) -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val photos = if (sale.photoDataList.isEmpty()) listOf<String?>(null) else sale.photoDataList
    val pagerState = rememberPagerState(pageCount = { photos.size })
    val isOwner = sale.sellerId == currentUserId

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            // Photo Gallery Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    SalePhotoImage(
                        photoData = photos[page],
                        contentDescription = "${sale.title} photo ${page + 1}",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                            .testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }

                    IconButton(
                        onClick = onToggleSave,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                            .testTag("detail_bookmark_button")
                    ) {
                        Icon(
                            imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = if (isSaved) "Remove from Hop List" else "Save to Hop List",
                            tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (photos.size > 1) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        repeat(photos.size) { idx ->
                            Box(
                                modifier = Modifier
                                    .size(if (pagerState.currentPage == idx) 10.dp else 7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (pagerState.currentPage == idx) Color.White
                                        else Color.White.copy(alpha = 0.5f)
                                    )
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (sale.isActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (sale.isActive) "OPEN NOW" else "ENDED",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (sale.isActive) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                    if (sale.priceRangeLabel.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = sale.priceRangeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                    Text(
                        text = String.format(Locale.US, "%.1f mi away", distanceMiles),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = sale.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Location & Hours Info Card
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = sale.addressLabel,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                if (sale.neighborhood.isNotBlank()) {
                                    Text(
                                        text = sale.neighborhood,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.EventAvailable,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "${sale.dateLabel} · ${sale.hoursLabel}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onViewOnMap,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("detail_view_on_map_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Show on Map")
                            }
                            Button(
                                onClick = {
                                    launchTurnByTurnNavigation(
                                        context,
                                        sale.latitude,
                                        sale.longitude,
                                        sale.title
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("detail_navigate_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Navigate")
                            }
                        }
                    }
                }

                // Description & Tags
                Text(
                    text = "About This Sale",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = sale.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (sale.categories.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sale.categories.forEach { cat ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = cat,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Gemini Streaming Multimodal Sale Scout
                AiSaleScoutCard(
                    scoutState = scoutState,
                    onAskScout = onAskScout
                )

                // Seller Community Trust Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onViewSeller(sale.sellerId) }
                        .testTag("detail_seller_card"),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                modifier = Modifier.size(44.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = sale.sellerName.firstOrNull()?.uppercase() ?: "S",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = sellerProfile?.displayName ?: sale.sellerName,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Tap to view profile & ratings",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            SellerRatingPill(profile = sellerProfile)
                            if (!isOwner) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Rate seller",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                if (isOwner) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (sale.isActive) {
                            FilledTonalButton(
                                onClick = { onEndSale(sale.id) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("end_sale_button")
                            ) {
                                Icon(Icons.Filled.StopCircle, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("End Sale")
                            }
                        }
                        OutlinedButton(
                            onClick = { onDeleteSale(sale.id) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("delete_sale_button")
                        ) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
