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

package com.google.ai.edge.gallery.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.data.ApiServerConfig
import com.google.ai.edge.gallery.data.AuthType
import com.google.ai.edge.gallery.data.displayName
import com.google.ai.edge.gallery.server.ApiServerViewModel
import com.google.ai.edge.gallery.server.ServerStatus
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/**
 * API Server Settings Screen
 */
@Composable
fun ApiServerSettingsScreen(
    modelManagerViewModel: ModelManagerViewModel,
    viewModel: ApiServerViewModel = hiltViewModel()
) {
    val serverStatus by viewModel.serverStatus.collectAsState()
    val serverInfo by viewModel.serverInfo.collectAsState()
    val config by viewModel.configFlow.collectAsState()
    val lanIp = remember { getLanIpAddress() }

    var customPort by remember(config.port) { mutableStateOf(config.port.toString()) }
    var customApiKey by remember(config.apiKey) { mutableStateOf(config.apiKey) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "本地API服务",
            style = MaterialTheme.typography.headlineMedium
        )

        // Service Toggle
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "启用API服务",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "允许其他设备调用已下载的模型",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = config.enabled,
                onCheckedChange = { enabled: Boolean ->
                    viewModel.updateConfig { currentConfig: ApiServerConfig ->
                        currentConfig.copy(enabled = enabled)
                    }
                    if (enabled) {
                        viewModel.startServer(modelManagerViewModel)
                    } else {
                        viewModel.stopServer()
                    }
                }
            )
        }

        // Status Card
        if (config.enabled) {
            when (serverStatus) {
                is ServerStatus.Running -> {
                    StatusCard(
                        status = "运行中",
                        host = config.host,
                        lanIp = lanIp,
                        port = config.port,
                        uptime = serverInfo?.uptime ?: 0,
                        connections = serverInfo?.connections ?: 0,
                        isRunning = true
                    )
                }
                is ServerStatus.Stopped -> {
                    StatusCard(
                        status = "已停止",
                        host = config.host,
                        lanIp = lanIp,
                        port = config.port,
                        uptime = 0,
                        connections = 0,
                        isRunning = false
                    )
                }
                is ServerStatus.Error -> {
                    StatusCard(
                        status = "错误",
                        host = config.host,
                        lanIp = lanIp,
                        port = config.port,
                        uptime = 0,
                        connections = 0,
                        isRunning = false,
                        error = (serverStatus as ServerStatus.Error).message
                    )
                }
            }
        }

        // Port Configuration
        OutlinedTextField(
            value = customPort,
            onValueChange = { newValue: String ->
                customPort = newValue
                val port = newValue.toIntOrNull() ?: 8080
                viewModel.updateConfig { currentConfig: ApiServerConfig ->
                    currentConfig.copy(port = port)
                }
            },
            label = { Text("端口") },
            enabled = !config.enabled,
            modifier = Modifier.fillMaxWidth()
        )

        // Authentication Type
        Column {
            Text(
                text = "认证方式",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AuthType.values().forEach { type ->
                    val isSelected = config.authType == type
                    Button(
                        onClick = {
                            viewModel.updateConfig { currentConfig: ApiServerConfig ->
                                currentConfig.copy(authType = type)
                            }
                        },
                        enabled = !config.enabled,
                        colors = if (isSelected) {
                            androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
                            )
                        } else {
                            androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                        }
                    ) {
                        Text(type.displayName)
                    }
                }
            }
        }

        // API Key (only when API_KEY auth is selected)
        if (config.authType == AuthType.API_KEY) {
OutlinedTextField(
            value = customApiKey,
            onValueChange = { newValue: String ->
                customApiKey = newValue
                viewModel.updateConfig { currentConfig: ApiServerConfig ->
                    currentConfig.copy(apiKey = newValue)
                }
            },
            label = { Text("API Key") },
            trailingIcon = {
                IconButton(onClick = {
                    val newKey = viewModel.generateApiKey()
                    customApiKey = newKey
                    viewModel.updateConfig { currentConfig: ApiServerConfig ->
                        currentConfig.copy(apiKey = newKey)
                    }
                }) {
                    Icon(Icons.Default.Refresh, "生成随机Key")
                }
            },
            enabled = !config.enabled,
            modifier = Modifier.fillMaxWidth()
        )
        }

        // Access Control
        Column {
            Text(
                text = "允许的客户端",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = config.host == "127.0.0.1" ||
                                  config.host.contains("127.0.0.1"),
                        onCheckedChange = { checked: Boolean ->
                            viewModel.updateConfig { currentConfig: ApiServerConfig ->
                                currentConfig.copy(
                                    host = if (checked) "127.0.0.1" else "0.0.0.0"
                                )
                            }
                        },
                        enabled = !config.enabled
                    )
                    Text("本地 (127.0.0.1)")
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = config.host == "0.0.0.0",
                        onCheckedChange = { checked: Boolean ->
                            viewModel.updateConfig { currentConfig: ApiServerConfig ->
                                currentConfig.copy(
                                    host = if (checked) "0.0.0.0" else "127.0.0.1"
                                )
                            }
                        },
                        enabled = !config.enabled
                    )
                    Text("局域网 (0.0.0.0)")
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    status: String,
    host: String,
    lanIp: String?,
    port: Int,
    uptime: Long,
    connections: Long,
    isRunning: Boolean,
    error: String? = null
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = when {
                                error != null -> Color.Red
                                isRunning -> Color.Green
                                else -> Color.Gray
                            },
                            shape = RectangleShape
                        )
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            Text("端口: $port")
            if (host == "0.0.0.0") {
                Text("本机地址: http://127.0.0.1:$port")
                Text("局域网地址: http://${lanIp ?: "设备IP"}:$port")
            } else {
                Text("地址: http://127.0.0.1:$port")
            }
            Text("访问范围: ${if (host == "0.0.0.0") "局域网" else "仅本机"}")
            
            if (isRunning) {
                Text("运行时间: ${uptime / 1000}秒")
                Text("当前连接: $connections")
            }
            
            error?.let {
                Text(
                    text = "错误: $it",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun getLanIpAddress(): String? {
    return runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { Collections.list(it.inetAddresses).asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    }.getOrNull()
}
