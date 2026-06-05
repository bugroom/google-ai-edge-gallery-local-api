/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.server

import android.content.Context
import android.util.Log
import com.google.ai.edge.gallery.data.ApiServerConfig
import com.google.ai.edge.gallery.data.AuthType
import com.google.ai.edge.gallery.data.HttpTrafficLogger
import com.google.ai.edge.gallery.data.api.ChatCompletionRequest
import com.google.ai.edge.gallery.data.api.ErrorResponse
import com.google.ai.edge.gallery.data.api.HealthResponse
import com.google.ai.edge.gallery.data.api.ModelsResponse
import com.google.ai.edge.gallery.data.api.toApiEngine
import com.google.ai.edge.gallery.data.api.toApiModel
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.http.HttpHeaders
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.cio.CIO
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "ApiServer"
private const val LOG_MARKER = "LOCAL_API"

/**
 * Local API server for serving model inference
 */
class ApiServer(
    private val context: Context,
    private val config: ApiServerConfig,
    private val inferenceHandler: ApiInferenceHandler,
) {
    private var server: ApplicationEngine? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val startTime = AtomicLong(0)
    private val currentConnections = AtomicLong(0)

    /**
     * Start the API server
     */
    fun start() {
        if (server != null) {
            Log.w(TAG, "Server is already running")
            return
        }

        try {
            startTime.set(System.currentTimeMillis())
            HttpTrafficLogger.logDebug(TAG, "$LOG_MARKER event=server_starting host=${config.host} port=${config.port}")
            
            val embedded = embeddedServer(CIO, port = config.port, host = config.host) {
                install(ContentNegotiation) {
                    json(Json {
                        prettyPrint = true
                        isLenient = true
                        ignoreUnknownKeys = true
                    })
                }

                install(CORS) {
                    allowMethod(io.ktor.http.HttpMethod.Options)
                    allowMethod(io.ktor.http.HttpMethod.Get)
                    allowMethod(io.ktor.http.HttpMethod.Post)
                    allowMethod(io.ktor.http.HttpMethod.Put)
                    allowMethod(io.ktor.http.HttpMethod.Delete)
                    allowHeader(io.ktor.http.HttpHeaders.ContentType)
                    allowHeader(io.ktor.http.HttpHeaders.Authorization)
                    allowHeader(io.ktor.http.HttpHeaders.AccessControlAllowOrigin)
                    allowCredentials = true
                    allowNonSimpleContentTypes = true
                    anyHost()
                }

                install(StatusPages) {
                    exception<IllegalArgumentException> { call, cause ->
                        Log.w(TAG, "$LOG_MARKER event=request_bad_request path=${call.request.path()} error=${cause.message}")
                        HttpTrafficLogger.logError(
                            call.request.path(),
                            "$LOG_MARKER event=request_bad_request error=${cause.message}",
                        )
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse(
                                error = cause.message ?: "Bad request",
                                type = "bad_request",
                            ),
                        )
                    }
                    exception<Exception> { call, cause ->
                        Log.e(TAG, "$LOG_MARKER event=request_error path=${call.request.path()} error=${cause.message}", cause)
                        HttpTrafficLogger.logError(
                            call.request.path(),
                            "$LOG_MARKER event=request_error error=${cause.message}",
                        )
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse(
                                error = cause.message ?: "Internal error",
                                type = "internal_error",
                            ),
                        )
                    }
                }

                routing {
                    get("/health") {
                        if (!call.authorize()) return@get
                        currentConnections.incrementAndGet()
                        try {
                            HttpTrafficLogger.logRequest("GET", "/health", call.safeHeaders())
                            Log.d(TAG, "$LOG_MARKER event=health_check path=/health")
                            call.respond(
                                HealthResponse(
                                    status = "ok",
                                    uptime = System.currentTimeMillis() - startTime.get(),
                                    connections = currentConnections.get().toInt(),
                                    loaded_model = null,
                                )
                            )
                            HttpTrafficLogger.logResponse("/health", HttpStatusCode.OK.value, "$LOG_MARKER event=health_ok")
                        } finally {
                            currentConnections.decrementAndGet()
                        }
                    }

                    route("/v1") {
                        get("/models") {
                            if (!call.authorize()) return@get
                            currentConnections.incrementAndGet()
                            try {
                                HttpTrafficLogger.logRequest("GET", "/v1/models", call.safeHeaders())
                                val models = inferenceHandler.getDownloadedLlmModels()
                                call.respond(
                                    ModelsResponse(data = models.map { it.toApiModel() })
                                )
                                HttpTrafficLogger.logResponse(
                                    "/v1/models",
                                    HttpStatusCode.OK.value,
                                    "$LOG_MARKER event=models_ok count=${models.size}",
                                )
                            } finally {
                                currentConnections.decrementAndGet()
                            }
                        }

                        post("/chat/completions") {
                            if (!call.authorize()) return@post
                            currentConnections.incrementAndGet()
                            try {
                                HttpTrafficLogger.logRequest("POST", "/v1/chat/completions", call.safeHeaders())
                                val request = call.receive<ChatCompletionRequest>()
                                if (request.stream) {
                                    call.respond(
                                        HttpStatusCode.BadRequest,
                                        ErrorResponse(
                                            error = "Streaming responses are not supported yet",
                                            type = "unsupported_feature",
                                        ),
                                    )
                                    HttpTrafficLogger.logResponse(
                                        "/v1/chat/completions",
                                        HttpStatusCode.BadRequest.value,
                                        "$LOG_MARKER event=chat_stream_unsupported model=${request.model}",
                                    )
                                } else {
                                    val response = inferenceHandler.handleChatCompletion(request)
                                    call.respond(response)
                                    HttpTrafficLogger.logResponse(
                                        "/v1/chat/completions",
                                        HttpStatusCode.OK.value,
                                        "$LOG_MARKER event=chat_ok request_id=${response.id} model=${response.model}",
                                    )
                                }
                            } finally {
                                currentConnections.decrementAndGet()
                            }
                        }

                        get("/engines") {
                            if (!call.authorize()) return@get
                            currentConnections.incrementAndGet()
                            try {
                                HttpTrafficLogger.logRequest("GET", "/v1/engines", call.safeHeaders())
                                val models = inferenceHandler.getDownloadedLlmModels()
                                call.respond(
                                    mapOf(
                                        "object" to "list",
                                        "data" to models.map { it.toApiEngine() }
                                    )
                                )
                                HttpTrafficLogger.logResponse(
                                    "/v1/engines",
                                    HttpStatusCode.OK.value,
                                    "$LOG_MARKER event=engines_ok count=${models.size}",
                                )
                            } finally {
                                currentConnections.decrementAndGet()
                            }
                        }
                    }
                }
            }

            server = embedded.engine
            server?.start(wait = false)
            Log.i(TAG, "$LOG_MARKER event=server_started host=${config.host} port=${config.port}")
            HttpTrafficLogger.logDebug(TAG, "$LOG_MARKER event=server_started host=${config.host} port=${config.port}")
        } catch (e: Exception) {
            Log.e(TAG, "$LOG_MARKER event=server_start_failed error=${e.message}", e)
            HttpTrafficLogger.logError("api-server", "$LOG_MARKER event=server_start_failed error=${e.message}")
            server = null
            throw e
        }
    }

    /**
     * Stop the API server
     */
    fun stop() {
        server?.stop(1000, 5000)
        server = null
        Log.i(TAG, "$LOG_MARKER event=server_stopped")
        HttpTrafficLogger.logDebug(TAG, "$LOG_MARKER event=server_stopped")
    }

    private suspend fun ApplicationCall.authorize(): Boolean {
        if (config.authType == AuthType.NONE) return true
        val authorization = request.header(HttpHeaders.Authorization)
        val expected = "Bearer ${config.apiKey}"
        val authorized = config.apiKey.isNotBlank() && authorization == expected
        if (!authorized) {
            Log.w(TAG, "$LOG_MARKER event=auth_failed path=${request.path()} auth_type=${config.authType}")
            HttpTrafficLogger.logError(
                request.path(),
                "$LOG_MARKER event=auth_failed auth_type=${config.authType}",
            )
            respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse(
                    error = "Unauthorized",
                    type = "authentication_error",
                ),
            )
        }
        return authorized
    }

    private fun ApplicationCall.safeHeaders(): String {
        return request.headers.entries().joinToString("\n") { entry ->
            val value = if (entry.key.equals(HttpHeaders.Authorization, ignoreCase = true)) {
                "<redacted>"
            } else {
                entry.value.joinToString(",")
            }
            "${entry.key}: $value"
        }
    }

    /**
     * Check if server is running
     */
    fun isRunning(): Boolean = server != null

    /**
     * Get server uptime in milliseconds
     */
    fun getUptime(): Long = if (startTime.get() > 0) {
        System.currentTimeMillis() - startTime.get()
    } else {
        0
    }

    /**
     * Get current connection count
     */
    fun getCurrentConnections(): Long = currentConnections.get()

    /**
     * Increment connection count
     */
    fun incrementConnections() {
        currentConnections.incrementAndGet()
    }

    /**
     * Decrement connection count
     */
    fun decrementConnections() {
        currentConnections.decrementAndGet()
    }
}
