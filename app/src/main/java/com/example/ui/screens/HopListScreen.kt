package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.SavedSaleStop
import com.example.data.model.YardSale
import com.example.data.model.haversineMiles
import com.example.ui.components.launchTurnByTurnNavigation
import java.util.Locale

@Composable
fun HopListScreen(
    savedStops: List<SavedSaleStop>,
    allSales: List<YardSale>,
    userLocation: Pair<Double, Double>,
    onToggleVisited: (String, Boolean) -> Unit,
    onRemoveStop: (YardSale) -> Unit,
    onOpenSaleDetail: (String) -> Unit
) {
    val context = LocalContext.current
    val salesById = allSales.associateBy { it.id }
    val visitedCount = savedStops.count { it.visited }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Weekend Hop List",
                    style = MaterialTheme.typography.headlineLarge
                )
                Text(
                    text = if (savedStops.isEmpty()) "Save sales to build your custom garage-sale route."
                    else "$visitedCount of ${savedStops.size} stops visited",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (savedStops.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_empty_sales),
                        contentDescription = "Empty Hop List illustration",
                        modifier = Modifier.size(180.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Your Hop List is empty",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap the bookmark icon on any yard sale in Discover or Map to add it to your route.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val nextUnvisited = savedStops.firstOrNull { !it.visited }
                if (nextUnvisited != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "NEXT STOP",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = nextUnvisited.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Button(
                                onClick = {
                                    launchTurnByTurnNavigation(
                                        context,
                                        nextUnvisited.latitude,
                                        nextUnvisited.longitude,
                                        nextUnvisited.title
                                    )
                                },
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("hop_next_stop_navigate")
                            ) {
                                Icon(Icons.Filled.Directions, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Go")
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 10.dp,
                        bottom = 104.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(savedStops, key = { it.saleId }) { stop ->
                        val dist = haversineMiles(
                            userLocation.first,
                            userLocation.second,
                            stop.latitude,
                            stop.longitude
                        )
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenSaleDetail(stop.saleId) },
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onToggleVisited(stop.saleId, !stop.visited) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = if (stop.visited) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                        contentDescription = if (stop.visited) "Mark unvisited" else "Mark visited",
                                        tint = if (stop.visited) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = stop.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        textDecoration = if (stop.visited) TextDecoration.LineThrough else TextDecoration.None
                                    )
                                    Text(
                                        text = String.format(
                                            Locale.US,
                                            "%.1f mi · %s",
                                            dist,
                                            stop.addressLabel
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        launchTurnByTurnNavigation(
                                            context,
                                            stop.latitude,
                                            stop.longitude,
                                            stop.title
                                        )
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Directions,
                                        contentDescription = "Navigate to ${stop.title}",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val saleObj = salesById[stop.saleId] ?: YardSale(
                                            id = stop.saleId,
                                            title = stop.title,
                                            addressLabel = stop.addressLabel,
                                            latitude = stop.latitude,
                                            longitude = stop.longitude
                                        )
                                        onRemoveStop(saleObj)
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = "Remove stop"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
