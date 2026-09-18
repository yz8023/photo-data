# OmniImage Studio - NDK 脚手架
# 注意：此 CMake 当前被 app/build.gradle.kts 注释停用（减少首包体积与构建风险）。
# 接手者如需启用 potrace/libwebp/resvg，请取消注释 externalNativeBuild 并保持本目录结构。
cmake_minimum_required(VERSION 3.22.1)
project(omniimage_ndk)

set(CMAKE_CXX_STANDARD 17)

# potrace（GPL，谨慎集成 —— 传染性分析见 docs/ARCHITECTURE.md）
add_library(potrace_wrapper SHARED potrace_wrapper.cpp)

# libwebp（BSD，Google）
add_library(webp_wrapper SHARED webp_wrapper.cpp)

# resvg（MIT/Apache 2.0，SVG 渲染）
add_library(resvg_wrapper SHARED resvg_wrapper.cpp)
