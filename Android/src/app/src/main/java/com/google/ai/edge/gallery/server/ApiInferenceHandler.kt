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
import com.google.ai.edge.gallery.common.processLlmResponse
import com.google.ai.edge.gallery.data.Accelerator
import com.google.ai.edge.gallery.data.ConfigKeys
import com.google.ai.edge.gallery.data.HttpTrafficLogger
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.data.api.ChatCompletionRequest
import com.google.ai.edge.gallery.data.api.ChatCompletionResponse
import com.google.ai.edge.gallery.data.api.ChatMessage
import com.google.ai.edge.gallery.data.api.Choice
import com.google.ai.edge.gallery.data.api.Usage
import com.google.ai.edge.gallery.runtime.runtimeHelper
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

private const val TAG = "ApiInferenceHandler"
private const val LOG_MARKER = "LOCAL_API"

class ApiInferenceHandler(
  private val context: Context,
  private val modelManagerViewModel: ModelManagerViewModel,
  maxConcurrent: Int,
  private val requestTimeoutMs: Long,
) {
  private val inferenceSemaphore = Semaphore(maxConcurrent.coerceAtLeast(1))

  fun getDownloadedLlmModels(): List<Model> {
    val models = modelManagerViewModel.getAllDownloadedModels()
    Log.d(TAG, "$LOG_MARKER event=models_list count=${models.size}")
    HttpTrafficLogger.logDebug(TAG, "$LOG_MARKER event=models_list count=${models.size}")
    return models
  }

  suspend fun handleChatCompletion(request: ChatCompletionRequest): ChatCompletionResponse {
    val requestId = "chatcmpl-${UUID.randomUUID()}"
    val startedAt = System.currentTimeMillis()
    Log.i(
      TAG,
      "$LOG_MARKER request_id=$requestId event=chat_start model=${request.model} messages=${request.messages.size}",
    )
    HttpTrafficLogger.logDebug(
      TAG,
      "$LOG_MARKER request_id=$requestId event=chat_start model=${request.model} messages=${request.messages.size}",
    )

    return try {
      withTimeout(requestTimeoutMs) {
        inferenceSemaphore.withPermit {
          val model = requireDownloadedModel(request.model, requestId)
          val task = requireTaskForModel(model, requestId)
          val modelConfigChanged = applyRequestParameters(model, request, requestId)
          ensureInitialized(task, model, requestId, force = modelConfigChanged)
          val output = runModelInference(model, request, requestId)
          val promptTokens = estimateTokens(request.messages.joinToString("\n") { it.content })
          val completionTokens = estimateTokens(output)
          Log.i(
            TAG,
            "$LOG_MARKER request_id=$requestId event=chat_done model=${model.name} duration_ms=${System.currentTimeMillis() - startedAt} completion_chars=${output.length}",
          )
          HttpTrafficLogger.logDebug(
            TAG,
            "$LOG_MARKER request_id=$requestId event=chat_done model=${model.name} duration_ms=${System.currentTimeMillis() - startedAt} completion_chars=${output.length}",
          )
          ChatCompletionResponse(
            id = requestId,
            model = model.name,
            choices =
              listOf(
                Choice(
                  index = 0,
                  message = ChatMessage(role = "assistant", content = output),
                  finish_reason = "stop",
                )
              ),
            usage =
              Usage(
                prompt_tokens = promptTokens,
                completion_tokens = completionTokens,
                total_tokens = promptTokens + completionTokens,
              ),
          )
        }
      }
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "$LOG_MARKER request_id=$requestId event=chat_timeout model=${request.model}", e)
      HttpTrafficLogger.logError(
        "/v1/chat/completions",
        "$LOG_MARKER request_id=$requestId event=chat_timeout model=${request.model}",
      )
      throw IllegalStateException("Request timed out after ${requestTimeoutMs}ms", e)
    } catch (e: Exception) {
      Log.e(TAG, "$LOG_MARKER request_id=$requestId event=chat_error model=${request.model}", e)
      HttpTrafficLogger.logError(
        "/v1/chat/completions",
        "$LOG_MARKER request_id=$requestId event=chat_error model=${request.model} error=${e.message}",
      )
      throw e
    }
  }
  
  suspend fun handleStreamChatCompletion(request: ChatCompletionRequest, onChunk: (String, Boolean) -> Unit) {
    val requestId = "chatcmpl-${UUID.randomUUID()}"
    val startedAt = System.currentTimeMillis()
    Log.i(
      TAG,
      "$LOG_MARKER request_id=$requestId event=chat_stream_start model=${request.model} messages=${request.messages.size}",
    )
    HttpTrafficLogger.logDebug(
      TAG,
      "$LOG_MARKER request_id=$requestId event=chat_stream_start model=${request.model} messages=${request.messages.size}",
    )

    try {
      withTimeout(requestTimeoutMs) {
        inferenceSemaphore.withPermit {
          val model = requireDownloadedModel(request.model, requestId)
          val task = requireTaskForModel(model, requestId)
          val modelConfigChanged = applyRequestParameters(model, request, requestId)
          ensureInitialized(task, model, requestId, force = modelConfigChanged)
          runModelInferenceStream(model, request, requestId, onChunk)
          
          Log.i(
            TAG,
            "$LOG_MARKER request_id=$requestId event=chat_stream_done model=${model.name} duration_ms=${System.currentTimeMillis() - startedAt}",
          )
          HttpTrafficLogger.logDebug(
            TAG,
            "$LOG_MARKER request_id=$requestId event=chat_stream_done model=${model.name} duration_ms=${System.currentTimeMillis() - startedAt}",
          )
        }
      }
    } catch (e: ClientDisconnectedException) {
      Log.i(TAG, "$LOG_MARKER request_id=$requestId event=chat_stream_client_disconnected model=${request.model} reason=${e.message}")
      HttpTrafficLogger.logDebug(
        TAG,
        "$LOG_MARKER request_id=$requestId event=chat_stream_client_disconnected model=${request.model} reason=${e.message}",
      )
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "$LOG_MARKER request_id=$requestId event=chat_stream_timeout model=${request.model}", e)
      HttpTrafficLogger.logError(
        "/v1/chat/completions",
        "$LOG_MARKER request_id=$requestId event=chat_stream_timeout model=${request.model}",
      )
      throw IllegalStateException("Request timed out after ${requestTimeoutMs}ms", e)
    } catch (e: Exception) {
      Log.e(TAG, "$LOG_MARKER request_id=$requestId event=chat_stream_error model=${request.model}", e)
      HttpTrafficLogger.logError(
        "/v1/chat/completions",
        "$LOG_MARKER request_id=$requestId event=chat_stream_error model=${request.model} error=${e.message}",
      )
      throw e
    }
  }

  private fun requireDownloadedModel(modelId: String, requestId: String): Model {
    val model = modelManagerViewModel.getModelByName(modelId)
    if (model == null) {
      Log.w(TAG, "$LOG_MARKER request_id=$requestId event=model_not_found model=$modelId")
      HttpTrafficLogger.logError(
        "/v1/chat/completions",
        "$LOG_MARKER request_id=$requestId event=model_not_found model=$modelId",
      )
      throw IllegalArgumentException("Model '$modelId' was not found")
    }
    val status = modelManagerViewModel.uiState.value.modelDownloadStatus[model.name]?.status
    if (status != ModelDownloadStatusType.SUCCEEDED) {
      Log.w(
        TAG,
        "$LOG_MARKER request_id=$requestId event=model_not_downloaded model=${model.name} status=$status",
      )
      HttpTrafficLogger.logError(
        "/v1/chat/completions",
        "$LOG_MARKER request_id=$requestId event=model_not_downloaded model=${model.name} status=$status",
      )
      throw IllegalArgumentException("Model '${model.name}' is not downloaded")
    }
    if (!model.isLlm) {
      Log.w(TAG, "$LOG_MARKER request_id=$requestId event=model_not_llm model=${model.name}")
      HttpTrafficLogger.logError(
        "/v1/chat/completions",
        "$LOG_MARKER request_id=$requestId event=model_not_llm model=${model.name}",
      )
      throw IllegalArgumentException("Model '${model.name}' is not an LLM model")
    }
    return model
  }

  private fun requireTaskForModel(model: Model, requestId: String): Task {
    val task = modelManagerViewModel.uiState.value.tasks.firstOrNull { task ->
      task.models.any { it.name == model.name }
    }
    if (task == null) {
      Log.w(TAG, "$LOG_MARKER request_id=$requestId event=task_not_found model=${model.name}")
      HttpTrafficLogger.logError(
        "/v1/chat/completions",
        "$LOG_MARKER request_id=$requestId event=task_not_found model=${model.name}",
      )
      throw IllegalStateException("No task is available for model '${model.name}'")
    }
    return task
  }

  private fun applyRequestParameters(model: Model, request: ChatCompletionRequest, requestId: String): Boolean {
    val accelerator = request.accelerator ?: Accelerator.GPU.label
    val visionAccelerator = request.vision_accelerator ?: Accelerator.GPU.label
    val validAccelerators = Accelerator.values().map { it.label }.toSet()
    if (accelerator !in validAccelerators) {
      throw IllegalArgumentException("Unsupported accelerator '$accelerator'")
    }
    if (visionAccelerator !in validAccelerators) {
      throw IllegalArgumentException("Unsupported vision accelerator '$visionAccelerator'")
    }
    if (model.accelerators.isNotEmpty() && model.accelerators.none { it.label == accelerator }) {
      throw IllegalArgumentException("Accelerator '$accelerator' is not compatible with model '${model.name}'")
    }
    val updates = mapOf(
      ConfigKeys.TEMPERATURE.label to (request.temperature ?: 0.7).toFloat(),
      ConfigKeys.MAX_TOKENS.label to (request.max_tokens ?: 1024),
      ConfigKeys.TOPP.label to (request.top_p ?: 0.95).toFloat(),
      ConfigKeys.TOPK.label to (request.top_k ?: 40),
      ConfigKeys.ACCELERATOR.label to accelerator,
      ConfigKeys.VISION_ACCELERATOR.label to visionAccelerator,
    )
    var changed = false
    val updatedValues = model.configValues.toMutableMap()
    updates.forEach { (key, value) ->
      if (updatedValues[key] != value) {
        updatedValues[key] = value
        changed = true
      }
    }
    if (changed) {
      model.prevConfigValues = model.configValues
      model.configValues = updatedValues
      modelManagerViewModel.updateConfigValuesUpdateTrigger()
      Log.i(
        TAG,
        "$LOG_MARKER request_id=$requestId event=model_params_applied model=${model.name} temperature=${request.temperature} max_tokens=${request.max_tokens} top_p=${request.top_p} top_k=${request.top_k} accelerator=$accelerator vision_accelerator=$visionAccelerator",
      )
      HttpTrafficLogger.logDebug(
        TAG,
        "$LOG_MARKER request_id=$requestId event=model_params_applied model=${model.name}",
      )
    }
    return changed
  }

  private suspend fun ensureInitialized(task: Task, model: Model, requestId: String, force: Boolean = false) {
    if (!force && model.instance != null) {
      Log.d(TAG, "$LOG_MARKER request_id=$requestId event=model_already_initialized model=${model.name}")
      HttpTrafficLogger.logDebug(
        TAG,
        "$LOG_MARKER request_id=$requestId event=model_already_initialized model=${model.name}",
      )
      return
    }
    val deferred = CompletableDeferred<Unit>()
    Log.i(TAG, "$LOG_MARKER request_id=$requestId event=model_init_start model=${model.name} task=${task.id}")
    HttpTrafficLogger.logDebug(
      TAG,
      "$LOG_MARKER request_id=$requestId event=model_init_start model=${model.name} task=${task.id}",
    )
    modelManagerViewModel.initializeModel(
      context = context,
      task = task,
      model = model,
      force = force,
      onDone = {
        Log.i(TAG, "$LOG_MARKER request_id=$requestId event=model_init_done model=${model.name}")
        HttpTrafficLogger.logDebug(
          TAG,
          "$LOG_MARKER request_id=$requestId event=model_init_done model=${model.name}",
        )
        deferred.complete(Unit)
      },
      onError = { error ->
        Log.e(TAG, "$LOG_MARKER request_id=$requestId event=model_init_error model=${model.name} error=$error")
        HttpTrafficLogger.logError(
          "/v1/chat/completions",
          "$LOG_MARKER request_id=$requestId event=model_init_error model=${model.name} error=$error",
        )
        deferred.completeExceptionally(IllegalStateException(error))
      },
    )
    deferred.await()
  }

  private suspend fun runModelInference(
    model: Model,
    request: ChatCompletionRequest,
    requestId: String,
  ): String {
    val deferred = CompletableDeferred<String>()
    val response = StringBuilder()
    val prompt = buildPrompt(request)
    Log.d(TAG, "$LOG_MARKER request_id=$requestId event=inference_start model=${model.name}")
    HttpTrafficLogger.logDebug(TAG, "$LOG_MARKER request_id=$requestId event=inference_start model=${model.name}")

    model.runtimeHelper.resetConversation(model = model)
    model.runtimeHelper.runInference(
      model = model,
      input = prompt,
      resultListener = { partialResult, done, _ ->
        if (partialResult.isNotEmpty()) {
          response.append(partialResult)
        }
        if (done && !deferred.isCompleted) {
          deferred.complete(processLlmResponse(response.toString()))
        }
      },
      cleanUpListener = {
        if (!deferred.isCompleted) {
          deferred.complete(processLlmResponse(response.toString()))
        }
      },
      onError = { error ->
        HttpTrafficLogger.logError(
          "/v1/chat/completions",
          "$LOG_MARKER request_id=$requestId event=inference_error model=${model.name} error=$error",
        )
        if (!deferred.isCompleted) {
          deferred.completeExceptionally(IllegalStateException(error))
        }
      },
    )

    return deferred.await()
  }
  
  private suspend fun runModelInferenceStream(
    model: Model,
    request: ChatCompletionRequest,
    requestId: String,
    onChunk: (String, Boolean) -> Unit,
  ) {
    val deferred = CompletableDeferred<Unit>()
    val prompt = buildPrompt(request)
    Log.d(TAG, "$LOG_MARKER request_id=$requestId event=inference_stream_start model=${model.name}")
    HttpTrafficLogger.logDebug(TAG, "$LOG_MARKER request_id=$requestId event=inference_stream_start model=${model.name}")

    model.runtimeHelper.resetConversation(model = model)
    model.runtimeHelper.runInference(
      model = model,
      input = prompt,
      resultListener = { partialResult, done, _ ->
        if (partialResult.isNotEmpty()) {
          onChunk(partialResult, done)
        }
        if (done && !deferred.isCompleted) {
          deferred.complete(Unit)
        }
      },
      cleanUpListener = {
        if (!deferred.isCompleted) {
          deferred.complete(Unit)
        }
      },
      onError = { error ->
        HttpTrafficLogger.logError(
          "/v1/chat/completions",
          "$LOG_MARKER request_id=$requestId event=inference_stream_error model=${model.name} error=$error",
        )
        if (!deferred.isCompleted) {
          deferred.completeExceptionally(IllegalStateException(error))
        }
      },
    )

    deferred.await()
  }

  private fun buildPrompt(request: ChatCompletionRequest): String {
    return buildString {
      request.messages.forEach { message ->
        append(message.role.lowercase())
        append(": ")
        appendLine(message.content)
      }
      append("assistant:")
    }
  }

  private fun estimateTokens(text: String): Int = (text.length / 4).coerceAtLeast(1)
}
