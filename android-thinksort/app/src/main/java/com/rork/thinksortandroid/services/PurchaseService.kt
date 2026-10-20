package com.rork.thinksortandroid.services

import android.app.Activity
import android.content.Context
import android.util.Log
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.getCustomerInfoWith
import com.revenuecat.purchases.getOfferingsWith
import com.revenuecat.purchases.getProductsWith
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.models.StoreProduct
import com.revenuecat.purchases.purchaseWith
import com.revenuecat.purchases.restorePurchasesWith
import com.rork.thinksortandroid.Config
import com.rork.thinksortandroid.data.AccessState
import com.rork.thinksortandroid.domain.Dates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.ceil

/**
 * Owns purchase state for the single lifetime unlock, plus the 7-day trial clock.
 * ThinkSort has no account, so the trial start date lives on device and the
 * purchase itself is restored from Google Play through RevenueCat.
 */
object PurchaseService {
    const val TRIAL_DAYS = 7
    const val LIFETIME_PRODUCT_ID = "thinksort_lifetime"
    private const val ENTITLEMENT_ID = "thinksort_pro"
    private const val TAG = "Purchases"

    /** RevenueCat public SDK key (safe to ship in the app). */
    private const val FALLBACK_PUBLIC_KEY = "goog_toVsEVMJDKNlMHSraQgBHBIAeqS"

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _priceText = MutableStateFlow<String?>(null)
    val priceText: StateFlow<String?> = _priceText.asStateFlow()

    private val _isPurchasing = MutableStateFlow(false)
    val isPurchasing: StateFlow<Boolean> = _isPurchasing.asStateFlow()

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var lifetimePackage: Package? = null
    private var lifetimeProduct: StoreProduct? = null

    private val apiKey: String
        get() = Config.allValues["EXPO_PUBLIC_REVENUECAT_ANDROID_API_KEY"]?.takeIf { it.isNotBlank() } ?: FALLBACK_PUBLIC_KEY

    /** Call once from Application.onCreate before any UI reads purchase state. */
    fun configure(context: Context) {
        if (Purchases.isConfigured) return
        try {
            Purchases.configure(PurchasesConfiguration.Builder(context.applicationContext, apiKey).build())
            Purchases.sharedInstance.updatedCustomerInfoListener = UpdatedCustomerInfoListener { apply(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Could not configure purchases: ${e.javaClass.simpleName}")
        }
    }

    /** Seeds the cached unlock so launch is instant and offline-safe, then refreshes. */
    fun attach(cachedUnlock: Boolean) {
        if (cachedUnlock) _isUnlocked.value = true
        loadProducts()
        refresh()
    }

    fun clearError() {
        _error.value = null
    }

    fun loadProducts() {
        if (!Purchases.isConfigured) return
        Purchases.sharedInstance.getOfferingsWith(
            onError = { error ->
                Log.w(TAG, "Offerings unavailable: ${error.code}")
                loadProductFallback()
            },
            onSuccess = { offerings ->
                val pkg = offerings.current?.lifetime ?: offerings.current?.availablePackages?.firstOrNull()
                if (pkg != null) {
                    lifetimePackage = pkg
                    lifetimeProduct = pkg.product
                    _priceText.value = pkg.product.price.formatted
                } else {
                    loadProductFallback()
                }
            },
        )
    }

    private fun loadProductFallback() {
        Purchases.sharedInstance.getProductsWith(
            productIds = listOf(LIFETIME_PRODUCT_ID),
            onError = { error -> Log.w(TAG, "Product unavailable: ${error.code}") },
            onGetStoreProducts = { products ->
                lifetimeProduct = products.firstOrNull()
                _priceText.value = products.firstOrNull()?.price?.formatted
            },
        )
    }

    fun refresh() {
        if (!Purchases.isConfigured) return
        Purchases.sharedInstance.getCustomerInfoWith(
            onError = { error -> Log.w(TAG, "Could not read purchase status: ${error.code}") },
            onSuccess = { apply(it) },
        )
    }

    private fun apply(info: CustomerInfo) {
        val unlocked = info.entitlements[ENTITLEMENT_ID]?.isActive == true ||
            info.nonSubscriptionTransactions.any { it.productIdentifier.startsWith(LIFETIME_PRODUCT_ID) }
        // Never revoke a cached unlock on a failed or empty refresh.
        if (unlocked && !_isUnlocked.value) _isUnlocked.value = true
    }

    fun purchase(activity: Activity) {
        if (!Purchases.isConfigured) {
            _error.value = "Google Play isn't available right now. Please try again later."
            return
        }
        val pkg = lifetimePackage
        val product = lifetimeProduct
        val params = when {
            pkg != null -> PurchaseParams.Builder(activity, pkg).build()
            product != null -> PurchaseParams.Builder(activity, product).build()
            else -> {
                _error.value = "Couldn't reach Google Play. Check your connection and try again."
                loadProducts()
                return
            }
        }
        _isPurchasing.value = true
        Purchases.sharedInstance.purchaseWith(
            params,
            onError = { error, userCancelled ->
                _isPurchasing.value = false
                if (userCancelled) return@purchaseWith
                _error.value = if (error.code == PurchasesErrorCode.PaymentPendingError) {
                    "Your purchase is waiting for approval. ThinkSort unlocks as soon as it goes through."
                } else {
                    error.message
                }
            },
            onSuccess = { _, info ->
                _isPurchasing.value = false
                apply(info)
            },
        )
    }

    fun restore() {
        if (!Purchases.isConfigured) {
            _error.value = "Google Play isn't available right now. Please try again later."
            return
        }
        _isRestoring.value = true
        Purchases.sharedInstance.restorePurchasesWith(
            onError = { error ->
                _isRestoring.value = false
                _error.value = error.message
            },
            onSuccess = { info ->
                _isRestoring.value = false
                apply(info)
                if (!_isUnlocked.value) _error.value = "We couldn't find a previous purchase on this Google account."
            },
        )
    }

    fun accessState(trialStartedAt: Long?, isUnlocked: Boolean, now: Long = System.currentTimeMillis()): AccessState {
        if (isUnlocked) return AccessState.Unlocked
        val start = trialStartedAt ?: return AccessState.TrialNotStarted
        val end = Dates.addDays(start, TRIAL_DAYS.toLong())
        if (now >= end) return AccessState.Expired
        val remaining = ceil((end - now) / 86_400_000.0).toInt()
        return AccessState.Trial(maxOf(1, remaining))
    }
}
