package com.example.domain.engine

import com.example.domain.model.Order
import com.example.domain.model.OrderStatus

object OrderLifecycleEngine {

    private val allowedTransitions: Map<OrderStatus, Set<OrderStatus>> = mapOf(
        OrderStatus.PLACED to setOf(OrderStatus.ACCEPTED, OrderStatus.REJECTED, OrderStatus.CANCELLED),
        OrderStatus.ACCEPTED to setOf(OrderStatus.PREPARING, OrderStatus.CANCELLED, OrderStatus.REJECTED),
        OrderStatus.PREPARING to setOf(OrderStatus.READY, OrderStatus.CANCELLED),
        OrderStatus.READY to setOf(OrderStatus.ASSIGNED, OrderStatus.CANCELLED),
        OrderStatus.ASSIGNED to setOf(OrderStatus.PICKED_UP, OrderStatus.READY),
        OrderStatus.PICKED_UP to setOf(OrderStatus.OUT_FOR_DELIVERY),
        OrderStatus.OUT_FOR_DELIVERY to setOf(OrderStatus.DELIVERED),
        OrderStatus.DELIVERED to setOf(OrderStatus.REFUND_PENDING),
        OrderStatus.CANCELLED to setOf(OrderStatus.REFUND_PENDING),
        OrderStatus.REJECTED to setOf(OrderStatus.REFUND_PENDING),
        OrderStatus.REFUND_PENDING to setOf(OrderStatus.REFUNDED),
        OrderStatus.REFUNDED to emptySet()
    )

    fun canTransition(from: OrderStatus, to: OrderStatus): Boolean {
        if (from == to) return false
        return allowedTransitions[from]?.contains(to) == true
    }

    fun validateTransition(from: OrderStatus, to: OrderStatus): Result<OrderStatus> {
        if (from == to) {
            return Result.failure(IllegalStateException("Order is already in $to status."))
        }
        return if (canTransition(from, to)) {
            Result.success(to)
        } else {
            Result.failure(
                IllegalStateException("Invalid order transition from ${from.name} to ${to.name}.")
            )
        }
    }

    fun canPartnerClaim(order: Order, partnerId: String, partnerIsBusy: Boolean): Result<Unit> {
        if (partnerId.isBlank()) {
            return Result.failure(IllegalArgumentException("Partner ID is required."))
        }
        if (partnerIsBusy) {
            return Result.failure(
                IllegalStateException("Delivery partner is currently busy with an active delivery.")
            )
        }
        if (order.status != OrderStatus.READY.name) {
            return Result.failure(
                IllegalStateException("Order must be READY before delivery assignment.")
            )
        }
        if (order.deliveryPartnerId.isNotBlank()) {
            return Result.failure(
                IllegalStateException("Order is already assigned to another delivery partner.")
            )
        }
        return Result.success(Unit)
    }

    fun appendTimelineEvent(existing: List<String>, eventLabel: String): List<String> {
        val updated = existing.toMutableList()
        if (updated.lastOrNull() != eventLabel) {
            updated.add(eventLabel)
        }
        return updated.takeLast(30)
    }
}
