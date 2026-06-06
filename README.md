# Google AI Edge Gallery Local API Mod

这是基于 Google AI Edge Gallery 的 Android 魔改版，核心目标是把原本只能在 App 内使用的本地 LLM 能力，扩展成可由其他客户端调用的本地 API 服务。

上游项目：`https://github.com/google-ai-edge/gallery`

当前仓库：`https://github.com/bugroom/google-ai-edge-gallery-local-api`

## 当前状态

- Release APK 已可构建通过。
- Android 最低版本已下调到 Android 9.0。
- 模型下载源保持官方 Hugging Face 源。
- 已新增本地 API Server 侧栏入口。
- 已接入 OpenAI 兼容接口和 LiteRT-LM 推理链路。
- 已支持默认模型、采样参数和 CPU/GPU/NPU/TPU 推理后端配置。
- 已添加统一日志标记 `LOCAL_API`，方便后续排查问题。
- 真机端到端调用仍建议结合实际已下载模型继续测试。

## 主要魔改内容

### Android 兼容性

- `minSdk` 从 31 调整为 28，支持 Android 9.0 及以上设备。
- 新增 `ApiCompatibilityHelper`，用于处理部分 Android API 版本兼容逻辑。
- Release 构建限制 `arm64-v8a`，降低 APK 体积。

### 中文化

- 新增 `values-zh/strings.xml` 中文资源。
- 保留英文资源，构建时包含 `en` 和 `zh`。

### 下载与日志

- 模型下载源保留官方 Hugging Face 源。
- 新增 HTTP 下载日志能力。
- 新增日志查看页面，便于复制和排查下载问题。

### 本地 API Server

- 新增本地 API 服务配置：启用状态、监听地址、端口、认证方式、API Key、并发数、队列大小、请求超时、默认模型、默认采样参数、文本推理后端和视觉输入后端。
- 新增 API 设置入口：侧栏 -> API Server。
- 新增 native `ServerSocket` HTTP Server。
- 新增 API Key 鉴权：`Authorization: Bearer <apiKey>`。
- 新增 CORS 支持。
- 新增 OpenAI 兼容接口：
  - `GET /health`
  - `GET /v1/models`
  - `GET /v1/engines`
  - `POST /v1/chat/completions`
- `/v1/models` 和 `/v1/engines` 返回已下载的 LLM 模型。
- `/v1/chat/completions` 已接入现有 LiteRT-LM 推理流程。
- `stream: true` 支持 SSE 流式响应。
- 请求未传 `model`、`temperature`、`max_tokens`、`top_p`、`top_k`、`accelerator`、`vision_accelerator` 时，会使用 API Server 页面中的默认配置。

### 可观测性

本地 API 相关日志统一使用 `LOCAL_API` 标记，并尽量包含以下字段：

- `request_id`
- `event`
- `model`
- `path`
- `duration_ms`
- `error`
- `auth_type`

可通过 Logcat 过滤：

```bash
# Filter local API logs
adb logcat | grep LOCAL_API
```

## API 使用说明

### 1. 在 App 内启动服务

1. 安装并打开 App。
2. 下载一个支持 LLM 的模型。
3. 从侧栏进入 `API Server`。
4. 选择默认模型。
5. 配置默认采样参数和推理后端。
6. 配置监听地址和端口。
7. 如需外部设备访问，选择 `0.0.0.0`。
8. 如启用 API Key，复制生成的 Key。
9. 打开 API 服务开关。

默认配置：

- Host: `127.0.0.1`
- Port: `8080`
- Auth: `NONE`
- Temperature: `0.7`
- Max tokens: `1024`
- Top P: `0.95`
- Top K: `40`
- Accelerator: `GPU`
- Vision accelerator: `GPU`

局域网访问时，手机和客户端设备需要位于同一网络。使用 `0.0.0.0` 监听后，客户端应访问手机的局域网 IP。

### 2. 健康检查

