package com.example.auth

import org.mindrot.jbcrypt.BCrypt

object PasswordHasher {

    // Higher = slower = more resistant to brute-force, but slower logins too.
    // 12 is a solid default for 2026 hardware.
    private const val WORK_FACTOR = 12

    fun hash(plainPassword: String): String =
        BCrypt.hashpw(plainPassword, BCrypt.gensalt(WORK_FACTOR))

    fun verify(plainPassword: String, hashedPassword: String): Boolean =
        BCrypt.checkpw(plainPassword, hashedPassword)
}