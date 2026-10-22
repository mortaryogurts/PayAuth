
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    application
    kotlin("plugin.serialization") version "2.4.20"
}

group = "com.example"
version = "1.0.0-SNAPSHOT"

application {
    mainClass.set("com.example.MainKt")
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.withType<JavaExec> {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")

    // Tests must never touch dev data or real Stripe; use in-memory H2 DB for fast, isolated tests
    environment("DB_URL", "jdbc:h2:mem:saas_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
    environment("DB_DRIVER", "org.h2.Driver")
    environment("DB_USER", "sa")
    environment("DB_PASSWORD", "")
    environment("JWT_SECRET", "test-secret-not-for-production")
    environment("STRIPE_SECRET_KEY", "sk_test_dummy")
    environment("STRIPE_WEBHOOK_SECRET", "whsec_test_secret")

    testLogging {
        events("failed")
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(libs.logback.classic)

    // Database layer
    implementation("org.jetbrains.exposed:exposed-core:0.55.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.55.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.55.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.55.0")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("com.zaxxer:HikariCP:5.1.0")

    // Gson dependency for Stripe ApiResource.GSON
    implementation("com.google.code.gson:gson:2.11.0")

    // H2 in-memory database for testing fallback
    testImplementation("com.h2database:h2:2.3.232")

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)

    // BCrypt
    implementation("at.favre.lib:bcrypt:0.9.0")
    implementation("org.mindrot:jbcrypt:0.4")

    // JWT
    implementation("com.auth0:java-jwt:4.4.0")

    implementation(ktorLibs.server.auth)
    implementation("io.ktor:ktor-server-auth-jwt:2.3.12")

    // stripe
    implementation(libs.stripe.java)

    // .env
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")

    implementation(ktorLibs.server.rateLimit)
}
