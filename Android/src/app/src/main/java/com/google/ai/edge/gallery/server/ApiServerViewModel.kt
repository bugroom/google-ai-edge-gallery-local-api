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

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.ApiServerConfig
import com.google.ai.edge.gallery.data.ApiServerConfigManager
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "ApiServerViewModel"

/**
 * ViewModel for managing API server
 */
class ApiServerViewModel(application: Application) : AndroidViewModel(application) {

    private val configManager = ApiServerConfigManager(application)
    private var apiServer: ApiServer? = null

    private val _serverStatus = MutableStateFlow<ServerStatus>(ServerStatus.Stopped)
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()

    private val _serverInfo = MutableStateFlow<ServerInfo?>(null)
    val serverInfo: StateFlow<ServerInfo?> = _serverInfo.asStateFlow()

    val configFlow = configManager.configFlow

    init {
        loadConfig()
    }

    /**
     * Load configuration and check if server should be auto-started
     */
    private fun loadConfig() {
        // Auto-start not needed for now, manual control via UI
    }

    /**
     * Update API server configuration
     */
    fun updateConfig(update: (ApiServerConfig) -> ApiServerConfig) {
        configManager.updateConfig(update)
    }

    /**
     * Start the API server
     */
    fun startServer(modelManagerViewModel: ModelManagerViewModel) {
        viewModelScope.launch {
            try {
                val config = configManager.configFlow.value
                val inferenceHandler = ApiInferenceHandler(
                    context = getApplication(),
                    modelManagerViewModel = modelManagerViewModel,
                    maxConcurrent = config.maxConcurrent,
                    requestTimeoutMs = config.requestTimeout,
                )
                apiServer = ApiServer(getApplication(), config, inferenceHandler)
                apiServer?.start()
                _serverStatus.value = ServerStatus.Running
                
                val info = ServerInfo(
                    host = config.host,
                    port = config.port,
                    uptime = 0L,
                    connections = 0L
                )
                _serverInfo.value = info
                
                Log.i(TAG, "LOCAL_API event=viewmodel_server_started host=${config.host} port=${config.port}")
            } catch (e: Exception) {
                Log.e(TAG, "LOCAL_API event=viewmodel_server_start_failed error=${e.message}", e)
                _serverStatus.value = ServerStatus.Error(e.message ?: "Unknown error")
                apiServer = null
            }
        }
    }

    /**
     * Stop the API server
     */
    fun stopServer() {
        viewModelScope.launch {
            try {
                apiServer?.stop()
                apiServer = null
                _serverStatus.value = ServerStatus.Stopped
                _serverInfo.value = null
                Log.i(TAG, "LOCAL_API event=viewmodel_server_stopped")
                
                // Update config to disabled
                configManager.updateConfig { it.copy(enabled = false) }
            } catch (e: Exception) {
                Log.e(TAG, "LOCAL_API event=viewmodel_server_stop_failed error=${e.message}", e)
                _serverStatus.value = ServerStatus.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Generate a new API key
     */
    fun generateApiKey(): String {
        return configManager.generateApiKey()
    }

    /**
     * Get current server info
     */
    fun refreshServerInfo() {
        viewModelScope.launch {
            val config = configManager.configFlow.value
            val server = apiServer ?: return@launch
            val info = ServerInfo(
                host = config.host,
                port = config.port,
                uptime = server.getUptime(),
                connections = server.getCurrentConnections()
            )
            _serverInfo.value = info
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopServer()
    }
}

/**
 * Server status
 */
sealed class ServerStatus {
    object Stopped : ServerStatus()
    object Running : ServerStatus()
    data class Error(val message: String) : ServerStatus()
}

/**
 * Server information
 */
data class ServerInfo(
    val host: String,
    val port: Int,
    val uptime: Long,
    val connections: Long
)
