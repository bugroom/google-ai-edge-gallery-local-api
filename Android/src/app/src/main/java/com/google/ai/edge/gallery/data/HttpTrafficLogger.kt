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
import android.util.Log
import androidx.core.content.edit
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * HTTP traffic logger for monitoring network requests in real-time
 */
object HttpTrafficLogger {
    private const val TAG = "HttpTrafficLogger"
    private const val MAX_LOG_ENTRIES = 1000
    private const val MAX_PERSISTED_LOGS = 100
    private const val PREFS_NAME = "http_traffic_logs"
    private const val KEY_LOGS = "logs"
    private const val KEY_LOG_ENABLED = "logging_enabled"
    
    private lateinit var sharedPreferences: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true }
    
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
        DEBUG,
        CRASH
    }
    
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val logQueue = ConcurrentLinkedQueue<LogEntry>()
    
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()
    
    private val _isLoggingEnabled = MutableStateFlow(true)
    val isLoggingEnabled: StateFlow<Boolean> = _isLoggingEnabled.asStateFlow()
    
    fun initialize(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadPersistedLogs()
        _isLoggingEnabled.value = sharedPreferences.getBoolean(KEY_LOG_ENABLED, true)
        setupCrashHandler()
    }
    
    private fun loadPersistedLogs() {
        try {
            val logsJson = sharedPreferences.getString(KEY_LOGS, null) ?: return
            val persistedLogs = json.decodeFromString<List<PersistedLogEntry>>(logsJson)
            logQueue.addAll(persistedLogs.map { it.toLogEntry() })
            _logs.value = logQueue.toList()
            Log.d(TAG, "Loaded ${persistedLogs.size} persisted logs")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load persisted logs", e)
        }
    }
    
    private fun persistLogs() {
        persistLogsImpl(commit = false)
    }
    
    /**
     * Synchronously commits the current log buffer to disk using
     * SharedPreferences.commit().  Use only for crash-critical paths
     * (uncaught exception handler, logException) where async apply()
     * would risk data loss on immediate process death.
     */
    private fun persistLogsSync() {
        persistLogsImpl(commit = true)
    }
    
    private fun persistLogsImpl(commit: Boolean) {
        try {
            val recentLogs = logQueue.toList().takeLast(MAX_PERSISTED_LOGS)
            val persistedLogs = recentLogs.map { it.toPersistedLogEntry() }
            val logsJson = json.encodeToString(persistedLogs)
            if (commit) {
                sharedPreferences.edit(commit = true) {
                    putString(KEY_LOGS, logsJson)
                    putBoolean(KEY_LOG_ENABLED, _isLoggingEnabled.value)
                }
            } else {
                sharedPreferences.edit {
                    putString(KEY_LOGS, logsJson)
                    putBoolean(KEY_LOG_ENABLED, _isLoggingEnabled.value)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist logs", e)
        }
    }
    
    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val stackTrace = sw.toString()
            
            logCrash("Thread: ${thread.name}", stackTrace)
            persistLogsSync()
            
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
    
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
    
    /**
     * Formats an exception into a compact single-line string including the full
     * cause chain.  Used to persist actionable error details even when the
     * original stack trace is lost.
     */
    fun formatExceptionChain(t: Throwable): String {
        val sb = StringBuilder()
        var current: Throwable? = t
        var depth = 0
        while (current != null && depth < 20) {
            if (depth > 0) sb.append(" ← ")
            sb.append(current.javaClass.simpleName)
            sb.append(": ")
            sb.append(current.message ?: "(no message)")
            current = current.cause
            depth++
        }
        return sb.toString()
    }
    
    /**
     * Logs a full exception with cause chain to the ERROR log, including the
     * stack trace of the root exception.  Suitable for inference failures.
     */
    fun logException(tag: String, url: String, throwable: Throwable) {
        if (!_isLoggingEnabled.value) return
        
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val chain = formatExceptionChain(throwable)
        val detail = "${chain}\n--- stack trace ---\n${sw}"
        
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            type = LogType.ERROR,
            method = tag,
            url = url,
            headers = "",
            body = "",
            error = detail
        )
        addEntry(entry)
        persistLogsSync()  // sync-commit for crash resilience
        Log.e(TAG, "[ERROR] $url: $chain")
    }
    
    fun logCrash(location: String, stackTrace: String) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            type = LogType.CRASH,
            method = location,
            url = "CRASH",
            headers = "",
            body = "",
            error = stackTrace
        )
        addEntry(entry)
        persistLogsSync()  // sync-commit: called from crash handler, must survive
        Log.e(TAG, "[CRASH] $location")
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
        persistLogs()
    }
    
    fun clearLogs() {
        logQueue.clear()
        _logs.value = emptyList()
        sharedPreferences.edit {
            remove(KEY_LOGS)
        }
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
            LogType.CRASH -> {
                """[${formatTimestamp(entry.timestamp)}] CRASH [${entry.method}]
${entry.error}
---"""
            }
        }
    }
    
    @Serializable
    private data class PersistedLogEntry(
        val timestamp: Long,
        val type: String,
        val method: String,
        val url: String,
        val headers: String,
        val body: String,
        val responseCode: Int? = null,
        val responseBody: String? = null,
        val error: String? = null
    )
    
    private fun LogEntry.toPersistedLogEntry(): PersistedLogEntry {
        return PersistedLogEntry(
            timestamp = timestamp,
            type = type.name,
            method = method,
            url = url,
            headers = headers.take(5000), // Limit header size for persistence
            body = body.take(10000),     // Limit body size for persistence
            responseCode = responseCode,
            responseBody = responseBody?.take(10000),
            error = error?.take(10000)
        )
    }
    
    private fun PersistedLogEntry.toLogEntry(): LogEntry {
        return LogEntry(
            timestamp = timestamp,
            type = LogType.valueOf(type),
            method = method,
            url = url,
            headers = headers,
            body = body,
            responseCode = responseCode,
            responseBody = responseBody,
            error = error
        )
    }
}
