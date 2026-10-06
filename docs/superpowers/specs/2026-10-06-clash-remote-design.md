# Clash Remote Android 首版设计

日期：2026-10-06

## 已确认的目标

创建一个原生 Android App，在同一 Wi-Fi / 局域网内，通过 Clash 控制 API 管理路由器上的代理。用户已确认使用 Kotlin + Jetpack Compose，以及概览、代理、连接、设置四个页面。

成功标准：在手机填写路由器控制地址和 secret 后，可以连接到路由器、切换代理模式、选择策略组节点、测试节点延迟、查看实时流量与连接，并关闭指定连接。用户不需要访问 Web 面板。

## 首版范围

- 原生 Kotlin + Jetpack Compose，中文界面，最低 Android 8.0（API 26）。
- 一个可编辑并持久保存的路由器配置：名称、HTTP/HTTPS 控制地址、secret。
- 支持标准 Clash 控制 API 与 Mihomo 的公共接口。
- 前台实时状态；切到后台停止实时流和定时请求，恢复前台重新获取。
- 控制地址由用户填写，首版不做设备扫描。
- 首版不包含订阅编辑、配置文件上传、内核升级、路由器 SSH 操作或手机 VPN 服务。
- App 改变路由器 Clash 的运行状态；手机流量是否经过路由器代理由路由器配置决定。

## 四个页面

### 概览

显示路由器名称、连接状态、内核版本、上下载速率、累计流量和活跃连接数。规则 / 全局 / 直连三个模式通过 PATCH /configs 修改，并在成功后重新读取配置。提供连接或重试按钮。未配置时引导到设置。

### 代理

从 GET /proxies 获取策略组与节点，保留 API 中的成员顺序。显示策略组名称、类型、当前选中节点和节点延迟。仅 Selector 提供首版手动切换；其他组展示当前状态，避免依赖不同内核对自动组固定选择的差异。节点名支持中文、空格、斜线和 emoji，并作为单个 URL 路径段编码。

支持搜索节点、展开策略组、单节点测速，以及对当前组最多四个请求并发的批量测速。测速由路由器执行，默认测试地址 https://www.gstatic.com/generate_204，超时 5000 ms；设置中可修改测试地址。超时显示“不可达”，没有测试记录显示“未测试”。切换后向路由器读取实际状态再更新选中项。

### 连接

显示域名或目的 IP、源 IP、协议、代理链路、匹配规则以及连接上下载字节数。支持按域名、IP 或代理链搜索。关闭单条连接使用 DELETE /connections/{id}；关闭全部连接使用 DELETE /connections，并在 App 内先显示确认对话框。空连接列表是正常状态。

### 设置

编辑路由器名称、控制地址、secret 和测速 URL。提供 secret 显隐、连接测试、保存并连接、清除本地配置。地址默认示例 http://192.168.1.1:9090。连接测试使用当前表单草稿，不覆盖正在使用的配置；保存并连接才建立新会话。测试反馈不暴露 secret。

## 架构和数据流

单 Activity，Compose 页面和 ViewModel 分离。ViewModel 持有 StateFlow UI 状态，并调度 ClashRepository。Repository 组合独立 API 客户端和配置存储；API 层只处理 URL、认证、JSON、HTTP / WebSocket 与错误映射，不依赖 Compose。

配置保存后创建一个会话。连接过程读取 /version、/configs、/proxies、/connections；完整成功后标记在线，部分请求失败显示对应错误而不伪造成功。手动刷新更新这些快照。在线时 /traffic 使用带 Authorization 请求头的 WebSocket 获取每秒速率；/connections 每 2 秒轮询获取连接与累计计数；/proxies 和 /configs 每 10 秒同步一次。

切换配置、断开连接或进入后台时取消旧会话请求、轮询、WebSocket 和测速任务。所有返回结果只允许更新同一会话，防止旧路由器请求覆盖新状态。模式修改和节点切换在请求成功及回读后更新状态，提交期间禁用重复操作。网络错误不无限重试；显示离线和重试入口。

## API 合约

