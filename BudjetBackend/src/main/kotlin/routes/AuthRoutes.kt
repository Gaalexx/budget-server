package com.routes

import com.auth.AuthErrorResponse
import com.auth.AuthException
import com.auth.AuthMessageResponse
import com.auth.AuthService
import com.auth.ChangePasswordRequest
import com.auth.LoginRequest
import com.auth.RefreshRequest
import com.auth.RegisterRequest
import com.config.JwtSettings
import io.ktor.http.*
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.registerAuthRoutes(jwtSettings: JwtSettings) {
    val authService = AuthService(jwtSettings)

    route("/auth") {
        post("/register") {
            call.respondAuth(HttpStatusCode.Created) {
                val request = call.receiveNullable<RegisterRequest>()
                authService.register(request)
            }
        }

        post("/login") {
            call.respondAuth {
                val request = call.receiveNullable<LoginRequest>()
                authService.login(request)
            }
        }

        post("/refresh") {
            call.respondAuth {
                val request = call.receiveNullable<RefreshRequest>()
                authService.refresh(request)
            }
        }

        authenticate {
            post("/logout") {
                call.respond(AuthMessageResponse("Logout completed"))
            }

            get("/me") {
                call.respondAuth {
                    authService.currentUser(call.requiredUserId())
                }
            }

            post("/change-password") {
                call.respondAuth {
                    val request = call.receiveNullable<ChangePasswordRequest>()
                    authService.changePassword(call.requiredUserId(), request)
                }
            }
        }
    }
}

private suspend fun ApplicationCall.respondAuth(
    status: HttpStatusCode = HttpStatusCode.OK,
    block: suspend () -> Any
) {
    try {
        respond(status, block())
    } catch (error: AuthException) {
        respond(error.status, AuthErrorResponse(error.message))
    }
}
