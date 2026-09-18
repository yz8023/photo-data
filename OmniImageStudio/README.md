# OmniImage Studio

Android 端纯离线全能图像工具：格式转换、EXIF 编辑、画布绘制与对称画笔、位图矢量化、SVG/XML 编辑导出、水印系统、批量处理。

- 语言 / UI：Kotlin · Jetpack Compose（Liquid Glass 视觉风格）
- 离线：无需网络权限，全本地处理
- 构建：仅 `arm64-v8a`，APK 体积最小化
- 发布：GitHub Releases（debug 包由 Actions 自动构建）

## 功能一览

| 模块 | 说明 |
|---|---|
| 格式转换 | PNG / JPEG / WEBP / BMP / ICO 输入输出，SVG/XML 输出（预留 GIF/TIFF/HEIC/AVIF/TGA/EPS/PDF 接口） |
| 裁剪与调节 | 比例裁剪、旋转翻转、8 种滤镜、亮度/对比度/饱和度/色温/曝光/阴影/高光、曲线/色阶、滤镜预设 |
| EXIF | 全量查看、编辑任意字段、GPS 清除、全部清除、预设保存与应用 |
| 矢量化 | 位图 → SVG / Android XML VectorDrawable（纯 Kotlin 轮廓追踪）、SVG 解析与预览渲染、PDF 导出 |
| 水印 | 文字/图片水印、九宫格定位、平铺模式、预设模板、LSB / DCT 盲水印嵌入与提取 |
| 画布 | 自定义画布、对称画笔（垂直/水平/四象限/径向/万花筒）、阻尼滑条、全色域色盘、图层系统 |
| 批量 | 多图/文件夹批量转换、统一尺寸、批量水印、批量重命名、EXIF 应用/清除、目录结构保留 |

## 快速构建

```bash
# 环境要求：JDK 17、Android SDK 35
git clone <your-repo-url>
cd OmniImageStudio
./gradlew :app:assembleDebug
# APK 输出：app/build/outputs/apk/debug/app-debug.apk
```

国内网络可将 Gradle 发行版换为腾讯镜像（已在 `gradle-wrapper.properties` 内置），依赖仓库已配置阿里云镜像。

## GitHub Actions

`.github/workflows/build-apk.yml` 会在 push / PR / tag 时自动构建 debug APK 并上传 artifact；打 `v*` tag 时自动创建 Release 并附 APK。

## 构建指引

详见 [docs/BUILD.md](docs/BUILD.md) 与 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## 协议

MIT License。注意：NDK 集成 potrace（GPL）会引入传染性，启用前请评估（见 docs/ARCHITECTURE.md 第 7 节）。
