package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class YardHopRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createSale_andObservePublicSales_succeedsForAuthenticatedUsers() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val aliceRepo = YardHopRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserAndSellerProfiles("Alice M.", ALICE_EMAIL, null).getOrThrow()
        }

        val saleId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createSale(
                sellerName = "Alice M.",
                sellerPhotoUrl = null,
                title = "Oakwood Moving Yard Sale",
                description = "Mid-century chairs, vinyl records, patio planters.",
                addressLabel = "742 Maple Ave, Oakwood",
                neighborhood = "Oakwood",
                latitude = 37.7749,
                longitude = -122.4194,
                dateLabel = "Sat, Nov 14",
                hoursLabel = "8:00 AM - 2:00 PM",
                categories = listOf("Furniture", "Books & Vinyl"),
                priceRangeLabel = "$5 - $90",
                photoDataList = emptyList()
            ).getOrThrow()
        }
        assertTrue(saleId.isNotBlank())

        signInTestUser(BOB_EMAIL)
        val bobRepo = YardHopRepository(firestore, auth)
        val publicSales = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observePublicSales().first { list -> list.any { it.id == saleId } }
        }
        assertTrue(publicSales.any { it.id == saleId && it.title == "Oakwood Moving Yard Sale" })
    }

    @Test
    fun submitSellerReview_andUpdateSellerRating_succeedsAtomically() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = YardHopRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserAndSellerProfiles("Alice M.", ALICE_EMAIL, null).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = YardHopRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.submitSellerReview(
                sellerId = aliceUid,
                reviewerName = "Bob K.",
                rating = 5,
                comment = "Great finds and accurate hours!"
            ).getOrThrow()
        }

        val profile = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeSellerProfile(aliceUid).first { it != null && it.ratingCount >= 1L }
        }
        assertNotNull(profile)
        assertEquals(5.0, profile!!.averageRating, 0.01)
    }

    @Test
    fun crossUser_endSaleAndPrivateSettingsAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = YardHopRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserAndSellerProfiles("Alice M.", ALICE_EMAIL, null).getOrThrow()
        }
        val saleId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createSale(
                sellerName = "Alice M.",
                sellerPhotoUrl = null,
                title = "Alice Patio Sale",
                description = "Books and mugs.",
                addressLabel = "100 Elm St",
                neighborhood = "Elm District",
                latitude = 37.78,
                longitude = -122.41,
                dateLabel = "Sun, Nov 15",
                hoursLabel = "9:00 AM - 1:00 PM",
                categories = listOf("Books & Vinyl"),
                priceRangeLabel = "$2 - $20",
                photoDataList = emptyList()
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = YardHopRepository(firestore, auth)

        val endResult = withTimeout(DEFAULT_TIMEOUT_MS) { bobRepo.endSale(saleId) }
        assertTrue(endResult.isFailure)
        val endEx = endResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(endEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, endEx!!.code)

        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid).get().await()
            }
            fail("Expected PERMISSION_DENIED reading another user's private settings")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertNotNull(fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
        }
    }

    @Test
    fun observePublicSales_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = YardHopRepository(firestore, auth)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observePublicSales().first()
            }
            fail("Expected IllegalStateException or PERMISSION_DENIED when unauthenticated")
        } catch (_: IllegalStateException) {
            // Repository fail-fast guard triggered
        }

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                firestore.collection("sales").whereEqualTo("visibility", "public").get().await()
            }
            fail("Expected PERMISSION_DENIED on direct unauthenticated query")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertNotNull(fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice_yardhop@test.com"
        const val BOB_EMAIL = "bob_yardhop@test.com"
        const val DEFAULT_TIMEOUT_MS = 10000L
        const val FLOW_TIMEOUT_MS = 8000L
    }
}
