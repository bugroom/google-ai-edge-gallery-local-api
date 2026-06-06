# 本地API服务技术设计规格

## 1. 系统架构

### 1.1 总体架构

```
┌─────────────────────────────────────────────────────────┐
│                     其他客户端                           │
│   (手机/平板/PC/浏览器/其他应用)                         │
└────────────────────┬────────────────────────────────────┘
                     │ HTTP/WebSocket
┌────────────────────┴────────────────────────────────────┐
│               Gallery应用 (API服务器)                    │
│  ┌──────────────────────────────────────────────────┐  │
│  │              HTTP服务器层                         │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────────┐  │  │
│  │  │ 路由层   │  │ 中间件   │  │ 认证层       │  │  │
│  │  └──────────┘  └──────────┘  └──────────────┘  │  │
│  └──────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────┐  │
│  │              业务逻辑层                           │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────────┐  │  │
│  │  │ 模型管理 │  │ 请求队列 │  │ 会话管理     │  │  │
│  │  └──────────┘  └──────────┘  └──────────────┘  │  │
│  └──────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────┐  │
│  │              推理引擎层                           │  │
│  │  ┌──────────────────────────────────────────┐  │  │
│  │  │          LiteRT-LM推理引擎               │  │  │
│  │  │  ┌──────────┐  ┌──────────┐  ┌──────┐  │  │
│  │  │  │ 模型加载 │  │ 推理执行 │  │ 卸载 │  │  │
│  │  │  └──────────┘  └──────────┘  └──────┘  │  │
│  │  └──────────────────────────────────────────┘  │  │
│  └──────────────────────────────────────────────────┘  │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────┴────────────────────────────────────┐
│              模型文件存储                                │
│  /data/data/com.google.ai.edge.gallery/files/          │
└─────────────────────────────────────────────────────────┘
```

### 1.2 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| HTTP服务器 | Java ServerSocket | 轻量级、减少移动端 Ktor CIO 兼容性问题 |
| JSON处理 | kotlinx.serialization | 类型安全的序列化 |
| 并发处理 | Kotlin Coroutines + Semaphore | 异步编程与并发限制 |
| 推理引擎 | LiteRT-LM | 已集成，复用现有代码 |
| 日志记录 | Android Log + HttpTrafficLogger | `LOCAL_API` 标记、实时日志、崩溃日志和持久化日志 |
| 持久化 | SharedPreferences | 配置存储和日志保留 |

---

### 1.3 当前实现说明

当前版本使用 native `ServerSocket` 实现 HTTP 服务，保留 OpenAI 兼容路由、CORS、Bearer API Key 鉴权和 SSE 流式响应。早期设计中的 Ktor/Netty 示例仅作为接口结构参考，最终代码以 `server/ApiServer.kt` 和 `server/ApiInferenceHandler.kt` 为准。

---

## 2. 详细设计

### 2.1 HTTP服务器设计

#### 2.1.1 服务器配置

```kotlin
data class ApiServerConfig(
    val enabled: Boolean = false,
    val host: String = "127.0.0.1",  // 默认仅本地
    val port: Int = 8080,
    val authType: AuthType = AuthType.NONE,
    val apiKey: String = "",
    val maxConcurrent: Int = 2,
    val queueSize: Int = 10,
    val requestTimeout: Long = 30000L,  // 30秒
    val defaultModelId: String = "",
    val defaultTemperature: Double = 0.7,
    val defaultMaxTokens: Int = 1024,
    val defaultTopP: Double = 0.95,
    val defaultTopK: Int = 40,
    val defaultAccelerator: String = "GPU",
    val defaultVisionAccelerator: String = "GPU"
)

enum class AuthType {
    NONE,       // 无认证（仅本地）
    API_KEY,    // API Key认证
    CUSTOM      // 自定义Token
}
```

#### 2.1.2 服务器启动/停止

