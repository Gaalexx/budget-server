package com.routes

import com.database.TransactionFilters
import com.database.TransactionUpsertRequest
import com.database.TransactionsTable
import io.ktor.http.*
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.registerTransactionRoutes() {
    authenticate {
        route("/transactions") {
            get {
                val filters = TransactionFilters(
                    type = call.request.queryParameters["type"],
                    categoryId = call.request.queryParameters["categoryId"],
                    from = call.request.queryParameters["from"],
                    to = call.request.queryParameters["to"],
                    page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1,
                    limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                )

                call.respond(TransactionsTable.list(call.requiredUserId(), filters))
            }

            get("/{id}") {
                call.respond(
                    TransactionsTable.get(
                        ownerId = call.requiredUserId(),
                        transactionId = call.requiredUuidParameter("id")
                    )
                )
            }

            post {
                val request = call.receiveNullable<TransactionUpsertRequest>()
                call.respond(HttpStatusCode.Created, TransactionsTable.create(call.requiredUserId(), request))
            }

            patch("/{id}") {
                val request = call.receiveNullable<TransactionUpsertRequest>()
                call.respond(
                    TransactionsTable.update(
                        ownerId = call.requiredUserId(),
                        transactionId = call.requiredUuidParameter("id"),
                        request = request
                    )
                )
            }

            delete("/{id}") {
                call.respond(
                    TransactionsTable.delete(
                        ownerId = call.requiredUserId(),
                        transactionId = call.requiredUuidParameter("id")
                    )
                )
            }
        }
    }
}

private fun ApplicationCall.requiredUuidParameter(name: String): UUID {
    val value = parameters[name]?.takeIf(String::isNotBlank)
        ?: throw IllegalArgumentException("$name is required")

    return runCatching { UUID.fromString(value) }
        .getOrElse { throw IllegalArgumentException("$name must be a valid UUID") }
}
