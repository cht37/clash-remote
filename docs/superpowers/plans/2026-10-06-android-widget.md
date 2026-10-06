# Android Proxy Control Widget Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans for native execution, or superpowers:subagent-driven-development if the user selects delegation. Implement task-by-task and track steps with checkboxes.

**Goal:** 实现用户已确认原型的 Android 桌面微件，可以快速切换代理模式、搜索并选择节点，以及手动刷新。

**Architecture:** core 新增一次性控制客户端，复用 ClashApi；app 新增私有微件存储、WorkManager 任务、RemoteViews 卡片和 Compose 配置 / 节点选择 Activity。配置代次与实例绑定令牌保护旧请求，桌面控制统一串行执行；主应用配置和已确认控制变更触发微件同步。

**Tech Stack:** JDK 17、Gradle 8.13、AGP 8.13.2、Kotlin 2.3.20、Android minSdk 26 / compileSdk 36 / targetSdk 36、RemoteViews、Compose Material 3、WorkManager 2.11.2、coroutines 1.10.2、JUnit / Robolectric。

**Spec:** `docs/superpowers/specs/2026-10-06-android-widget-design.md`（用户已于 2026-10-06 确认通过）。

## Global Constraints

- 延续最低 Android 8.0、compile / target SDK 36。
- 一个可调整大小的微件，默认目标 4×2；具体桌面网格由 Launcher 决定，布局按 dp 适配。
- 微件只展示控制快照；不进行后台实时流量连接或高频轮询。
- 复用 ProfileStore、OkHttpClashApi、现有 API 数据模型和错误映射。
- 仅 Selector 可手动选择，节点必须属于该组 all；保留 API 成员顺序。
- 只在收到回读结果后更新选中按钮；写操作失败不自动重放。
- Intent、WorkManager Data、缓存、日志和界面不包含 secret。
- 新增微件沿用应用配色与系统深浅色，不增加测速、订阅、服务启停或 VPN 功能。
- 每个操作验证 appWidgetId、绑定令牌和路由器配置代次；旧结果不得覆盖当前状态。
- HTTPS 系统验证、现有认证和节点路径编码保持一致。

## Review Focus

- 用户连续快速点击不同模式，或在多个微件上操作：桌面请求串行执行，重复提交受抑制，所有实例显示远端确认的全局模式；Task 2。
- Worker 被系统停止后重建：不自动重放已尝试的写操作，先回读确认，卡片不永久停在处理中；Task 2。
- 添加被取消、微件 ID 被伪造或微件被删除：不保存孤立绑定，不发送控制请求；Tasks 2、4。
- 绑定组成员含相同前缀、中文 / 斜线 / emoji，或刷新后消失：精确成员验证，搜索保序，失效节点不可提交；Tasks 1、4。
- 保存相同路由器或只修改 secret、清除配置后重新保存：每次保存旋转代次，旧缓存失效，不允许旧任务回写；Task 5。

## Files and Interfaces

创建：

```text
core/src/main/kotlin/com/clashremote/core/WidgetControlClient.kt
core/src/test/kotlin/com/clashremote/core/WidgetControlClientTest.kt
app/src/main/java/com/clashremote/app/widget/
  WidgetStore.kt               绑定、快照、任务令牌
  WidgetRuntime.kt             加密配置读取、串行请求与代次校验
  WidgetWorker.kt              一次性 WorkManager 任务
  WidgetCoordinator.kt         调度、配置失效与主应用事件
  WidgetRenderer.kt            RemoteViews 和 PendingIntent
  ProxyWidgetProvider.kt       系统微件回调
  WidgetConfigActivity.kt      策略组绑定
  WidgetNodeActivity.kt        节点选择入口
  WidgetPickerViewModel.kt     加载、搜索、提交状态
  WidgetPickerScreen.kt        Compose 配置 / 节点面板
app/src/main/res/layout/widget_proxy.xml
app/src/main/res/xml/proxy_widget_info.xml
app/src/main/res/drawable/widget_background.xml
app/src/main/res/drawable/widget_button.xml
app/src/main/res/drawable/widget_node_background.xml
app/src/main/res/drawable/ic_widget_refresh.xml
app/src/main/res/drawable/ic_widget_chevron.xml
app/src/main/res/values-night/widget_colors.xml
app/src/test/java/com/clashremote/app/widget/
  WidgetStoreTest.kt / WidgetRuntimeTest.kt / WidgetRendererTest.kt
  WidgetPickerUiTest.kt
docs/verification-android-widget.md
```

