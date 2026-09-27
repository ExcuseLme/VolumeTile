# VolumeTile · 音量磁贴

[![Build Release APK](https://github.com/ExcuseLme/VolumeTile/actions/workflows/build.yml/badge.svg?branch=release)](https://github.com/ExcuseLme/VolumeTile/actions/workflows/build.yml)

零界面（No-UI）Android 工具应用：无任何窗口，仅向快捷设置面板提供两个可固定磁贴，
分别控制媒体音量 **+1 级 / −1 级**，级数完全跟随系统实际值（15 级机型即 15 级）。

- 无 Activity、无运行时权限弹窗、无后台常驻、无第三方依赖
- 技术栈：Kotlin · Gradle 8.13 · AGP 8.13.0 · Kotlin 2.3.0 · minSdk 34 · targetSdk 36
- 详细设计：[docs/design.md](docs/design.md)

## 构建（纯 GitHub Actions，无需本地 Android 环境）

本仓库不携带 Gradle wrapper，也不需要本地 Android SDK，全部构建在 CI 完成：

1. **触发**：push 到 `release` 分支，或在 **Actions → Build Release APK → Run workflow** 手动触发
2. **等构建变绿**（Build and publish APK 任务成功即代表构建通过）
3. **下载 APK**（二选一）：
   - 该次 run 的 **Artifacts → `VolumeTile-release-<运行号>`**
   - 仓库 **Releases → Continuous Build（`continuous` 标签）→ `VolumeTile-release.apk`**（始终是最近一次成功构建）
4. **手机安装**：直接安装；若提示与已装版本签名冲突，先卸载旧版再装
   （应用无任何本地数据，卸载无损失；仓库内固定签名密钥通常可直接覆盖安装）

## 使用

安装后：下拉快捷设置 → 点「编辑」（铅笔图标）→ 在列表中找到「音量 +」「音量 -」→
拖入面板固定。点击即调节，磁贴副标题实时显示 `媒体 7/15`，
并弹出系统新版音量面板作为反馈。

## 签名说明

`keystore/release.p12` 为本项目专用测试密钥，**有意随仓库提交**——
保证每次 CI 构建签名一致、手机可覆盖安装。密码写在 `app/build.gradle.kts` 中。
该密钥仅用于本项目自用分发，请勿用于任何有用户的应用。

## 目录结构

```
.github/workflows/build.yml   CI 构建工作流（release 分支触发）
docs/design.md                设计文档
keystore/release.p12          发布签名密钥（固定签名）
app/src/main/AndroidManifest.xml
app/src/main/java/dev/volumetile/
  ├─ volume/VolumeController.kt      音量调节核心（API 34 分组 + 经典流回退）
  └─ tile/VolumeTileServiceBase.kt   磁贴基类（点击/刷新）
      VolumeUpTileService.kt         「音量 +」磁贴
      VolumeDownTileService.kt       「音量 -」磁贴
```
