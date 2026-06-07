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
import com.google.ai.edge.gallery.data.ApiServerConfig
import com.google.ai.edge.gallery.data.ApiServerConfigManager
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val MANAGER_TAG = "ApiServerManager"

/**
 * Process-level owner for the local API server.
 *
 * The Compose screen and its ViewModel are UI lifecycles. This manager is tied to the application
 * process so the server keeps running while users navigate away from the API settings page.
 */
object ApiServerManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private lateinit var application: Application
    private lateinit var configManager: ApiServerConfigManager

    @Volatile private var initialized = false
    @Volatile private var apiServer: ApiServer? = null

    private val _serverStatus = MutableStateFlow<ServerStatus>(ServerStatus.Stopped)
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()

    private val _serverInfo = MutableStateFlow<ServerInfo?>(null)
    val serverInfo: StateFlow<ServerInfo?> = _serverInfo.asStateFlow()

    fun initialize(application: Application) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            this.application = application
            configManager = ApiServerConfigManager(application)
            initialized = true
            startInfoRefreshLoop()
            syncConfigWithServerState()
            Log.i(MANAGER_TAG, "LOCAL_API event=manager_initialized")
        }
    }

    fun startServer(modelManagerViewModel: ModelManagerViewModel) {
        ensureInitialized()
        scope.launch {
            mutex.withLock {
                try {
                    val config = configManager.configFlow.value
                    val existingServer = apiServer
                    val server = if (existingServer?.isRunning() == true) {
                        Log.i(MANAGER_TAG, "LOCAL_API event=manager_reuse_running_server host=${config.host} port=${config.port}")
                        existingServer
                    } else {
                        createAndStartServer(config, modelManagerViewModel).also { apiServer = it }
                    }

                    _serverStatus.value = ServerStatus.Running
                    configManager.updateConfig { it.copy(enabled = true) }
                    refreshServerInfoNow(server, config)

                    Log.i(MANAGER_TAG, "LOCAL_API event=manager_server_started host=${config.host} port=${config.port}")
                } catch (e: Exception) {
                    Log.e(MANAGER_TAG, "LOCAL_API event=manager_server_start_failed error=${e.message}", e)
                    apiServer = null
                    _serverStatus.value = ServerStatus.Error(e.message ?: "Unknown error")
                    _serverInfo.value = null
                    configManager.updateConfig { it.copy(enabled = false) }
                }
            }
        }
    }

    fun stopServer() {
        ensureInitialized()
        scope.launch {
            mutex.withLock {
                try {
                    apiServer?.stop()
                    apiServer = null
                    _serverStatus.value = ServerStatus.Stopped
                    _serverInfo.value = null
                    configManager.updateConfig { it.copy(enabled = false) }
                    Log.i(MANAGER_TAG, "LOCAL_API event=manager_server_stopped")
                } catch (e: Exception) {
                    Log.e(MANAGER_TAG, "LOCAL_API event=manager_server_stop_failed error=${e.message}", e)
                    _serverStatus.value = ServerStatus.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    fun refreshServerInfo() {
        ensureInitialized()
        scope.launch {
            val config = configManager.configFlow.value
            refreshServerInfoNow(apiServer, config)
        }
    }

    fun isRunning(): Boolean = apiServer?.isRunning() == true

    private fun createAndStartServer(
        config: ApiServerConfig,
        modelManagerViewModel: ModelManagerViewModel,
    ): ApiServer {
        val inferenceHandler = ApiInferenceHandler(
            context = application,
            modelManagerViewModel = modelManagerViewModel,
            maxConcurrent = config.maxConcurrent,
            requestTimeoutMs = config.requestTimeout.coerceAtLeast(180000L),
        )
        return ApiServer(application, config, inferenceHandler).also { it.start() }
    }

    private fun startInfoRefreshLoop() {
        scope.launch {
            while (isActive) {
                val server = apiServer
                val config = configManager.configFlow.value
                refreshServerInfoNow(server, config)
                delay(1000L)
            }
        }
    }

    private fun refreshServerInfoNow(server: ApiServer?, config: ApiServerConfig) {
        if (server?.isRunning() == true) {
            _serverStatus.value = ServerStatus.Running
            _serverInfo.value = ServerInfo(
                host = config.host,
                port = config.port,
                uptime = server.getUptime(),
                connections = server.getCurrentConnections(),
            )
        } else if (_serverStatus.value is ServerStatus.Running) {
            _serverStatus.value = ServerStatus.Stopped
            _serverInfo.value = null
            configManager.updateConfig { it.copy(enabled = false) }
        }
    }

    private fun syncConfigWithServerState() {
        val running = isRunning()
        val currentConfig = configManager.configFlow.value
        if (currentConfig.enabled != running) {
            configManager.updateConfig { it.copy(enabled = running) }
        }
    }

    private fun ensureInitialized() {
        check(initialized) { "ApiServerManager is not initialized" }
    }
}
