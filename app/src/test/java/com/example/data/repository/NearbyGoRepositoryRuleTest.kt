package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.domain.engine.NearbyGoPaymentGateway
import com.example.domain.model.CartItem
import com.example.domain.model.OrderStatus
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PlatformSettings
import com.example.domain.model.Store
import com.example.domain.model.StoreCategory
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class NearbyGoRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun unauthenticatedCaller_observeApprovedStores_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = FirestoreNearbyGoRepository(firestore)

        val thrown = runCatching {
            withTimeout(FLOW_TIMEOUT_MS) { repo.observeApprovedStores().first() }
        }.exceptionOrNull()

        val firestoreEx = checkNotNull(thrown?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException, got $thrown"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
    }

    @Test
    fun customerProfile_crossUserRead_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_${UUID.randomUUID().toString().take(6)}@nearbygo.test")
        val repo = FirestoreNearbyGoRepository(firestore)
        withTimeout(OP_TIMEOUT_MS) {
            repo.ensureUserProfile(aliceUid, "Alice", "alice@nearbygo.test").getOrThrow()
        }

        signInTestUser("bob_${UUID.randomUUID().toString().take(6)}@nearbygo.test")
        val crossReadError = runCatching {
            withTimeout(OP_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid).get().await()
            }
        }.exceptionOrNull()

        val firestoreEx = checkNotNull(crossReadError?.unwrapFirestoreException()) {
            "Expected PERMISSION_DENIED on cross-user profile read, got $crossReadError"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
    }

    @Test
    fun sellerStoreAndProduct_creationAndOrderLifecycle_succeedsThroughRepository() = runBlocking {
        val sellerUid = signInTestUser("seller_${UUID.randomUUID().toString().take(6)}@nearbygo.test")
        val repo = FirestoreNearbyGoRepository(firestore)

        val storeId = withTimeout(OP_TIMEOUT_MS) {
            repo.createStore(
                ownerId = sellerUid,
                isAdmin = false,
                name = "Test Fresh Store",
                category = StoreCategory.GROCERY,
                description = "Local veggies",
                address = "10 Residency Road",
                latitude = 12.9716,
                longitude = 77.5946,
                openingHours = "08:00 - 22:00",
                autoAcceptOrders = false
            ).getOrThrow()
        }
        assertTrue(storeId.isNotBlank())

        val prodId = withTimeout(OP_TIMEOUT_MS) {
            repo.upsertProduct(
                productId = null,
                storeId = storeId,
                ownerId = sellerUid,
                name = "Fresh Mango Box",
                category = "Fruits",
                description = "Sweet Alphonso",
                priceInr = 350.0,
                discountPercent = 10L,
                stockCount = 20L,
                isAvailable = true,
                imageUrl = "",
                unitLabel = "1 kg"
            ).getOrThrow()
        }
        assertTrue(prodId.isNotBlank())

        // Customer places order against the store
        val customerUid = signInTestUser("cust_${UUID.randomUUID().toString().take(6)}@nearbygo.test")
        val orderId = withTimeout(OP_TIMEOUT_MS) {
            repo.placeOrder(
                customerId = customerUid,
                customerName = "Customer One",
                store = Store(id = storeId, ownerId = sellerUid, name = "Test Fresh Store"),
                cartItems = listOf(
                    CartItem(
                        id = prodId,
                        productId = prodId,
                        storeId = storeId,
                        storeName = "Test Fresh Store",
                        productName = "Fresh Mango Box",
                        unitPriceInr = 350.0,
                        discountPercent = 10L,
                        quantity = 1L
                    )
                ),
                deliveryAddress = "22 Indiranagar 100ft Rd",
                distanceMeters = 1200L,
                coupon = null,
                paymentMethod = PaymentMethod.COD,
                paymentReference = "",
                settings = PlatformSettings(),
                gatewayAdapter = NearbyGoPaymentGateway()
            ).getOrThrow()
        }
        assertTrue(orderId.isNotBlank())

        val orders = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeCustomerOrders(customerUid).first { list -> list.any { it.id == orderId } }
        }
        assertEquals(OrderStatus.PLACED.name, orders.first { it.id == orderId }.status)
    }

    @Test
    fun complaints_sellerCannotReadCustomerComplaint() = runBlocking {
        val custUid = signInTestUser("cmp_cust_${UUID.randomUUID().toString().take(6)}@nearbygo.test")
        val repo = FirestoreNearbyGoRepository(firestore)
        val complaintId = withTimeout(OP_TIMEOUT_MS) {
            repo.submitComplaint(
                customerId = custUid,
                customerName = "Cust",
                orderId = "ord_test_1",
                subject = "Damaged package",
                description = "Bottle seal was broken on arrival."
            ).getOrThrow()
        }

        signInTestUser("cmp_seller_${UUID.randomUUID().toString().take(6)}@nearbygo.test")
        val err = runCatching {
            withTimeout(OP_TIMEOUT_MS) {
                firestore.collection("complaints").document(complaintId).get().await()
            }
        }.exceptionOrNull()

        val firestoreEx = checkNotNull(err?.unwrapFirestoreException()) {
            "Expected PERMISSION_DENIED when seller reads private complaint, got $err"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
    }

    private companion object {
        const val OP_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