修改：app/build.gradle.kts、AndroidManifest.xml、values/strings.xml、values/colors.xml、values/themes.xml、ProfileStore.kt、AppearanceStore.kt、RemoteViewModel.kt、MainActivity.kt、ui/ClashRemoteApp.kt、README.md。

跨任务约定：

```kotlin
sealed interface WidgetCommand {
    data object Refresh : WidgetCommand
    data class Mode(val mode: String) : WidgetCommand
    data class Select(val group: String, val node: String) : WidgetCommand
}
data class ControlSnapshot(val mode: String, val proxies: Map<String, ProxyInfo>)
class WidgetControlClient(private val factory: (RouterProfile) -> ClashApi = { OkHttpClashApi(it) }) {
    suspend fun execute(profile: RouterProfile, command: WidgetCommand,
                        isCurrent: () -> Boolean = { true }): ControlSnapshot
}
data class WidgetBinding(val widgetId: Int, val group: String, val token: String)
data class WidgetCache(val mode: String? = null, val node: String? = null,
                       val updatedAt: Long = 0, val error: String? = null,
                       val busyUntil: Long = 0)
data class WidgetTicket(val widgetId: Int, val bindingToken: String,
                        val profileRevision: String, val requestToken: String)
```

## Task 1: 一次性控制客户端与远端确认

**Files:** core WidgetControlClient.kt / WidgetControlClientTest.kt。

**Consumes:** RouterProfile、ClashApi、RuntimeConfig、ProxyInfo。
**Produces:** 上述 WidgetCommand、ControlSnapshot、WidgetControlClient.execute。

- [x] 写测试替身，记录请求顺序、关闭次数和写入参数。替身默认 mode=rule，Selector “代理/🚀” 包含 “香港 01” 与 “日本 01”。先执行新测试，确认因缺少新类型失败。

```kotlin
@Test fun modeUsesConfirmedRemoteValue() = runBlocking {
    val api = FakeApi().apply { reportedAfterWrite = "direct" }
    val result = WidgetControlClient { api }.execute(profile, WidgetCommand.Mode("global"))
    assertEquals("global", api.writtenMode)
    assertEquals("direct", result.mode)
    assertEquals(1, api.closed)
}
@Test fun selectionRejectsMissingMemberWithoutWriting() = runBlocking {
    val api = FakeApi()
    assertFailsWith<ClashException> {
        WidgetControlClient { api }.execute(profile, WidgetCommand.Select("代理/🚀", "已删除"))
    }
    assertNull(api.writtenNode)
    assertEquals(1, api.closed)
}
```

测试使用 JUnit assertThrows 或本地 suspend 异常断言助手，不引入新的测试框架。补充非法模式、自动组、写失败、写成功但回读失败、配置代次校验失败、取消与 close() 测试。

- [x] 实现 execute：创建客户端；读取选择操作需要的 proxies；验证 Selector 和精确成员；每次写入前检查 isCurrent；执行写操作；读取 config 与 proxies；finally close。写成功后的读取失败转为可辨认的“操作可能已生效，请刷新确认”，CancellationException 原样传播。

```kotlin
val client = factory(profile.validated())
try {
    when (command) {
        WidgetCommand.Refresh -> Unit
        is WidgetCommand.Mode -> {
            require(command.mode in setOf("rule", "global", "direct"))
            check(isCurrent()) { "配置已变化，请重新操作" }
            client.setMode(command.mode)
        }
        is WidgetCommand.Select -> {
            val group = client.proxies().proxies[command.group]
            if (group?.type != "Selector" || command.node !in group.all.orEmpty())
                throw ClashException("策略组或节点不可用，请刷新后重试")
            check(isCurrent()) { "配置已变化，请重新操作" }
            client.selectProxy(command.group, command.node)
        }
    }
    return ControlSnapshot(client.config().mode, client.proxies().proxies)
} finally { client.close() }
```