```kotlin
class ApiServer(
    private val context: Context,
    private val config: ApiServerConfig,
    private val modelManager: ModelManager
) {
    private var server: NettyApplicationEngine? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start() {
        if (server != null) return

        server = embeddedServer(Netty, port = config.port, host = config.host) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = false
                })
            }
            install(CallLogging)
            install(CORS) {
                if (config.host != "127.0.0.1") {
                    anyHost()  // 允许跨域（局域网访问）
                }
            }
            install(StatusPages) {
                exception<Exception> { call, cause ->
                    call.respond(HttpStatusCode.InternalServerError, 
                        ErrorResponse(
                            error = cause.message ?: "Internal error",
                            type = "internal_error"
                        )
                    )
                }
            }

            routing {
                // 认证中间件
                intercept(ApplicationCallPipeline.Plugins) {
                    if (config.authType != AuthType.NONE) {
                        authenticate()
                    }
                    proceed()
                }

                // API路由
                apiRoutes()
            }
        }

        server?.start(wait = false)
        log.info("API server started on ${config.host}:${config.port}")
    }

    fun stop() {
        server?.stop(1000, 5000)
        server = null
        log.info("API server stopped")
    }

    fun isRunning(): Boolean = server != null
}
```

#### 2.1.3 路由设计

```kotlin
fun Route.apiRoutes() {
    // 健康检查
    get("/health") {
        call.respond(HealthResponse(
            status = "ok",
            uptime = getUptime(),
            connections = getCurrentConnections(),
            loadedModel = getLoadedModel()
        ))
    }

    // OpenAI兼容API
    route("/v1") {
        // 模型列表
        get("/models") {
            val models = modelManager.getDownloadedModels()
            call.respond(ModelsResponse(
                object = "list",
                data = models.map { it.toApiModel() }
            ))
        }

        // 聊天补全
        post("/chat/completions") {
            val request = call.receive<ChatCompletionRequest>()
            val response = handleChatCompletion(request)
            call.respond(response)
        }

        // 文本补全（预留）
        post("/completions") {
            call.respond(HttpStatusCode.NotImplemented)
        }

        // 引擎列表（兼容OpenAI）
        get("/engines") {
            call.respond(ModelsResponse(
                object = "list",
                data = modelManager.getDownloadedModels().map { it.toApiEngine() }
            ))
        }
    }

    // WebSocket（阶段2）
    webSocket("/v1/chat/stream") {
        // WebSocket处理逻辑
    }
}
```

### 2.2 API接口设计

#### 2.2.1 数据模型

```kotlin
// 请求模型
@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 1024,
    val top_p: Double = 0.95,
    val top_k: Int = 40,
    val stream: Boolean = false,
    val stop: List<String>? = null
)

@Serializable
data class ChatMessage(
    val role: String,  // "user", "assistant", "system"
    val content: String
)

// 响应模型
@Serializable
data class ChatCompletionResponse(
    val id: String,
    val object: String = "chat.completion",
    val created: Long = System.currentTimeMillis() / 1000,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage
)

@Serializable
data class Choice(
    val index: Int,
    val message: ChatMessage,
    val finish_reason: String
)

@Serializable
data class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)

// 健康检查响应
@Serializable
data class HealthResponse(
    val status: String,
    val uptime: Long,
    val connections: Int,
    val loaded_model: String?
)

// 错误响应
@Serializable
data class ErrorResponse(
    val error: String,
    val type: String,
    val code: String? = null
)
```

#### 2.2.2 聊天补全处理

```kotlin
suspend fun handleChatCompletion(
    request: ChatCompletionRequest
): ChatCompletionResponse {
    // 1. 验证模型
    val model = modelManager.getModel(request.model)
        ?: throw ModelNotFoundException(request.model)

    // 2. 加载模型（如果未加载）
    if (modelManager.getLoadedModelId() != request.model) {
        modelManager.loadModel(request.model)
    }

    // 3. 构建推理输入
    val messages = request.messages.map { 
        Message(role = it.role, content = it.content) 
    }

    // 4. 执行推理
    val inferenceConfig = InferenceConfig(
        temperature = request.temperature,
        topK = request.top_k,
        topP = request.top_p,
        maxTokens = request.max_tokens
    )

    val startTime = System.currentTimeMillis()
    val result = modelManager.runInference(
        model = model,
        messages = messages,
        config = inferenceConfig
    )
    val latency = System.currentTimeMillis() - startTime

    // 5. 构建响应
    return ChatCompletionResponse(
        id = generateId(),
        model = request.model,
        choices = listOf(Choice(
            index = 0,
            message = ChatMessage(
                role = "assistant",
                content = result.outputText
            ),
            finish_reason = result.finishReason
        )),
        usage = Usage(
            prompt_tokens = result.inputTokenCount,
            completion_tokens = result.outputTokenCount,
            total_tokens = result.inputTokenCount + result.outputTokenCount
        )
    )
}
```

### 2.3 认证机制

