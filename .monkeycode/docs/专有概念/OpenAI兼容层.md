# OpenAI 兼容层

## 数据模型

OpenAI 兼容层由 `data/api/` 包提供。关键模型包括：

- `ChatCompletionRequest`
- `ChatMessage`
- `ChatCompletionResponse`
- `Choice`
- `Usage`
- `ModelsResponse`
- `HealthResponse`

## 请求兼容范围

`ChatCompletionRequest` 支持 `model`、`messages`、`temperature`、`max_tokens`、`top_p`、`top_k`、`accelerator`、`vision_accelerator`、`stream`、`stop`。其中 `stop` 字段在请求模型中存在，当前读取到的推理链路未应用 stop 逻辑。

## Prompt 组装

`ApiInferenceHandler.buildPrompt()` 将每条消息格式化为：

```text
<role>: <content>
```

所有消息追加后，以 `assistant:` 结尾。

## 非流式响应

非流式路径调用 `handleChatCompletion()`，返回 `ChatCompletionResponse`，`object` 为 `chat.completion`，`finish_reason` 为 `stop`。`usage` 使用字符长度估算 token。

## 流式响应

流式路径由 `ApiServer.handleStreamChatCompletion()` 写入 SSE。每个 chunk 使用 `StreamChatChunk`，`object` 为 `chat.completion.chunk`。结束时写入 `data: [DONE]`。

## 与 OpenAI API 的差异

- 当前没有 `tools`、`tool_choice`、`function_call` 等工具调用字段。
- 当前消息 `content` 是字符串，未实现 OpenAI 多模态数组结构。
- Token 统计为估算值。
- 错误对象为项目内 `ErrorResponse`，兼容程度取决于客户端容错能力。
