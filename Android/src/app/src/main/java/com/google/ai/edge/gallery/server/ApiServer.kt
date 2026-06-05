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
import com.google.ai.edge.gallery.data.api.HealthResponse
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.cio.CIO
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.path
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

/**
 * Local API server for serving model inference
 */
class ApiServer(
    private val context: Context,
    private val config: ApiServerConfig
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
                    exception<Exception> { call, cause ->
                        Log.e(TAG, "Request error: ${cause.message}", cause)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to (cause.message ?: "Internal error"))
                        )
                    }
                }

                routing {
                    // Health check endpoint
                    get("/health") {
                        call.respond(
                            HealthResponse(
                                status = "ok",
                                uptime = System.currentTimeMillis() - startTime.get(),
                                connections = currentConnections.get().toInt(),
                                loaded_model = null  // TODO: Get loaded model
                            )
                        )
                    }

                    // OpenAI compatible API
                    route("/v1") {
                        get("/models") {
                            call.respond(
                                mapOf(
                                    "object" to "list",
                                    "data" to emptyList<Any>()  // TODO: Get downloaded models
                                )
                            )
                        }

                        post("/chat/completions") {
                            call.respond(
                                mapOf(
                                    "id" to "chatcmpl-placeholder",
                                    "object" to "chat.completion",
                                    "created" to System.currentTimeMillis() / 1000,
                                    "model" to "placeholder",
                                    "choices" to emptyList<Any>(),
                                    "usage" to mapOf(
                                        "prompt_tokens" to 0,
                                        "completion_tokens" to 0,
                                        "total_tokens" to 0
                                    )
                                )
                            )
                        }

                        get("/engines") {
                            call.respond(
                                mapOf(
                                    "object" to "list",
                                    "data" to emptyList<Any>()  // TODO: Get engines
                                )
                            )
                        }
                    }
                }
            }

            server = embedded.engine
            server?.start(wait = false)
            Log.i(TAG, "API server started on ${config.host}:${config.port}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server: ${e.message}", e)
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
        Log.i(TAG, "API server stopped")
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

/**
 * Application plugin configuration
 */
fun Application.configureApiServer() {
    // Additional server configuration if needed
}
