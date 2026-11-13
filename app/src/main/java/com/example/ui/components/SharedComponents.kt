package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.SellerProfile
import com.example.ui.viewmodel.SaleWithDistance
import com.example.util.PhotoHelper
import java.util.Locale

@Composable
fun SalePhotoImage(
    photoData: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    when {
        photoData.isNullOrBlank() -> {
            Image(
                painter = painterResource(id = R.drawable.img_sale_placeholder),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale
            )
        }
        photoData.startsWith("data:image") -> {
            val bitmap = remember(photoData) { PhotoHelper.decodeDataUriToImageBitmap(photoData) }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = contentDescription,
                    modifier = modifier,
                    contentScale = contentScale
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.img_sale_placeholder),
                    contentDescription = contentDescription,
                    modifier = modifier,
                    contentScale = contentScale
                )
            }
        }
        else -> {
            AsyncImage(
                model = photoData,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                placeholder = painterResource(id = R.drawable.img_sale_placeholder),
                error = painterResource(id = R.drawable.img_sale_placeholder)
            )
        }
    }
}

@Composable
fun SellerRatingPill(
    profile: SellerProfile?,
    modifier: Modifier = Modifier
) {
    val count = profile?.ratingCount ?: 0L
    val ratingText = if (count > 0L) {
        String.format(Locale.US, "%.1f (%d)", profile!!.averageRating, count)
    } else {
        "New seller"
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = ratingText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YardSaleCard(
    item: SaleWithDistance,
    isSaved: Boolean,
    onCardClick: () -> Unit,
    onToggleSave: () -> Unit,
    onSellerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sale = item.sale
    val distLabel = if (item.distanceMiles < 0.1) {
        "< 0.1 mi"
    } else {
        String.format(Locale.US, "%.1f mi", item.distanceMiles)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sale_card_${sale.id}")
            .clickable(onClick = onCardClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
            ) {
                SalePhotoImage(
                    photoData = sale.photoDataList.firstOrNull(),
                    contentDescription = sale.title,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.12f), Color.Transparent, Color.Black.copy(alpha = 0.45f))
                            )
                        )
                )

                // Top-left Status & Distance Badges
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (sale.isActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (sale.isActive) "OPEN" else "ENDED",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (sale.isActive) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    ) {
                        Text(
                            text = distLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Top-right Bookmark button
                IconButton(
                    onClick = onToggleSave,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                        .testTag("bookmark_sale_${sale.id}")
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (isSaved) "Remove from Hop List" else "Save to Hop List",
                        tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                // Bottom-left Price Tag Badge
                if (sale.priceRangeLabel.isNotBlank()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = sale.priceRangeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                // Photo count indicator
                if (sale.photoDataList.size > 1) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "${sale.photoDataList.size} photos",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = sale.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = sale.addressLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${sale.dateLabel} · ${sale.hoursLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (sale.categories.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        sale.categories.take(3).forEach { cat ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(
                                        0.5.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = cat,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onSellerClick)
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(24.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = sale.sellerName.firstOrNull()?.uppercase() ?: "S",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = sale.sellerName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    SellerRatingPill(
                        profile = item.sellerProfile,
                        modifier = Modifier.clickable(onClick = onSellerClick)
                    )
                }
            }
        }
    }
}

fun launchTurnByTurnNavigation(context: Context, lat: Double, lng: Double, label: String) {
    val encodedLabel = Uri.encode(label)
    val navUri = Uri.parse("google.navigation:q=$lat,$lng")
    val navIntent = Intent(Intent.ACTION_VIEW, navUri)
    if (navIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(navIntent)
        return
    }
    val geoUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($encodedLabel)")
    val geoIntent = Intent(Intent.ACTION_VIEW, geoUri)
    if (geoIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(geoIntent)
        return
    }
    val webUri = Uri.parse("https://www.openstreetmap.org/?mlat=$lat&mlon=$lng#map=16/$lat/$lng")
    context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
}
