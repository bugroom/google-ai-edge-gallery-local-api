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
 * Chat completion request (compatible with OpenAI API format)
 */
@Serializable
data class ChatCompletionRequest(
    val model: String = "",
    val messages: List<ChatMessage>,
    val temperature: Double? = null,
    val max_tokens: Int? = null,
    val top_p: Double? = null,
    val top_k: Int? = null,
    val accelerator: String? = null,
    val vision_accelerator: String? = null,
    val stream: Boolean = false,
    val stop: List<String>? = null
)

/**
 * Chat message in conversation
 */
@Serializable
data class ChatMessage(
    val role: String,  // "user", "assistant", "system"
    val content: String
)