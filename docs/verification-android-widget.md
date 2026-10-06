# Android 桌面微件验证记录

日期：2026-10-06。

## 实现范围

- RemoteViews 桌面控制卡片，规则 / 全局 / 直连模式切换、当前节点、明确同步时刻、刷新和策略组配置入口。
- 独立 Compose 底部面板，搜索、Selector 成员点选、提交期间重复操作抑制和失败反馈。
- 每个实例独立绑定策略组；远端回读成功后同步所有实例的全局模式。
- WorkManager 一次性工作承载模式及节点提交；关闭面板只停止结果观察，后台提交继续执行。任务恢复只读取状态，持久写入令牌防止重复控制。
- 加密路由器配置、配置代次和快照归属检查，拒绝旧路由器 / 已删除实例返回结果。
- 主应用设置入口、保存 / 清除后的微件失效更新，以及应用配色与系统深浅色。
- Android 8.0 基础 metadata，Android 9+ 重配置 metadata，Android 12+ 网格与布局预览 metadata。

## 自动验证

在当前隔离工作区实际执行：

```powershell
:core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --console=plain --no-daemon
```

复用原仓库的 Gradle 8.13、JDK 17、Android SDK 36 和只读依赖缓存，构建输出写入当前工作区。新增 WorkManager 及其依赖已从既有 Google Maven / Maven Central 仓库补齐。

修复审查问题后的最终完整验证：42 项 core 测试、36 项 Android / Compose / Robolectric 测试，合计 78 项，零失败、错误或跳过。APK 构建通过；lint 0 errors、19 warnings，主要为固定依赖的版本更新提示与资源建议。最终完整命令在 25 秒内成功结束。

新增测试覆盖控制回读、非法模式 / 组 / 成员、写失败、回读失败、取消关闭客户端、不同实例绑定、旧配置结果拒绝、重复写入保护、超时反馈、多实例串行写入、RemoteViews 状态与 PendingIntent 隔离、节点搜索与点选、失败保留、非法配置 ID、取消重配置、系统重调度只读恢复、配置加密 / 代次旋转 / 清除，以及设置定位入口。

控制客户端、存储 / 状态边界、卡片、节点界面及配置集成均先执行缺失功能的失败测试，再实现并确认通过；另通过超时错误反馈和旧请求丢弃的行为断言复现并修复问题。

原型实现阶段的 Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，包名 `com.clashremote.app`，当时版本为 0.2.0 / versionCode 2，20,067,696 字节。`apksigner verify --verbose` 成功，APK Signature Scheme v2、一个签名者。该阶段未签名正式发行版、推送、合并或发布。

## 审查状态

已完成一次独立代码审查，无 Critical 问题，两个 Important 问题已修复：

1. 节点提交原先随 ViewModel 清理而取消，可能中断写入后的回读。现通过持久 Worker 提交，面板观察任务结果；关闭面板不取消 Worker。恢复的节点任务只回读，并返回实际节点，避免重放 PUT。
2. 原刷新使用 KEEP，可能丢弃主应用已确认状态变更后的唯一通知。现刷新与写操作使用独立任务身份，刷新 REPLACE 合并过期读取，保证后续读取执行且不会取消写任务。

`closingPickerAfterPutStillConfirmsWithoutReplayingWrite` 在原直接提交路径暂停于 PUT 后并清理 ViewModel，确认会超时；恢复持久任务路径后通过。`mainAppChangeDuringOldRefreshEventuallyPublishesNewMode` 在旧刷新跨越主应用模式变化时等待新模式超时，调度修复后通过。另验证恢复的节点 Worker 返回确认结果且写入次数为零。

一个 Minor 暂缓：提交失败后，面板不提供重新加载节点列表按钮；用户可关闭后重开刷新列表。测试清理 ViewModel 模拟关闭后的生命周期取消，不代表已在真实桌面启动器完成该操作验收。

## 尚需真机与局域网验收

Robolectric 不代表已完成实际桌面启动器或路由器验证。请在设备检查：

1. 添加微件、取消添加 / 重配置、选择不同策略组。
2. 缩放、长名称、系统深浅色和应用四套配色。
3. 多个微件共享模式，并分别显示其组的当前节点。
4. 主应用退到后台、进程被系统回收或手机重启后的刷新与控制。
5. 保存 / 更换 / 清除路由器配置后不显示旧快照。
6. 断网、错误密钥、节点 / 组被删除以及网络恢复。

桌面卡片是上次确认的快照，不表示持续联网；不同 Launcher 的实际占格和预览呈现由平台决定。

## 工作区与版本控制

工作发生在 Codex 已管理的隔离工作区，未创建第二个工作区。Windows 沙箱无法启动 Git Bash 的 MSYS 辅助脚本，因此使用 PowerShell 记录相同的任务进度与验证日志。

实现完成时 Git 作者未配置，变更先保留在工作区供审阅。用户随后要求发布正式版，发布提交使用代理自身的 Codex 作者身份，只通过单次命令参数配置，不修改全局 Git 身份。构建使用的本地 SDK 配置、签名文件和缓存均不入库。

## 正式版 0.3.0 的发布前验证

用户于 2026-10-06 明确要求“发布正式版”。版本更新为 0.3.0 / versionCode 3，保持既有包名与正式签名，支持已有正式版覆盖升级。

本地实际执行 `:core:test :app:testDebugUnitTest :app:lintRelease :app:assembleRelease --offline`，72 秒构建成功。78 项测试零失败、错误或跳过；Release lint 无错误、10 条提示。正式 APK 为 `app/build/outputs/apk/release/app-release.apk`，12,965,684 字节。

`apksigner verify --verbose --print-certs` 验证成功；证书 SHA-256 为 `4ee37ba90f0ce8d9e68350ef00f131df502c39d6554a803adf281c43af944d59`，与既有正式证书一致。公开发行使用已配置的 Android Release 工作流生成 APK 和 SHA256SUMS.txt，发布步骤见 `docs/releases.md`。
