package com.example.repository

import com.example.db.DatabaseFactory.dbQuery
import com.example.db.Payments.bool
import com.example.db.Payments.default
import com.example.db.Subscriptions
import com.example.serialization.InstantSerializer
import com.example.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

@Serializable
data class Subscription(
    @Serializable(with = UUIDSerializer::class)val id: UUID,
    @Serializable(with = UUIDSerializer::class)val userId: UUID,
    @Serializable(with = UUIDSerializer::class)val planId: UUID,
    val status: String,
    val stripeSubscriptionId: String,
    @Serializable(with = InstantSerializer::class) val currentPeriodEnd: Instant,
    val cancelAtPeriodEnd: Boolean = false
)

private fun rowToSubscription(row: ResultRow) = Subscription(
    id = row[Subscriptions.id].value,
    userId = row[Subscriptions.userId].value,
    planId = row[Subscriptions.planId].value,
    status = row[Subscriptions.status],
    stripeSubscriptionId = row[Subscriptions.stripeSubscriptionId],
    currentPeriodEnd = row[Subscriptions.currentPeriodEnd],
    cancelAtPeriodEnd = row[Subscriptions.cancelAtPeriodEnd]
)

class SubscriptionRepository {

    /** Called once, right after Stripe confirms a Checkout Session succeeded. */
    suspend fun create(
        userId: UUID,
        planId: UUID,
        status: String,
        stripeSubscriptionId: String,
        currentPeriodEnd: Instant
    ): Subscription = dbQuery {
        val id = Subscriptions.insertAndGetId {
            it[Subscriptions.userId] = userId
            it[Subscriptions.planId] = planId
            it[Subscriptions.status] = status
            it[Subscriptions.stripeSubscriptionId] = stripeSubscriptionId
            it[Subscriptions.currentPeriodEnd] = currentPeriodEnd
        }
        Subscription(id.value, userId, planId, status, stripeSubscriptionId, currentPeriodEnd)
    }

    /** Looked up by webhook events, which identify the subscription by Stripe's own id, not yours. */
    suspend fun findByStripeSubscriptionId(stripeSubscriptionId: String): Subscription? = dbQuery {
        Subscriptions.selectAll()
            .where { Subscriptions.stripeSubscriptionId eq stripeSubscriptionId }
            .map(::rowToSubscription)
            .singleOrNull()
    }

    /**
     * A user could technically have more than one subscription row over time
     * (e.g. resubscribing after canceling), so this returns the most recent one
     * by current_period_end. This is what your app calls to gate premium features.
     */
    suspend fun findLatestByUserId(userId: UUID): Subscription? = dbQuery {
        Subscriptions.selectAll()
            .where { Subscriptions.userId eq userId }
            .orderBy(Subscriptions.currentPeriodEnd, SortOrder.DESC)
            .limit(1)
            .map(::rowToSubscription)
            .singleOrNull()
    }

    /** Called every time a webhook event reports a status or period change. */
    /** Called every time a webhook event reports a status, period, cancel or plan change. */
    suspend fun updateStatus(
        stripeSubscriptionId: String,
        newStatus: String,
        newCurrentPeriodEnd: Instant,
        newCancelAtPeriodEnd: Boolean = false,
        newPlanId: UUID? = null          // null = leave the plan as it is
    ): Boolean = dbQuery {
        val updatedRows = Subscriptions.update({ Subscriptions.stripeSubscriptionId eq stripeSubscriptionId }) {
            it[status] = newStatus
            it[currentPeriodEnd] = newCurrentPeriodEnd
            it[cancelAtPeriodEnd] = newCancelAtPeriodEnd
            if (newPlanId != null) it[planId] = newPlanId
        }
        updatedRows > 0
    }
}