package com.database

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.compoundAnd
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update as exposedUpdate
import org.jetbrains.exposed.sql.javatime.CurrentDate
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime

object TransactionsTable : UUIDTable("transactions") {
    val userId = reference("user_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val categoryId = optReference("category_id", CategoriesTable, onDelete = ReferenceOption.SET_NULL)

    val type = customEnumeration(
        name = "type",
        sql = "VARCHAR(20)",
        fromDb = { TransactionType.fromDb(it as String) },
        toDb = { it.value }
    )
    val amount = decimal("amount", precision = 12, scale = 2)

    val title = varchar("title", 150).nullable()
    val description = text("description").nullable()

    val transactionDate = date("transaction_date").defaultExpression(CurrentDate)

    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)

    init {
        check("transactions_amount_check") { amount greater BigDecimal.ZERO }

        index("idx_transactions_user_id", false, userId)
        index("idx_transactions_user_date", false, userId, transactionDate)
        index("idx_transactions_user_type", false, userId, type)
    }

    fun list(ownerId: UUID, filters: TransactionFilters): TransactionsResponse = transaction {
        UsersTable.ensureExistsInCurrentTransaction(ownerId)

        val conditions = mutableListOf(userId eq EntityID(ownerId, UsersTable))
        filters.type?.let { conditions += type eq TransactionType.parse(it) }
        filters.categoryId?.let {
            if (it != "null") {
                conditions += categoryId eq EntityID(parseUuid(it, "categoryId"), CategoriesTable)
            }
        }
        filters.from?.let { conditions += transactionDate greaterEq parseDate(it, "from") }
        filters.to?.let { conditions += transactionDate lessEq parseDate(it, "to") }

        val pageSize = filters.limit.coerceIn(1, 500)
        val page = filters.page.coerceAtLeast(1)
        val offset = ((page - 1) * pageSize).toLong()

        val baseQuery = leftJoin(CategoriesTable)
            .selectAll()
            .where(conditions.compoundAnd())

        val total = baseQuery.count()

        val rows = leftJoin(CategoriesTable)
            .selectAll()
            .where(conditions.compoundAnd())
            .orderBy(
                transactionDate to SortOrder.DESC,
                createdAt to SortOrder.DESC
            )
            .limit(pageSize)
            .offset(offset)
            .map { it.toTransactionResponse() }

        TransactionsResponse(
            items = rows,
            filters = filters,
            total = total,
            page = page,
            limit = pageSize,
            totalPages = if (total == 0L) 1 else ((total + pageSize - 1) / pageSize).toInt()
        )
    }

    fun get(ownerId: UUID, transactionId: UUID): TransactionResponse = transaction {
        UsersTable.ensureExistsInCurrentTransaction(ownerId)
        findByIdInCurrentTransaction(ownerId, transactionId)?.toTransactionResponse()
            ?: throw TransactionNotFoundException(transactionId)
    }

    fun create(ownerId: UUID, request: TransactionUpsertRequest?): TransactionResponse = transaction {
        UsersTable.ensureExistsInCurrentTransaction(ownerId)

        val payload = request ?: throw IllegalArgumentException("Request body is required")
        val parsedType = TransactionType.parse(payload.type)
        val parsedAmount = parseAmount(payload.amount)
        val parsedCategoryId = payload.categoryId?.let { parseUuid(it, "categoryId") }
        validateCategory(ownerId, parsedCategoryId)

        val newId = insertAndGetId {
            it[userId] = EntityID(ownerId, UsersTable)
            it[categoryId] = parsedCategoryId?.let { id -> EntityID(id, CategoriesTable) }
            it[type] = parsedType
            it[amount] = parsedAmount
            it[title] = payload.title?.takeIf(String::isNotBlank)
            it[description] = payload.description
                ?.takeIf(String::isNotBlank)
                ?: payload.comment?.takeIf(String::isNotBlank)
            it[transactionDate] = payload.date?.let { value -> parseDate(value, "date") } ?: LocalDate.now()
        }.value

        findByIdInCurrentTransaction(ownerId, newId)?.toTransactionResponse(syncStatus = "created")
            ?: throw TransactionNotFoundException(newId)
    }

