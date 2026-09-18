# 编译说明

## 环境要求

| 组件 | 版本 |
|---|---|
| JDK | 17（Temurin 推荐） |
| Android SDK | compileSdk 35, build-tools 35.0.0, platform-tools |
| Gradle | 8.7（wrapper 内置，发行版走腾讯镜像） |
| AGP | 8.5.2 |
| Kotlin | 1.9.24 |
| NDK | 26.x（当前仅脚手架，未参与构建） |

## 一键构建

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug --stacktrace
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

## 镜像配置（已内置）

- 依赖仓库：`settings.gradle.kts` 已配置阿里云 google/gradle-plugin/central
- Gradle 发行版：`gradle/wrapper/gradle-wrapper.properties` 指向腾讯镜像
- 获取优先级：本地缓存 → 阿里云/腾讯镜像 → 官方源

## 常见坑

1. **JDK 版本**：AGP 8.x 要求 JDK 17，旧 JDK 会报 `Unsupported class file major version`
2. **SDK 未找到**：确认 `ANDROID_HOME` / `local.properties` 指向正确 SDK，且已 `sdkmanager --licenses`
3. **依赖下载慢**：镜像已在 `settings.gradle.kts` 配置；若某些库官方源被墙，按顺序尝试代理（见 HANDOVER.md 第 4 节）
4. **仅 arm64-v8a**：`app/build.gradle.kts` 中 `abiFilters`，无需 NDK 时不影响构建

## GitHub Actions

`.github/workflows/build-apk.yml`：push/PR/tag 触发，自动构建 debug APK 并上传 artifact，`v*` tag 自动发 Release。
