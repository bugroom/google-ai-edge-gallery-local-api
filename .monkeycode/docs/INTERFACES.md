# 接口文档

## HTTP API 总览

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| `GET` | `/health` | 健康检查 | 受 `authType` 控制 |
| `GET` | `/v1/models` | 返回已下载 LLM 模型列表 | 受 `authType` 控制 |
| `GET` | `/v1/engines` | 返回已下载 LLM 引擎列表 | 受 `authType` 控制 |
| `POST` | `/v1/chat/completions` | 聊天补全，支持非流式和流式 | 受 `authType` 控制 |
| `OPTIONS` | 任意路径 | CORS 预检 | 返回 204 |

当 `ApiServerConfig.authType == AuthType.NONE` 时不校验认证头。当认证类型为 `API_KEY` 或其他非 `NONE` 值时，服务端要求 `Authorization: Bearer <API_KEY>`。

## 通用响应行为

- 认证失败返回 `401` 和 `ErrorResponse(error = "Unauthorized", type = "authentication_error")`。
- 未匹配路由返回 `404` 和 `ErrorResponse(error = "Not found", type = "not_found")`。
- 请求解析或参数校验失败返回 `400`。
- 未处理异常返回 `500`。
- 普通响应包含 CORS header，SSE 响应也包含 CORS header。

## `GET /health`

返回 `HealthResponse`：

| 字段 | 类型 | 来源 |
|------|------|------|
| `status` | String | 固定为 `ok` |
| `uptime` | Long | 当前时间减 `startTime` |
| `connections` | Int | 当前连接计数 |
| `loaded_model` | String? | 当前代码返回 `null` |

## `GET /v1/models`

通过 `ApiInferenceHandler.getDownloadedLlmModels()` 获取 `ModelManagerViewModel.getAllDownloadedModels()` 结果，再映射为 `ModelsResponse`。

响应对象结构：

| 字段 | 类型 | 说明 |
|------|------|------|
| `object` | String | `list` |
| `data` | List | API 模型列表 |

## `GET /v1/engines`

实现与 `/v1/models` 类似，输出 `EnginesResponse`，用于兼容 OpenAI 旧版 engines 风格接口。

## `POST /v1/chat/completions`

请求模型为 `ChatCompletionRequest`：

| 字段 | 类型 | 默认行为 |
|------|------|----------|
| `model` | String | 空字符串时使用 `ApiServerConfig.defaultModelId` |
| `messages` | List<ChatMessage> | 必填 |
| `temperature` | Double? | 空值时使用 `defaultTemperature` |
| `max_tokens` | Int? | 空值时使用 `defaultMaxTokens` |
| `top_p` | Double? | 空值时使用 `defaultTopP` |
| `top_k` | Int? | 空值时使用 `defaultTopK` |
| `accelerator` | String? | 空值时使用 `defaultAccelerator` |
| `vision_accelerator` | String? | 空值时使用 `defaultVisionAccelerator` |
| `stream` | Boolean | 默认 `false` |
| `stop` | List<String>? | 当前请求模型包含字段，源码中未看到推理链路应用该字段 |

`ChatMessage` 字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `role` | String | `user`、`assistant`、`system` 等字符串 |
| `content` | String | 消息文本 |

## 非流式响应

非流式响应为 `ChatCompletionResponse`：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | String | `chatcmpl-<uuid>` |
| `object` | String | `chat.completion` |
| `created` | Long | 秒级时间戳 |
| `model` | String | 实际模型名称 |
| `choices` | List<Choice> | 当前代码生成一个 choice |
| `usage` | Usage | 估算 token 统计 |

`Usage` 的 token 统计由 `estimateTokens(text)` 生成，算法为 `text.length / 4` 后至少为 1。

## 流式响应

当 `stream = true` 时，服务端返回 `text/event-stream`，每个增量输出写为：

```text
data: {"id":"...","object":"chat.completion.chunk","created":...,"model":"...","choices":[...]}
```

流结束时写入：

```text
data: [DONE]
```

如果推理回调没有触发 done，`ApiServer` 会通过 `writeDoneEvent()` 补写一个带 `finish_reason = "stop"` 的 chunk，然后写入 `[DONE]`。

## 配置接口字段

配置项来自 `ApiServerConfig`：

| 字段 | 默认值 | 说明 |
|------|--------|------|
| `enabled` | `false` | 服务是否启用 |
| `host` | `127.0.0.1` | 监听地址 |
| `port` | `8080` | 监听端口 |
| `authType` | `NONE` | 认证类型 |
| `apiKey` | 空字符串 | Bearer Token |
| `maxConcurrent` | `2` | 推理并发许可数 |
| `queueSize` | `10` | 配置字段存在，当前读取代码未看到队列实现 |
| `requestTimeout` | `180000L` | 请求超时，最小 180 秒 |
| `defaultModelId` | 空字符串 | 默认模型 |
| `defaultTemperature` | `0.7` | 默认温度 |
| `defaultMaxTokens` | `1024` | 默认最大 token |
| `defaultTopP` | `0.95` | 默认 Top P |
| `defaultTopK` | `40` | 默认 Top K |
| `defaultAccelerator` | `GPU` | 默认文本加速器 |
| `defaultVisionAccelerator` | `GPU` | 默认视觉加速器 |
