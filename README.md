# Clash Remote

[源代码](https://github.com/cht37/clash-remote) · [发行版](https://github.com/cht37/clash-remote/releases/latest) · [MIT License](LICENSE)

通过原生 Android 界面管理路由器上的 Clash / Mihomo。在同一 Wi-Fi 或局域网中，填写控制 API 地址和密钥，即可使用。最低 Android 8.0。

## 首版功能

- 概览：内核版本、在线状态、实时上下载速率、流量曲线、累计流量、规则 / 全局 / 直连模式。
- 代理：策略组、当前节点、搜索、Selector 手动切换、节点与整组测速（最多 4 个并发）。
- 桌面微件：规则 / 全局 / 直连一键切换、可搜索的节点选择面板、手动刷新；每个微件可绑定不同 Selector 策略组。
- 连接：域名、来源 IP、协议、代理链、规则、流量；搜索、关闭单个或全部连接。
- 设置：路由器地址、secret、测速地址、独立连接测试、保存并连接、清除本地配置。
- 保存成功后自动进入概览，立即显示新路由器与连接状态，并提示配置已保存。
- 配色：松绿、海蓝、鸢紫、琥珀四套配色，可即时切换并保存，深浅模式跟随系统。
- 更新：在设置中获取 GitHub 最新正式发行版，显示版本和说明；有新版时提供 APK 下载链接，通过浏览器下载后由安卓系统确认安装。

图标使用经典 Clash 猫咪，支持安卓自适应形状和 Android 13+ 主题图标。素材来源与许可证见 [第三方素材声明](THIRD_PARTY_NOTICES.md)。

密钥使用 Android Keystore AES-GCM 加密保存，应用不备份配置。主界面进入后台停止轮询和流量连接，回到前台重新连接；桌面微件的操作通过一次性后台任务执行。保存一个路由器配置。

## 桌面微件

1. 先在应用设置中保存路由器配置。
2. 长按安卓桌面空白处，打开“微件 / 小组件”，找到“Clash Remote · 代理控制”，选择 2×2、4×1、4×2 或 4×3 并添加。
3. 选择此微件要控制的 Selector 策略组。可添加多个微件，分别选择不同策略组；模式切换会同步到所有微件。
4. 点击“规则 / 全局 / 直连”直接切换；点击当前节点打开面板，搜索并点选节点即可切换。右上角刷新状态，“策略组”按钮可重新配置绑定。

默认微件为标准 4×2，另提供紧凑 2×2、横条 4×1 和大卡片 4×3，所有尺寸均保留模式切换、节点选择、策略组配置与刷新入口。长按后拖动边缘可调整大小，布局随可用空间切换；2×2 的最低尺寸为两列两行，4×1 横条至少需要四列，4×2 / 4×3 可缩至 2×2。Android 12+ 声明明确的网格尺寸，旧版使用对应的 dp 尺寸；实际占格仍取决于桌面启动器。升级后已有微件保留绑定，旧占格需要手动缩放或移除后重新添加。配色沿用应用设置，深浅模式跟随系统。文字采用统一层级，4×1 上行显示节点、下行显示策略组和操作；节点名称可换行并在有限字号范围内适配。紧凑尺寸优先显示节点、策略组和模式，详细错误显示在 4×3 大卡片；超长文字仍受桌面空间限制。同步时间显示为 HH:mm，后台不持续轮询；需要确认最新状态时点击刷新。同步失败保留最后确认的状态并显示错误，写入成功但回读失败时提示刷新确认。

主界面中的已确认控制变更会触发微件同步。保存或清除路由器配置会使旧快照与旧任务失效；未配置时，微件提供设置入口。系统重新调度已中断的控制任务时只读取实际状态，不自动重复写入。节点选择面板只允许手选 Selector，失效策略组需要重新配置。

节点提交交给持久后台任务；提交后关闭选择面板，任务仍会完成回读并更新卡片。提交失败后可重试；需要重新获取节点列表时，关闭面板后再次打开。

完成真机验收时，请检查添加及取消、缩放、深浅色、多实例、进程被系统回收后的操作、切换路由器、断网及恢复。不同启动器和实际路由器的行为需要在你的设备与局域网验证。

## 路由器准备

在路由器的 Clash / Mihomo 设置或配置文件中启用控制 API，例如：

```yaml
external-controller: 0.0.0.0:9090
secret: "替换为自己的强密钥"
```

如果使用 OpenClash，使用其控制接口设置中的实际监听端口和密钥；端口可能不是 9090。需要确保配置最终生效，并让路由器防火墙只向可信 LAN 开放控制端口。

在 App 的“设置”页面填写：

1. 控制地址：例如 `http://192.168.1.1:9090`，使用自己的路由器 IP 和控制端口。
2. 控制密钥：与路由器 `secret` 相同。
3. 点击“测试连接”，成功后“保存并连接”。

控制 API 端口和 HTTP / SOCKS 代理端口用途不同，不能将常见代理端口 7890 当作控制端口。原生 App 不需要 Web 面板或 CORS 配置。地址也可使用 HTTPS、IPv6、局域网主机名或反向代理路径前缀；HTTPS 使用系统证书验证，不接受绕过证书检查。

App 修改路由器 Clash 的运行配置；手机流量是否经过代理取决于路由器转发配置。直连模式表示 Clash 直连目标，不等于停止 OpenClash 服务。App 首版不管理路由器服务启停、订阅、SSH、配置文件或手机 VPN。

## 构建

使用 Android Studio 打开此目录，安装 Android SDK 36 与 Build Tools 35.0.0，使用 JDK 17。Gradle 8.13、AGP 8.13.2、Kotlin 2.3.20 与 Compose BOM 2025.10.01 已固定。

Windows：

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

macOS / Linux：

```sh
chmod +x gradlew
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。这是开发调试签名，正式发布需要自己的签名配置。

仓库已配置 GitHub Actions：日常提交自动运行测试并构建 Debug APK；推送与 `versionName` 一致的 `v*` 标签后，自动构建正式签名的通用 APK 并发布 GitHub Release。签名 Secrets、密钥备份要求及发布步骤见 [自动构建与发行版](docs/releases.md)。

无需 Android SDK 也可以运行网络与状态测试：

```powershell
.\gradlew.bat -PcoreOnly :core:test
```

如果本项目目录已包含 `.tooling/android-sdk` 和 `.tooling/gradle-8.13`，可以运行 `scripts/build-local.ps1` 复用它们。此脚本不自动安装全局工具，也不会修改系统 Java 配置。

Gradle wrapper 启用官方发行包 SHA-256 校验。网络访问官方 Gradle 下载地址受限时，可手动下载同一版本的镜像发行包，用官方 SHA-256 `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78` 校验后使用；无需修改项目仓库来源。

## 工程结构

- `core`：与 Android 无关的地址验证、API 客户端、数据模型、会话状态和加密配置编码；JUnit + MockWebServer 测试。
- `app`：Android Keystore 存储、ViewModel、生命周期、Compose 四页界面与 RemoteViews 桌面微件 / 节点面板。
- `docs/superpowers`：本次已批准的设计和实现计划。

target SDK 36 使用 INTERNET 权限访问局域网。未来升级至 target SDK 37 或更高时，应按 Android 官方要求声明并请求 ACCESS_LOCAL_NETWORK；首版不请求该权限。HTTP 用于可信局域网内常见的 Clash 控制接口，密钥在网络上传输时依赖所选 HTTP / HTTPS 协议。

## 验证范围

自动测试使用本地 MockWebServer 和受控 API 替身，不依赖真实路由器密钥。覆盖认证、路径编码、204、错误响应、实时流量、后台取消、会话替换、写操作失败、测速并发、密钥加密与损坏配置。

Compose 界面回归测试使用 Robolectric，实际点击保存并验证自动进入概览、新路由器显示和非法地址错误。配色与更新检查的测试覆盖持久化、数字版本比较、暂无正式发行版、请求失败、取消和可信 APK 附件选择。测试不需要安卓模拟器；首次运行会下载测试依赖。

更新检查使用公开的 `cht37/clash-remote` GitHub Releases API，不发送路由器密钥。仓库尚无正式发行版时显示“暂无正式发行版”，没有 APK 附件时提供发行页面入口。发布时使用 `v0.3.0` 这样的版本标签，并附加通用 APK；每次发布应同时递增 Android versionCode。版本变化见 [更新日志](CHANGELOG.md)。

真机和实际路由器连接需在你的局域网中验收：安装 APK，连接、切换模式、切换 Selector 节点、测速、查看连接、切到后台再返回，以及故意填写错误密钥验证错误提示。

## 官方参考

- [Mihomo 控制 API](https://wiki.metacubex.one/api/)
- [external-controller 与 secret 配置](https://wiki.metacubex.one/config/general/)
- [Android 局域网权限](https://developer.android.com/privacy-and-security/local-network-permission)
- [Gradle 官方校验值](https://gradle.org/release-checksums/)
