# API 服务模块

## 模块位置

```text
Android/src/app/src/main/java/com/google/ai/edge/gallery/server/
├── ApiServer.kt
├── ApiInferenceHandler.kt
├── ApiServerManager.kt
├── ApiServerViewModel.kt
└── ClientDisconnectedException.kt
```

## `ApiServer`

`ApiServer` 负责底层 HTTP 服务。它直接使用 `ServerSocket`，手工解析 HTTP 请求并写出 HTTP 响应。

主要职责：

- 启动和停止监听 socket。
- 接收客户端连接并分发到线程池。
- 解析 HTTP method、path、headers、body。
- 执行 CORS 预检响应。
- 执行 Bearer Token 认证。
- 路由 `/health`、`/v1/models`、`/v1/engines`、`/v1/chat/completions`。
- 写出 JSON 响应或 SSE 响应。
- 处理客户端断开、SocketException、IOException。

## `ApiInferenceHandler`

`ApiInferenceHandler` 是 API 层与模型推理层之间的桥接器。

主要职责：

- 获取已下载模型列表。
- 校验请求模型存在、已下载、且是 LLM。
- 查找拥有目标模型的 `Task`。
- 将请求参数写入 `model.configValues`。
- 参数变化时触发模型重新初始化。
- 调用 `model.runtimeHelper.resetConversation()`。
- 调用 `model.runtimeHelper.runInference()`。
- 对非流式请求返回 `ChatCompletionResponse`。
- 对流式请求通过回调输出增量文本。

## `ApiServerManager`

`ApiServerManager` 是应用进程级单例，用于持有 `ApiServer`。它避免 API 服务跟随 Compose 页面或 ViewModel 生命周期结束。

关键机制：

- `CoroutineScope(SupervisorJob() + Dispatchers.IO)` 运行后台任务。
- `Mutex` 序列化启动和停止操作。
- `MutableStateFlow<ServerStatus>` 暴露服务状态。
- `MutableStateFlow<ServerInfo?>` 暴露 host、port、uptime、connections。
- 每秒刷新服务信息。
- 启停时同步 `ApiServerConfig.enabled`。

## `ApiServerViewModel`

`ApiServerViewModel` 是 UI 桥接层。它不直接持有 server 实例，而是调用 `ApiServerManager`。其状态流来自 manager。

## `ClientDisconnectedException`

该异常用于把流式输出时的客户端断开从错误路径中区分出来。`ApiServer` 和 `ApiInferenceHandler` 会将 Socket 写入失败转换为该异常，并记录为 debug/info 类型事件。

## 主要控制流

```text
ApiServerSettingsScreen
  -> ApiServerViewModel.startServer()
  -> ApiServerManager.startServer()
  -> ApiServer.start()
  -> ApiServer.route()
  -> ApiInferenceHandler.handleChatCompletion()
  -> model.runtimeHelper.runInference()
```

## 重要约束

- `executor` 是 cached thread pool，实际推理并发由 `ApiInferenceHandler` 的 `Semaphore` 控制。
- `queueSize` 存在于配置模型中，当前读取到的服务实现没有显式请求队列逻辑。
- 流式请求的客户端断开被视为正常事件，不作为崩溃处理。
