package com.routes

import com.database.ApiMessage
import com.database.BudgetLimitRequest
import com.database.BudgetLimitsTable
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

fun Route.registerBudgetLimitsRoutes() {
    authenticate {
        route("/budget/limits") {
            get {
                call.respond(BudgetLimitsTable.list(call.requiredUserId()))
            }

            post {
                val request = call.receiveNullable<BudgetLimitRequest>()
                    ?: throw IllegalArgumentException("Request body is required")
                call.respond(HttpStatusCode.OK, BudgetLimitsTable.upsert(call.requiredUserId(), request))
            }

            delete("/{id}") {
                val limitId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: throw IllegalArgumentException("id must be a valid UUID")

                val deleted = BudgetLimitsTable.delete(call.requiredUserId(), limitId)
                if (deleted) {
                    call.respond(ApiMessage("Budget limit deleted"))
                } else {
                    call.respond(HttpStatusCode.NotFound, ApiMessage("Budget limit not found"))
                }
            }
        }
    }
}
