# Clash Remote

[源代码](https://github.com/cht37/clash-remote) · [MIT License](LICENSE)

通过原生 Android 界面管理路由器上的 Clash / Mihomo。在同一 Wi-Fi 或局域网中，填写控制 API 地址和密钥，即可使用。最低 Android 8.0。

## 首版功能

- 概览：内核版本、在线状态、实时上下载速率、流量曲线、累计流量、规则 / 全局 / 直连模式。
- 代理：策略组、当前节点、搜索、Selector 手动切换、节点与整组测速（最多 4 个并发）。
- 连接：域名、来源 IP、协议、代理链、规则、流量；搜索、关闭单个或全部连接。
- 设置：路由器地址、secret、测速地址、独立连接测试、保存并连接、清除本地配置。

密钥使用 Android Keystore AES-GCM 加密保存，应用不备份配置。进入后台停止轮询和流量连接，回到前台重新连接。首版保存一个路由器配置。

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
.\gradlew.bat :core:test :app:assembleDebug :app:lintDebug
```

macOS / Linux：

```sh
chmod +x gradlew
./gradlew :core:test :app:assembleDebug :app:lintDebug
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
- `app`：Android Keystore 存储、ViewModel、生命周期以及 Compose 四页界面。
- `docs/superpowers`：本次已批准的设计和实现计划。

target SDK 36 使用 INTERNET 权限访问局域网。未来升级至 target SDK 37 或更高时，应按 Android 官方要求声明并请求 ACCESS_LOCAL_NETWORK；首版不请求该权限。HTTP 用于可信局域网内常见的 Clash 控制接口，密钥在网络上传输时依赖所选 HTTP / HTTPS 协议。

## 验证范围

自动测试使用本地 MockWebServer 和受控 API 替身，不依赖真实路由器密钥。覆盖认证、路径编码、204、错误响应、实时流量、后台取消、会话替换、写操作失败、测速并发、密钥加密与损坏配置。

真机和实际路由器连接需在你的局域网中验收：安装 APK，连接、切换模式、切换 Selector 节点、测速、查看连接、切到后台再返回，以及故意填写错误密钥验证错误提示。

## 官方参考

- [Mihomo 控制 API](https://wiki.metacubex.one/api/)
- [external-controller 与 secret 配置](https://wiki.metacubex.one/config/general/)
- [Android 局域网权限](https://developer.android.com/privacy-and-security/local-network-permission)
- [Gradle 官方校验值](https://gradle.org/release-checksums/)
