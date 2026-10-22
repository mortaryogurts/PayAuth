package com.example.repository

import com.example.db.DatabaseFactory.dbQuery
import com.example.db.Plans
import com.example.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

@Serializable
data class Plan(
    @Serializable(with = UUIDSerializer::class) val id : UUID,
    val name: String,
    val priceCents: Int,
    val billingInterval: String,
    val stripePriceId: String
)

private fun rowToPlan(row: ResultRow) = Plan(
    id = row[Plans.id].value,
    name = row[Plans.name],
    priceCents = row[Plans.priceCents],
    billingInterval = row[Plans.billingInterval],
    stripePriceId = row[Plans.stripePriceId]
)

class PlanRepository {

    /** Used once (or a few times) to seed plans that mirror what's configured in the Stripe dashboard. */
    suspend fun create(name: String, priceCents: Int, billingInterval: String, stripePriceId: String): Plan = dbQuery {
        val id = Plans.insertAndGetId {
            it[Plans.name] = name
            it[Plans.priceCents] = priceCents
            it[Plans.billingInterval] = billingInterval
            it[Plans.stripePriceId] = stripePriceId
        }
        Plan(id.value, name, priceCents, billingInterval, stripePriceId)
    }

    suspend fun findAll(): List<Plan> = dbQuery {
        Plans.selectAll().map(::rowToPlan)
    }

    suspend fun findById(id: UUID): Plan? = dbQuery {
        Plans.selectAll()
            .where { Plans.id eq id }
            .map(::rowToPlan)
            .singleOrNull()
    }

    /** Looked up when a Checkout Session request comes in with a plan the user picked. */
    suspend fun findByStripePriceId(stripePriceId: String): Plan? = dbQuery {
        Plans.selectAll()
            .where { Plans.stripePriceId eq stripePriceId }
            .map(::rowToPlan)
            .singleOrNull()
    }
}