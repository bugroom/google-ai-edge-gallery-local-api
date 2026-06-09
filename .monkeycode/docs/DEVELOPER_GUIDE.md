# 开发者指南

## 构建入口

Android 工程根目录为 `Android/src`，所有 Gradle 命令都应在该目录下执行。

```bash
# Enter Android project root
cd Android/src

# Build release APK
./gradlew :app:assembleRelease
```

Release APK 输出路径：

```text
Android/src/app/build/outputs/apk/release/app-release.apk
```

## 关键构建配置

`Android/src/app/build.gradle.kts` 中与本地 API 版相关的配置包括：

- `compileSdk = 35`
- `minSdk = 28`
- `targetSdk = 35`
- `versionName = "1.0.15"`
- `resourceConfigurations += listOf("en", "zh")`
- `ndk.abiFilters += listOf("arm64-v8a")`
- release 构建使用 debug 签名：`signingConfig = signingConfigs.getByName("debug")`

`AndroidManifest.xml` 中 `application` 设置了 `android:usesCleartextTraffic="true"`，用于允许明文 HTTP 请求。

## 本地 API 开发入口

修改 API 服务通常涉及这些文件：

| 目标 | 文件 |
|------|------|
| 新增 HTTP 路由 | `server/ApiServer.kt` |
| 修改聊天补全逻辑 | `server/ApiInferenceHandler.kt` |
| 新增请求字段 | `data/api/ChatCompletionRequest.kt` |
| 新增响应字段 | `data/api/ChatCompletionResponse.kt` |
| 修改默认配置 | `data/ApiServerConfig.kt` |
| 修改配置持久化 | `data/ApiServerConfigManager.kt` |
| 修改设置 UI | `ui/settings/ApiServerSettingsScreen.kt` |
| 修改日志行为 | `data/HttpTrafficLogger.kt` |

## 添加 API 参数的流程

1. 在 `ChatCompletionRequest` 增加可序列化字段。
2. 在 `ApiServer.applyConfiguredDefaults()` 中设置默认值。
3. 在 `ApiInferenceHandler.applyRequestParameters()` 中校验并应用到 `model.configValues`。
4. 如需设置页支持，更新 `ApiServerConfig`、`ApiServerConfigManager` 和 `ApiServerSettingsScreen`。
5. 更新 `README.md` 和 `.monkeycode/docs/INTERFACES.md`。

## 调试 API 服务

API 服务日志统一使用 `LOCAL_API` 标记，可通过 Logcat 搜索：

```bash
# Filter local API logs
adb logcat | grep LOCAL_API
```

App 内还有 `HttpLogsScreen`，可查看 `HttpTrafficLogger` 收集的 REQUEST、RESPONSE、ERROR、DEBUG、CRASH 日志。

## 常见排查路径

### 服务启动失败

检查：

- `ApiServerConfig.host` 是否能被 `InetAddress.getByName()` 解析。
- `ApiServerConfig.port` 是否可绑定。
- Logcat 是否出现 `LOCAL_API event=server_start_failed`。
- App 是否拥有网络权限。`AndroidManifest.xml` 已声明 `android.permission.INTERNET`。

### 模型列表为空

检查：

- App 内是否已下载 LLM 模型。
- `ModelManagerViewModel.getAllDownloadedModels()` 是否返回内容。
- 日志是否出现 `LOCAL_API event=models_list count=...`。

### 聊天补全失败

检查：

- 请求 `model` 是否能匹配 `ModelManagerViewModel.getModelByName()`。
- 模型下载状态是否为 `ModelDownloadStatusType.SUCCEEDED`。
- 模型是否满足 `model.isLlm`。
- 任务列表中是否存在包含该模型的 `Task`。
- `model.instance` 在初始化和 `resetConversation()` 后是否为空。

### 流式响应异常

检查：

- 请求体是否设置 `stream = true`。
- 客户端是否按 SSE 解析 `data:` 行。
- 客户端是否处理 `data: [DONE]`。
- 日志是否出现 `chat_stream_client_disconnected`，该事件表示客户端断开连接。

## 文档维护规则

本仓库已经存在 README、BUILD_MANUAL、ROADMAP 和 `.monkeycode/docs/`。API 行为、配置项、构建方式变化后，应同步更新相关文档。文档应严格基于代码和配置，不记录密钥、密码或真实凭证。
