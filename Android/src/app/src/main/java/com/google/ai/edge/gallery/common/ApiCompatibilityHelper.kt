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

package com.google.ai.edge.gallery.common

import android.content.Context
import android.os.Build
import android.view.Window
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * API 兼容性工具类
 * 
 * 处理 Android 9.0 (API 28) 到 Android 12+ (API 31+) 的差异
 */
object ApiCompatibilityHelper {

  /**
   * 检查是否为 Android 12+ (API 31+)
   */
  @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
  fun isAndroid12OrAbove(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
  }

  /**
   * 检查是否为 Android 11+ (API 30+)
   */
  @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
  fun isAndroid11OrAbove(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
  }

  /**
   * 检查是否为 Android 10+ (API 29+)
   */
  @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
  fun isAndroid10OrAbove(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
  }

  /**
   * 获取设备性能等级
   */
  fun getDevicePerformanceLevel(context: Context): DevicePerformanceLevel {
    val memoryInfo = android.app.ActivityManager.MemoryInfo()
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    activityManager.getMemoryInfo(memoryInfo)
    
    val totalMemory = memoryInfo.totalMem
    
    return when {
      totalMemory >= 6L * 1024 * 1024 * 1024 -> DevicePerformanceLevel.HIGH    // 6GB+
      totalMemory >= 4L * 1024 * 1024 * 1024 -> DevicePerformanceLevel.MEDIUM  // 4GB+
      else -> DevicePerformanceLevel.LOW                                        // <4GB
    }
  }

  /**
   * 启用 Edge-to-Edge 显示（兼容 API 28+）
   */
  fun enableEdgeToEdge(window: Window) {
    if (isAndroid12OrAbove()) {
      // Android 12+ 原生支持
      WindowCompat.setDecorFitsSystemWindows(window, false)
    } else {
      // Android 9-11 使用兼容性方案
      WindowCompat.setDecorFitsSystemWindows(window, false)
      
      // 设置系统栏颜色为透明
      window.statusBarColor = android.graphics.Color.TRANSPARENT
      window.navigationBarColor = android.graphics.Color.TRANSPARENT
    }
    
    // 设置系统栏图标颜色
    WindowInsetsControllerCompat(window, window.decorView).apply {
      isAppearanceLightStatusBars = true
      isAppearanceLightNavigationBars = true
    }
  }

  /**
   * 设置导航栏对比度（Android 10+）
   */
  fun setNavigationBarContrast(window: Window, enforce: Boolean) {
    if (isAndroid10OrAbove()) {
      window.isNavigationBarContrastEnforced = enforce
    }
  }

  /**
   * 获取推荐的模型配置
   */
  fun getRecommendedModelConfig(context: Context): ModelConfig {
    val performanceLevel = getDevicePerformanceLevel(context)
    val sdkVersion = Build.VERSION.SDK_INT
    
    return when {
      // 高端设备 + Android 12+
      performanceLevel == DevicePerformanceLevel.HIGH && isAndroid12OrAbove() -> {
        ModelConfig(
          maxModelSize = 4L * 1024 * 1024 * 1024,  // 4GB
          supportedModels = listOf("Gemma-3n-E4B", "Gemma-3n-E2B", "Gemma3-1B"),
          enableGPUAcceleration = true,
          enableThinkingMode = true,
          maxContextLength = 4096
        )
      }
      // 中端设备 + Android 10+
      performanceLevel == DevicePerformanceLevel.MEDIUM && sdkVersion >= Build.VERSION_CODES.Q -> {
        ModelConfig(
          maxModelSize = 2L * 1024 * 1024 * 1024,  // 2GB
          supportedModels = listOf("Gemma3-1B", "Qwen2.5-1.5B"),
          enableGPUAcceleration = true,
          enableThinkingMode = false,
          maxContextLength = 2048
        )
      }
      // 低端设备或 Android 9
      else -> {
        ModelConfig(
          maxModelSize = 1L * 1024 * 1024 * 1024,  // 1GB
          supportedModels = listOf("Gemma3-1B"),
          enableGPUAcceleration = false,  // 强制 CPU
          enableThinkingMode = false,
          maxContextLength = 1024
        )
      }
    }
  }

  /**
   * 检查是否支持特定功能
   */
  fun isFeatureSupported(feature: AppFeature): Boolean {
    return when (feature) {
      AppFeature.EDGE_TO_EDGE -> isAndroid12OrAbove()
      AppFeature.THINKING_MODE -> isAndroid12OrAbove()
      AppFeature.GPU_ACCELERATION -> isAndroid10OrAbove()
      AppFeature.CAMERAX_ADVANCED -> isAndroid10OrAbove()
      AppFeature.MCP_INTEGRATION -> isAndroid10OrAbove()
      AppFeature.BASIC_CHAT -> true  // 所有版本支持
      AppFeature.IMAGE_INPUT -> true
      AppFeature.AUDIO_INPUT -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    }
  }
}

/**
 * 设备性能等级
 */
enum class DevicePerformanceLevel {
  LOW,    // <4GB RAM
  MEDIUM, // 4-6GB RAM
  HIGH    // 6GB+ RAM
}

/**
 * 应用功能
 */
enum class AppFeature {
  EDGE_TO_EDGE,
  THINKING_MODE,
  GPU_ACCELERATION,
  CAMERAX_ADVANCED,
  MCP_INTEGRATION,
  BASIC_CHAT,
  IMAGE_INPUT,
  AUDIO_INPUT
}

/**
 * 模型配置
 */
data class ModelConfig(
  val maxModelSize: Long,
  val supportedModels: List<String>,
  val enableGPUAcceleration: Boolean,
  val enableThinkingMode: Boolean,
  val maxContextLength: Int
)
