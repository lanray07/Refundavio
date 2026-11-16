package app.refundavio

import android.content.Context
import com.amazon.device.iap.PurchasingListener
import com.amazon.device.iap.PurchasingService
import com.amazon.device.iap.model.*
import com.amazon.device.iap.model.Receipt as AmazonReceipt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class PremiumState(
    val price: String? = null,
    val active: Boolean = false,
    val busy: Boolean = false,
    val message: Int? = null,
) {
    val canBuy get() = !active && !busy && price != null &&
        (message == null || message == R.string.premium_no_purchase || message == R.string.premium_purchase_incomplete)
}

internal class PremiumBilling private constructor(context: Context) : PurchasingListener {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("premium", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(PremiumState())
    val state = mutable.asStateFlow()
    private var catalogueId: RequestId? = null
    private var operationId: RequestId? = null
    private var timeout: Job? = null
    private var registered = false
    private var historyUser: String? = null
    private val history = linkedMapOf<String, AmazonReceipt>()
    private val cancelled = mutableSetOf<String>()

    fun recoverPreviousPurchase() {
        if (preferences.getBoolean("previouslyVerified", false)) open()
    }

    fun open() {
        if (!configured) return
        try {
            if (!registered) {
                PurchasingService.registerListener(app, this)
                registered = true
            }
            if (!state.value.busy) {
                catalogueId = PurchasingService.getProductData(setOf(SKU))
                restore()
            }
        } catch (_: RuntimeException) {
            finish(R.string.premium_error)
        }
    }

    fun buy() {
        if (!registered || !state.value.canBuy) return
        request { PurchasingService.purchase(SKU) }
    }

    fun restore() {
        if (!registered || state.value.busy) return
        history.clear()
        cancelled.clear()
        historyUser = null
        mutable.value = state.value.copy(active = false)
        request { PurchasingService.getPurchaseUpdates(true) }
    }

    private fun request(start: () -> RequestId) {
        mutable.value = state.value.copy(busy = true, message = null)
        timeout?.cancel()
        try {
            operationId = start()
            timeout = scope.launch {
                delay(90_000)
                finish(R.string.premium_error)
            }
        } catch (_: RuntimeException) {
            finish(R.string.premium_error)
        }
    }

    private fun accept(id: RequestId): Boolean {
        if (operationId != id) return false
        operationId = null
        timeout?.cancel()
        return true
    }

    private fun finish(message: Int?) {
        operationId = null
        timeout?.cancel()
        mutable.value = state.value.copy(busy = false, message = message)
    }

    override fun onUserDataResponse(response: UserDataResponse) = Unit

    override fun onProductDataResponse(response: ProductDataResponse) {
        if (catalogueId != response.requestId) return
        catalogueId = null
        val product = response.productData?.get(SKU)
        val price = product?.price?.takeIf {
            response.requestStatus == ProductDataResponse.RequestStatus.SUCCESSFUL &&
                product.productType == ProductType.ENTITLED && it.isNotBlank()
        }
        mutable.value = state.value.copy(price = price)
    }

    override fun onPurchaseResponse(response: PurchaseResponse) {
        if (!accept(response.requestId)) return
        when (response.requestStatus) {
            PurchaseResponse.RequestStatus.PENDING -> finish(R.string.premium_pending)
            PurchaseResponse.RequestStatus.ALREADY_PURCHASED -> {
                finish(null)
                restore()
            }
            PurchaseResponse.RequestStatus.SUCCESSFUL -> {
                val user = response.userData?.userId
                val receipt = response.receipt
                if (user.isNullOrBlank() || receipt == null || !matches(receipt)) {
                    finish(R.string.premium_error)
                } else if (receipt.isDeferred) {
                    finish(R.string.premium_pending)
                } else if (receipt.isCanceled) {
                    finish(R.string.premium_no_purchase)
                } else {
                    verify(user, listOf(receipt), R.string.premium_no_purchase)
                }
            }
            else -> finish(R.string.premium_purchase_incomplete)
        }
    }

    override fun onPurchaseUpdatesResponse(response: PurchaseUpdatesResponse) {
        if (!accept(response.requestId)) return
        val user = response.userData?.userId
        if (response.requestStatus != PurchaseUpdatesResponse.RequestStatus.SUCCESSFUL ||
            user.isNullOrBlank() || (historyUser != null && historyUser != user)) {
            finish(R.string.premium_error)
            return
        }
        historyUser = user
        for (receipt in response.receipts.orEmpty().filter(::matches)) {
            if (receipt.isCanceled) cancelled.add(receipt.receiptId)
            if (!receipt.isDeferred && !receipt.isCanceled) history[receipt.receiptId] = receipt
        }
        if (response.hasMore()) {
            request { PurchasingService.getPurchaseUpdates(false) }
        } else {
            verify(user, history.filterKeys { it !in cancelled }.values.toList(), R.string.premium_no_purchase)
        }
    }

    private fun verify(user: String, receipts: List<AmazonReceipt>, missing: Int) {
        mutable.value = state.value.copy(active = false)
        scope.launch {
            var unavailable = false
            for (receipt in receipts) {
                val active = try {
                    withContext(Dispatchers.IO) { verifyReceipt(user, receipt.receiptId) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    unavailable = true
                    false
                }
                if (active) {
                    preferences.edit().putBoolean("previouslyVerified", true).apply()
                    mutable.value = state.value.copy(active = true)
                    try {
                        PurchasingService.notifyFulfillment(receipt.receiptId, FulfillmentResult.FULFILLED)
                    } catch (_: RuntimeException) {
                        // A subsequent restore can retry the acknowledgment.
                    }
                    finish(null)
                    return@launch
                }
            }
            if (!unavailable) preferences.edit().remove("previouslyVerified").apply()
            finish(if (unavailable) R.string.premium_error else missing)
        }
    }

    private fun verifyReceipt(user: String, receipt: String): Boolean {
        val connection = URL(BuildConfig.PREMIUM_VERIFIER).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 12_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            val payload = JSONObject().put("userId", user).put("receiptId", receipt).toString()
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            check(connection.responseCode == 200)
            val reply = connection.inputStream.bufferedReader().use { it.readLine() }
            return JSONObject(reply).get("active") == true
        } finally {
            connection.disconnect()
        }
    }

    private fun matches(receipt: AmazonReceipt) =
        receipt.sku == SKU && receipt.productType == ProductType.ENTITLED && !receipt.receiptId.isNullOrBlank()

    companion object {
        private const val SKU = BuildConfig.PREMIUM_SKU
        val configured get() = BuildConfig.PREMIUM_VERIFIER.isNotBlank()
        private var instance: PremiumBilling? = null
        fun get(context: Context): PremiumBilling = instance ?: PremiumBilling(context).also { instance = it }
    }
}
