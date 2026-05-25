package com.routes

import com.database.BulkSyncRequest
import com.database.BulkSyncResponse
import com.database.SyncResultItem
import com.database.TransactionUpsertRequest
import com.database.TransactionsTable
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.registerSyncRoutes() {
    authenticate {
        route("/sync") {
            post("/transactions") {
                val request = call.receiveNullable<BulkSyncRequest>()
                    ?: throw IllegalArgumentException("Request body is required")

                val userId = call.requiredUserId()
                val results = mutableListOf<SyncResultItem>()

                for (item in request.transactions) {
                    val result = runCatching {
                        TransactionsTable.create(
                            ownerId = userId,
                            request = TransactionUpsertRequest(
                                type = item.type,
                                amount = item.amount,
                                categoryId = item.categoryId,
                                date = item.date,
                                title = item.title,
                                description = item.description
                            )
                        )
                    }



                    results += if (result.isSuccess) {
                        SyncResultItem(
                            localId = item.localId,
                            transaction = result.getOrNull(),
                            status = "synced"
                        )
                    } else {
                        SyncResultItem(
                            localId = item.localId,
                            transaction = null,
                            status = "error",
                            error = result.exceptionOrNull()?.message
                        )
                    }
                }

                call.respond(
                    BulkSyncResponse(
                        synced = results,
                        successCount = results.count { it.status == "synced" },
                        errorCount = results.count { it.status == "error" }
                    )
                )
            }
        }
    }
}
