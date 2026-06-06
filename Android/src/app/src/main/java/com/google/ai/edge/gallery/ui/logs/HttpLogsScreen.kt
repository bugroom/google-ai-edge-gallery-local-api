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

package com.google.ai.edge.gallery.ui.logs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.google.ai.edge.gallery.data.HttpTrafficLogger
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HttpLogsScreen(
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val logs by HttpTrafficLogger.logs.collectAsState()
    val isLoggingEnabled by HttpTrafficLogger.isLoggingEnabled.collectAsState()
    val listState = rememberLazyListState()
    var copyToast by remember { mutableStateOf<String?>(null) }
    val clipboardManager = LocalClipboardManager.current
    
    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HTTP Traffic Logs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Copy all logs button
                    IconButton(
                        onClick = {
                            val allLogs = logs.joinToString("\n\n") { entry ->
                                HttpTrafficLogger.getFormattedLog(entry)
                            }
                            clipboardManager.setText(AnnotatedString(allLogs))
                            copyToast = "已复制所有日志 (${logs.size} 条)"
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy all logs"
                        )
                    }
                    
                    // Logging toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isLoggingEnabled) "Logging ON" else "Logging OFF",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Switch(
                            checked = isLoggingEnabled,
                            onCheckedChange = { HttpTrafficLogger.setLoggingEnabled(it) }
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Clear logs button
                    IconButton(
                        onClick = { HttpTrafficLogger.clearLogs() }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ClearAll,
                            contentDescription = "Clear logs"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Stats bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total entries: ${logs.size}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = if (isLoggingEnabled) "● Live" else "● Paused",
                    color = if (isLoggingEnabled) Color(0xFF4CAF50) else Color.Gray,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            // Logs list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs) { entry ->
                    LogEntryCard(
                        entry = entry,
                        onClick = {
                            clipboardManager.setText(AnnotatedString(HttpTrafficLogger.getFormattedLog(entry)))
                            copyToast = "已复制日志条目"
                        }
                    )
                }
            }
        }
    }
    
    // Copy success toast
    LaunchedEffect(copyToast) {
        copyToast?.let {
            delay(2000)
            copyToast = null
        }
    }
    
    if (copyToast != null) {
        AlertDialog(
            onDismissRequest = { copyToast = null },
            text = {
                Text(copyToast!!)
            },
            confirmButton = {
                TextButton(onClick = { copyToast = null }) {
                    Text("确定")
                }
            }
        )
    }
}

@Composable
private fun LogEntryCard(
    entry: HttpTrafficLogger.LogEntry,
    onClick: () -> Unit = {}
) {
    val backgroundColor = when (entry.type) {
        HttpTrafficLogger.LogType.REQUEST -> Color(0xFFC8E6C9)
        HttpTrafficLogger.LogType.RESPONSE -> Color(0xFFBBDEFB)
        HttpTrafficLogger.LogType.ERROR -> Color(0xFFFFCDD2)
        HttpTrafficLogger.LogType.DEBUG -> Color(0xFFFFE082)
        HttpTrafficLogger.LogType.CRASH -> Color(0xFFFF5252)
    }
    
    val borderColor = when (entry.type) {
        HttpTrafficLogger.LogType.REQUEST -> Color(0xFF4CAF50)
        HttpTrafficLogger.LogType.RESPONSE -> Color(0xFF2196F3)
        HttpTrafficLogger.LogType.ERROR -> Color(0xFFF44336)
        HttpTrafficLogger.LogType.DEBUG -> Color(0xFFFFC107)
        HttpTrafficLogger.LogType.CRASH -> Color(0xFFD32F2F)
    }
    
    // 使用深色文字提高可读性
    val textColor = Color(0xFF212121)
    val secondaryTextColor = Color(0xFF616161)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .selectable(
                selected = false,
                onClick = onClick
            ),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(2.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header row with timestamp and type
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = HttpTrafficLogger.formatTimestamp(entry.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                
                val typeText = when (entry.type) {
                    HttpTrafficLogger.LogType.REQUEST -> "REQUEST"
                    HttpTrafficLogger.LogType.RESPONSE -> "RESPONSE"
                    HttpTrafficLogger.LogType.ERROR -> "ERROR"
                    HttpTrafficLogger.LogType.DEBUG -> "DEBUG"
                    HttpTrafficLogger.LogType.CRASH -> "CRASH"
                }
                
                Text(
                    text = typeText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = borderColor
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            when (entry.type) {
                HttpTrafficLogger.LogType.REQUEST -> {
                    Text(
                        text = "${entry.method} ${entry.url}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = textColor,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 2
                    )
                    if (entry.body.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Body: ${entry.body.take(200)}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = secondaryTextColor
                            ),
                            maxLines = 3
                        )
                    }
                }
                HttpTrafficLogger.LogType.RESPONSE -> {
                    Text(
                        text = "Status: ${entry.responseCode} - ${entry.url}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = textColor,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 2
                    )
                }
                HttpTrafficLogger.LogType.ERROR -> {
                    Text(
                        text = "Error: ${entry.error}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFFB71C1C), // 错误保持红色文字
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 3
                    )
                }
                HttpTrafficLogger.LogType.DEBUG -> {
                    Text(
                        text = "[${entry.method}] ${entry.body}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = textColor,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 5
                    )
                }
                HttpTrafficLogger.LogType.CRASH -> {
                    Text(
                        text = "Location: ${entry.method}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 2
                    )
                    if (entry.error?.isNotEmpty() == true) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Stack: ${entry.error.take(500)}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 10
                        )
                    }
                }
            }
        }
    }
}