| 操作 | 方法 / 路径 | 数据 |
| --- | --- | --- |
| 版本 | GET /version | version，meta 可选 |
| 配置 | GET /configs | mode |
| 模式修改 | PATCH /configs | {"mode":"rule" / "global" / "direct"} |
| 代理和策略组 | GET /proxies | proxies 映射，name/type/all/now/history |
| 节点切换 | PUT /proxies/{group} | {"name":"节点名称"} |
| 测速 | GET /proxies/{name}/delay | url 和 timeout 查询参数；响应 delay |
| 实时流量 | WebSocket /traffic | up/down，单位 bytes/s |
| 连接快照 | GET /connections | uploadTotal/downloadTotal/connections |
| 关闭连接 | DELETE /connections/{id} | 204 空响应 |
| 关闭全部 | DELETE /connections | 204 空响应 |

secret 非空时发送 Authorization: Bearer <secret>，HTTP 和 WebSocket 一致。204 响应不尝试解析 JSON。JSON 忽略未知字段，给合法缺省字段默认值；关键根结构缺失时报接口不兼容错误。代理组通过 all 字段识别。

## 地址、认证与存储

控制地址支持 HTTP 或 HTTPS、IPv4、IPv6 和主机名，可带反向代理路径前缀。拒绝缺失主机、用户名密码、查询参数、片段以及其他协议。HTTP 允许用于用户确认的局域网控制场景。HTTPS 使用系统证书信任和默认主机名校验。

OkHttp 不跟随重定向，避免将 Bearer secret 发往其他地址。控制请求直连填写的控制器，不使用系统 HTTP 代理。secret 用 Android Keystore 的 AES-GCM 密钥加密后保存到 App 私有存储，每次加密使用独立 IV；名称和地址与密文一起原子保存。解密或配置读取失败时要求重新填写，而不发送空 secret 冒充恢复成功。禁用应用备份，禁止日志记录认证信息。

首版 target SDK 36；依据 Android 官方说明，该 target 通过 INTERNET 权限访问局域网，不额外请求仅 target SDK 37+ 使用的 ACCESS_LOCAL_NETWORK。网络权限与 target 升级规则写入 README。

## 错误与界面行为

- 401 / 403：提示控制密钥不正确或访问被拒绝。
- 连接失败 / 超时：提示检查 Wi-Fi、控制地址、external-controller 监听和路由器防火墙。
- TLS 错误：提示证书验证失败，保留系统校验。
- 非 JSON / 缺失关键字段 / 404：提示控制地址或 API 不兼容。
- 取消操作不作为错误弹窗；后台取消不产生离线通知。
- 独立测速失败只影响该节点；不清空现有代理列表。
- 显示陈旧状态时明确标记离线；断线后速率归零。
- 系统深浅色主题、Material 3、可滚动布局、48dp 触摸目标，横屏和键盘弹出时保持可操作。

## 验证和交付

- Gradle wrapper、完整 Android 工程、中文 README 和路由器配置示例。
- JVM 单元测试：地址验证与路径编码、API 请求方法 / Bearer 头 / 请求体、204、401、响应兼容、流量解析。
- 状态测试：配置会话替换、取消与错误后不会回写旧会话，失败写操作不显示成功。
- 使用 MockWebServer 验证 API 与 WebSocket，不需要用户的路由器地址或真实密钥。
- 构建 debug APK；环境不能构建时明确标注阻塞原因，不宣称安装或真机验证完成。
- README 提供 Android Studio 和命令行构建方式，解释控制 API 端口与代理端口不同，配置 external-controller 和 secret，说明路由器端只开放给 LAN。
- 实际路由器和真机联网验收需要用户在其网络环境执行；本次以测试与构建证据交付。

## 已核对的官方参考

- https://wiki.metacubex.one/api/
- https://github.com/MetaCubeX/Meta-Docs/blob/main/docs/config/general.md
- https://developer.android.com/privacy-and-security/local-network-permission

## 本机环境

目录是尚无提交的空 Git 仓库；没有既有应用约束或 AGENTS.md。已找到 D:/env/java17/bin/java.exe，尚未发现 Android SDK 或 Gradle。构建工具优先安装在项目内的忽略目录中，并保留可重现的 Gradle wrapper。
