# 项目编译手册

本文档详细说明如何编译 Google AI Edge Gallery 本地 API 魔改版，包含常见问题和解决方案。

## 目录

1. [环境准备](#环境准备)
2. [项目结构](#项目结构)
3. [编译步骤](#编译步骤)
4. [常见问题](#常见问题)
5. [真机调试](#真机调试)
6. [性能优化](#性能优化)

---

## 环境准备

### 必需软件

| 软件 | 版本要求 | 用途 |
|------|----------|------|
| Android Studio | 最新稳定版 | IDE 和构建工具 |
| JDK | 17+ | 编译环境 |
| Android SDK | API 28+ | 最低支持 Android 9.0 |
| Gradle | 项目自带 | 构建系统 |

### 推荐配置

- **内存**: 至少 16GB RAM（构建过程需要大量内存）
- **磁盘**: 至少 20GB 可用空间
- **网络**: 稳定的网络连接（下载依赖）

### 环境变量

```bash
# 在 ~/.bashrc 或 ~/.zshrc 中添加
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
```

---

## 项目结构

```
gallery/
├── Android/
│   └── src/                      # Android 工程根目录
│       ├── app/                  # App 模块
│       │   ├── src/main/         # 源代码
│       │   └── build.gradle.kts  # 构建配置
│       ├── gradlew               # Gradle wrapper
│       └── settings.gradle.kts   # 项目设置
├── .monkeycode/                  # 项目文档
└── README.md
```

**重要**: 所有编译命令都在 `Android/src` 目录下执行。

---

## 编译步骤

### 1. 进入工程目录

```bash
cd Android/src
```

### 2. 首次构建前准备

```bash
# 给 gradlew 添加执行权限
chmod +x gradlew

# 下载依赖（可选，首次构建会自动下载）
./gradlew :app:dependencies
```

### 3. 编译 Kotlin 源码

```bash
# 编译 release 版本 Kotlin 源码
./gradlew :app:compileReleaseKotlin
```

**成功标志**: 看到 `BUILD SUCCESSFUL`

### 4. 构建 Release APK

```bash
# 构建 release APK
./gradlew :app:assembleRelease
```

**输出路径**: `Android/src/app/build/outputs/apk/release/app-release.apk`

### 5. 构建 Debug APK（可选）

```bash
# 构建 debug APK（更快，但未优化）
./gradlew :app:assembleDebug
```

**输出路径**: `Android/src/app/build/outputs/apk/debug/app-debug.apk`

---

## 常见问题

### 问题 1: Gradle 下载失败

**症状**:
```
Could not resolve all dependencies for configuration ':app:classpath'.
```

**解决方案**:

1. 配置国内镜像（`Android/src/gradle.properties`）:
```properties
# 在文件末尾添加
systemProp.http.proxyHost=mirrors.aliyun.com
systemProp.http.proxyPort=8080
systemProp.https.proxyHost=mirrors.aliyun.com
systemProp.https.proxyPort=8080
```

2. 或使用离线模式:
```bash
./gradlew :app:assembleRelease --offline
```

### 问题 2: Kotlin 编译警告

**症状**:
```
w: Experimental context receivers are superseded by context parameters.
```

**说明**: 这是上游项目的已知警告，不影响功能，可忽略。

### 问题 3: 内存不足

**症状**:
```
java.lang.OutOfMemoryError: Java heap space
```

**解决方案**:

在 `Android/src/gradle.properties` 中增加内存:
```properties
org.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=512m
org.gradle.parallel=true
org.gradle.configureondemand=true
org.gradle.daemon=true
```

### 问题 4: 签名配置错误

**症状**:
```
Execution failed for task ':app:packageRelease'. 
> com.android.ide.common.signing.SigningException: 
Failed to read key from keystore
```

**解决方案**:

项目使用 debug 签名，如需 release 签名，创建 `Android/src/local.properties`:

```properties
STORE_FILE=/path/to/your/keystore.jks
STORE_PASSWORD=your_password
KEY_ALIAS=your_alias
KEY_PASSWORD=your_password
```

或使用 debug 版本测试:
```bash
./gradlew :app:assembleDebug
```

### 问题 5: NDK 版本不匹配

**症状**:
```
NDK version mismatch
```

**解决方案**:

在 Android Studio 中:
1. Tools → SDK Manager → SDK Tools
2. 安装 NDK (Side by side)
3. 在项目 `build.gradle.kts` 中指定版本:

```kotlin
android {
    ndkVersion = "25.2.9519653"
}
```

### 问题 6: 依赖冲突

**症状**:
```
Duplicate class found
```

**解决方案**:

清理并重新构建:
```bash
./gradlew clean
./gradlew :app:assembleRelease
```

### 问题 7: 资源合并失败

**症状**:
```
MergeResources task failed
```

**解决方案**:

1. 检查中文资源文件编码是否为 UTF-8
2. 检查 XML 文件格式是否正确
3. 清理构建:
```bash
./gradlew clean
rm -rf app/build
./gradlew :app:assembleRelease
```

### 问题 8: 版本号冲突

**症状**:
```
Version code conflict
```

**解决方案**:

修改 `Android/src/app/build.gradle.kts`:
```kotlin
android {
    defaultConfig {
        versionCode = (System.currentTimeMillis() / 1000).toInt()
        versionName = "1.0-custom"
    }
}
```

---

## 真机调试

### 启用 USB 调试

1. 手机设置 → 关于手机 → 连续点击版本号 7 次开启开发者模式
2. 开发者选项 → 启用 USB 调试
3. 连接电脑，授权调试

### 安装 APK

```bash
# 通过 ADB 安装
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 覆盖安装（保留数据）
adb install -r app/build/outputs/apk/release/app-release.apk
```

### 查看日志

```bash
# 过滤 LOCAL_API 相关日志
adb logcat | grep LOCAL_API

# 过滤特定 tag
adb logcat -s ApiServerManager:D ApiInferenceHandler:D

# 清除日志
adb logcat -c
```

### 网络调试

如需调试 API 服务:

```bash
# 端口转发（将手机 8080 端口转发到电脑）
adb forward tcp:8080 tcp:8080

# 测试连接
curl http://localhost:8080/health
```

---

## 性能优化

### 减小 APK 体积

项目已配置:
- `minSdk = 28` 支持 Android 9.0+
- `abiFilters += listOf("arm64-v8a")` 仅 ARM64
- 移除 x86 和 armeabi-v7a 支持

如需进一步减小:

```kotlin
android {
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

### 加快构建速度

```bash
# 使用 Build Cache
./gradlew :app:assembleRelease --build-cache

# 并行构建
./gradlew :app:assembleRelease --parallel

# 配置按需
./gradlew :app:assembleRelease --configure-on-demand

# 守护进程模式（默认开启）
./gradlew --daemon :app:assembleRelease
```

### 内存优化

对于低内存设备构建:

```bash
# 减少 Gradle 内存使用
export GRADLE_OPTS="-Xmx4g -XX:MaxMetaspaceSize=256m"

# 关闭 Gradle Daemon
./gradlew --stop
./gradlew :app:assembleRelease --no-daemon
```

---

## 验证构建

### 检查 APK 信息

```bash
# 查看 APK 基本信息
aapt dump badging app/build/outputs/apk/release/app-release.apk

# 查看签名信息
apksigner verify -v app/build/outputs/apk/release/app-release.apk

# 查看文件列表
unzip -l app/build/outputs/apk/release/app-release.apk
```

### 验证功能

安装后验证:

1. App 正常启动
2. 侧栏 → API Server 页面可打开
3. 能正常启动/停止服务
4. 健康检查 `curl http://<ip>:<port>/health` 返回 ok
5. 能正确返回已下载模型列表

---

## 高级配置

### 自定义版本号

修改 `Android/src/app/build.gradle.kts`:

```kotlin
android {
    defaultConfig {
        versionCode = 1
        versionName = "1.0-local-api"
    }
}
```

### 自定义包名

```kotlin
android {
    namespace = "com.yourcompany.localai"
    
    defaultConfig {
        applicationId = "com.yourcompany.localai"
    }
}
```

### 添加自定义模型

修改 `Android/src/app/src/main/res/raw/model_allowlist.json`

---

## 故障排查清单

构建失败时按以下顺序检查:

1. [ ] JDK 版本是否为 17+
2. [ ] Android SDK 是否正确安装
3. [ ] 是否有足够磁盘空间 (>20GB)
4. [ ] 是否有足够内存 (>16GB 推荐)
5. [ ] 网络连接是否正常（下载依赖）
6. [ ] 是否执行了 `./gradlew clean`
7. [ ] 是否使用了正确的构建命令

---

## 参考链接

- [上游项目](https://github.com/google-ai-edge/gallery)
- [Gradle 官方文档](https://docs.gradle.org/)
- [Android 构建指南](https://developer.android.com/studio/build)
