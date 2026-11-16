package com.retrobit.app.billing

import android.app.Activity
import android.content.Context

/** Direct APK build: no store connection, the CD-key path is the only unlock. */
class NoStoreSupporterGateway(
    private val onResult: (SupporterPurchaseResult) -> Unit
) : SupporterPurchaseGateway {
    override fun start() = onResult(SupporterPurchaseResult.Unavailable)
    override fun purchase(activity: Activity) = onResult(SupporterPurchaseResult.Unavailable)
    override fun restore() = onResult(SupporterPurchaseResult.Unavailable)
    override fun close() = Unit
}

object SupporterPurchaseFactory {
    fun create(
        context: Context,
        onResult: (SupporterPurchaseResult) -> Unit
    ): SupporterPurchaseGateway = NoStoreSupporterGateway(onResult)
}
