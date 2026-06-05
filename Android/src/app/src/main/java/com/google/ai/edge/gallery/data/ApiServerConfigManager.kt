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

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manager for API server configuration
 */
class ApiServerConfigManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("api_server_config", Context.MODE_PRIVATE)
    
    private val _configFlow = MutableStateFlow(loadConfig())
    val configFlow: StateFlow<ApiServerConfig> = _configFlow.asStateFlow()

    private object PreferenceKeys {
        const val ENABLED = "enabled"
        const val HOST = "host"
        const val PORT = "port"
        const val AUTH_TYPE = "auth_type"
        const val API_KEY = "api_key"
        const val MAX_CONCURRENT = "max_concurrent"
        const val QUEUE_SIZE = "queue_size"
        const val REQUEST_TIMEOUT = "request_timeout"
    }

    private fun loadConfig(): ApiServerConfig {
        return ApiServerConfig(
            enabled = sharedPreferences.getBoolean(PreferenceKeys.ENABLED, false),
            host = sharedPreferences.getString(PreferenceKeys.HOST, "127.0.0.1") ?: "127.0.0.1",
            port = sharedPreferences.getInt(PreferenceKeys.PORT, 8080),
            authType = try {
                AuthType.valueOf(
                    sharedPreferences.getString(PreferenceKeys.AUTH_TYPE, AuthType.NONE.name) ?: AuthType.NONE.name
                )
            } catch (e: Exception) {
                AuthType.NONE
            },
            apiKey = sharedPreferences.getString(PreferenceKeys.API_KEY, "") ?: "",
            maxConcurrent = sharedPreferences.getInt(PreferenceKeys.MAX_CONCURRENT, 2),
            queueSize = sharedPreferences.getInt(PreferenceKeys.QUEUE_SIZE, 10),
            requestTimeout = sharedPreferences.getLong(PreferenceKeys.REQUEST_TIMEOUT, 30000L)
        )
    }

    /**
     * Save API server configuration
     */
    fun saveConfig(config: ApiServerConfig) {
        sharedPreferences.edit {
            putBoolean(PreferenceKeys.ENABLED, config.enabled)
            putString(PreferenceKeys.HOST, config.host)
            putInt(PreferenceKeys.PORT, config.port)
            putString(PreferenceKeys.AUTH_TYPE, config.authType.name)
            putString(PreferenceKeys.API_KEY, config.apiKey)
            putInt(PreferenceKeys.MAX_CONCURRENT, config.maxConcurrent)
            putInt(PreferenceKeys.QUEUE_SIZE, config.queueSize)
            putLong(PreferenceKeys.REQUEST_TIMEOUT, config.requestTimeout)
        }
        _configFlow.value = config
    }

    /**
     * Update a single configuration field
     */
    fun updateConfig(update: (ApiServerConfig) -> ApiServerConfig) {
        val currentConfig = loadConfig()
        val updatedConfig = update(currentConfig)
        saveConfig(updatedConfig)
    }

    /**
     * Generate a random API key
     */
    fun generateApiKey(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..32)
            .map { chars.random() }
            .joinToString("")
    }
}