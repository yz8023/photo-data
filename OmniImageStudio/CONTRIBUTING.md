# Contributing to OmniImage Studio

感谢参与 OmniImage Studio 开源项目。

## 行为准则

- 尊重所有参与者，评审聚焦代码与设计，避免人身评论。
- 所有代码必须可离线运行，不引入任何网络调用（发布与崩溃上报除外）。

## 开发流程

1. Fork 仓库并创建功能分支：`YYMMDD-feat-描述`
2. 本地构建验证：`./gradlew :app:assembleDebug`
3. 提交信息格式：`feat(module): 描述` / `fix(module): 描述`
4. 发起 Pull Request，说明改动动机与验证方式

## 代码规范

- Kotlin，遵循 Android Kotlin Style Guide
- Compose UI 组件放在 `ui/`，业务逻辑放在 `engine/`，模型放在 `model/`
- 纯算法（滤镜/盲水印/矢量化）不依赖 Android 框架可测试部分，应尽量独立
- 新增依赖需在 PR 中说明许可协议

## 环境

- JDK 17 / Android SDK 35 / Gradle 8.7（wrapper）
- 国内网络：Gradle 发行版走腾讯镜像，依赖走阿里云镜像（已配置）
