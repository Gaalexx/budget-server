package com.database

import java.time.LocalDateTime
import java.util.Locale
import java.util.UUID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update as exposedUpdate
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object UsersTable : UUIDTable("users") {
    val email = varchar("email", 255).uniqueIndex("users_email_key")
    val passwordHash = text("password_hash")
    val name = varchar("name", 100).nullable()
    val currency = varchar("currency", 16).default("RUB")
    val timezone = varchar("timezone", 64).default("Europe/Moscow")

    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)

    fun create(email: String, passwordHash: String, name: String): UserRecord = transaction {
        val userId = insertAndGetId {
            it[UsersTable.email] = email
            it[UsersTable.passwordHash] = passwordHash
            it[UsersTable.name] = name
        }.value

        findByIdInCurrentTransaction(userId) ?: error("Created user $userId not found")
    }

    fun findById(userId: UUID): UserRecord? = transaction {
        findByIdInCurrentTransaction(userId)
    }

    fun findByEmail(email: String): UserRecord? = transaction {
        selectAll()
            .where { UsersTable.email eq email }
            .singleOrNull()
            ?.toUserRecord()
    }

    fun findByEmailWithPassword(email: String): UserCredentialsRecord? = transaction {
        selectAll()
            .where { UsersTable.email eq email }
            .singleOrNull()
            ?.toUserCredentialsRecord()
    }

    fun findByIdWithPassword(userId: UUID): UserCredentialsRecord? = transaction {
        selectAll()
            .where { UsersTable.id eq userId }
            .singleOrNull()
            ?.toUserCredentialsRecord()
    }

    fun updatePassword(userId: UUID, passwordHash: String): Boolean = transaction {
        exposedUpdate({ UsersTable.id eq userId }) {
            it[UsersTable.passwordHash] = passwordHash
            it[updatedAt] = LocalDateTime.now()
        } > 0
    }

    fun updateProfile(userId: UUID, request: UserProfileUpdateRequest): UserRecord = transaction {
        ensureExistsInCurrentTransaction(userId)

        exposedUpdate({ UsersTable.id eq userId }) {
            request.name?.let { value -> it[name] = normalizeName(value) }
            request.email?.let { value -> it[email] = normalizeEmail(value) }
            request.currency?.let { value -> it[currency] = normalizeText(value, "currency", 16) }
            request.timezone?.let { value -> it[timezone] = normalizeText(value, "timezone", 64) }
            it[updatedAt] = LocalDateTime.now()
        }

        findByIdInCurrentTransaction(userId) ?: error("User $userId not found")
    }

    fun delete(userId: UUID): Boolean = transaction {
        deleteWhere { UsersTable.id eq userId } > 0
    }

    fun ensureExists(userId: UUID): UserRecord = transaction {
        ensureExistsInCurrentTransaction(userId)
        findByIdInCurrentTransaction(userId) ?: error("User $userId not found")
    }

    internal fun ensureExistsInCurrentTransaction(userId: UUID) {
        require(findByIdInCurrentTransaction(userId) != null) {
            "User $userId not found"
        }
    }

    private fun findByIdInCurrentTransaction(userId: UUID): UserRecord? {
        return selectAll()
            .where { UsersTable.id eq userId }
            .singleOrNull()
            ?.toUserRecord()
    }

    private fun ResultRow.toUserRecord(): UserRecord {
        return UserRecord(
            id = this[UsersTable.id].value.toString(),
            email = this[email],
            name = this[name] ?: this[email].substringBefore("@"),
            currency = this[currency],
            timezone = this[timezone]
        )
    }

    private fun ResultRow.toUserCredentialsRecord(): UserCredentialsRecord {
        return UserCredentialsRecord(
            id = this[UsersTable.id].value.toString(),
            email = this[email],
            name = this[name] ?: this[email].substringBefore("@"),
            currency = this[currency],
            timezone = this[timezone],
            passwordHash = this[passwordHash]
        )
    }

    private fun normalizeEmail(value: String): String {
        val normalized = value.trim().lowercase(Locale.ROOT)
        require(normalized.isNotBlank()) { "email cannot be blank" }
        require(normalized.length <= 255) { "email must be at most 255 characters" }
        require(emailRegex.matches(normalized)) { "email has invalid format" }

        return normalized
    }

    private fun normalizeName(value: String): String? {
        val normalized = value.trim().takeIf { it.isNotBlank() }
        require(normalized == null || normalized.length <= 100) {
            "name must be at most 100 characters"
        }

        return normalized
    }

    private fun normalizeText(value: String, field: String, maxLength: Int): String {
        val normalized = value.trim()
        require(normalized.isNotBlank()) { "$field cannot be blank" }
        require(normalized.length <= maxLength) { "$field must be at most $maxLength characters" }

        return normalized
    }

    private val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
}