#### 2.3.1 认证中间件

```kotlin
private suspend fun PipelineContext<Unit, ApplicationCall>.authenticate() {
    val call = call
    val authHeader = call.request.headers["Authorization"]
    
    when (config.authType) {
        AuthType.API_KEY -> {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                call.respond(HttpStatusCode.Unauthorized,
                    ErrorResponse("Missing or invalid Authorization header", "auth_error")
                )
                return
            }
            val token = authHeader.removePrefix("Bearer ")
            if (token != config.apiKey) {
                call.respond(HttpStatusCode.Unauthorized,
                    ErrorResponse("Invalid API key", "auth_error")
                )
                return
            }
        }
        AuthType.CUSTOM -> {
            val customToken = call.request.headers["X-API-Token"]
            if (customToken != config.apiKey) {
                call.respond(HttpStatusCode.Unauthorized,
                    ErrorResponse("Invalid token", "auth_error")
                )
                return
            }
        }
        AuthType.NONE -> {
            // 不需要认证
        }
    }
}
```

### 2.4 请求队列管理

#### 2.4.1 队列实现

```kotlin
class RequestQueue(
    private val maxConcurrent: Int,
    private val queueSize: Int
) {
    private val semaphore = Semaphore(maxConcurrent)
    private val queue = Channel<suspend () -> Unit>(queueSize)
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start() {
        scope.launch {
            while (true) {
                val task = queue.receive()
                semaphore.acquire()
                launch {
                    try {
                        task()
                    } finally {
                        semaphore.release()
                    }
                }
            }
        }
    }

    suspend fun <T> submit(block: suspend () -> T): T {
        val deferred = CompletableDeferred<T>()
        queue.send {
            try {
                deferred.complete(block())
            } catch (e: Exception) {
                deferred.completeExceptionally(e)
            }
        }
        return deferred.await()
    }

    fun getQueueSize(): Int = queue.remaining

    fun getAvailablePermits(): Int = semaphore.availablePermits
}
```

### 2.5 UI设计

#### 2.5.1 设置界面

```kotlin
@Composable
fun ApiServerSettingsScreen(
    config: ApiServerConfig,
    onConfigChange: (ApiServerConfig) -> Unit
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "本地API服务",
            style = MaterialTheme.typography.headlineMedium
        )

        // 服务开关
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("启用API服务")
            Switch(
                checked = config.enabled,
                onCheckedChange = { 
                    onConfigChange(config.copy(enabled = it)) 
                }
            )
        }

        // 端口配置
        OutlinedTextField(
            value = config.port.toString(),
            onValueChange = { 
                onConfigChange(config.copy(port = it.toIntOrNull() ?: 8080))
            },
            label = { Text("端口") },
            enabled = !config.enabled
        )

        // 认证方式
        Text("认证方式")
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AuthType.values().forEach { type ->
                FilterChip(
                    selected = config.authType == type,
                    onClick = { 
                        onConfigChange(config.copy(authType = type))
                    },
                    label = { Text(type.displayName) },
                    enabled = !config.enabled
                )
            }
        }

        // API Key输入（当认证方式为API_KEY时显示）
        if (config.authType == AuthType.API_KEY) {
            OutlinedTextField(
                value = config.apiKey,
                onValueChange = { 
                    onConfigChange(config.copy(apiKey = it))
                },
                label = { Text("API Key") },
                trailingIcon = {
                    IconButton(onClick = { 
                        onConfigChange(config.copy(apiKey = generateApiKey()))
                    }) {
                        Icon(Icons.Default.Refresh, "生成随机Key")
                    }
                },
                enabled = !config.enabled
            )
        }

        // 访问控制
        Text("允许的客户端")
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = config.host == "127.0.0.1" || 
                              config.host.contains("127.0.0.1"),
                    onCheckedChange = { 
                        onConfigChange(config.copy(
                            host = if (it) "127.0.0.1" else "0.0.0.0"
                        ))
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
                    onCheckedChange = { 
                        onConfigChange(config.copy(
                            host = if (it) "0.0.0.0" else "127.0.0.1"
                        ))
                    },
                    enabled = !config.enabled
                )
                Text("局域网 (0.0.0.0)")
            }
        }

        // 状态显示
        if (config.enabled) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
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
                                    color = Color.Green,
                                    shape = CircleShape
                                )
                        )
                        Text("运行中")
                    }
                    Text("端口: ${config.port}")
                    Text("地址: http://${config.host}:${config.port}")
                    Text("认证: ${config.authType.displayName}")
                }
            }
        }
    }
}
```

