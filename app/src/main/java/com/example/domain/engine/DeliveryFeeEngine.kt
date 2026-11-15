package com.example.domain.engine

import com.example.domain.model.PlatformSettings
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

data class DeliveryFeeQuote(
    val distanceMeters: Long,
    val slabCount: Long,
    val deliveryFeeInr: Double,
    val estimatedMinutes: Int,
    val sourceLabel: String
)

object DeliveryFeeEngine {

    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val MAX_HYPERLOCAL_DISTANCE_METERS = 35_000L

    fun calculateHaversineMeters(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double
    ): Long {
        if (startLat !in -90.0..90.0 || endLat !in -90.0..90.0 ||
            startLng !in -180.0..180.0 || endLng !in -180.0..180.0
        ) {
            return -1L
        }
        val dLat = Math.toRadians(endLat - startLat)
        val dLng = Math.toRadians(endLng - startLng)
        val lat1Rad = Math.toRadians(startLat)
        val lat2Rad = Math.toRadians(endLat)

        val a = sin(dLat / 2.0).pow(2) +
            cos(lat1Rad) * cos(lat2Rad) * sin(dLng / 2.0).pow(2)
        val c = 2.0 * asin(sqrt(a.coerceIn(0.0, 1.0)))
        return (EARTH_RADIUS_METERS * c).roundToLong().coerceAtLeast(100L)
    }

    /**
     * Default slab formula:
     * 0 - 500m   = ₹5 (1 slab)
     * 501 - 1000m = ₹10 (2 slabs)
     * 1001 - 1500m = ₹15 (3 slabs)
     * 1501 - 2000m = ₹20 (4 slabs)
     * Every additional 500m = +₹5
     */
    fun calculateQuote(
        distanceMeters: Long,
        settings: PlatformSettings,
        sourceLabel: String = "Manual / Haversine Distance"
    ): Result<DeliveryFeeQuote> {
        if (distanceMeters < 0L) {
            return Result.failure(IllegalArgumentException("Route distance cannot be negative."))
        }
        if (distanceMeters > MAX_HYPERLOCAL_DISTANCE_METERS) {
            return Result.failure(
                IllegalArgumentException("Store is outside hyperlocal delivery range (>35 km).")
            )
        }
        val effectiveMeters = distanceMeters.coerceAtLeast(1L)
        val slabCount = ceil(effectiveMeters / 500.0).toLong().coerceAtLeast(1L)
        val baseFee = settings.deliveryBaseFeeInr.coerceAtLeast(0.0)
        val extraPerSlab = settings.deliveryPer500mInr.coerceAtLeast(0.0)
        val fee = baseFee + ((slabCount - 1L).coerceAtLeast(0L) * extraPerSlab)
        val etaMinutes = (10 + (effectiveMeters / 250.0)).toInt().coerceIn(10, 75)
        return Result.success(
            DeliveryFeeQuote(
                distanceMeters = distanceMeters,
                slabCount = slabCount,
                deliveryFeeInr = fee,
                estimatedMinutes = etaMinutes,
                sourceLabel = sourceLabel
            )
        )
    }
}
