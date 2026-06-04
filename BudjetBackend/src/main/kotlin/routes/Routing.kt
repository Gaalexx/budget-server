package com.routes

import com.config.JwtSettings
import io.ktor.server.application.*
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.*

fun Application.configureRouting(jwtSettings: JwtSettings) {
    routing {
        staticResources("/", "web")
        registerSystemRoutes()
        registerPwaRoutes()

        route("/api") {
            registerApiSystemRoutes()
            registerAuthRoutes(jwtSettings)
            registerUserRoutes()
            registerTransactionRoutes()
            registerCategoryRoutes()
            registerStatsRoutes()
            registerBudgetLimitsRoutes()
            registerSyncRoutes()
            registerExportRoutes()
        }
    }
}
