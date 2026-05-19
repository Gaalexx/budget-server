package com.database

import kotlinx.serialization.Serializable

@Serializable
data class StatsPeriod(val from: String?, val to: String?)

@Serializable
data class StatsSummaryResponse(
    val period: StatsPeriod,
    val totalIncome: Double,
    val totalExpenses: Double,
    val balance: Double,
    val transactionCount: Long
)

@Serializable
data class CategoryStatsItem(
    val categoryId: String?,
    val categoryName: String?,
    val total: Double,
    val transactionCount: Long,
    val percentage: Double
)

@Serializable
data class CategoryStatsResponse(
    val period: StatsPeriod,
    val type: String?,
    val total: Double,
    val items: List<CategoryStatsItem>
)

@Serializable
data class MonthlyStatsItem(
    val month: Int,
    val label: String,
    val totalIncome: Double,
    val totalExpenses: Double,
    val balance: Double
)

@Serializable
data class MonthlyStatsResponse(
    val year: Int,
    val months: List<MonthlyStatsItem>
)
