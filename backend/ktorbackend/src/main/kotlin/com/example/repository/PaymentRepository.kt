package com.example.repository

import com.example.db.DatabaseFactory.dbQuery
import com.example.db.Payments
import com.example.serialization.InstantSerializer
import com.example.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import java.time.Instant
import java.util.UUID

@Serializable
data class Payment(
    @Serializable(with = UUIDSerializer::class)val id: UUID,
    @Serializable(with = UUIDSerializer::class)val userId: UUID,
    @Serializable(with = UUIDSerializer::class)val subscriptionId: UUID,
    val amountCents: Int,
    val status: String,
    val stripePaymentId: String,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant
)

private fun rowToPayment(row: ResultRow) = Payment(
    id = row[Payments.id].value,
    userId = row[Payments.userId].value,
    subscriptionId = row[Payments.subscriptionId].value,
    amountCents = row[Payments.amountCents],
    status = row[Payments.status],
    stripePaymentId = row[Payments.stripePaymentId],
    createdAt = row[Payments.createdAt]
)

class PaymentRepository {

    /** Called from the webhook handler each time Stripe reports a charge succeeding or failing. */
    suspend fun create(
        userId: UUID,
        subscriptionId: UUID,
        amountCents: Int,
        status: String,
        stripePaymentId: String
    ): Payment = dbQuery {
        val now = Instant.now()
        val id = Payments.insertAndGetId {
            it[Payments.userId] = userId
            it[Payments.subscriptionId] = subscriptionId
            it[Payments.amountCents] = amountCents
            it[Payments.status] = status
            it[Payments.stripePaymentId] = stripePaymentId
            it[Payments.createdAt] = now
        }
        Payment(id.value, userId, subscriptionId, amountCents, status, stripePaymentId, now)
    }

    /** Prevents double-processing if Stripe redelivers the same webhook event. */
    suspend fun findByStripePaymentId(stripePaymentId: String): Payment? = dbQuery {
        Payments.selectAll()
            .where { Payments.stripePaymentId eq stripePaymentId }
            .map(::rowToPayment)
            .singleOrNull()
    }

    /** For a future "billing history" screen in the app. */
    suspend fun findByUserId(userId: UUID): List<Payment> = dbQuery {
        Payments.selectAll()
            .where { Payments.userId eq userId }
            .orderBy(Payments.createdAt, SortOrder.DESC)
            .map(::rowToPayment)
    }
}