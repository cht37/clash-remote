# Clash Remote Android Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking. Native execution is recommended; the user must approve this plan and select execution before product code is written.

**Goal:** 交付可构建、可安装、通过局域网控制路由器 Clash 的原生 Android 首版。

**Architecture:** `core` 是独立 JVM 模块，包含可测试的地址、数据模型、API 与会话状态机；`app` 是 Android 模块，包含 Keystore 配置存储、ViewModel 与 Compose 四页界面。API 通过 OkHttp / Kotlin serialization 工作，StateFlow 驱动 UI；配置替换和生命周期变化取消旧会话。

**Tech Stack:** Java 17、Gradle 8.13、AGP 8.13.2、Kotlin 与 Compose compiler plugin 2.3.20、Compose Material 3、OkHttp 4.12.0、kotlinx.serialization 1.9.0、kotlinx.coroutines 1.10.2、JUnit 4、MockWebServer。

**Spec:** `docs/superpowers/specs/2026-10-06-clash-remote-design.md`

## Global Constraints

- Kotlin + Jetpack Compose，中文界面，最低 Android 8.0（API 26）。
- 同一 Wi-Fi / 局域网，一个可编辑并持久保存的路由器配置。
- 首版 target SDK 36；声明 INTERNET，不请求 ACCESS_LOCAL_NETWORK。
- 四个页面：概览、代理、连接、设置。
- 支持标准 Clash 控制 API 与 Mihomo 的公共接口；仅 Selector 提供手动切换。
- secret 使用 Android Keystore AES-GCM，独立 IV，禁用备份和认证日志。
- HTTPS 保留系统证书信任和主机名校验，禁用 HTTP 重定向。
- 后台停止轮询 / WebSocket / 测速；旧会话结果不能回写新会话。
- 模式和节点修改成功并回读后更新状态，204 空响应不解析 JSON。
- 默认测速地址 https://www.gstatic.com/generate_204，5000 ms，最多四个并发请求。

## Review Focus

- 中文、斜线、emoji 节点名及控制地址前缀：正确生成单段路径且不丢前缀；Task 1 测试。
- 401、204、错误 JSON、缺失关键字段、额外字段：认证错误和兼容性错误明确，合法响应可读取；Task 1 测试。
- 切换路由器 / 后台时延迟响应：旧请求与旧测速不会覆盖新状态；Task 2 测试。
- 模式修改 / 选节点失败：保留远端最后确认状态并允许重试；Task 2 测试。
- secret 解密失败 / 保存失败：提供重新配置入口，禁止将失败当作成功或悄悄使用空密钥；Task 3 检查及测试。

## File Structure

```text
settings.gradle.kts / build.gradle.kts / gradle.properties / gradlew / gradlew.bat
gradle/wrapper/gradle-wrapper.jar / gradle-wrapper.properties
core/build.gradle.kts
core/src/main/kotlin/com/clashremote/core/
  RouterProfile.kt        地址和配置验证
  Models.kt               代理 / 连接 / 流量 / UI 状态模型
  ClashApi.kt             API 接口和 OkHttp 实现
  RemoteController.kt     会话、轮询、流量、操作与错误状态
core/src/test/kotlin/com/clashremote/core/
  ClashApiTest.kt / RemoteControllerTest.kt
app/build.gradle.kts / app/src/main/AndroidManifest.xml
app/src/main/java/com/clashremote/app/
  MainActivity.kt / RemoteViewModel.kt
  storage/ProfileStore.kt
  ui/ClashRemoteApp.kt / Theme.kt / Components.kt
  ui/OverviewScreen.kt / ProxiesScreen.kt / ConnectionsScreen.kt / SettingsScreen.kt
app/src/main/res/values/strings.xml / themes.xml
app/src/main/res/drawable/ic_launcher.xml
README.md / .gitignore / scripts/build-local.ps1
```

### Task 1: 可验证的控制 API

**Files:** 创建根 Gradle 工程、wrapper、core 模块、RouterProfile.kt、Models.kt、ClashApi.kt、ClashApiTest.kt。

**Interfaces:**
- `RouterProfile(name: String, endpoint: String, secret: String, testUrl: String)`，`validated(): RouterProfile`。
- `ClashApi`: `suspend version(): String`、`config(): RuntimeConfig`、`proxies(): ProxySnapshot`、`connections(): ConnectionSnapshot`、`setMode(mode: String)`、`selectProxy(group: String, name: String)`、`delay(name: String, url: String): Int`、`closeConnection(id: String?)`，`traffic(): Flow<Traffic>`，`close()`。
- `OkHttpClashApi(profile: RouterProfile)` 实现接口，错误以 `ClashException` 提供中文消息。

