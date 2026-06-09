# 本地 API 服务

## 概念定义

本地 API 服务是运行在 Android App 进程内的 HTTP 服务，用于将已下载的本地 LLM 模型暴露为 OpenAI 兼容接口。服务由 `ApiServer` 实现，由 `ApiServerManager` 在应用进程级持有。

## 访问方式

- 本机访问：默认 Host 为 `127.0.0.1`。
- 局域网访问：设置 Host 为 `0.0.0.0` 后，其他设备可访问手机局域网 IP 和配置端口。

## 生命周期

`ApiServerManager.initialize()` 在 `GalleryApplication.onCreate()` 中执行。服务开关位于 API Server 设置页面。服务启动后由 `ApiServerManager` 持有，离开设置页面后继续运行。App 进程结束后服务停止。

## 配置来源

配置由 `ApiServerConfigManager` 从 `SharedPreferences("api_server_config")` 读写。设置页面可配置监听地址、端口、认证、默认模型、采样参数和加速器。

## 并发与超时

`ApiInferenceHandler` 使用 `Semaphore(maxConcurrent)` 控制并发。请求超时由 `requestTimeoutMs` 控制，`ApiServerManager` 创建 handler 时会将该值限制为至少 180 秒。

## 认证

`AuthType.NONE` 表示跳过认证。其他认证类型要求请求头完全匹配 `Authorization: Bearer <API_KEY>`。`CUSTOM` 枚举存在于 `AuthType`，但当前授权实现对所有非 `NONE` 类型使用同一种 Bearer 校验逻辑。

## 限制

- 服务不独立于 App 进程运行。
- 服务端没有 TLS 终止逻辑，Manifest 中开启了 cleartext traffic。
- 当前 API Server 没有实现工具调用、函数调用、图片输入或音频输入。