### 2.6 日志和监控

#### 2.6.1 请求日志

```kotlin
fun Route.installRequestLogging() {
    intercept(ApplicationCallPipeline.Monitoring) {
        val startTime = System.currentTimeMillis()
        proceed()
        val duration = System.currentTimeMillis() - startTime
        
        log.info(
            "API Request: ${call.request.httpMethod.value} ${call.request.uri} " +
            "Status: ${call.response.status()} " +
            "Duration: ${duration}ms"
        )
    }
}

// 请求统计
data class RequestStats(
    val totalRequests: Long = 0,
    val successfulRequests: Long = 0,
    val failedRequests: Long = 0,
    val averageLatency: Double = 0.0,
    val currentConnections: Int = 0
)
```

---

## 3. 数据流设计

### 3.1 聊天补全请求流程

```
客户端
  ↓ POST /v1/chat/completions
HTTP服务器
  ↓ 路由 → 认证
请求队列
  ↓ 队列管理
业务逻辑层
  ↓ 模型验证 → 模型加载
推理引擎
  ↓ LiteRT-LM推理
模型文件
  ↑ 读取模型
推理引擎
  ↑ 返回结果
业务逻辑层
  ↑ 构建响应
HTTP服务器
  ↑ JSON序列化
客户端
  ↑ 接收响应
```

### 3.2 数据转换

```
OpenAI API格式
  ↓ 解析
ChatCompletionRequest
  ↓ 转换
LiteRT-LM格式
  ↓ 推理
LLM输出
  ↑ 转换
ChatCompletionResponse
  ↑ 序列化
OpenAI API格式
```

---

## 4. 安全设计

### 4.1 网络安全

| 风险 | 缓解措施 |
|------|----------|
| 未授权访问 | 认证机制 + IP限制 |
| DDoS攻击 | 连接数限制 + 超时 |
| 数据泄露 | 仅本地访问 + 可选局域网 |
| 中间人攻击 | HTTPS支持（可选）|

### 4.2 资源安全

| 风险 | 缓解措施 |
|------|----------|
| 内存耗尽 | 请求队列 + 超时 |
| CPU过载 | 并发限制 |
| 模型损坏 | 验证机制 |

---

## 5. 性能优化

### 5.1 推理优化

- 复用引擎实例
- 模型预加载
- 批处理支持（未来）

### 5.2 网络优化

- 连接池
- 响应压缩
- 流式传输

### 5.3 内存优化

- 及时释放资源
- 自动卸载模型
- 缓存管理

---

## 6. 测试策略

### 6.1 单元测试

- 服务器启动/停止
- 路由处理
- 认证逻辑
- 请求队列

### 6.2 集成测试

- API端点测试
- 完整请求流程
- 错误处理

### 6.3 性能测试

- 并发请求
- 响应时间
- 资源占用

### 6.4 安全测试

- 认证测试
- 权限测试
- 注入测试

---

## 7. 部署计划

### 7.1 阶段1: 基础功能 (v1.0)

**任务列表**:
1. 集成Ktor Server依赖
2. 实现服务器启动/停止
3. 实现核心API端点
4. 集成LiteRT-LM推理
5. 实现基础UI

**验收标准**:
- 服务器可正常启动
- API可正常调用
- 推理结果正确

### 7.2 阶段2: 增强功能 (v1.5)

**任务列表**:
1. 实现流式响应
2. 实现认证机制
3. 完善错误处理
4. 实现请求队列

**验收标准**:
- 流式响应正常
- 认证功能正常
- 错误处理完善

### 7.3 阶段3: 优化扩展 (v2.0)

**任务列表**:
1. 性能优化
2. 实现WebSocket
3. 实现监控面板
4. 文档完善

**验收标准**:
- 性能达到要求
- WebSocket正常
- 监控功能完整

---

## 8. 风险评估

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|----------|
| 性能不达标 | 中 | 中 | 性能测试 + 优化 |
| 安全漏洞 | 低 | 高 | 安全审计 + 测试 |
| 兼容性问题 | 低 | 中 | 标准化API + 测试 |
| 资源耗尽 | 中 | 中 | 资源限制 + 监控 |

---

**文档版本**: 1.0
**创建日期**: 2026-06-04
**最后更新**: 2026-06-04
**状态**: 待评审