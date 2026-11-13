package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.auth.signOutUser
import com.example.data.model.NearbyAlertItem
import com.example.data.model.SellerProfile
import com.example.data.model.SellerReview
import com.example.data.model.UserSettings
import com.example.data.model.YardSale
import com.example.ui.components.SellerRatingPill
import com.example.ui.viewmodel.UiState
import com.example.util.NearbyNotificationHelper
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    currentUserEmail: String?,
    sellerProfile: SellerProfile?,
    userSettings: UserSettings?,
    mySalesState: UiState<List<YardSale>>,
    isSubmitting: Boolean,
    onUpdateSellerProfile: (displayName: String, neighborhood: String, bio: String) -> Unit,
    onUpdateAlertPreferences: (enabled: Boolean, radiusMiles: Int) -> Unit,
    onOpenSaleDetail: (String) -> Unit,
    onViewMyReviews: () -> Unit,
    onPostSaleClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var showEditDialog by remember { mutableStateOf(false) }

    val alertsEnabled = userSettings?.alertsEnabled ?: true
    val alertRadius = (userSettings?.alertRadiusMiles ?: 10L).toInt()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onUpdateAlertPreferences(granted, alertRadius)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Seller Profile",
                    style = MaterialTheme.typography.headlineLarge
                )
                IconButton(
                    onClick = { showEditDialog = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("edit_profile_button")
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit seller profile")
                }
            }

            // Seller Trust Card
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = sellerProfile?.displayName?.firstOrNull()?.uppercase() ?: "Y",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sellerProfile?.displayName ?: "Neighbor",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = sellerProfile?.neighborhood?.ifBlank { "Local Neighborhood" }
                                    ?: "Local Neighborhood",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        SellerRatingPill(
                            profile = sellerProfile,
                            modifier = Modifier.clickable(onClick = onViewMyReviews)
                        )
                    }

                    Text(
                        text = sellerProfile?.bio?.ifBlank { "Friendly neighborhood seller on YardHop." }
                            ?: "Friendly neighborhood seller on YardHop.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onViewMyReviews) {
                            Text("View community reviews (${sellerProfile?.ratingCount ?: 0})")
                        }
                        FilledTonalButton(onClick = { showEditDialog = true }) {
                            Text("Edit Profile")
                        }
                    }
                }
            }

            // Sign-In Accounts & Cross-Platform Readiness Card
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Connected Sign-In Accounts",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Google Account Connected",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = currentUserEmail ?: "Signed in with Google",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "Cross-platform sync: Your Firestore profile, listings, and trust score sync across Android and iOS. To enable Apple ID sign-in for iOS/Android users, turn on the Apple provider with your Apple Service ID in Firebase Console.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            // Real-Time Nearby Notifications Card
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Real-Time Nearby Alerts",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Notify me when new sales open nearby",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = alertsEnabled,
                            onCheckedChange = { wantEnabled ->
                                if (wantEnabled &&
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    !NearbyNotificationHelper.hasNotificationPermission(context)
                                ) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onUpdateAlertPreferences(wantEnabled, alertRadius)
                                }
                            },
                            modifier = Modifier.testTag("alerts_toggle_switch")
                        )
                    }

                    Text(
                        text = "Alert Radius: $alertRadius miles",
                        style = MaterialTheme.typography.labelLarge
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2, 5, 10, 25).forEach { miles ->
                            FilterChip(
                                selected = alertRadius == miles,
                                onClick = { onUpdateAlertPreferences(alertsEnabled, miles) },
                                label = { Text("$miles mi") }
                            )
                        }
                    }
                }
            }

            // Seller's Own Listings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "My Posted Sales",
                    style = MaterialTheme.typography.titleLarge
                )
                FilledTonalButton(onClick = onPostSaleClick) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Post")
                }
            }

            when (mySalesState) {
                is UiState.Success -> {
                    val mySales = mySalesState.data
                    if (mySales.isEmpty()) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "You haven't posted a yard sale yet. Tap Post to list yours with photos and a live map pin!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        mySales.forEach { sale ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenSaleDetail(sale.id) },
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(sale.title, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${sale.dateLabel} · ${sale.addressLabel}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = if (sale.isActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = if (sale.isActive) "Active" else "Ended",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                is UiState.Loading -> Text("Loading your listings...", style = MaterialTheme.typography.bodySmall)
                is UiState.Error -> Text(mySalesState.message, color = MaterialTheme.colorScheme.error)
            }

            OutlinedButton(
                onClick = {
                    signOutUser(credentialManager = credentialManager, scope = scope)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("sign_out_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out")
            }
        }
    }

    if (showEditDialog) {
        var nameInput by remember { mutableStateOf(sellerProfile?.displayName ?: "") }
        var hoodInput by remember { mutableStateOf(sellerProfile?.neighborhood ?: "") }
        var bioInput by remember { mutableStateOf(sellerProfile?.bio ?: "") }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Seller Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Display Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = hoodInput,
                        onValueChange = { hoodInput = it },
                        label = { Text("Neighborhood") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = bioInput,
                        onValueChange = { bioInput = it },
                        label = { Text("Seller Bio") },
                        minLines = 2,
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateSellerProfile(nameInput, hoodInput, bioInput)
                        showEditDialog = false
                    },
                    enabled = !isSubmitting
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellerTrustSheet(
    sellerProfile: SellerProfile?,
    reviews: List<SellerReview>,
    currentUserId: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmitReview: (rating: Int, comment: String) -> Unit
) {
    val profile = sellerProfile ?: return
    val isSelf = profile.sellerId == currentUserId
    val existingMyReview = reviews.find { it.reviewerId == currentUserId }

    var rating by remember(existingMyReview) {
        mutableIntStateOf(existingMyReview?.rating?.toInt() ?: 5)
    }
    var comment by remember(existingMyReview) {
        mutableStateOf(existingMyReview?.comment ?: "")
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(profile.displayName, style = MaterialTheme.typography.headlineSmall)
                    if (profile.neighborhood.isNotBlank()) {
                        Text(
                            profile.neighborhood,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                SellerRatingPill(profile = profile)
            }

            if (profile.bio.isNotBlank()) {
                Text(
                    text = profile.bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isSelf) {
                HorizontalDivider()
                Text(
                    text = if (existingMyReview != null) "Update Your Rating" else "Rate This Seller",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (star in 1..5) {
                        IconButton(
                            onClick = { rating = star },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("rate_star_$star")
                        ) {
                            Icon(
                                imageVector = if (star <= rating) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = "$star stars",
                                tint = if (star <= rating) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Share feedback for neighbors") },
                    placeholder = { Text("Fair prices, accurate hours, friendly...") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("review_comment_input")
                )
                Button(
                    onClick = { onSubmitReview(rating, comment) },
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("submit_review_button")
                ) {
                    Text(if (existingMyReview != null) "Update Rating" else "Publish Rating")
                }
            }

            HorizontalDivider()
            Text(
                text = "Community Reviews (${reviews.size})",
                style = MaterialTheme.typography.titleMedium
            )
            if (reviews.isEmpty()) {
                Text(
                    text = "No reviews yet. Be the first neighbor to leave feedback!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(200.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(reviews, key = { it.reviewerId }) { review ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(review.reviewerName, style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        text = "${review.rating} ★",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (review.comment.isNotBlank()) {
                                    Text(
                                        text = review.comment,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyAlertsSheet(
    alerts: List<NearbyAlertItem>,
    onDismiss: () -> Unit,
    onSelectAlert: (String) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Nearby Yard Sale Alerts", style = MaterialTheme.typography.headlineSmall)
            if (alerts.isEmpty()) {
                Text(
                    text = "You're all caught up! New sales posted within your alert radius will appear here and trigger a real-time notification.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(alerts, key = { it.saleId }) { alert ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectAlert(alert.saleId) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(alert.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "%.1f mi away · %s",
                                        alert.distanceMiles,
                                        alert.addressLabel
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