- [x] 运行 `gradlew.bat -PcoreOnly :core:test --tests '*WidgetControlClientTest*'`，通过后执行 core 全部测试。仅在 Git 作者已配置时保存任务提交，不虚构作者身份。

## Task 2: 实例存储、后台任务与旧结果保护

**Files:** WidgetStore.kt、WidgetRuntime.kt、WidgetWorker.kt、WidgetCoordinator.kt、WidgetStoreTest.kt、WidgetRuntimeTest.kt；app/build.gradle.kts。

**Consumes:** WidgetControlClient、ProfilePersistence；Task 3 renderer 通过注入 `(Int) -> Unit` 回调接入。
**Produces:** WidgetStore 的 bind(id,group)、binding(id)、cache(id)、remove(id)、invalidate()；WidgetRuntime 的 ticket(id): WidgetTicket? 与 execute(ticket,command): ControlSnapshot?；WidgetCoordinator 的 refreshAll(context)、profileChanged(context)、appearanceChanged(context)。

- [x] app 增加 `implementation("androidx.work:work-runtime-ktx:2.11.2")`、`testImplementation("androidx.work:work-testing:2.11.2")` 与 coroutines-test 1.10.2；WorkManager 版本固定，不跟随浮动版本。
- [x] 使用 Robolectric ApplicationProvider 和内存 ProfilePersistence，测试两个实例绑定不互串、重新绑定旋转 token、删除后旧结果被拒绝、缓存不包含 secret。使用 CompletableDeferred 让旧请求返回晚于配置修改。

```kotlin
@Test fun deletedWidgetCannotReceiveLateResult() = runTest {
    val binding = store.bind(7, "代理")
    val ticket = runtime.ticket(7)!!
    val task = async { runtime.execute(ticket, WidgetCommand.Refresh) }
    api.started.await()
    store.remove(binding.widgetId)
    api.complete.complete(Unit)
    task.await()
    assertNull(store.binding(7))
    assertNull(store.cache(7))
}
```

- [x] Store 使用 app 私有 SharedPreferences 和 org.json，绑定与单个快照原子提交。token 使用 UUID；不序列化 RouterProfile。busy 使用有截止时间的租约，默认不超过 90 秒；渲染时过期租约不禁用按钮。
- [x] Runtime 注入配置存储、revision 读取函数、WidgetStore、客户端、时钟和渲染回调；在一个进程级 Mutex 内执行桌面 API。先验证 ticket 与现有实例，再读加密配置，写前与发布前校验 profileRevision / bindingToken。发布确认快照时把全局模式同步到所有当前绑定实例，并只更新仍属于对应策略组的节点值。
- [x] Coordinator 为每实例使用 `enqueueUniqueWork("proxy-widget-$id", ExistingWorkPolicy.KEEP, request)`；工作 Data 只保存 ticket 与 command 参数。读任务可重新调度，写任务先持久记录 requestToken 已尝试；遇到已尝试 token 或 runAttemptCount>0，只执行 Refresh，禁止重复 PATCH / PUT。没有网络约束，失败及时显示并交给用户重试，不无限等待 LAN 连接。

```kotlin
val request = OneTimeWorkRequestBuilder<WidgetWorker>()
    .setInputData(workDataOf("widgetId" to ticket.widgetId,
        "bindingToken" to ticket.bindingToken, "profileRevision" to ticket.profileRevision,
        "requestToken" to ticket.requestToken, "action" to "refresh"))
    .addTag("proxy-widgets").build()
WorkManager.getInstance(context)
    .enqueueUniqueWork("proxy-widget-${ticket.widgetId}", ExistingWorkPolicy.KEEP, request)
```

- [x] Worker 对 execute 设置 45 秒超时；完成或失败解除其持有的 busy 租约，finally 渲染当前有效实例；返回 success / failure，不返回 retry 重放写入。profileChanged 取消旧 tag 工作、清空缓存并调度当前实例读任务。测试并发、重复提交、已尝试写入恢复和租约恢复。
- [x] 运行 `:app:testDebugUnitTest --tests '*WidgetStoreTest*' --tests '*WidgetRuntimeTest*'`，确认通过。

