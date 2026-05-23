package com.database

import kotlinx.serialization.Serializable

@Serializable
data class BudgetLimitRecord(
    val id: String,
    val categoryId: String,
    val categoryName: String?,
    val limitAmount: Double,
    val spentAmount: Double,
    val remainingAmount: Double,
    val period: String,
    val percentage: Double
)

@Serializable
data class BudgetLimitRequest(
    val categoryId: String? = null,
    val amount: Double? = null,
    val period: String? = null
)

@Serializable
data class BudgetLimitsResponse(
    val items: List<BudgetLimitRecord>
)
