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

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * HTTP traffic logger for monitoring network requests in real-time
 */
object HttpTrafficLogger {
    private const val TAG = "HttpTrafficLogger"
    private const val MAX_LOG_ENTRIES = 1000
    
    data class LogEntry(
        val timestamp: Long,
        val type: LogType,
        val method: String,
        val url: String,
        val headers: String,
        val body: String,
        val responseCode: Int? = null,
        val responseBody: String? = null,
        val error: String? = null
    )
    
    enum class LogType {
        REQUEST,
        RESPONSE,
        ERROR,
        DEBUG
    }
    
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val logQueue = ConcurrentLinkedQueue<LogEntry>()
    
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()
    
    private val _isLoggingEnabled = MutableStateFlow(true)
    val isLoggingEnabled: StateFlow<Boolean> = _isLoggingEnabled.asStateFlow()
    
    fun logRequest(method: String, url: String, headers: String, body: String = "") {
        if (!_isLoggingEnabled.value) return
        
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            type = LogType.REQUEST,
            method = method,
            url = url,
            headers = headers,
            body = body
        )
        addEntry(entry)
        Log.d(TAG, "[$method] $url")
    }
    
    fun logResponse(url: String, responseCode: Int, responseBody: String, headers: String = "") {
        if (!_isLoggingEnabled.value) return
        
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            type = LogType.RESPONSE,
            method = "",
            url = url,
            headers = headers,
            body = "",
            responseCode = responseCode,
            responseBody = responseBody
        )
        addEntry(entry)
        Log.d(TAG, "[RESPONSE $responseCode] $url")
    }
    
    fun logError(url: String, error: String) {
        if (!_isLoggingEnabled.value) return
        
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            type = LogType.ERROR,
            method = "",
            url = url,
            headers = "",
            body = "",
            error = error
        )
        addEntry(entry)
        Log.e(TAG, "[ERROR] $url: $error")
    }
    
    fun logDebug(tag: String, message: String) {
        if (!_isLoggingEnabled.value) return
        
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            type = LogType.DEBUG,
            method = tag,
            url = "",
            headers = "",
            body = message
        )
        addEntry(entry)
        Log.d(tag, message)
    }
    
    private fun addEntry(entry: LogEntry) {
        logQueue.offer(entry)
        
        // Remove old entries if queue is too large
        while (logQueue.size > MAX_LOG_ENTRIES) {
            logQueue.poll()
        }
        
        _logs.value = logQueue.toList()
    }
    
    fun clearLogs() {
        logQueue.clear()
        _logs.value = emptyList()
    }
    
    fun setLoggingEnabled(enabled: Boolean) {
        _isLoggingEnabled.value = enabled
    }
    
    fun formatTimestamp(timestamp: Long): String {
        return dateFormat.format(Date(timestamp))
    }
    
    fun getFormattedLog(entry: LogEntry): String {
        return when (entry.type) {
            LogType.REQUEST -> {
                """[${formatTimestamp(entry.timestamp)}] REQUEST ${entry.method}
URL: ${entry.url}
Headers: ${entry.headers}
Body: ${entry.body}
---"""
            }
            LogType.RESPONSE -> {
                """[${formatTimestamp(entry.timestamp)}] RESPONSE ${entry.responseCode}
URL: ${entry.url}
Body: ${entry.responseBody?.take(500) ?: ""}... 
---"""
            }
            LogType.ERROR -> {
                """[${formatTimestamp(entry.timestamp)}] ERROR
URL: ${entry.url}
Error: ${entry.error}
---"""
            }
            LogType.DEBUG -> {
                """[${formatTimestamp(entry.timestamp)}] DEBUG [${entry.method}]
${entry.body}
---"""
            }
        }
    }
}
