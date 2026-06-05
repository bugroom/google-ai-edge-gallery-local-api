# Google AI Edge Gallery 兼容性修改说明

## 修改概述

本项目已将 Google AI Edge Gallery 的最低兼容版本从 Android 12 (API 31) 降级到 Android 9.0 (API 28)，并添加了中文支持。

## 修改内容

### 1. build.gradle.kts 修改

```kotlin
defaultConfig {
    applicationId = "com.google.aiedge.gallery"
    minSdk = 28  // 从 31 改为 28 (Android 9.0)
    targetSdk = 35
    versionCode = 33
    versionName = "1.0.15"
    
    // 仅保留英语和中文资源，减少APK体积
    resourceConfigurations += listOf("en", "zh")
    ...
}
```

**影响：**
- 兼容 Android 9.0+ 设备
- 覆盖约 90% 的活跃 Android 设备
- APK体积减少（移除了其他语言资源）

### 2. 中文支持 (values-zh/strings.xml)

已创建中文资源文件，包含以下翻译：

**应用相关：**
- app_name: Google AI Edge Gallery
- app_intro: 探索来自 Hugging Face 的出色设备端模型世界

**常用操作：**
- add: 添加
- cancel: 取消
- delete: 删除
- download: 下载
- save: 保存
- ok: 确定

**导航：**
- drawer_settings_label: 设置
- drawer_models_label: 模型
- drawer_notifications_label: 通知

**聊天功能：**
- chat_textinput_placeholder: 输入消息…
- chat_you: 你
- chat_llm_agent_name: AI 助手
- show_thinking: 显示思考过程

**模型管理：**
- model_manager: 模型管理器
- select_model: 选择模型
- loading_model_list: 正在加载模型列表…

**功能分类：**
- category_llm: 大语言模型
- category_agents: 智能体
- category_experimental: 实验性功能

**基准测试：**
- benchmark: 基准测试
- run_benchmark: 运行基准测试
- baseline: 基准线

**MCP：**
- mcp: MCP
- manage_mcp_servers: 管理 MCP 服务器
- mcp_tool_allow_once: 允许一次

### 3. 兼容性工具类 (ApiCompatibilityHelper.kt)

已创建兼容性工具类，提供以下功能：

**API级别检测：**
- `isAndroid12OrAbove()`: 检查 Android 12+
- `isAndroid11OrAbove()`: 检查 Android 11+
- `isAndroid10OrAbove()`: 检查 Android 10+

**设备性能分级：**
- `getDevicePerformanceLevel()`: 获取设备性能等级（HIGH/MEDIUM/LOW）

**模型配置推荐：**
- `getRecommendedModelConfig()`: 根据设备和API级别推荐模型配置

**功能支持检测：**
- `isFeatureSupported()`: 检查特定功能是否支持

**Edge-to-Edge 兼容：**
- `enableEdgeToEdge()`: 兼容 Android 9-11 的 Edge-to-Edge 实现
- `setNavigationBarContrast()`: 设置导航栏对比度

## 功能兼容性矩阵

| 功能 | API 31+ | API 28-30 | 说明 |
|------|---------|-----------|------|
| **基础聊天** | ✅ | ✅ | 所有版本支持 |
| **图像问答** | ✅ | ✅ | 所有版本支持 |
| **语音转录** | ✅ | ✅ | 所有版本支持 |
| **Prompt Lab** | ✅ | ✅ | 所有版本支持 |
| **模型管理** | ✅ | ✅ | 所有版本支持 |
| **基准测试** | ✅ | ✅ | 所有版本支持 |
| **GPU加速** | ✅ | ⚠️ | API 28-29 降级到CPU |
| **思考模式** | ✅ | ⚠️ | API 28-30 禁用 |
| **MCP集成** | ✅ | ⚠️ | API 28-29 禁用 |
| **Edge-to-Edge** | ✅ | ⚠️ | API 28-30 兼容性实现 |

## 模型支持配置

根据设备和API级别自动调整：

### 高端设备 (6GB+ RAM) + Android 12+
- 支持模型：Gemma-3n-E4B, Gemma-3n-E2B
- GPU加速：启用
- 思考模式：启用
- 上下文长度：4096

### 中端设备 (4-6GB RAM) + Android 10+
- 支持模型：Gemma3-1B, Qwen2.5-1.5B
- GPU加速：启用
- 思考模式：禁用
- 上下文长度：2048

### 低端设备 (<4GB RAM) 或 Android 9
- 支持模型：Gemma3-1B
- GPU加速：禁用（强制CPU）
- 思考模式：禁用
- 上下文长度：1024

## 注意事项

1. **Android 9.0 (API 28) 限制：**
   - 部分动画效果降级
   - 缺少某些 Material3 组件特性
   - GPU 加速可能不稳定

2. **性能建议：**
   - 低端设备建议使用 1B 以下模型
   - Android 9.0 设备建议禁用 GPU 加速
   - 内存 < 4GB 设备建议限制并发任务

3. **测试建议：**
   - 在 Android 9/10/11 设备上充分测试
   - 验证 GPU 加速稳定性
   - 测试大模型内存占用

## 构建命令

```bash
cd /workspace/gallery/Android/src

# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK
./gradlew assembleRelease
```

## 最低系统要求

- **Android 版本**: 9.0 (API 28)
- **RAM**: 3GB 最低，4GB 推荐
- **存储**: 2GB 可用空间
- **CPU**: ARM64 (arm64-v8a)

## 已知问题

1. Android 9.0 上的 splash screen 动画可能有轻微延迟
2. 某些 Material3 组件在 API 28 上样式略有不同
3. GPU 加速在部分 Android 9 设备上可能不稳定

## 后续优化建议

1. 添加更多运行时性能监控
2. 实现动态模型加载策略
3. 优化低端设备的内存管理
4. 添加设备兼容性白名单

---

修改日期: 2026年6月3日
版本: v1.0 (兼容 Android 9.0+)
