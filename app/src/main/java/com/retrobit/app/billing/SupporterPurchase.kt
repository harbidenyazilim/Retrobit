package com.retrobit.app.billing

import android.app.Activity
import android.content.Context

sealed interface SupporterPurchaseResult {
    data class Price(val label: String) : SupporterPurchaseResult
    data class Purchased(val source: String) : SupporterPurchaseResult
    data object Unavailable : SupporterPurchaseResult
    data class Failed(val reason: String) : SupporterPurchaseResult
}

interface SupporterPurchaseGateway {
    fun start()
    fun purchase(activity: Activity)
    fun restore()
    fun close()
}

/**
 * The Play flavour provides a BillingClient-backed factory, the sideload flavour a stub that
 * reports the store as unavailable. The CD-key path works in both.
 */
object SupporterPurchase {
    fun create(
        context: Context,
        onResult: (SupporterPurchaseResult) -> Unit
    ): SupporterPurchaseGateway =
        SupporterPurchaseFactory.create(context.applicationContext, onResult)
}