- [x] **Step 1: 配置构建工具与 core 单元测试依赖**

```kotlin
// core/build.gradle.kts
plugins { kotlin("jvm"); kotlin("plugin.serialization") }
kotlin { jvmToolchain(17) }
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
```

- [x] **Step 2: 写失败测试并执行 `./gradlew :core:test`**

```kotlin
@Test fun selectionEncodesNameAndKeepsPrefix() = runBlocking {
    server.enqueue(MockResponse().setResponseCode(204))
    val api = OkHttpClashApi(RouterProfile("路由器", server.url("/api/").toString(), "test-secret", "https://example.com/204"))
    api.selectProxy("香港/自动 🚀", "节点 A")
    val request = server.takeRequest()
    assertEquals("PUT", request.method)
    assertEquals("Bearer test-secret", request.getHeader("Authorization"))
    assertEquals("/api/proxies/%E9%A6%99%E6%B8%AF%2F%E8%87%AA%E5%8A%A8%20%F0%9F%9A%80", request.path)
    assertEquals("节点 A", Json.parseToJsonElement(request.body.readUtf8()).jsonObject["name"]!!.jsonPrimitive.content)
}
```

补充 401、重定向不跟随、204、未知 JSON 字段、缺失 proxies 根字段、地址拒绝 credentials / query / fragment、IPv6 和 WebSocket 流量测试。首次失败应是尚不存在的核心类型；不要把下载失败记作测试失败。

- [x] **Step 3: 实现模型、验证和 HTTP / WebSocket 客户端**

```kotlin
val url = baseUrl.newBuilder().addPathSegment("proxies").addPathSegment(group).build()
val body = buildJsonObject { put("name", name) }.toString().toRequestBody("application/json".toMediaType())
val request = Request.Builder().url(url).put(body).apply {
    if (profile.secret.isNotEmpty()) header("Authorization", "Bearer ${profile.secret}")
}.build()
```

异步请求使用 suspendCancellableCoroutine，在取消时 call.cancel()。客户端设置 Proxy.NO_PROXY、followRedirects(false)、followSslRedirects(false)，保持系统 TLS。WebSocket 通过 callbackFlow 与 awaitClose 释放；正常运行的客户端支持 close() 取消在途请求。

- [x] **Step 4: 执行 `./gradlew :core:test` 并确认断言通过；保存 Task 1 Git 提交。**

### Task 2: 会话与实时控制状态

**Files:** RemoteController.kt、RemoteControllerTest.kt。

**Interfaces:** 消费 Task 1 ClashApi；`RemoteController(scope: CoroutineScope, factory: (RouterProfile) -> ClashApi)` 提供 `state: StateFlow<RemoteState>`、`connect(profile)`、`setForeground(Boolean)`、`disconnect()`、`refresh()`、`changeMode(mode)`、`select(group,name)`、`testNode(name)`、`testGroup(names)`、`closeConnection(id)`、`closeAllConnections()`。

- [x] **Step 1: 写失败测试并执行 `./gradlew :core:test --tests '*RemoteControllerTest*'`。**

```kotlin
@Test fun switchingProfilesRejectsPreviousSessionResults() = runTest {
    val controller = RemoteController(backgroundScope) { profile -> fakeApis.getValue(profile.name) }
    controller.connect(profileA)
    runCurrent()
    controller.connect(profileB)
    advanceTimeBy(100)
    runCurrent()
    assertEquals("B", controller.state.value.profile!!.name)
    assertEquals("B-version", controller.state.value.version)
}
```

假 API 用 CompletableDeferred 延迟返回 A，用 B 立即返回；补充失败 mode / select 保持旧状态、后台取消 stream 与 poll、测速并发不超过 4 的测试。

- [x] **Step 2: 实现会话 Job 与编号守卫。**

```kotlin
private fun publish(session: Long, update: (RemoteState) -> RemoteState) {
    if (session == generation) stateMutable.update(update)
}
```

启动同一会话中的初始快照、traffic collector、2 秒 connections 和 10 秒 config / proxies 循环；前台恢复重连；背景保留快照但标记暂停。故障取消整个会话、速率归零，提供重试。所有写操作和测速是会话 Job 的子任务；使用 Semaphore(4) 限制批量测速。CancellationException 必须重新抛出。

