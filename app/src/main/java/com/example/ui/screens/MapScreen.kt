package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.model.SavedSaleStop
import com.example.ui.components.SalePhotoImage
import com.example.ui.components.SellerRatingPill
import com.example.ui.components.launchTurnByTurnNavigation
import com.example.ui.map.OsmTileLoader
import com.example.ui.map.OsmTileMath
import com.example.ui.viewmodel.SaleWithDistance
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun MapScreen(
    salesState: UiState<List<SaleWithDistance>>,
    savedStops: List<SavedSaleStop>,
    userLocation: Pair<Double, Double>,
    currentUserId: String,
    focusedSaleId: String?,
    alertRadiusMiles: Int,
    onConsumeFocusedSale: () -> Unit,
    onRefreshLocation: () -> Unit,
    onOpenSaleDetail: (String) -> Unit
) {
    val context = LocalContext.current

    var centerLat by remember { mutableDoubleStateOf(userLocation.first) }
    var centerLng by remember { mutableDoubleStateOf(userLocation.second) }
    var zoom by remember { mutableIntStateOf(13) }
    var hasUserPanned by remember { mutableStateOf(false) }
    var selectedSaleItem by remember { mutableStateOf<SaleWithDistance?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val loadedTiles = remember { mutableStateMapOf<String, ImageBitmap>() }

    val salesList = remember(salesState) {
        (salesState as? UiState.Success)?.data.orEmpty()
    }
    val savedIds = remember(savedStops) { savedStops.map { it.saleId }.toSet() }
    val tileSizePx = 512f

    // Center on newly posted or focused yard sale when it arrives in the live snapshot
    LaunchedEffect(focusedSaleId, salesList) {
        if (!focusedSaleId.isNullOrBlank()) {
            val target = salesList.find { it.sale.id == focusedSaleId }
            if (target != null) {
                centerLat = target.sale.latitude
                centerLng = target.sale.longitude
                zoom = 14
                selectedSaleItem = target
                hasUserPanned = true
                onConsumeFocusedSale()
            }
        }
    }

    // Follow live GPS updates until the user manually pans or focuses a sale
    LaunchedEffect(userLocation) {
        if (!hasUserPanned && focusedSaleId.isNullOrBlank()) {
            centerLat = userLocation.first
            centerLng = userLocation.second
        }
    }

    // Keep selected preview card synced with live Firestore updates
    LaunchedEffect(salesList) {
        selectedSaleItem?.let { current ->
            selectedSaleItem = salesList.find { it.sale.id == current.sale.id }
        }
    }

    // Fetch missing OpenStreetMap tiles off the draw pass
    LaunchedEffect(centerLat, centerLng, zoom, canvasSize) {
        if (canvasSize.width <= 0 || canvasSize.height <= 0) return@LaunchedEffect
        val (centerTileX, centerTileY) = OsmTileMath.latLngToTileCoords(centerLat, centerLng, zoom)
        val minTileX = floor(centerTileX - (canvasSize.width / 2f) / tileSizePx).toInt()
        val maxTileX = ceil(centerTileX + (canvasSize.width / 2f) / tileSizePx).toInt()
        val minTileY = floor(centerTileY - (canvasSize.height / 2f) / tileSizePx).toInt()
        val maxTileY = ceil(centerTileY + (canvasSize.height / 2f) / tileSizePx).toInt()
        val maxIndex = 1 shl zoom

        for (ty in minTileY..maxTileY) {
            if (ty < 0 || ty >= maxIndex) continue
            for (tx in minTileX..maxTileX) {
                val wrappedX = ((tx % maxIndex) + maxIndex) % maxIndex
                val key = "$zoom/$wrappedX/$ty"
                if (loadedTiles[key] == null) {
                    val cached = OsmTileLoader.getCached(zoom, wrappedX, ty)
                    if (cached != null) {
                        loadedTiles[key] = cached
                    } else {
                        launch {
                            OsmTileLoader.fetchTile(zoom, wrappedX, ty)?.let { fetched ->
                                loadedTiles[key] = fetched
                            }
                        }
                    }
                }
            }
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .testTag("interactive_map_canvas")
                .pointerInput(zoom) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        hasUserPanned = true
                        val (cx, cy) = OsmTileMath.latLngToTileCoords(centerLat, centerLng, zoom)
                        val newTileX = cx - dragAmount.x / tileSizePx
                        val newTileY = cy - dragAmount.y / tileSizePx
                        val maxTile = 2.0.pow(zoom)
                        val (newLat, newLng) = OsmTileMath.tileCoordsToLatLng(
                            newTileX.coerceIn(0.0, maxTile),
                            newTileY.coerceIn(0.0, maxTile),
                            zoom
                        )
                        centerLat = newLat
                        centerLng = newLng
                    }
                }
                .pointerInput(Unit) {
                    var cumulativeZoom = 1f
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        hasUserPanned = true
                        val (cx, cy) = OsmTileMath.latLngToTileCoords(centerLat, centerLng, zoom)
                        val newTileX = cx - pan.x / tileSizePx
                        val newTileY = cy - pan.y / tileSizePx
                        val maxTile = 2.0.pow(zoom)
                        val (newLat, newLng) = OsmTileMath.tileCoordsToLatLng(
                            newTileX.coerceIn(0.0, maxTile),
                            newTileY.coerceIn(0.0, maxTile),
                            zoom
                        )
                        centerLat = newLat
                        centerLng = newLng

                        cumulativeZoom *= gestureZoom
                        if (cumulativeZoom > 1.35f && zoom < 17) {
                            zoom += 1
                            cumulativeZoom = 1f
                        } else if (cumulativeZoom < 0.72f && zoom > 9) {
                            zoom -= 1
                            cumulativeZoom = 1f
                        }
                    }
                }
                .pointerInput(salesList, zoom, centerLat, centerLng) {
                    detectTapGestures { tapOffset ->
                        val (centerTileX, centerTileY) = OsmTileMath.latLngToTileCoords(centerLat, centerLng, zoom)
                        val originX = size.width / 2f - (centerTileX * tileSizePx).toFloat()
                        val originY = size.height / 2f - (centerTileY * tileSizePx).toFloat()

                        var bestMatch: SaleWithDistance? = null
                        var bestDist = 78f
                        for (item in salesList) {
                            val (tx, ty) = OsmTileMath.latLngToTileCoords(
                                item.sale.latitude,
                                item.sale.longitude,
                                zoom
                            )
                            val px = originX + (tx * tileSizePx).toFloat()
                            val py = originY + (ty * tileSizePx).toFloat()
                            val d = hypot(tapOffset.x - px, tapOffset.y - py)
                            if (d < bestDist) {
                                bestDist = d
                                bestMatch = item
                            }
                        }
                        selectedSaleItem = bestMatch
                    }
                }
        ) {
            drawRect(color = surfaceColor)

            val (centerTileX, centerTileY) = OsmTileMath.latLngToTileCoords(centerLat, centerLng, zoom)
            val originX = size.width / 2f - (centerTileX * tileSizePx).toFloat()
            val originY = size.height / 2f - (centerTileY * tileSizePx).toFloat()

            val minTileX = floor((centerTileX - (size.width / 2f) / tileSizePx)).toInt()
            val maxTileX = ceil((centerTileX + (size.width / 2f) / tileSizePx)).toInt()
            val minTileY = floor((centerTileY - (size.height / 2f) / tileSizePx)).toInt()
            val maxTileY = ceil((centerTileY + (size.height / 2f) / tileSizePx)).toInt()

            val maxIndex = 1 shl zoom
            for (ty in minTileY..maxTileY) {
                if (ty < 0 || ty >= maxIndex) continue
                for (tx in minTileX..maxTileX) {
                    val wrappedX = ((tx % maxIndex) + maxIndex) % maxIndex
                    val key = "$zoom/$wrappedX/$ty"
                    val cached = loadedTiles[key] ?: OsmTileLoader.getCached(zoom, wrappedX, ty)

                    val left = (originX + tx * tileSizePx).roundToInt()
                    val top = (originY + ty * tileSizePx).roundToInt()
                    val tilePxInt = tileSizePx.roundToInt()

                    if (cached != null) {
                        drawImage(
                            image = cached,
                            dstOffset = IntOffset(left, top),
                            dstSize = IntSize(tilePxInt + 1, tilePxInt + 1)
                        )
                    } else {
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.16f),
                            start = Offset(left.toFloat(), top.toFloat()),
                            end = Offset((left + tilePxInt).toFloat(), top.toFloat()),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.16f),
                            start = Offset(left.toFloat(), top.toFloat()),
                            end = Offset(left.toFloat(), (top + tilePxInt).toFloat()),
                            strokeWidth = 1f
                        )
                    }
                }
            }

            // Draw alert radius circle around user's location
            val (userTx, userTy) = OsmTileMath.latLngToTileCoords(userLocation.first, userLocation.second, zoom)
            val userPx = Offset(
                originX + (userTx * tileSizePx).toFloat(),
                originY + (userTy * tileSizePx).toFloat()
            )
            val mpp = OsmTileMath.metersPerPixel(userLocation.first, zoom, tileSizePx)
            val radiusMeters = alertRadiusMiles * 1609.34
            val radiusPx = (radiusMeters / mpp).toFloat()

            if (radiusPx in 20f..4000f) {
                drawCircle(
                    color = secondaryColor.copy(alpha = 0.08f),
                    radius = radiusPx,
                    center = userPx
                )
                drawCircle(
                    color = secondaryColor.copy(alpha = 0.45f),
                    radius = radiusPx,
                    center = userPx,
                    style = Stroke(
                        width = 3f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f))
                    )
                )
            }

            // Draw User GPS Marker
            drawCircle(color = Color.White, radius = 20f, center = userPx)
            drawCircle(color = Color(0xFF2563EB), radius = 13f, center = userPx)

            // Draw Yard Sale Pins (owner pins drawn last so they appear clearly on top)
            val orderedSales = salesList.sortedBy { it.sale.sellerId == currentUserId }
            for (item in orderedSales) {
                val (tx, ty) = OsmTileMath.latLngToTileCoords(item.sale.latitude, item.sale.longitude, zoom)
                val pinCenter = Offset(
                    originX + (tx * tileSizePx).toFloat(),
                    originY + (ty * tileSizePx).toFloat()
                )
                val isSelected = selectedSaleItem?.sale?.id == item.sale.id
                val isMine = item.sale.sellerId == currentUserId
                val isSaved = item.sale.id in savedIds
                val pinColor = when {
                    !item.sale.isActive -> Color(0xFF8C8278)
                    isMine -> primaryColor
                    isSaved -> tertiaryColor
                    else -> secondaryColor
                }
                val outerR = if (isSelected) 34f else if (isMine) 26f else 22f
                val innerR = if (isSelected) 25f else if (isMine) 19f else 15f

                if (isMine || isSelected) {
                    drawCircle(
                        color = pinColor.copy(alpha = 0.22f),
                        radius = outerR + 12f,
                        center = pinCenter
                    )
                }
                drawCircle(
                    color = Color.Black.copy(alpha = 0.25f),
                    radius = outerR + 3f,
                    center = pinCenter.copy(y = pinCenter.y + 4f)
                )
                drawCircle(color = Color.White, radius = outerR, center = pinCenter)
                drawCircle(color = pinColor, radius = innerR, center = pinCenter)
                if (isMine) {
                    drawCircle(color = Color.White, radius = 6f, center = pinCenter)
                }
            }
        }

        // Top Floating Status Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val myLatestSale = salesList.firstOrNull { it.sale.sellerId == currentUserId }
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shadowElevation = 4.dp,
                modifier = if (myLatestSale != null) {
                    Modifier.clickable {
                        centerLat = myLatestSale.sale.latitude
                        centerLng = myLatestSale.sale.longitude
                        zoom = 14
                        selectedSaleItem = myLatestSale
                        hasUserPanned = true
                    }
                } else {
                    Modifier
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Storefront,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (myLatestSale != null) {
                            "${salesList.size} sales on map · Tap to view yours"
                        } else {
                            "${salesList.size} sales on map · ${alertRadiusMiles} mi ring"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
            ) {
                Text(
                    text = "© OpenStreetMap",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Right-side Map Controls (Zoom & Recenter)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { if (zoom < 17) zoom += 1 },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Zoom in")
            }
            SmallFloatingActionButton(
                onClick = { if (zoom > 9) zoom -= 1 },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Zoom out")
            }
            SmallFloatingActionButton(
                onClick = {
                    onRefreshLocation()
                    centerLat = userLocation.first
                    centerLng = userLocation.second
                    zoom = 14
                    hasUserPanned = false
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("map_recenter_button")
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = "Center on my location")
            }
        }

        // Floating Bottom Selected Sale Preview Card
        AnimatedVisibility(
            visible = selectedSaleItem != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 96.dp)
        ) {
            selectedSaleItem?.let { item ->
                val sale = item.sale
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSaleDetail(sale.id) }
                        .testTag("map_preview_card"),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SalePhotoImage(
                                photoData = sale.photoDataList.firstOrNull(),
                                contentDescription = sale.title,
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = sale.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "%.1f mi · %s",
                                        item.distanceMiles,
                                        sale.addressLabel
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = sale.hoursLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    SellerRatingPill(profile = item.sellerProfile)
                                }
                            }
                            IconButton(
                                onClick = { selectedSaleItem = null },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Close preview")
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { onOpenSaleDetail(sale.id) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text("View Details")
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
                                    .testTag("map_navigate_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Directions,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Navigate")
                            }
                        }
                    }
                }
            }
        }
    }
}
