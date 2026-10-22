package com.example.db

import com.example.config.Env
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import javax.sql.DataSource

private val initialized = java.util.concurrent.atomic.AtomicBoolean(false)

object DatabaseFactory {

    fun init() {
        if (!initialized.compareAndSet(false, true)) return
        try {
            val dataSource = createHikariDataSource()
            Database.connect(dataSource)

            transaction {
                SchemaUtils.create(Users, Plans, Subscriptions, Payments, RefreshTokens)
            }
        } catch (e: Throwable) {
            val url = Env.get("DB_URL") ?: ""
            if (url.contains("localhost:5432") && e.message?.contains("refused", ignoreCase = true) == true) {
                try {
                    val fallbackConfig = HikariConfig().apply {
                        jdbcUrl = "jdbc:h2:mem:saas_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
                        driverClassName = "org.h2.Driver"
                        username = "sa"
                        password = ""
                        maximumPoolSize = 10
                        isAutoCommit = false
                        transactionIsolation = "TRANSACTION_READ_COMMITTED"
                    }
                    val dataSource = HikariDataSource(fallbackConfig)
                    Database.connect(dataSource)
                    transaction {
                        SchemaUtils.create(Users, Plans, Subscriptions, Payments, RefreshTokens)
                    }
                    return
                } catch (_: Throwable) {
                    initialized.set(false)
                    throw e
                }
            }
            initialized.set(false)
            throw e
        }
    }

    private fun createHikariDataSource(): DataSource {
        val url = Env.get("DB_URL") ?: "jdbc:postgresql://localhost:5432/saas_db"
        val driver = Env.get("DB_DRIVER") ?: if (url.startsWith("jdbc:h2:")) "org.h2.Driver" else "org.postgresql.Driver"
        val user = Env.get("DB_USER") ?: "saas_user"
        val pass = Env.get("DB_PASSWORD") ?: "saas_password"

        val config = HikariConfig().apply {
            jdbcUrl = url
            driverClassName = driver
            username = user
            password = pass
            maximumPoolSize = 10
            isAutoCommit = false
            transactionIsolation = if (url.startsWith("jdbc:h2:")) "TRANSACTION_READ_COMMITTED" else "TRANSACTION_REPEATABLE_READ"
            validate()
        }
        return HikariDataSource(config)
    }

    suspend fun <T> dbQuery(block: () -> T): T =
        withContext(Dispatchers.IO) {
            transaction { block() }
        }
}