# 架构说明

## 分层

```
ui/      Jetpack Compose 界面（8 个屏幕 + Liquid Glass 主题组件）
engine/  纯 Kotlin 图像引擎（可独立测试）
model/   数据模型（ImageTask / ExifPreset / WatermarkPreset / Layer）
vector/  SVG 子集解析器 + 渲染器（path/rect/circle/ellipse/line/polygon）
util/    PresetStorage(JSON) / RecentFiles / UndoRedoStack / FileSaver
cpp/     NDK 脚手架（potrace / libwebp / resvg，默认停用）
```

## 关键引擎

| 引擎 | 职责 | 实现 |
|---|---|---|
| ConvertEngine | 编解码、BMP/ICO 手工编码 | BitmapFactory/ImageDecoder + 自研编码器 |
| ResizeEngine | 缩放、限大小降质 | createScaledBitmap + 迭代质量/尺寸 |
| ExifEngine | EXIF 读写、GPS 清除 | AndroidX ExifInterface（经缓存文件读写） |
| VectorizeEngine | 位图描摹 → SVG/XML | 纯 Kotlin 颜色分层 + 轮廓追踪 + Douglas-Peucker |
| SymmetryBrush | 对称画笔 | android Matrix 变换 + Compose 实时预览 |
| FilterEngine | 8 滤镜 + 7 项调节 + 曲线/色阶 | CPU 像素级纯 Kotlin |
| WatermarkEngine | 文字/图片水印、平铺 | Canvas 绘制 |
| SteganoEngine | LSB / 8x8 DCT 盲水印 | 纯 Kotlin |
| LayerEngine | 图层合成、合并 | PorterDuff 混合 |
| BatchEngine | 批量流水线 | 目录递归 + 保留相对结构 |

## SVG 能力边界

- 支持：`<path>`（M/L/H/V/C/S/Q/T/Z 全命令）、`rect/circle/ellipse/line/polygon/polyline`
- 属性：`fill / stroke / stroke-width / fill-opacity / viewBox`
- 不支持：`<g>` 嵌套、渐变、滤镜、clipPath、CSS 样式
- 完整 SVG 渲染请启用 `cpp/resvg_wrapper`（JNI 脚手架已就绪）

## NDK 与协议风险

1. **potrace（GPL）**：以 NDK 链接进 APK 分发会触发 GPL 传染。备选：独立进程调用 CLI、或继续使用自研 Kotlin 追踪（当前默认）
2. **libwebp（BSD）**：无传染性，可直接启用
3. **resvg（MIT/Apache 2.0）**：无传染性，可直接启用
4. **motion-web（CC BY-NC 4.0）**：仅借鉴弹簧阻尼思路，禁止复制代码；本项目 `DampedSlider` 为自研实现
5. **AndroidLiquidGlass**：本项目以自研玻璃拟态组件实现视觉，未直接引入该库

## 已知技术债

- HEIC/AVIF 编解码未启用（需 libavif / 系统 ImageDecoder 扩展）
- ExifInterface 对 PNG/WEBP 写入依赖缓存文件替换，部分字段可能受限
- 画布柔边画笔为近似实现（alpha 模拟）
- 矢量化轮廓追踪在图像复杂时可能存在闭合路径边界锯齿
