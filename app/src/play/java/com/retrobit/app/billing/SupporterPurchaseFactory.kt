package com.retrobit.app.billing

import android.app.Activity
import android.content.Context
import android.util.Base64
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.retrobit.app.BuildConfig
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

private const val PRODUCT_ID = "supporter_pack"

/**
 * Local receipt check. Play already validates the token when it is queried, but verifying the
 * signature keeps modified clients and fake billing helpers from flipping the supporter flag.
 * An empty public key (local builds, before the Play listing exists) skips the check.
 */
private object ReceiptVerifier {
    fun isVerified(purchase: Purchase): Boolean {
        val publicKey = BuildConfig.BILLING_PUBLIC_KEY
        if (publicKey.isBlank()) return true
        return runCatching {
            val key = KeyFactory.getInstance("RSA").generatePublic(
                X509EncodedKeySpec(Base64.decode(publicKey, Base64.DEFAULT))
            )
            val signature = Base64.decode(purchase.signature, Base64.DEFAULT)
            val payload = purchase.originalJson.toByteArray(Charsets.UTF_8)
            listOf("SHA1withRSA", "SHA256withRSA").any { algorithm ->
                runCatching {
                    Signature.getInstance(algorithm).run {
                        initVerify(key)
                        update(payload)
                        verify(signature)
                    }
                }.getOrDefault(false)
            }
        }.getOrDefault(false)
    }
}

class PlaySupporterGateway(
    private val context: Context,
    private val onResult: (SupporterPurchaseResult) -> Unit
) : SupporterPurchaseGateway {

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener { result, purchases -> handlePurchases(result, purchases) }
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    private var product: ProductDetails? = null

    override fun start() {
        if (client.isReady) {
            queryProduct()
            restoreInternal()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProduct()
                    restoreInternal()
                } else {
                    onResult(SupporterPurchaseResult.Unavailable)
                }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        client.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                product = queryResult.productDetailsList.firstOrNull()
                product?.oneTimePurchaseOfferDetails?.formattedPrice?.let {
                    onResult(SupporterPurchaseResult.Price(it))
                }
            }
        }
    }

    override fun purchase(activity: Activity) {
        val details = product
        if (details == null) {
            onResult(SupporterPurchaseResult.Unavailable)
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            onResult(SupporterPurchaseResult.Failed("flow ${result.responseCode}"))
        }
    }

    override fun restore() {
        if (client.isReady) restoreInternal() else start()
    }

    private fun restoreInternal() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                deliver(purchases)
            }
        }
    }

    private fun handlePurchases(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
                onResult(SupporterPurchaseResult.Failed("billing ${result.responseCode}"))
            }
            return
        }
        deliver(purchases)
    }

    private fun deliver(purchases: List<Purchase>?) {
        val purchase = purchases?.firstOrNull {
            it.products.contains(PRODUCT_ID) &&
                it.purchaseState == Purchase.PurchaseState.PURCHASED
        } ?: return
        if (!ReceiptVerifier.isVerified(purchase)) {
            onResult(SupporterPurchaseResult.Failed("signature"))
            return
        }
        finish(purchase)
    }

    private fun finish(purchase: Purchase) {
        if (purchase.isAcknowledged) {
            onResult(SupporterPurchaseResult.Purchased("play"))
            return
        }
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        client.acknowledgePurchase(params) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                onResult(SupporterPurchaseResult.Purchased("play"))
            } else {
                onResult(SupporterPurchaseResult.Failed("ack ${result.responseCode}"))
            }
        }
    }

    override fun close() {
        runCatching { client.endConnection() }
    }
}

object SupporterPurchaseFactory {
    fun create(
        context: Context,
        onResult: (SupporterPurchaseResult) -> Unit
    ): SupporterPurchaseGateway = PlaySupporterGateway(context, onResult)
}
