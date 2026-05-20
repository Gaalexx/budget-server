package com.database

import kotlinx.serialization.Serializable

@Serializable
data class TransactionUpsertRequest(
    val type: String? = null,
    val amount: Double? = null,
    val categoryId: String? = null,
    val date: String? = null,
    val title: String? = null,
    val description: String? = null,
    val comment: String? = null
)

@Serializable
data class TransactionFilters(
    val type: String? = null,
    val categoryId: String? = null,
    val from: String? = null,
    val to: String? = null,
    val page: Int = 1,
    val limit: Int = 20
)

@Serializable
data class TransactionResponse(
    val id: String,
    val type: String,
    val amount: Double,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val date: String,
    val title: String? = null,
    val description: String? = null,
    val comment: String? = null,
    val syncStatus: String = "synced"
)

@Serializable
data class TransactionsResponse(
    val items: List<TransactionResponse>,
    val filters: TransactionFilters,
    val total: Long = 0,
    val page: Int = 1,
    val limit: Int = 20,
    val totalPages: Int = 1
)
