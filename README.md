# VolumeTile · 音量磁贴

[![Build Release APK](https://github.com/ExcuseLme/VolumeTile/actions/workflows/build.yml/badge.svg?branch=release/1.4)](https://github.com/ExcuseLme/VolumeTile/actions/workflows/build.yml)

零界面（No-UI）Android 工具应用：无任何窗口，仅向快捷设置面板提供两个可固定磁贴，
分别控制媒体音量 **+1 级 / −1 级**，调节**自动对齐到整数级**（滑条停在 4.3 这类分数位置时，
点击后吸附回 4 / 5 整数格），级数跟随系统实际值。

- 无 Activity、无运行时权限弹窗、无第三方依赖
- 技术栈：Kotlin · Gradle 8.13 · AGP 8.13.0 · Kotlin 2.3.0 · minSdk 34 · targetSdk 36
- 详细设计：[docs/design.md](docs/design.md)

## v2.0：修复澎湃 4（HyperOS 4 / Android 17）磁贴失效

小米 17 等机型升级澎湃 4（Android 17）后，v1.4 的磁贴点击**完全无反应且不报错**。

**根因**：Android 17 新增 *Background audio hardening*，后台应用的音量调用被**静默丢弃**。
磁贴是 SystemUI 绑定的后台服务，进程处于 cached 态，命中 `level: partial` 加固。
其他视频 App 没事，是因为它们有可见 Activity 或带 WIU 的前台服务 —— **与权限无关**。

**修复**：点击磁贴时先启动一个极短的前台服务（`VolumeAdjustService`）再调音量，
调完立即退出。之所以**保持 targetSdk 36 而不升 37**，是因为 36 走宽松档
（只要有前台服务即放行），升到 37 反而要求前台服务必须具备 while-in-use 能力，
限制更严。详见 [docs/design.md §0](docs/design.md)。

## 构建（纯 GitHub Actions，无需本地 Android 环境）

本仓库不携带 Gradle wrapper，也不需要本地 Android SDK，全部构建在 CI 完成：

1. **触发**：push 到 `release/1.0`（及任意 `release/**` 分支），或在 **Actions → Build Release APK → Run workflow** 手动触发
2. **等构建变绿**（Build and publish APK 任务成功即代表构建通过）
3. **下载 APK**（二选一）：
   - 该次 run 的 **Artifacts → `VolumeTile-release-<运行号>`**
   - 仓库 **Releases → Continuous Build（`continuous` 标签）→ `VolumeTile-release.apk`**（始终是最近一次成功构建）
4. **手机安装**：直接安装；若提示与已装版本签名冲突，先卸载旧版再装
   （应用无任何本地数据，卸载无损失；仓库内固定签名密钥通常可直接覆盖安装）

## 使用

安装后：下拉快捷设置 → 点「编辑」（铅笔图标）→ 在列表中找到「音量 +」「音量 -」→
拖入面板固定。点击即调节并**自动对齐到整数级**（静默模式，无额外弹窗）；磁贴为**纯静态工具外观**（固定名称、无高亮状态），级数变化由控制中心自带的音量条实时反馈。

## 签名说明

`keystore/release.p12` 为本项目专用测试密钥，**有意随仓库提交**——
保证每次 CI 构建签名一致、手机可覆盖安装。密码写在 `.github/workflows/build.yml` 的签名步骤中（Gradle 侧不配置签名，产物为 unsigned APK，由 CI 用 `apksigner --ks-type PKCS12` 显式签名）。
该密钥仅用于本项目自用分发，请勿用于任何有用户的应用。

## 目录结构

```
.github/workflows/build.yml   CI 构建工作流（release 分支触发）
docs/design.md                设计文档
keystore/release.p12          发布签名密钥（固定签名）
app/src/main/AndroidManifest.xml
app/src/main/java/com/tedexcuseme/volumetile/
  ├─ volume/VolumeController.kt      音量调节核心（v1.3 整数级对齐网格，step=max/15）
  ├─ volume/VolumeAdjustService.kt   v2.0 前台服务（Android 17 音量加固适配，调完即退）
  └─ tile/VolumeTileServiceBase.kt   磁贴基类（点击经前台服务调音量 / 静态外观）
      VolumeUpTileService.kt         「音量 +」磁贴
      VolumeDownTileService.kt       「音量 -」磁贴
```
