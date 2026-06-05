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

package com.google.ai.edge.gallery.data

/**
 * Configuration for local API server
 */
data class ApiServerConfig(
    val enabled: Boolean = false,
    val host: String = "127.0.0.1",  // Default: local only
    val port: Int = 8080,
    val authType: AuthType = AuthType.NONE,
    val apiKey: String = "",
    val maxConcurrent: Int = 2,
    val queueSize: Int = 10,
    val requestTimeout: Long = 30000L  // 30 seconds
)

/**
 * Authentication type for API server
 */
enum class AuthType {
    NONE,       // No authentication (local only)
    API_KEY,    // API Key authentication
    CUSTOM      // Custom token authentication
}

/**
 * Extension property for display name
 */
val AuthType.displayName: String
    get() = when (this) {
        AuthType.NONE -> "无认证"
        AuthType.API_KEY -> "API Key"
        AuthType.CUSTOM -> "自定义Token"
    }