```bash
# Check API server health
curl http://127.0.0.1:8080/health
```

响应示例：

```json
{
  "status": "ok",
  "uptime": 12345,
  "connections": 1,
  "loaded_model": null
}
```

### 3. 获取模型列表

```bash
# List downloaded LLM models
curl http://127.0.0.1:8080/v1/models
```

启用 API Key 后：

```bash
# List models with API key
curl http://127.0.0.1:8080/v1/models \
  -H "Authorization: Bearer YOUR_API_KEY"
```

响应示例：

```json
{
  "object": "list",
  "data": [
    {
      "id": "model-name",
      "object": "model",
      "owned_by": "google",
      "created": 1780680000
    }
  ]
}
```

### 4. 聊天补全

```bash
# Call chat completions
curl http://127.0.0.1:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "model-name",
    "messages": [
      {"role": "user", "content": "你好，介绍一下你自己"}
    ],
    "temperature": 0.7,
    "max_tokens": 1024,
    "top_p": 0.95,
    "top_k": 40,
    "accelerator": "GPU",
    "vision_accelerator": "GPU"
  }'
```

启用 API Key 后：

```bash
# Call chat completions with API key
curl http://127.0.0.1:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -d '{
    "model": "model-name",
    "messages": [
      {"role": "system", "content": "你是一个本地离线助手"},
      {"role": "user", "content": "用三句话说明端侧模型的优势"}
    ]
  }'
```

响应格式兼容 OpenAI Chat Completions：

```json
{
  "id": "chatcmpl-uuid",
  "object": "chat.completion",
  "created": 1780680000,
  "model": "model-name",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "..."
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 10,
    "completion_tokens": 20,
    "total_tokens": 30
  }
}
```

### 5. 流式聊天补全

```bash
# Call streaming chat completions
curl http://127.0.0.1:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [
      {"role": "user", "content": "持续输出一个简短故事"}
    ],
    "stream": true
  }'
```

当请求未传 `model` 时，服务会使用 API Server 页面选择的默认模型。

## 构建说明

开发环境位于 Android 工程目录：

```bash
# Enter Android project
cd Android/src
```

编译 Kotlin：

```bash
# Compile release Kotlin sources
./gradlew :app:compileReleaseKotlin
```

构建 Release APK：

```bash
# Build release APK
./gradlew :app:assembleRelease
```

生成文件：

`Android/src/app/build/outputs/apk/release/app-release.apk`

## 故障排查

### 服务无法启动

- 检查端口是否被占用。
- 检查监听地址是否为 `127.0.0.1` 或 `0.0.0.0`。
- 使用 Logcat 搜索 `LOCAL_API event=server_start_failed`。

### 401 Unauthorized

- 检查是否启用了 API Key。
- 检查请求头是否包含 `Authorization: Bearer YOUR_API_KEY`。
- 使用 Logcat 搜索 `LOCAL_API event=auth_failed`。

### 模型列表为空

- 先在 App 内下载一个 LLM 模型。
- 仅下载成功且 `isLlm = true` 的模型会返回。
- 使用 Logcat 搜索 `LOCAL_API event=models_list`。

### 聊天补全失败

- 确认 `model` 参数等于 `/v1/models` 返回的 `id`。
- 确认模型已下载成功。
- 查看 `LOCAL_API request_id=<id>` 相关日志。
- 重点搜索 `model_init_error`、`inference_start`、`chat_error`、`chat_timeout`。

## 当前限制

- `stream=true` 目前会返回不支持错误。
- 多模态输入暂未开放为 API 参数。
- Token 统计为估算值。
- 真机性能取决于设备、模型大小和加速器配置。
- 本地 API 服务随 App 进程运行，App 进程被系统回收后服务会停止。

## 变更记录

- `feat: add local API server support`
- `feat: connect local API server to LLM inference`

## License

本项目基于 Google AI Edge Gallery 修改，保留上游 Apache License 2.0 授权。详见 `LICENSE`。
