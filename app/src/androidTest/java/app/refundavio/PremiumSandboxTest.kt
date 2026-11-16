package app.refundavio

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.amazon.device.iap.PurchasingListener
import com.amazon.device.iap.PurchasingService
import com.amazon.device.iap.model.*
import com.amazon.device.iap.model.Receipt as StoreReceipt
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PremiumSandboxTest {
    private val sku = "app.refundavio.premium"
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val products = LinkedBlockingQueue<ProductDataResponse>()
    private val purchases = LinkedBlockingQueue<PurchaseResponse>()
    private val updates = LinkedBlockingQueue<PurchaseUpdatesResponse>()
    private val users = LinkedBlockingQueue<UserDataResponse>()

    private fun sandboxOnly() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("premiumSandbox") == "true")
        assertEquals("app.refundavio.qa", context.packageName)
        val mode = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("getprop debug.amazon.sandboxmode")
        ).bufferedReader().use { it.readText().trim() }
        assertEquals("App Tester routing must be set before making purchase requests", "debug", mode)
        @Suppress("DEPRECATION")
        assertNotNull(context.packageManager.getPackageInfo("com.amazon.sdktestclient", 0))
    }

    private fun <T> onMain(request: () -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync { result = request() }
        return checkNotNull(result)
    }

    private fun register() = onMain {
        PurchasingService.registerListener(context.applicationContext, object : PurchasingListener {
            override fun onProductDataResponse(response: ProductDataResponse) { products.add(response) }
            override fun onPurchaseResponse(response: PurchaseResponse) { purchases.add(response) }
            override fun onPurchaseUpdatesResponse(response: PurchaseUpdatesResponse) { updates.add(response) }
            override fun onUserDataResponse(response: UserDataResponse) { users.add(response) }
        })
    }

    private fun <T> await(queue: LinkedBlockingQueue<T>, expected: RequestId, requestId: (T) -> RequestId): T {
        val timeout = System.nanoTime() + TimeUnit.SECONDS.toNanos(75)
        while (System.nanoTime() < timeout) {
            val response = queue.poll((timeout - System.nanoTime()).coerceAtLeast(0), TimeUnit.NANOSECONDS) ?: break
            if (requestId(response) == expected) return response
        }
        error("No matching Amazon App Tester callback")
    }

    private fun requireActiveEntitlement(receipt: StoreReceipt) {
        assertEquals(sku, receipt.sku)
        assertEquals(ProductType.ENTITLED, receipt.productType)
        assertFalse(receipt.isCanceled)
        assertFalse(receipt.isDeferred)
        assertTrue(receipt.receiptId.isNotBlank())
    }

    private fun writeEvidence(name: String, receipt: StoreReceipt, user: UserData) {
        requireActiveEntitlement(receipt)
        require(user.userId.isNotBlank())
        File(context.filesDir, name).writeText(JSONObject().put("sku", receipt.sku)
            .put("receiptId", receipt.receiptId).put("userId", user.userId).toString())
    }

    @Test fun purchaseOneTimePremium() {
        sandboxOnly()
        ActivityScenario.launch(MainActivity::class.java).use {
            register()
            val user = await(users, onMain { PurchasingService.getUserData() }, UserDataResponse::getRequestId)
            assertEquals(UserDataResponse.RequestStatus.SUCCESSFUL, user.requestStatus)
            val response = await(products, onMain { PurchasingService.getProductData(setOf(sku)) }, ProductDataResponse::getRequestId)
            assertEquals(ProductDataResponse.RequestStatus.SUCCESSFUL, response.requestStatus)
            val product = checkNotNull(response.productData[sku])
            assertEquals(ProductType.ENTITLED, product.productType)
            assertTrue(product.price.isNotBlank())
            val existing = await(updates, onMain { PurchasingService.getPurchaseUpdates(true) }, PurchaseUpdatesResponse::getRequestId)
            assertEquals(PurchaseUpdatesResponse.RequestStatus.SUCCESSFUL, existing.requestStatus)
            assertTrue("Use a fresh App Tester transaction for this entitlement", existing.receipts.none { it.sku == sku && !it.isCanceled })
            val purchase = await(purchases, onMain { PurchasingService.purchase(sku) }, PurchaseResponse::getRequestId)
            assertEquals(PurchaseResponse.RequestStatus.SUCCESSFUL, purchase.requestStatus)
            assertTrue(user.userData.userId == purchase.userData.userId)
            writeEvidence("premium-purchase.json", purchase.receipt, purchase.userData)
        }
    }

    @Test fun restoreOneTimePremiumFromAmazonHistory() {
        sandboxOnly()
        ActivityScenario.launch(MainActivity::class.java).use {
            register()
            val user = await(users, onMain { PurchasingService.getUserData() }, UserDataResponse::getRequestId)
            assertEquals(UserDataResponse.RequestStatus.SUCCESSFUL, user.requestStatus)
            var reset = true
            val active = mutableListOf<StoreReceipt>()
            do {
                val history = await(updates, onMain { PurchasingService.getPurchaseUpdates(reset) }, PurchaseUpdatesResponse::getRequestId)
                assertEquals(PurchaseUpdatesResponse.RequestStatus.SUCCESSFUL, history.requestStatus)
                assertTrue(user.userData.userId == history.userData.userId)
                active += history.receipts.filter { it.sku == sku && !it.isCanceled && !it.isDeferred }
                reset = false
            } while (history.hasMore())
            writeEvidence("premium-restore.json", active.distinctBy { it.receiptId }.single(), user.userData)
        }
    }

    @Test fun fulfillVerifiedPremiumReceipt() {
        sandboxOnly()
        val receipt = JSONObject(File(context.filesDir, "premium-purchase.json").readText())
        val verified = JSONObject(File(context.filesDir, "premium-verified.json").readText())
        assertEquals(sku, verified.getString("productId"))
        assertEquals("ENTITLED", verified.getString("productType"))
        assertTrue(verified.getBoolean("testTransaction"))
        assertTrue(verified.has("cancelDate") && verified.isNull("cancelDate"))
        assertTrue(receipt.getString("receiptId") == verified.getString("receiptId"))
        ActivityScenario.launch(MainActivity::class.java).use {
            register()
            onMain { PurchasingService.notifyFulfillment(receipt.getString("receiptId"), FulfillmentResult.FULFILLED) }
        }
    }
}