- [x] **Step 3: 执行 core 全部测试，通过后保存 Task 2 Git 提交。**

### Task 3: Android 存储与四页操作界面

**Files:** app 模块所有列出的文件、AndroidManifest.xml、资源文件。

**Interfaces:** `ProfileStore(context)` 的 `load(): RouterProfile?`、`save(profile)`、`clear()`；RemoteViewModel 持有 controller，将持久配置和草稿测试状态暴露给 UI。

- [x] **Step 1: 配置 Android app（compileSdk / targetSdk 36，minSdk 26，Java 17），启用 Compose；连接 core 模块。**

```kotlin
android {
    namespace = "com.clashremote.app"
    compileSdk = 36
    defaultConfig { applicationId = "com.clashremote.app"; minSdk = 26; targetSdk = 36; versionCode = 1; versionName = "0.1.0" }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
```

- [x] **Step 2: 实现 AES-GCM 存储，原子保存配置并处理读取异常。**

```kotlin
val cipher = Cipher.getInstance("AES/GCM/NoPadding")
cipher.init(Cipher.ENCRYPT_MODE, key)
val ciphertext = cipher.doFinal(profile.secret.toByteArray(Charsets.UTF_8))
val saved = preferences.edit().putString("profile", encodeProfile(profile, cipher.iv, ciphertext)).commit()
check(saved) { "配置保存失败，请重试" }
```

Keystore key 的 KeyGenParameterSpec 使用 AES、GCM、ENCRYPT / DECRYPT，禁止备份。所有配置仅存为一个 JSON 字符串。load() 在损坏 / 解密失败时返回可见错误，不建立空 secret 会话；clear() 删除持久内容，ViewModel 同时断开。草稿测试创建独立临时 API，finally close()，与活动 controller 分离。

- [x] **Step 3: 实现 MainActivity 生命周期、ViewModel 和主题 / 公用组件。**

```kotlin
DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_START) viewModel.setForeground(true)
        if (event == Lifecycle.Event.ON_STOP) viewModel.setForeground(false)
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
}
```

使用 collectAsStateWithLifecycle，Scaffold 与 NavigationBar，明确在线 / 离线 / 暂停 / 连接中；错误通过可关闭的提示卡呈现。界面采用清晰的仪表信息、柔和绿色强调色和 Material 3 系统深浅色。

- [x] **Step 4: 实现四页。**

概览提供版本、速率、累计、模式选择和刷新；代理页 LazyColumn、搜索、展开组、选节点、测速与批量测速；连接页 LazyColumn、搜索、行关闭与全部关闭确认；设置页密码显隐、地址 / 测速 URL、测试、保存、清除与 LAN 接入说明。在线且非 busy 时才允许远端写操作。保存失败保留草稿；测试结果明确属于当前草稿。

- [x] **Step 5: 执行 `./gradlew :app:assembleDebug :app:lintDebug`，修复编译 / lint 错误；保存 Task 3 Git 提交。**

### Task 4: 构建交付和最终检查

**Files:** README.md、.gitignore、scripts/build-local.ps1；修正前述实现缺陷。

- [x] **Step 1: README 写入安装、局域网接入、构建与限制。**

```yaml
external-controller: 0.0.0.0:9090
secret: "替换为自己的强密钥"
```

说明端口 9090 是控制 API，7890 等是代理端口；防火墙仅向 LAN 开放控制端口，secret 与 App 设置一致。App 不管理路由器电源或防火墙；切换运行模式不等于启动 / 停止路由器服务。给出 `gradlew.bat :core:test :app:assembleDebug :app:lintDebug` 与 APK 路径。

- [x] **Step 2: 若需要，本地工具下载到 .tooling，校验官方 checksum；使用 sdkmanager 安装 platforms;android-36 与 build-tools;35.0.0；Gradle 缓存放在忽略目录。**
- [x] **Step 3: 执行完整 `:core:test :app:assembleDebug :app:lintDebug`；检查输出、测试数量和 APK 文件实际存在。**
- [x] **Step 4: 完成一次独立代码审查，重点看认证泄漏、取消任务、并发更新、Compose 列表 key 和空状态；发现问题后只重跑相关验证。**
- [x] **Step 5: 保存最终提交，提供 APK 与 README 文件链接，准确报告尚未进行真机 / 用户路由器验证。**
