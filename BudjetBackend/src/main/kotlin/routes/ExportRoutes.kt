package com.routes

import com.database.TransactionFilters
import com.database.TransactionResponse
import com.database.TransactionsTable
import io.ktor.http.ContentType
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.registerExportRoutes() {
    authenticate {
        route("/export") {
            get {
                val format = call.request.queryParameters["format"]?.lowercase() ?: "json"
                val from = call.request.queryParameters["from"]
                val to = call.request.queryParameters["to"]

                val data = TransactionsTable.list(
                    ownerId = call.requiredUserId(),
                    filters = TransactionFilters(from = from, to = to)
                )

                when (format) {
                    "csv" -> {
                        call.response.headers.append(
                            "Content-Disposition",
                            "attachment; filename=\"transactions.csv\""
                        )
                        call.respondText(buildCsv(data.items), ContentType.parse("text/csv"))
                    }
                    else -> {
                        call.response.headers.append(
                            "Content-Disposition",
                            "attachment; filename=\"transactions.json\""
                        )
                        call.respond(data)
                    }
                }
            }
        }
    }
}

private fun buildCsv(items: List<TransactionResponse>): String {
    val header = "id,type,amount,categoryId,categoryName,date,title,description"
    val rows = items.joinToString("\n") { item ->
        listOf(
            item.id,
            item.type,
            item.amount.toString(),
            item.categoryId ?: "",
            item.categoryName ?: "",
            item.date,
            item.title ?: "",
            item.description ?: ""
        ).joinToString(",") { csvEscape(it) }
    }
    return if (items.isEmpty()) header else "$header\n$rows"
}

private fun csvEscape(value: String): String {
    if (!value.contains(',') && !value.contains('"') && !value.contains('\n')) return value
    return "\"${value.replace("\"", "\"\"")}\""
}
