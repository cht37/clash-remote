# 首版交付验证

日期：2026-10-06。源代码保存在当前目录的 `codex/clash-remote` 分支。

## 安装包

- `app/build/outputs/apk/debug/app-debug.apk`
- 版本：0.1.0；包名：com.clashremote.app。
- 最低 Android 8.0 / API 26，target SDK 36。
- 文件大小：19,416,016 字节，开发调试签名，APK v2 签名校验通过。
- SHA-256：`706d6a97bc645cae4890c4fbdf2ac70fc37559737d09e94a08f6efe0997fb9c7`。

## 实际执行结果

`:core:test :app:assembleDebug :app:lintDebug`：BUILD SUCCESSFUL。

| 测试类 | 测试数 | 失败 / 错误 |
| --- | ---: | ---: |
| ClashApiTest | 10 | 0 / 0 |
| ProfileCodecTest | 2 | 0 / 0 |
| RemoteControllerTest | 6 | 0 / 0 |
| SettingsControllerTest | 3 | 0 / 0 |
| 合计 | 21 | 0 / 0 |

Android lint：0 个错误，7 个警告；5 个提示有更新依赖版本，2 个建议使用 SharedPreferences KTX 写法。固定的依赖组合已成功编译，本次保持版本一致；同步 commit() 用来识别保存失败。

`scripts/build-local.ps1` 在本机实际执行成功。`apksigner verify --verbose` 对最终安装包返回 Verifies。

## 独立代码审查和修复

完成一次独立、只读、全范围审查，随后修复所有发现的问题：

- 设置草稿移入 ViewModel 持有的 SettingsController，页面切换和旋转不会重新从保存值覆盖草稿；编辑草稿取消旧测试、清除旧结果，以代次守卫阻止旧测试更新新草稿。
- 处理服务器 WebSocket Close 帧的 onClosing 回调，使实时流量不会长期冻结在旧速率；通过先失败后通过的 MockWebServer 回归测试验证。
- 显式排除云备份和设备迁移中的所有存储域；配置资源编译和 lint 验证通过，原 DataExtractionRules 警告消失。
- 连接轮询与关闭操作 / 回读共用控制互斥锁，防止旧快照重新显示已经关闭的连接；延迟响应回归测试先失败后通过。
- 节点采用包含标签与选中状态的单一可选择行，单选图标由父行处理选择，测速保留独立操作；编译和 lint 验证通过。

没有延期的代码审查发现。

## 执行中的决定和取舍

- 在用户指定的空目录和新 `codex/clash-remote` 分支实现，未另建工作树；如果后来需要并行隔离，可以再转移分支。
- 增加 JVM core 模块和 `-PcoreOnly`，让 API / 会话测试无需 Android SDK；代价是多一个构建模块。
- 在 Windows 用 PowerShell 记录执行进度，替代技能中的 POSIX 辅助脚本；进度记录由执行者维护。
- 官方 Gradle 下载暂时停滞时使用公开镜像，以官方 SHA-256 校验发行包；生成的 wrapper JAR 也与官方 SHA-256 一致。
- Android SDK 使用 Google 官方归档和下载清单的校验值，在项目 `.tooling` 中准备；后续 SDK 升级仍建议使用 Android Studio / sdkmanager，可能需要补齐新元数据。
- 用可注入的 JVM ProfileCodec 测试实际 AES-GCM 和元数据认证，Android 端密钥来自 Keystore；真机 Keystore 行为仍需设备验证。
- 将草稿与独立连接测试的状态抽到 SettingsController，方便验证取消与保存失败，未把未保存的密钥放进 saved-state Bundle；进程被系统终止后未保存草稿会丢失。
- 将旧连接回显和无标签选择控件视为核心操作体验问题一并修复；慢连接请求期间关闭操作可能等待当前请求结束。

## 尚未进行的环境验证

没有连接用户的路由器，也没有使用真实手机或安卓模拟器。因此路由器 / OpenClash 具体配置、真机联网、Keystore、设备迁移、TalkBack、系统字体放大、横屏和软键盘布局仍需在实际设备验收。自动测试与构建通过不代替这些检查。

接入和真机验收步骤见项目 README。工程和安装包完整保留，可直接安装首版并继续迭代。
