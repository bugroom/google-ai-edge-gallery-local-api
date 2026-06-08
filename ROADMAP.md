# 魔改版未来功能规划

本文档记录 Google AI Edge Gallery 本地 API 版的未来功能扩展计划。

## 高优先级功能

### 1. MCP 工具调用支持（客户端模式）

**状态**: 规划中  
**目标版本**: v2.0  
**难度**: 中等

**功能描述**:
- API Server 支持 OpenAI 兼容的 `tools` 和 `tool_choice` 参数
- 客户端自己维护 MCP 连接，服务端只返回 `tool_calls` 指示
- 支持标准 MCP SDK 的工具定义格式

**实现要点**:
```yaml
# 请求扩展示例
POST /v1/chat/completions
{
  "model": "qwen2.5",
  "messages": [...],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "filesystem_read",
        "description": "Read file content",
        "parameters": {
          "type": "object",
          "properties": {
            "path": {"type": "string"}
          }
        }
      }
    }
  ],
  "tool_choice": "auto"
}

# 响应扩展示例
{
  "choices": [{
    "message": {
      "role": "assistant",
      "content": null,
      "tool_calls": [{
        "id": "call_xxx",
        "type": "function",
        "function": {
          "name": "filesystem_read",
          "arguments": "{\"path\":\"/data/file.txt\"}"
        }
      }]
    }
  }]
}
```

**技术方案**:
1. 修改 `ChatCompletionRequest` 模型，添加 `tools` 字段
2. 在 `ApiInferenceHandler` 中添加工具调用检测逻辑
3. 通过 prompt 工程将 tools 描述注入 system message
4. 解析 LLM 输出，识别工具调用意图
5. 返回标准 OpenAI 格式的 `tool_calls` 响应

**优势**:
- 保持服务端轻量，不维护 MCP 长连接
- 兼容现有 OpenAI 客户端生态
- 支持任何 MCP Server（文件系统、数据库、搜索等）

---

## 中优先级功能

### 2. 多模态输入支持（图片理解）

**状态**: 调研中  
**目标版本**: v2.1  
**难度**: 高

**功能描述**:
- 支持 Vision-Language 模型（如 LLaVA、Moondream）
- API 支持 `image_url` 或 `image_base64` 输入
- 图像编码与文本融合推理

**技术挑战**:
- 需要同时加载 Vision Encoder + LLM，内存占用大
- 图像预处理（resize、normalize）增加延迟
- 需要扩展 API 格式支持多模态消息

**方案**:
```yaml
POST /v1/chat/completions
{
  "model": "llava",
  "messages": [{
    "role": "user",
    "content": [
      {"type": "text", "text": "描述这张图片"},
      {"type": "image_url", "image_url": {"url": "base64://..."}}
    ]
  }]
}
```

---

### 3. Embedding 接口

**状态**: 规划中  
**目标版本**: v2.0  
**难度**: 低

**功能描述**:
- 添加 `/v1/embeddings` 端点
- 支持文本向量化模型
- 用于 RAG、语义搜索场景

**接口设计**:
```yaml
POST /v1/embeddings
{
  "model": "bge-small",
  "input": "需要向量化的文本"
}

# 响应
{
  "data": [{
    "embedding": [0.1, 0.2, ...],
    "index": 0
  }]
}
```

---

### 4. 模型热切换

**状态**: 规划中  
**目标版本**: v1.5  
**难度**: 中等

**功能描述**:
- 请求中指定 `model` 参数时，自动切换已下载模型
- 无需重启 API Server
- 支持多模型并发加载（内存允许情况下）

**当前限制**:
- 默认模型切换需要重启服务
- 不支持同时加载多个模型

---

## 低优先级功能

### 5. 语音转文字（Whisper）

**状态**: 待调研  
**目标版本**: v3.0  
**难度**: 高

**功能描述**:
- 添加 `/v1/audio/transcriptions` 端点
- 支持 Whisper 模型本地语音转文字
- 支持音频文件上传

**技术挑战**:
- 音频预处理（重采样、分片）
- 大文件上传处理
- 流式语音识别（实时转录）

---

### 6. 图像生成（文生图）

**状态**: 待调研  
**目标版本**: v3.0  
**难度**: 高

**功能描述**:
- 添加 `/v1/images/generations` 端点
- 支持 Stable Diffusion 等本地模型
- 支持文生图、图生图

**技术挑战**:
- 图像生成耗时较长（10-60秒）
- 需要异步任务队列
- 结果存储和回调机制

---

### 7. 高级参数控制

**状态**: 规划中  
**目标版本**: v1.5  
**难度**: 低

**功能描述**:
- 支持更多采样参数（`presence_penalty`, `frequency_penalty`, `seed` 等）
- 支持 `stop` 序列
- 支持 `logit_bias`

---

### 8. 对话历史持久化

**状态**: 规划中  
**目标版本**: v2.0  
**难度**: 中等

**功能描述**:
- 支持 `conversation_id` 参数
- 服务端缓存对话历史
- 支持跨请求上下文保持

**接口扩展**:
```yaml
POST /v1/chat/completions
{
  "model": "qwen2.5",
  "conversation_id": "conv_xxx",  // 新增
  "messages": [{"role": "user", "content": "继续刚才的话题"}]
}
```

---

## 技术债务

### 9. 性能优化

- [ ] 模型量化支持（INT8/INT4）
- [ ] 动态批处理（Batching）
- [ ] 模型并行推理
- [ ] 内存优化（模型分页加载）

### 10. 可观测性增强

- [ ] 添加 Prometheus 指标导出
- [ ] 结构化日志（JSON 格式）
- [ ] 分布式追踪支持

---

## 实现建议

### 版本路线图

```
v1.5 (近期):
  - 高级参数控制 (#7)
  - 模型热切换 (#4)
  - Embedding 接口 (#3)

v2.0 (中期):
  - MCP 工具调用支持 (#1) ⭐ 高优先级
  - 对话历史持久化 (#8)
  - 性能优化 (#9)

v2.1 (中远期):
  - 多模态输入 (#2)
  - 可观测性增强 (#10)

v3.0 (远期):
  - 语音转文字 (#5)
  - 图像生成 (#6)
```

### MCP 功能快速实现检查清单

如果你需要优先实现 MCP 支持，按以下顺序进行：

1. [ ] 修改 `ChatCompletionRequest`，添加 `tools` 字段
2. [ ] 添加工具描述到 system prompt 的转换逻辑
3. [ ] 实现 LLM 输出的工具调用检测
4. [ ] 返回标准 `tool_calls` JSON 格式
5. [ ] 处理客户端传回的 `tool` 角色消息
6. [ ] 测试与真实 MCP Server 的集成

---

## 贡献指南

欢迎提交 PR 实现上述功能。建议流程：

1. 选择功能并创建 Issue 讨论实现方案
2. 在 `.monkeycode/specs/{feature}/` 目录下编写设计文档
3. 提交 PR 时包含：
   - 功能实现代码
   - 单元测试
   - README 更新
   - API 变更说明

---

**最后更新**: 2026-06-08  
**维护者**: bugroom