## Task 3: 原型卡片、PendingIntent 与系统微件注册

**Files:** WidgetRenderer.kt、ProxyWidgetProvider.kt、widget_proxy.xml、proxy_widget_info.xml、widget drawables、values / values-night 资源、AndroidManifest.xml、WidgetRendererTest.kt。

**Consumes:** WidgetStore、ProfileStore revision、AppearanceStore、WidgetCoordinator。
**Produces:** WidgetRenderer.render(context,id) / renderAll(context)；ProxyWidgetProvider 系统入口；明确的 refresh / mode / config / node PendingIntent。

- [x] 用 Robolectric apply RemoteViews 到 FrameLayout，测试未配置时三个模式不高亮、失效组文案、当前节点、错误与同步时刻，比较不同实例 / 操作的 PendingIntent 身份。

```kotlin
@Test fun modePendingIntentsHaveDistinctIdentity() {
    val rule = renderer.modeIntent(context, ticket, "rule")
    val global = renderer.modeIntent(context, ticket, "global")
    val other = renderer.modeIntent(context, otherTicket, "rule")
    assertNotEquals(rule, global)
    assertNotEquals(rule, other)
}
```

- [x] XML 采用垂直 LinearLayout：标题与刷新、路由器与时间、三个同宽 TextView 模式按钮、当前节点行和策略组入口。按钮至少 48dp，主控卡片 minHeight 196dp；名称有限行 / 省略，卡片允许横向 / 纵向调整。目标网格为 4×2，不承诺所有 Launcher 实际占格相同。
- [x] Renderer 用 AppearanceStore 当前 palette 和 night mode 设置可读文字色及选中态，圆角背景以 drawable 配合 RemoteViews 可支持的 tint / background 操作实现；资源不用 Compose 控件。状态点与文案明确最后同步结果；更新时间用明确 HH:mm。
- [x] 每个 PendingIntent 使用明确组件、FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT，以及包含 widgetId / action / bindingToken 的独立 URI。模式 / 刷新走 Provider，节点 / 配置走 getActivity；不把 Activity 启动放在后台 BroadcastReceiver 里。
- [x] metadata 设置 `updatePeriodMillis="0"`、`resizeMode="horizontal|vertical"`、initialLayout、previewLayout、configure Activity；Provider 回调先渲染再入队，onDeleted 清存储并取消该实例工作，onAppWidgetOptionsChanged 重渲染。
- [x] Manifest 注册 Provider 和配置 Activity 的平台入口；节点 Activity exported=false。测试拒绝没有本应用 binding 的自定义控制 Intent。执行 renderer 测试与 `:app:assembleDebug :app:lintDebug`。

## Task 4: 添加配置与节点选择底部面板

**Files:** WidgetConfigActivity.kt、WidgetNodeActivity.kt、WidgetPickerViewModel.kt、WidgetPickerScreen.kt、themes.xml、WidgetPickerUiTest.kt。

**Consumes:** WidgetRuntime、WidgetStore、WidgetCoordinator、ClashRemoteTheme。
**Produces:** 配置页完成 RESULT_OK + appWidgetId；节点页成功后更新微件并 finish；搜索与错误状态。

- [x] 先写 Compose 测试：读取策略组列表只显示 Selector；搜索后点选“日本 01”发送精确名称；提交失败保留面板 / 搜索 / 当前节点；重复点击不发第二次写入；删除绑定后提交被拒绝。

```kotlin
@Test fun failedSelectionKeepsSearchAndCurrentNode() {
    val vm = pickerWithFailingSelection()
    compose.setContent { ClashRemoteTheme { WidgetPickerScreen(vm, onClose = {}) } }
    compose.onNodeWithTag("widget-search").performTextReplacement("日本")
    compose.onNodeWithText("日本 01").performClick()
    compose.onNodeWithText("切换失败，请重试").assertIsDisplayed()
    compose.onNodeWithTag("widget-search").assertTextContains("日本")
    assertEquals("香港 01", vm.state.value.current)
}
```

