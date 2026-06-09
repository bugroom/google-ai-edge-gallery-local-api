# Google AI Edge Gallery 本地 API 版文档索引

本文档集基于当前仓库代码、构建配置、README、构建手册和规划文档生成，聚焦本仓库相对上游 Google AI Edge Gallery 增加或修改的本地 API 服务能力。

## 文档结构

| 文档 | 说明 |
|------|------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | 系统架构、模块关系、本地 API 请求链路 |
| [INTERFACES.md](INTERFACES.md) | HTTP API、请求响应模型、配置字段 |
| [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) | 构建、运行、调试、日志排查指南 |
| [专有概念/本地API服务.md](专有概念/本地API服务.md) | 本地 API 服务的职责、生命周期和限制 |
| [专有概念/OpenAI兼容层.md](专有概念/OpenAI兼容层.md) | OpenAI 兼容请求、响应和 SSE 流式输出 |
| [专有概念/日志与诊断.md](专有概念/日志与诊断.md) | `LOCAL_API` 日志、HTTP 日志、崩溃日志 |
| [模块/API服务模块.md](模块/API服务模块.md) | `server/` 包核心类说明 |
| [模块/配置与持久化模块.md](模块/配置与持久化模块.md) | API 配置数据结构与 SharedPreferences 持久化 |
| [模块/设置界面模块.md](模块/设置界面模块.md) | API Server 设置页面、模型选择和参数配置 |

## 当前实现范围

- App 侧栏提供 `API Server` 入口。
- 本地 HTTP 服务使用 `ServerSocket` 实现。
- 支持 `GET /health`、`GET /v1/models`、`GET /v1/engines`、`POST /v1/chat/completions`。
- `/v1/chat/completions` 支持非流式响应和 SSE 流式响应。
- API 服务通过 `ApiServerManager` 绑定到应用进程生命周期。
- API 配置通过 `ApiServerConfigManager` 保存到 `SharedPreferences`。
- 推理复用 Gallery 现有 LiteRT-LM `runtimeHelper` 链路。
- 请求参数可覆盖温度、最大 token、Top P、Top K、文本加速器和视觉加速器。
- API 日志使用 `LOCAL_API` 标记，并写入 `HttpTrafficLogger`。

## 当前限制

- API 参数中尚未开放图片、音频等多模态输入。
- Token 统计由字符数估算，算法为 `text.length / 4` 后至少为 1。
- 默认模型切换、模型加载和推理仍受设备内存、模型大小、加速器支持情况影响。
- API 服务随 Android App 进程存在，App 进程被系统回收后服务停止。
- MCP 工具调用在 `ROADMAP.md` 中处于规划状态；当前 API Server 代码中未实现 OpenAI `tools` 或 `tool_choice` 参数。

## 相关源码入口

| 文件 | 作用 |
|------|------|
| `Android/src/app/src/main/java/com/google/ai/edge/gallery/server/ApiServer.kt` | HTTP 服务、路由、认证、响应、SSE 输出 |
| `Android/src/app/src/main/java/com/google/ai/edge/gallery/server/ApiInferenceHandler.kt` | 模型校验、参数应用、模型初始化、推理执行 |
| `Android/src/app/src/main/java/com/google/ai/edge/gallery/server/ApiServerManager.kt` | 应用进程级 API 服务生命周期管理 |
| `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/ApiServerConfig.kt` | API 服务配置数据结构 |
| `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/ApiServerConfigManager.kt` | 配置持久化和 API Key 生成 |
| `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/api/*.kt` | OpenAI 兼容 API 请求响应模型 |
| `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/settings/ApiServerSettingsScreen.kt` | API Server 设置页面 |
| `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/HttpTrafficLogger.kt` | HTTP、DEBUG、ERROR、CRASH 日志记录 |
| `Android/src/app/build.gradle.kts` | Android 构建配置、minSdk、ABI、依赖 |
