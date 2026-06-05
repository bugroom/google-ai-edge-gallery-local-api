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

package com.google.ai.edge.gallery.data.api

import kotlinx.serialization.Serializable

/**
 * Health check response
 */
@Serializable
data class HealthResponse(
    val status: String,
    val uptime: Long,
    val connections: Int,
    val loaded_model: String?
)

/**
 * Error response
 */
@Serializable
data class ErrorResponse(
    val error: String,
    val `type`: String,
    val code: String? = null
)