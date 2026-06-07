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
import androidx.lifecycle.AndroidViewModel
import com.google.ai.edge.gallery.data.ApiServerConfig
import com.google.ai.edge.gallery.data.ApiServerConfigManager
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel bridge for API server UI.
 *
 * The server is owned by [ApiServerManager] at application scope so it survives navigation away from
 * this screen. This ViewModel only exposes configuration and delegates start/stop commands.
 */
class ApiServerViewModel(application: Application) : AndroidViewModel(application) {

    private val configManager = ApiServerConfigManager(application)

    val serverStatus: StateFlow<ServerStatus> = ApiServerManager.serverStatus
    val serverInfo: StateFlow<ServerInfo?> = ApiServerManager.serverInfo
    val configFlow = configManager.configFlow

    init {
        ApiServerManager.initialize(application)
    }

    fun updateConfig(update: (ApiServerConfig) -> ApiServerConfig) {
        configManager.updateConfig(update)
    }

    fun startServer(modelManagerViewModel: ModelManagerViewModel) {
        ApiServerManager.startServer(modelManagerViewModel)
    }

    fun stopServer() {
        ApiServerManager.stopServer()
    }

    fun generateApiKey(): String {
        return configManager.generateApiKey()
    }

    fun refreshServerInfo() {
        ApiServerManager.refreshServerInfo()
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
    val connections: Long,
)
