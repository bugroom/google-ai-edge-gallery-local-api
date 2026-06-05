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

import com.google.ai.edge.gallery.data.Model
import kotlinx.serialization.Serializable

/**
 * Models list response (compatible with OpenAI API format)
 */
@Serializable
data class ModelsResponse(
    val `object`: String = "list",
    val data: List<ApiModel>
)

/**
 * Model information for API
 */
@Serializable
data class ApiModel(
    val id: String,
    val `object`: String = "model",
    val owned_by: String,
    val created: Long = System.currentTimeMillis() / 1000
)

/**
 * Extension function to convert Model to ApiModel
 */
fun Model.toApiModel(): ApiModel {
    return ApiModel(
        id = name,
        owned_by = when {
            name.startsWith("Gemma") -> "google"
            name.startsWith("Qwen") -> "litert-community"
            else -> "community"
        }
    )
}

/**
 * Engine information for API (compatible with OpenAI format)
 */
@Serializable
data class ApiEngine(
    val id: String,
    val `object`: String = "engine",
    val owner: String = "organization-owner",
    val ready: Boolean = true
)

/**
 * Extension function to convert Model to ApiEngine
 */
fun Model.toApiEngine(): ApiEngine {
    return ApiEngine(
        id = name
    )
}