- [x] Picker state 定义 loaded、busy、error、query、routerName、groupName、choices、current；ViewModel 使用 viewModelScope 与注入 runtime。打开时查询最新控制快照，筛选 Selector / 精确绑定组；搜索使用 contains(query, ignoreCase=true)，保留原顺序。
- [x] 配置 Activity 在 onCreate 先 RESULT_CANCELED，检查 ID 是本 Provider 的实际实例；无配置提供设置入口，有错误提供重试。保存所选组、渲染微件、调度刷新后返回 RESULT_OK。取消时不建立新绑定；重新配置取消保留旧绑定。
- [x] 节点 Activity 使用透明背景主题及 Compose ModalBottomSheet，显示与原型一致的标题、关闭、组 / 路由器、搜索、单选列表和“点击节点即可切换”。提交期间禁用列表，成功回读才关闭；取消浏览不执行写操作。状态恢复以 widgetId / ticket 重读，不持久保存 secret。
- [x] 非 Selector、空成员和组被删除时显示重新配置入口；切换客户端复用 Task 1 验证。面板和配置页跟随用户配色；测试中文 / emoji 搜索与取消添加。执行 picker UI 测试。

## Task 5: 主应用集成、完整验证与交付

**Files:** ProfileStore.kt、AppearanceStore.kt、RemoteViewModel.kt、MainActivity.kt、ClashRemoteApp.kt、README.md、docs/verification-android-widget.md；补充相关回归测试。

**Consumes:** WidgetCoordinator 的 profileChanged / appearanceChanged / refreshAll。
**Produces:** 主应用配置 / 控制变化与微件一致；设置定位入口；可构建 APK 与准确验证记录。

- [x] ProfileStore 增加 revision()，读取 `router` preferences 中 widget_revision，旧配置默认 legacy。save 原子保存 profile 和新 UUID；clear 原子移除 profile 并保存新 UUID，提交成功后通知 profileChanged。ProfileStore 不在读取时生成新 revision，以免多客户端读取互相失效。

```kotlin
val committed = preferences.edit().putString("profile", codec.encode(profile))
    .putString("widget_revision", UUID.randomUUID().toString()).commit()
if (!committed) throw ClashException("配置保存失败，请重试")
WidgetCoordinator.profileChanged(context)
```

- [x] AppearanceStore 保存成功后仅重渲染卡片。RemoteViewModel 观察 ONLINE 且 !busy 的已确认 mode / proxies，使用 distinctUntilChanged 限制重复通知；模式 / 节点实际变化触发微件刷新，不让流量和连接轮询每次触发。
- [x] MainActivity 读取明确的设置入口参数，ClashRemoteApp 接收 initialPage（默认 0），保持现有首次无配置和保存后自动跳转逻辑；添加回归测试，不改变普通启动行为。
- [x] README 增加添加步骤、选择策略组、多实例共享全局模式、手动刷新和失败反馈；说明状态是同步快照及真机验证限制。核对 manifest 合并结果是否只增加所需 WorkManager 平台声明。
- [x] 复用原工程 .tooling 与缓存作为只读工具来源，把 local.properties 和任务构建缓存放在当前可写工作区。保持 JDK 17，不安装全局工具，不修改全局 Git 身份。执行：

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
```

- [x] 阅读所有测试输出、lint 报告及 APK metadata，记录测试总数、APK 路径和实际验证范围。独立代码审查检查任务恢复 / 代次、PendingIntent、Selector 成员验证和敏感信息；审查发现修正后只重跑受影响检查。
- [x] 更新计划勾选与验证文档，提供 APK / README 文件链接。Git 作者仍未配置时保留变更并报告，不冒用已有提交的作者、不推送或发布发行版。

## Plan self-review

设计的配置、卡片、操作、节点搜索、缓存、取消 / 进程恢复、配色、安全与验证分别落在 Tasks 1–5。公共类型与方法在接口部分或拥有该组件的任务中定义。Review Focus 的五类条件均分配了所属任务与测试；没有待定产品行为。

## Execution handoff

推荐 **Native**：由当前会话逐项实现，完成后做一次独立整体验证与代码审查。五项任务依赖同一配置代次和状态接口，集中实现方便及时核对接口。

用户于 2026-10-06 回复“继续”，采用推荐的 Native 执行方式。计划已批准，当前会话持续执行全部任务。
