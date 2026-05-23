package com.database

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update as exposedUpdate
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime
import kotlin.math.min
import kotlin.math.roundToLong

object BudgetLimitsTable : UUIDTable("budget_limits") {
    val userId = reference("user_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val categoryId = reference("category_id", CategoriesTable, onDelete = ReferenceOption.CASCADE)
    val amount = decimal("amount", precision = 12, scale = 2)
    val period = varchar("period", 20).default("monthly")

    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)

    init {
        uniqueIndex("budget_limits_user_category_period_key", userId, categoryId, period)
    }

    fun list(ownerId: UUID): BudgetLimitsResponse = transaction {
        UsersTable.ensureExistsInCurrentTransaction(ownerId)

        val items = leftJoin(CategoriesTable)
            .selectAll()
            .where { userId eq EntityID(ownerId, UsersTable) }
            .map { row ->
                val catId = row[categoryId].value
                val limitPeriod = row[period]
                val limitAmount = row[amount].toDouble()
                val spent = currentPeriodSpent(ownerId, catId, limitPeriod)

                BudgetLimitRecord(
                    id = row[BudgetLimitsTable.id].value.toString(),
                    categoryId = catId.toString(),
                    categoryName = row.getOrNull(CategoriesTable.name),
                    limitAmount = limitAmount,
                    spentAmount = spent,
                    remainingAmount = (limitAmount - spent).coerceAtLeast(0.0),
                    period = limitPeriod,
                    percentage = if (limitAmount > 0)
                        min((spent / limitAmount * 10000).roundToLong() / 100.0, 100.0)
                    else
                        0.0
                )
            }

        BudgetLimitsResponse(items = items)
    }

    fun upsert(ownerId: UUID, request: BudgetLimitRequest): BudgetLimitRecord = transaction {
        UsersTable.ensureExistsInCurrentTransaction(ownerId)

        val catId = request.categoryId?.let { parseUuid(it) }
            ?: throw IllegalArgumentException("categoryId is required")
        val limitAmount = request.amount
            ?: throw IllegalArgumentException("amount is required")
        require(limitAmount > 0) { "amount must be greater than 0" }
        val limitPeriod = normalizePeriod(request.period)

        require(CategoriesTable.existsForUserInCurrentTransaction(ownerId, catId)) {
            "Category $catId not found for current user"
        }

        val existing = selectAll()
            .where {
                (userId eq EntityID(ownerId, UsersTable)) and
                (categoryId eq EntityID(catId, CategoriesTable)) and
                (period eq limitPeriod)
            }
            .singleOrNull()

        val limitId: UUID = if (existing != null) {
            exposedUpdate({
                (BudgetLimitsTable.id eq existing[BudgetLimitsTable.id]) and
                (BudgetLimitsTable.userId eq EntityID(ownerId, UsersTable))
            }) {
                it[amount] = BigDecimal.valueOf(limitAmount)
                it[updatedAt] = LocalDateTime.now()
            }
            existing[BudgetLimitsTable.id].value
        } else {
            insertAndGetId {
                it[userId] = EntityID(ownerId, UsersTable)
                it[categoryId] = EntityID(catId, CategoriesTable)
                it[amount] = BigDecimal.valueOf(limitAmount)
                it[period] = limitPeriod
            }.value
        }

        val spent = currentPeriodSpent(ownerId, catId, limitPeriod)

        leftJoin(CategoriesTable)
            .selectAll()
            .where {
                (BudgetLimitsTable.id eq limitId) and
                (BudgetLimitsTable.userId eq EntityID(ownerId, UsersTable))
            }
            .singleOrNull()
            ?.let { row ->
                BudgetLimitRecord(
                    id = limitId.toString(),
                    categoryId = catId.toString(),
                    categoryName = row.getOrNull(CategoriesTable.name),
                    limitAmount = limitAmount,
                    spentAmount = spent,
                    remainingAmount = (limitAmount - spent).coerceAtLeast(0.0),
                    period = limitPeriod,
                    percentage = if (limitAmount > 0)
                        min((spent / limitAmount * 10000).roundToLong() / 100.0, 100.0)
                    else
                        0.0
                )
            } ?: error("Budget limit $limitId not found after upsert")
    }

    fun delete(ownerId: UUID, limitId: UUID): Boolean = transaction {
        deleteWhere {
            (BudgetLimitsTable.id eq limitId) and
            (BudgetLimitsTable.userId eq EntityID(ownerId, UsersTable))
        } > 0
    }

    private fun currentPeriodSpent(ownerId: UUID, catId: UUID, period: String): Double {
        val now = LocalDate.now()
        val (from, to) = when (period) {
            "monthly" -> {
                val start = LocalDate.of(now.year, now.month, 1)
                start to start.plusMonths(1).minusDays(1)
            }
            "yearly" -> LocalDate.of(now.year, 1, 1) to LocalDate.of(now.year, 12, 31)
            else -> return 0.0
        }

        return TransactionsTable
            .selectAll()
            .where {
                (TransactionsTable.userId eq EntityID(ownerId, UsersTable)) and
                (TransactionsTable.categoryId eq EntityID(catId, CategoriesTable)) and
                (TransactionsTable.type eq TransactionType.EXPENSE) and
                (TransactionsTable.transactionDate greaterEq from) and
                (TransactionsTable.transactionDate lessEq to)
            }
            .sumOf { it[TransactionsTable.amount].toDouble() }
    }

    private fun normalizePeriod(value: String?): String {
        val normalized = value?.trim()?.lowercase() ?: "monthly"
        require(normalized in setOf("monthly", "yearly")) { "period must be monthly or yearly" }
        return normalized
    }

    private fun parseUuid(value: String): UUID =
        runCatching { UUID.fromString(value) }
            .getOrElse { throw IllegalArgumentException("categoryId must be a valid UUID") }
}