    fun update(ownerId: UUID, transactionId: UUID, request: TransactionUpsertRequest?): TransactionResponse = transaction {
        UsersTable.ensureExistsInCurrentTransaction(ownerId)

        val payload = request ?: throw IllegalArgumentException("Request body is required")
        val parsedCategoryId = payload.categoryId?.let { parseUuid(it, "categoryId") }
        validateCategory(ownerId, parsedCategoryId)

        val updated = exposedUpdate({
            (TransactionsTable.id eq transactionId) and (TransactionsTable.userId eq EntityID(ownerId, UsersTable))
        }) { statement ->
            payload.type?.let { value -> statement[type] = TransactionType.parse(value) }
            payload.amount?.let { value -> statement[amount] = parseAmount(value) }
            payload.categoryId?.let { statement[categoryId] = parsedCategoryId?.let { id -> EntityID(id, CategoriesTable) } }
            payload.date?.let { value -> statement[transactionDate] = parseDate(value, "date") }
            payload.title?.let { value -> statement[title] = value.takeIf(String::isNotBlank) }
            (payload.description ?: payload.comment)?.let { value ->
                statement[description] = value.takeIf(String::isNotBlank)
            }
            statement[updatedAt] = LocalDateTime.now()
        }

        if (updated == 0) {
            throw TransactionNotFoundException(transactionId)
        }

        findByIdInCurrentTransaction(ownerId, transactionId)?.toTransactionResponse(syncStatus = "updated")
            ?: throw TransactionNotFoundException(transactionId)
    }

    fun delete(ownerId: UUID, transactionId: UUID): ApiMessage = transaction {
        UsersTable.ensureExistsInCurrentTransaction(ownerId)

        val deleted = deleteWhere {
            (TransactionsTable.id eq transactionId) and (TransactionsTable.userId eq EntityID(ownerId, UsersTable))
        }

        if (deleted == 0) {
            throw TransactionNotFoundException(transactionId)
        }

        ApiMessage("Transaction $transactionId deleted")
    }

    private fun findByIdInCurrentTransaction(ownerId: UUID, transactionId: UUID): ResultRow? {
        return leftJoin(CategoriesTable)
            .selectAll()
            .where {
                (TransactionsTable.id eq transactionId) and (TransactionsTable.userId eq EntityID(ownerId, UsersTable))
            }
            .singleOrNull()
    }

    private fun validateCategory(ownerId: UUID, categoryId: UUID?) {
        if (categoryId == null) {
            return
        }

        require(CategoriesTable.existsForUserInCurrentTransaction(ownerId, categoryId)) {
            "Category $categoryId not found for current user"
        }
    }

    private fun ResultRow.toTransactionResponse(syncStatus: String = "synced"): TransactionResponse {
        val description = this[TransactionsTable.description]

        return TransactionResponse(
            id = this[TransactionsTable.id].value.toString(),
            type = this[TransactionsTable.type].value,
            amount = this[TransactionsTable.amount].toDouble(),
            categoryId = this[TransactionsTable.categoryId]?.value?.toString(),
            categoryName = getOrNull(CategoriesTable.name),
            date = this[TransactionsTable.transactionDate].toString(),
            title = this[TransactionsTable.title],
            description = description,
            comment = description,
            syncStatus = syncStatus
        )
    }

    private fun parseAmount(value: Double?): BigDecimal {
        require(value != null) { "amount is required" }
        require(value > 0) { "amount must be greater than 0" }

        return BigDecimal.valueOf(value)
    }

    private fun parseDate(value: String, field: String): LocalDate {
        return runCatching { LocalDate.parse(value) }
            .getOrElse { throw IllegalArgumentException("$field must use ISO date format yyyy-MM-dd") }
    }

    private fun parseUuid(value: String, field: String): UUID {
        return runCatching { UUID.fromString(value) }
            .getOrElse { throw IllegalArgumentException("$field must be a valid UUID") }
    }
}
