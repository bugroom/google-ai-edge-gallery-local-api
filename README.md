# Google AI Edge Gallery 本地 API 版

> 代码全由 AI 修改生成。

基于 [Google AI Edge Gallery](https://github.com/google-ai-edge/gallery) 修改的 Android 项目。这个版本把 App 内的本地 LLM 推理能力扩展为可由本机或局域网客户端调用的 OpenAI 兼容 API 服务。

- 上游项目：[google-ai-edge/gallery](https://github.com/google-ai-edge/gallery)
- 当前仓库：[bugroom/google-ai-edge-gallery-local-api](https://github.com/bugroom/google-ai-edge-gallery-local-api)
- API 调试助手：[bugroom/local-api-debug-helper](https://github.com/bugroom/local-api-debug-helper)
- 构建手册：[BUILD_MANUAL.md](BUILD_MANUAL.md)

## 目录

- [当前状态](#当前状态)
- [功能概览](#功能概览)
- [快速开始](#快速开始)
- [API 手册](#api-手册)
- [构建说明](#构建说明)
- [GitHub Actions 工作流适配](#github-actions-工作流适配)
- [故障排查](#故障排查)
- [当前限制](#当前限制)
- [变更记录](#变更记录)
- [License](#license)

## 当前状态

- Release APK 已可构建通过。
- Android 最低版本为 Android 9.0，`minSdk = 28`。
- 模型下载源保持官方 Hugging Face 源。
- 本地 API Server 已独立为侧栏入口。
- OpenAI 兼容接口已接入 LiteRT-LM 推理链路。
- 已支持默认模型、采样参数、API Key 和 CPU/GPU/NPU/TPU 推理后端配置。
- 流式响应已支持 SSE chunk、客户端断开处理和 `data: [DONE]` 结束事件。
- 本地 API 相关日志统一使用 `LOCAL_API` 标记。

## 功能概览

### Android 兼容性

- `minSdk` 从 31 调整为 28，支持 Android 9.0 及以上设备。
- 新增 `ApiCompatibilityHelper` 处理部分 Android API 版本兼容逻辑。
- 主题切换已兼容 Android 12 以下设备。
- Release 构建限制 `arm64-v8a`，降低 APK 体积。
- 配置弹窗数值滑块兼容 `Int`、`Float`、`Double` 等类型。
- API 服务由应用进程级 `ApiServerManager` 持有，离开 API Server 页面后继续运行。

### 中文化

- 新增 `values-zh/strings.xml` 中文资源。
- 保留英文资源，构建时包含 `en` 和 `zh`。

### 下载与日志

- 模型下载源保留官方 Hugging Face 源。
- 新增 HTTP 下载日志能力。
- 新增日志查看页面，支持复制日志用于排查问题。

### 本地 API Server

- 入口：侧栏 -> `API Server`。
- HTTP 服务实现：native `ServerSocket`。
- 认证方式：无认证或 API Key。
- 认证头：`Authorization: Bearer <apiKey>`。
- 网络支持：本机访问和局域网访问。
- CORS：已支持跨域请求。
- 模型来源：已下载的 LLM 模型。
- 推理链路：复用现有 LiteRT-LM 运行时。

### 可配置参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| Host | `127.0.0.1` | 本机访问；局域网访问设置为 `0.0.0.0` |
| Port | `8080` | 服务监听端口 |
| Auth | `NONE` | 可选 `API_KEY` |
| Temperature | `0.7` | 采样温度，范围 0-2 |
| Max tokens | `1024` | 最大生成 token 数 |
| Top P | `0.95` | 核采样概率阈值 |
| Top K | `40` | Top-K 采样限制 |
| Accelerator | `GPU` | 文本推理后端：CPU/GPU/NPU/TPU |
| Vision accelerator | `GPU` | 视觉输入后端：CPU/GPU/NPU/TPU |

### 日志标记

本地 API 相关日志统一使用 `LOCAL_API`，常见字段如下：

| 字段 | 说明 |
|------|------|
| `request_id` | 请求 ID |
| `event` | 事件名称 |
| `model` | 模型 ID |
| `path` | HTTP 路径 |
| `duration_ms` | 请求耗时 |
| `error` | 错误信息 |
| `auth_type` | 认证方式 |

```bash
# Filter local API logs
adb logcat | grep LOCAL_API
```

## 快速开始

1. 安装并打开 App。
2. 下载一个支持 LLM 的模型，例如 Gemma 或 Qwen。
3. 从侧栏进入 `API Server`。
4. 选择默认模型。
5. 配置默认采样参数和推理后端。
6. 配置监听地址和端口。
7. 局域网访问场景将 Host 设置为 `0.0.0.0`。
8. 按需启用 API Key 并复制 Key。
9. 打开 API 服务开关。
10. 使用 `/health` 或 `/v1/models` 验证服务。

局域网访问时，手机和客户端设备需要位于同一网络。Host 设置为 `0.0.0.0` 后，客户端应访问手机的局域网 IP。

## API 手册

可使用独立调试工具 [local-api-debug-helper](https://github.com/bugroom/local-api-debug-helper) 验证 `/health`、`/v1/models` 和 `/v1/chat/completions`，支持非流式与流式响应测试。

### 端点总览

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health` | 健康检查 |
| GET | `/v1/models` | 获取已下载 LLM 模型列表 |
| GET | `/v1/engines` | 获取已下载 LLM 引擎列表 |
| POST | `/v1/chat/completions` | 聊天补全，支持非流式和流式 |

### 健康检查

```bash
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

### 获取模型列表

无认证：

```bash
curl http://127.0.0.1:8080/v1/models
```

带 API Key 认证：

```bash
curl http://127.0.0.1:8080/v1/models \
  -H "Authorization: Bearer YOUR_API_KEY"
```

响应示例：

```json
{
  "object": "list",
  "data": [
    {
      "id": "Qwen2.5-1.5B-Instruct",
      "object": "model",
      "owned_by": "litert-community",
      "created": 1780680000
    }
  ]
}
```

### 聊天补全

非流式请求：

```bash
curl http://127.0.0.1:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -d '{
    "model": "Qwen2.5-1.5B-Instruct",
    "messages": [
      {"role": "user", "content": "你好"}
    ],
    "temperature": 0.7,
    "max_tokens": 1024,
    "top_p": 0.95,
    "top_k": 40,
    "accelerator": "GPU",
    "vision_accelerator": "GPU"
  }'
```

响应示例：

```json
{
  "id": "chatcmpl-xxx",
  "object": "chat.completion",
  "created": 1780680000,
  "model": "Qwen2.5-1.5B-Instruct",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "你好！很高兴为你服务。"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 2,
    "completion_tokens": 50,
    "total_tokens": 52
  }
}
```

请求参数：

| 参数 | 类型 | 必需 | 默认值 | 说明 |
|------|------|------|--------|------|
| `model` | string | 否 | 默认模型 | 模型 ID；空值使用 API Server 页面设置的默认模型 |
| `messages` | array | 是 | - | 对话消息列表，每个消息包含 `role` 和 `content` |
| `temperature` | float | 否 | 0.7 | 采样温度，范围 0-2 |
| `max_tokens` | integer | 否 | 1024 | 最大生成 token 数 |
| `top_p` | float | 否 | 0.95 | 核采样概率阈值 |
| `top_k` | integer | 否 | 40 | Top-K 采样限制 |
| `accelerator` | string | 否 | GPU | 文本推理后端：CPU/GPU/NPU/TPU |
| `vision_accelerator` | string | 否 | GPU | 视觉输入后端：CPU/GPU/NPU/TPU |
| `stream` | boolean | 否 | false | 是否启用流式响应 |

### 流式聊天补全

```bash
curl http://127.0.0.1:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -d '{
    "model": "Qwen2.5-1.5B-Instruct",
    "messages": [
      {"role": "user", "content": "讲个故事"}
    ],
    "stream": true
  }'
```

流式响应使用 Server-Sent Events 格式：

```text
data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","choices":[{"delta":{"content":"你好"}}]}

data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","choices":[{"delta":{"content":"！"}}]}

data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","choices":[{"delta":{"content":""},"finish_reason":"stop"}]}

data: [DONE]
```

客户端应累积每个 chunk 的 `choices[0].delta.content`。服务端正常结束时会发送 `data: [DONE]`。

## 构建说明

Android 工程目录：

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

APK 输出路径：

```text
Android/src/app/build/outputs/apk/release/app-release.apk
```

更多构建环境、常见问题、性能优化和真机调试说明见 [BUILD_MANUAL.md](BUILD_MANUAL.md)。

## GitHub Actions 工作流适配

Android APK 构建工作流位于：

```text
.github/workflows/build_android.yaml
```

### 项目目录映射

当前仓库的 Android 工程位于 `Android/src`，Gradle wrapper 位于：

```text
Android/src/gradlew
```

工作流通过 `defaults.run.working-directory` 固定执行目录：

```yaml
defaults:
  run:
    working-directory: ./Android/src
```

因此所有 Gradle 命令都从 `Android/src` 目录执行。

### 触发规则

当前工作流支持手动触发、push 触发和 pull request 触发：

```yaml
on:
  workflow_dispatch:
  push:
    branches: ["main", "feature/local-api-server"]
    paths:
      - "Android/**"
      - ".github/workflows/build_android.yaml"
  pull_request:
    branches: ["main", "feature/local-api-server"]
    paths:
      - "Android/**"
      - ".github/workflows/build_android.yaml"
```

适配关系如下：

| 配置项 | 当前值 | 说明 |
|--------|--------|------|
| 默认开发分支 | `feature/local-api-server` | 当前仓库默认分支 |
| 兼容分支 | `main` | 保留常规主分支触发 |
| Android 触发路径 | `Android/**` | Android 工程变化时触发构建 |
| 工作流触发路径 | `.github/workflows/build_android.yaml` | CI 配置变化时触发自检 |

### 构建环境

工作流使用 Ubuntu runner、Temurin JDK 21 和 Gradle 官方缓存：

```yaml
runs-on: ubuntu-latest

- uses: actions/setup-java@v4
  with:
    distribution: temurin
    java-version: "21"

- uses: gradle/actions/setup-gradle@v4
```

JDK 21 与当前 Android Gradle Plugin / Kotlin 构建链路兼容。Gradle 缓存用于减少后续构建耗时。

### 构建命令

工作流构建 Release APK：

```yaml
- name: Make Gradle wrapper executable
  run: chmod +x ./gradlew

- name: Build release APK
  run: ./gradlew :app:assembleRelease
```

本地等价命令：

```bash
# Enter Android project
cd Android/src

# Build release APK
./gradlew :app:assembleRelease
```

### APK 产物上传与自动发布

构建完成后，工作流会执行两步操作：

#### 1. Artifact 上传

```yaml
- name: Upload release APK
  uses: actions/upload-artifact@v4
  with:
    name: google-ai-edge-gallery-local-api-release
    path: Android/src/app/build/outputs/apk/release/app-release.apk
```

临时下载路径：

1. 打开仓库的 `Actions` 页面。
2. 选择一次 `Build Android APK` workflow run。
3. 在页面底部 `Artifacts` 区域下载 `google-ai-edge-gallery-local-api-release`。

#### 2. Release 自动发布

工作流会自动创建/更新 GitHub Release：

```yaml
- name: Create Release
  uses: softprops/action-gh-release@v2
  with:
    tag_name: v1.0.15-latest
    name: Gallery Local API - Latest Build
    files: Android/src/app/build/outputs/apk/release/app-release.apk
    prerelease: true
```

**稳定下载地址**:
- 直接链接: `https://github.com/bugroom/google-ai-edge-gallery-local-api/releases`
- Release 标签: `v1.0.15-latest`（预发布版本）

**注意**: 
- Artifact 保存期为 90 天，Release 中的文件永久保存
- 建议优先从 Releases 页面下载

### 工作流权限与分支保护

当前仓库建议保持以下安全配置：

| 配置 | 推荐值 | 说明 |
|------|--------|------|
| Actions default workflow permissions | `read` | 工作流默认只读仓库内容 |
| can approve pull request reviews | `false` | 禁止 Actions 自动批准 PR |
| 默认分支保护 | 开启 | 防止分支被误删或强推 |
| Required approving reviews | `1` | 合并 PR 至少需要一次审核 |
| Allow force pushes | 关闭 | 防止强制覆盖历史 |
| Allow deletions | 关闭 | 防止删除默认分支 |

当前默认分支是 `feature/local-api-server`。如果后续默认分支改为 `main`，需要同步更新：

- `.github/workflows/build_android.yaml` 的 `branches` 列表。
- GitHub 仓库 `Settings -> Branches` 中的分支保护规则。
- README 中本节的默认分支说明。

### 适配检查清单

修改工作流或 Android 工程结构后，按以下清单检查：

- `Android/src/gradlew` 是否存在。
- `Android/src/settings.gradle.kts` 是否存在。
- `defaults.run.working-directory` 是否仍指向 `./Android/src`。
- 构建命令是否仍为 `./gradlew :app:assembleRelease`。
- APK 输出路径是否仍为 `Android/src/app/build/outputs/apk/release/app-release.apk`。
- 触发分支是否包含当前默认分支。
- artifact 上传路径是否匹配实际 APK 输出路径。

## 故障排查

### 服务无法启动

- 检查端口是否被占用。
- 检查监听地址是否为 `127.0.0.1` 或 `0.0.0.0`。
- 使用 Logcat 搜索 `LOCAL_API event=server_start_failed`。

### 401 Unauthorized

- 检查 API Server 页面是否启用了 API Key。
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

### 流式响应无内容显示

- 确认请求体包含 `"stream": true`。
- 检查客户端是否按 SSE 格式解析 `data:` 事件。
- 检查客户端是否累积 `choices[0].delta.content`。
- 检查流结束事件 `data: [DONE]` 是否到达。

## 当前限制

- 多模态输入（图片、音频）暂未开放为 API 参数。
- Token 统计为估算值（按字符数/4）。
- 真机性能取决于设备、模型大小和加速器配置。
- 本地 API 服务随 App 进程运行，App 进程被系统回收后服务会停止。
- 流式响应为逐 token 返回，每个 token 可能包含 1-4 个汉字。

## 变更记录

- `feat: add local API server support`
- `feat: connect local API server to LLM inference`
- `feat: add streaming response support`
- `feat: add API server sidebar settings`
- `feat: add API defaults and accelerator settings`
- `fix: model instance lifecycle management`
- `fix: streaming response content parsing`
- `fix: send data: [DONE] at stream end`

## License

本项目基于 Google AI Edge Gallery 修改，保留上游 Apache License 2.0 授权。详见 [LICENSE](LICENSE)。
